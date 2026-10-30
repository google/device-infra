/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Shared helper for loading host configurations and evaluating per-host writability in batch RPCs.
 */
@Singleton
public final class BatchHostConfigHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Maximum number of distinct host names accepted by one batch request. */
  public static final int MAX_BATCH_HOSTS = 200;

  /** Maximum number of lab config RPCs or permission checks in flight at once. */
  static final int MAX_CONCURRENT_CALLS = 16;

  static final String REASON_CONFIG_NOT_SUPPORTED = "Configuration is not supported for this host";
  static final String REASON_INVALID_HOST_NAME = "Host name must not be blank";
  static final String REASON_HOST_CONFIG_LOAD_FAILED = "Failed to load host configuration";
  static final String REASON_CONFIG_PUSHER = "Managed by Config Pusher";
  static final String REASON_NO_PERMISSION = "No permission";

  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ConfigPusherHelper configPusherHelper;
  private final IamPermissionChecker iamPermissionChecker;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchHostConfigHelper(
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ConfigPusherHelper configPusherHelper,
      IamPermissionChecker iamPermissionChecker,
      Environment environment,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.configPusherHelper = configPusherHelper;
    this.iamPermissionChecker = iamPermissionChecker;
    this.environment = environment;
    this.executor = executor;
  }

  /** Loaded host configuration data and writability evaluation for a single host. */
  public record HostEvaluation(
      String hostName,
      Optional<LabConfig> existingLabConfig,
      boolean writable,
      String readOnlyReason,
      UpdateError.Code errorCode) {

    static HostEvaluation createWritable(String hostName, Optional<LabConfig> existingLabConfig) {
      return new HostEvaluation(
          hostName, existingLabConfig, true, "", UpdateError.Code.CODE_UNSPECIFIED);
    }

    static HostEvaluation createReadOnly(
        String hostName,
        Optional<LabConfig> existingLabConfig,
        String reason,
        UpdateError.Code errorCode) {
      return new HostEvaluation(hostName, existingLabConfig, false, reason, errorCode);
    }
  }

  private record LoadedHost(
      String hostName, Optional<HostEvaluation> readOnly, Optional<LabConfig> existingLabConfig) {

    static LoadedHost fromReadOnly(HostEvaluation evaluation) {
      return new LoadedHost(
          evaluation.hostName(), Optional.of(evaluation), evaluation.existingLabConfig());
    }

    static LoadedHost candidate(String hostName, Optional<LabConfig> existingLabConfig) {
      return new LoadedHost(hostName, Optional.empty(), existingLabConfig);
    }
  }

  /** Returns trimmed {@code hostNames} without duplicates, preserving first-occurrence order. */
  public static ImmutableList<String> distinctHostNames(List<String> hostNames) {
    return hostNames.stream().map(String::trim).distinct().collect(toImmutableList());
  }

  /**
   * Returns the reason {@code hostNames} cannot be processed in one batch request, or empty when it
   * holds at most {@link #MAX_BATCH_HOSTS} distinct hosts.
   */
  public static Optional<String> checkHostCount(List<String> hostNames) {
    long count = hostNames.stream().map(String::trim).distinct().count();
    return count > MAX_BATCH_HOSTS
        ? Optional.of(
            String.format(
                "A batch request accepts at most %d hosts, got %d.", MAX_BATCH_HOSTS, count))
        : Optional.empty();
  }

  /**
   * Extracts the effective host-level default Wi-Fi from {@code labConfigOpt}, preferring {@code
   * LabConfig.wifi_config} and falling back to {@code default_device_config.default_wifi}.
   */
  // BasicDeviceConfig.default_wifi is read as a fallback beneath LabConfig.wifi_config.
  @SuppressWarnings("deprecation")
  public static DeviceWifi extractCurrentHostWifi(Optional<LabConfig> labConfigOpt) {
    if (labConfigOpt.isEmpty()) {
      return DeviceWifi.getDefaultInstance();
    }
    LabConfig labConfig = labConfigOpt.get();
    if (!labConfig.getWifiConfig().getSsid().isEmpty()) {
      return toDeviceWifi(labConfig.getWifiConfig());
    }
    if (labConfig.getDefaultDeviceConfig().hasDefaultWifi()
        && !labConfig.getDefaultDeviceConfig().getDefaultWifi().getSsid().isEmpty()) {
      return toDeviceWifi(labConfig.getDefaultDeviceConfig().getDefaultWifi());
    }
    return DeviceWifi.getDefaultInstance();
  }

  private static DeviceWifi toDeviceWifi(WifiConfig wifi) {
    return DeviceWifi.newBuilder()
        .setSsid(wifi.getSsid())
        .setPsk(wifi.getPsk())
        .setScanSsid(wifi.getScanSsid())
        .build();
  }

  /** Evaluates all distinct {@code hostNames} in request order. */
  public ListenableFuture<ImmutableList<HostEvaluation>> evaluateHosts(
      List<String> hostNames, UniverseScope universe, Optional<String> username) {
    ImmutableList<String> hosts = distinctHostNames(hostNames);
    if (hosts.isEmpty()) {
      return immediateFuture(ImmutableList.of());
    }
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(
          hosts.stream()
              .map(
                  host ->
                      HostEvaluation.createReadOnly(
                          host,
                          Optional.empty(),
                          REASON_CONFIG_NOT_SUPPORTED,
                          UpdateError.Code.VALIDATION_ERROR))
              .collect(toImmutableList()));
    }

    return FluentFuture.from(inWaves(hosts, host -> loadHost(host, universe)))
        .transformAsync(
            loaded -> inWaves(loaded, host -> checkPermission(host, universe, username)), executor);
  }

  private ListenableFuture<LoadedHost> loadHost(String hostName, UniverseScope universe) {
    if (hostName.isEmpty()) {
      return immediateFuture(
          LoadedHost.fromReadOnly(
              HostEvaluation.createReadOnly(
                  hostName,
                  Optional.empty(),
                  REASON_INVALID_HOST_NAME,
                  UpdateError.Code.VALIDATION_ERROR)));
    }
    return FluentFuture.from(configurationProvider.getLabConfig(hostName, universe))
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log("Failed to fetch LabConfig for %s", hostName);
              return ConfigResult.unavailable();
            },
            executor)
        .transform(result -> resolveLoadedHost(hostName, result), executor);
  }

  private LoadedHost resolveLoadedHost(String hostName, ConfigResult<LabConfig> labConfigResult) {
    if (!labConfigResult.isAvailable()) {
      return LoadedHost.fromReadOnly(
          HostEvaluation.createReadOnly(
              hostName,
              Optional.empty(),
              REASON_HOST_CONFIG_LOAD_FAILED,
              UpdateError.Code.UNKNOWN));
    }
    Optional<LabConfig> labConfig = labConfigResult.config();
    if (labConfig.isPresent()
        && configPusherHelper.isSectionRestricted(
            HostConfigSection.DEVICE_CONFIG, labConfig.get())) {
      return LoadedHost.fromReadOnly(
          HostEvaluation.createReadOnly(
              hostName, labConfig, REASON_CONFIG_PUSHER, UpdateError.Code.PERMISSION_DENIED));
    }
    return LoadedHost.candidate(hostName, labConfig);
  }

  private ListenableFuture<HostEvaluation> checkPermission(
      LoadedHost host, UniverseScope universe, Optional<String> username) {
    if (host.readOnly().isPresent()) {
      return immediateFuture(host.readOnly().get());
    }
    HostEvaluation writable =
        HostEvaluation.createWritable(host.hostName(), host.existingLabConfig());
    if (!environment.isGoogleInternal()) {
      return immediateFuture(writable);
    }
    HostEvaluation noPermission =
        HostEvaluation.createReadOnly(
            host.hostName(),
            host.existingLabConfig(),
            REASON_NO_PERMISSION,
            UpdateError.Code.PERMISSION_DENIED);
    if (username.isEmpty()) {
      return immediateFuture(noPermission);
    }
    return FluentFuture.from(iamPermissionChecker.canConfigHost(host.hostName(), universe))
        .transform(hasPermission -> hasPermission ? writable : noPermission, executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to check IAM permission for host %s", host.hostName());
              return noPermission;
            },
            executor);
  }

  /**
   * Applies {@code call} to every item in waves of at most {@link #MAX_CONCURRENT_CALLS},
   * preserving input order.
   */
  static <T, R> ListenableFuture<ImmutableList<R>> inWaves(
      List<T> items, Function<T, ListenableFuture<R>> call) {
    ListenableFuture<ImmutableList<R>> results = immediateFuture(ImmutableList.of());
    for (List<T> wave : Lists.partition(items, MAX_CONCURRENT_CALLS)) {
      results =
          Futures.transformAsync(
              results,
              done ->
                  Futures.transform(
                      Futures.allAsList(wave.stream().map(call).collect(toImmutableList())),
                      waveResults ->
                          ImmutableList.<R>builder().addAll(done).addAll(waveResults).build(),
                      directExecutor()),
              directExecutor());
    }
    return results;
  }
}
