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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DeviceUuidMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Shared helper for loading device data and evaluating per-device writability in batch RPCs.
 *
 * <p>Device data for a whole batch is loaded with one lab info query, one batch device config read
 * and one lab config read per distinct host. Calls that may block a thread of the shared executor
 * (lab config reads and permission checks) run in waves of at most {@link #MAX_CONCURRENT_CALLS}.
 */
@Singleton
public final class BatchDeviceConfigHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Maximum number of distinct device IDs accepted by one batch request. */
  public static final int MAX_BATCH_DEVICE_IDS = 1000;

  /** Maximum number of lab config reads or permission checks in flight at once. */
  static final int MAX_CONCURRENT_CALLS = 16;

  static final String REASON_CONFIG_NOT_SUPPORTED =
      "Configuration is not supported for this device";
  static final String REASON_DEVICE_NOT_FOUND = "Device not found";
  static final String REASON_DEVICE_INFO_LOAD_FAILED = "Failed to load device information";
  static final String REASON_HOST_CONFIG_LOAD_FAILED = "Failed to load host configuration";
  static final String REASON_DEVICE_CONFIG_LOAD_FAILED = "Failed to load device configuration";
  static final String REASON_HOST_MANAGED = "Managed by host shared config";
  static final String REASON_CONFIG_PUSHER = "Managed by Config Pusher";
  static final String REASON_NO_PERMISSION = "No permission";

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ConfigPusherHelper configPusherHelper;
  private final IamPermissionChecker iamPermissionChecker;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchDeviceConfigHelper(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ConfigPusherHelper configPusherHelper,
      IamPermissionChecker iamPermissionChecker,
      Environment environment,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.configPusherHelper = configPusherHelper;
    this.iamPermissionChecker = iamPermissionChecker;
    this.environment = environment;
    this.executor = executor;
  }

  /** Loaded device configuration data and writability evaluation for a single device. */
  @AutoValue
  public abstract static class DeviceEvaluation {
    public abstract String deviceId();

    /** The configuration currently in effect for the device. */
    public abstract DeviceConfig effectiveDeviceConfig();

    /**
     * The device's own DeviceConfig as stored by the config service. Empty for a writable device
     * means the config service has no DeviceConfig for it, never that the read failed.
     */
    public abstract Optional<DeviceConfig> rawIndividualConfig();

    public abstract boolean writable();

    public abstract String readOnlyReason();

    public abstract UpdateError.Code errorCode();

    static DeviceEvaluation createWritable(
        String deviceId, Optional<DeviceConfig> rawIndividualConfig) {
      return new AutoValue_BatchDeviceConfigHelper_DeviceEvaluation(
          deviceId,
          rawIndividualConfig.orElse(DeviceConfig.getDefaultInstance()),
          rawIndividualConfig,
          true,
          "",
          UpdateError.Code.CODE_UNSPECIFIED);
    }

    static DeviceEvaluation createReadOnly(
        String deviceId, DeviceConfig effectiveDeviceConfig, String reason, UpdateError.Code code) {
      return new AutoValue_BatchDeviceConfigHelper_DeviceEvaluation(
          deviceId, effectiveDeviceConfig, Optional.empty(), false, reason, code);
    }
  }

  /** Configuration state of one device before the permission check. */
  private record LoadedDevice(
      String deviceId,
      Optional<DeviceEvaluation> readOnly,
      Optional<DeviceConfig> rawIndividualConfig) {

    static LoadedDevice readOnly(DeviceEvaluation evaluation) {
      return new LoadedDevice(evaluation.deviceId(), Optional.of(evaluation), Optional.empty());
    }

    static LoadedDevice candidate(String deviceId, Optional<DeviceConfig> rawIndividualConfig) {
      return new LoadedDevice(deviceId, Optional.empty(), rawIndividualConfig);
    }
  }

  /** Returns {@code deviceIds} without duplicates, in first-occurrence order. */
  public static ImmutableList<String> distinctDeviceIds(List<String> deviceIds) {
    return deviceIds.stream().distinct().collect(toImmutableList());
  }

  /**
   * Returns the reason {@code deviceIds} cannot be processed in one batch request, or empty when it
   * holds at most {@link #MAX_BATCH_DEVICE_IDS} distinct IDs.
   */
  public static Optional<String> checkDeviceIdCount(List<String> deviceIds) {
    long count = deviceIds.stream().distinct().count();
    return count > MAX_BATCH_DEVICE_IDS
        ? Optional.of(
            String.format(
                "A batch request accepts at most %d devices, got %d.", MAX_BATCH_DEVICE_IDS, count))
        : Optional.empty();
  }

  /**
   * Evaluates all distinct {@code deviceIds} in request order.
   *
   * <p>Callers validate that {@code deviceIds} holds at most {@link #MAX_BATCH_DEVICE_IDS} distinct
   * IDs.
   */
  public ListenableFuture<ImmutableList<DeviceEvaluation>> evaluateDevices(
      List<String> deviceIds, UniverseScope universe, Optional<String> username) {
    ImmutableList<String> ids = distinctDeviceIds(deviceIds);
    if (ids.isEmpty()) {
      return immediateFuture(ImmutableList.of());
    }
    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(
          ids.stream()
              .map(
                  id ->
                      DeviceEvaluation.createReadOnly(
                          id,
                          DeviceConfig.getDefaultInstance(),
                          REASON_CONFIG_NOT_SUPPORTED,
                          UpdateError.Code.VALIDATION_ERROR))
              .collect(toImmutableList()));
    }

    ListenableFuture<Optional<ImmutableMap<String, String>>> hostNamesFuture =
        loadHostNames(ids, universe);
    ListenableFuture<Optional<ImmutableMap<String, DeviceConfig>>> deviceConfigsFuture =
        loadDeviceConfigs(ids, universe);

    return FluentFuture.from(
            Futures.whenAllSucceed(hostNamesFuture, deviceConfigsFuture)
                .callAsync(
                    () ->
                        loadDevices(
                            ids,
                            Futures.getDone(hostNamesFuture),
                            Futures.getDone(deviceConfigsFuture),
                            universe),
                    executor))
        .transformAsync(loaded -> checkPermissions(loaded, universe, username), executor);
  }

  /** Maps each found device ID to its host name, or returns empty when the lookup fails. */
  private ListenableFuture<Optional<ImmutableMap<String, String>>> loadHostNames(
      ImmutableList<String> ids, UniverseScope universe) {
    GetLabInfoRequest request =
        GetLabInfoRequest.newBuilder()
            .setLabQuery(
                LabQuery.newBuilder()
                    .setFilter(
                        LabQuery.Filter.newBuilder()
                            .setDeviceFilter(
                                DeviceFilter.newBuilder()
                                    .addDeviceMatchCondition(
                                        DeviceMatchCondition.newBuilder()
                                            .setDeviceUuidMatchCondition(
                                                DeviceUuidMatchCondition.newBuilder()
                                                    .setCondition(
                                                        StringMatchCondition.newBuilder()
                                                            .setInclude(
                                                                StringMatchCondition.Include
                                                                    .newBuilder()
                                                                    .addAllExpected(ids)))))))
                    .setDeviceViewRequest(LabQuery.DeviceViewRequest.getDefaultInstance()))
            .build();
    return FluentFuture.from(labInfoProvider.getLabInfoAsync(request, universe))
        .transform(response -> Optional.of(extractHostNames(response)), executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to load device information for %d devices in batch evaluation",
                  ids.size());
              return Optional.empty();
            },
            executor);
  }

  private static ImmutableMap<String, String> extractHostNames(GetLabInfoResponse response) {
    return response
        .getLabQueryResult()
        .getDeviceView()
        .getGroupedDevices()
        .getDeviceList()
        .getDeviceInfoList()
        .stream()
        .filter(DeviceInfo::hasDeviceLocator)
        .collect(
            toImmutableMap(
                info -> info.getDeviceLocator().getId(),
                info -> info.getDeviceLocator().getLabLocator().getHostName(),
                (first, second) -> first));
  }

  /** Maps each device ID to its stored DeviceConfig, or returns empty when the read fails. */
  private ListenableFuture<Optional<ImmutableMap<String, DeviceConfig>>> loadDeviceConfigs(
      ImmutableList<String> ids, UniverseScope universe) {
    return FluentFuture.from(configurationProvider.getDeviceConfigs(ids, universe))
        .transform(
            configs ->
                Optional.of(
                    configs.stream()
                        .filter(config -> !config.getUuid().isEmpty())
                        .collect(
                            toImmutableMap(
                                DeviceConfig::getUuid,
                                config -> config,
                                (first, second) -> first))),
            executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to load device configs for %d devices in batch evaluation", ids.size());
              return Optional.empty();
            },
            executor);
  }

  private ListenableFuture<ImmutableList<LoadedDevice>> loadDevices(
      ImmutableList<String> ids,
      Optional<ImmutableMap<String, String>> hostNames,
      Optional<ImmutableMap<String, DeviceConfig>> deviceConfigs,
      UniverseScope universe) {
    if (hostNames.isEmpty()) {
      return immediateFuture(
          readOnlyForAll(ids, REASON_DEVICE_INFO_LOAD_FAILED, UpdateError.Code.UNKNOWN));
    }
    ImmutableList<String> distinctHosts =
        ids.stream()
            .filter(hostNames.get()::containsKey)
            .map(hostNames.get()::get)
            .distinct()
            .collect(toImmutableList());
    return Futures.transform(
        inWaves(distinctHosts, hostName -> loadLabConfig(hostName, universe)),
        labConfigs -> {
          ImmutableMap<String, ConfigResult<LabConfig>> labConfigByHost =
              IntStream.range(0, distinctHosts.size())
                  .boxed()
                  .collect(toImmutableMap(distinctHosts::get, labConfigs::get));
          return ids.stream()
              .map(id -> resolveDevice(id, hostNames.get(), labConfigByHost, deviceConfigs))
              .collect(toImmutableList());
        },
        executor);
  }

  private ListenableFuture<ConfigResult<LabConfig>> loadLabConfig(
      String hostName, UniverseScope universe) {
    return Futures.catching(
        configurationProvider.getLabConfig(hostName, universe),
        Throwable.class,
        t -> {
          logger.atWarning().withCause(t).log("Failed to fetch LabConfig for %s", hostName);
          return ConfigResult.unavailable();
        },
        executor);
  }

  private LoadedDevice resolveDevice(
      String deviceId,
      ImmutableMap<String, String> hostNames,
      ImmutableMap<String, ConfigResult<LabConfig>> labConfigByHost,
      Optional<ImmutableMap<String, DeviceConfig>> deviceConfigs) {
    if (!hostNames.containsKey(deviceId)) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              DeviceConfig.getDefaultInstance(),
              REASON_DEVICE_NOT_FOUND,
              UpdateError.Code.VALIDATION_ERROR));
    }
    ConfigResult<LabConfig> labConfigResult = labConfigByHost.get(hostNames.get(deviceId));
    if (!labConfigResult.isAvailable()) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              DeviceConfig.getDefaultInstance(),
              REASON_HOST_CONFIG_LOAD_FAILED,
              UpdateError.Code.UNKNOWN));
    }
    Optional<LabConfig> labConfig = labConfigResult.config();
    if (labConfig.isPresent() && DeviceDataLoader.isHostManaged(labConfig.get())) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              DeviceConfig.newBuilder()
                  .setUuid(deviceId)
                  .setBasicConfig(labConfig.get().getDefaultDeviceConfig())
                  .build(),
              REASON_HOST_MANAGED,
              UpdateError.Code.VALIDATION_ERROR));
    }
    Optional<DeviceConfig> rawIndividualConfig =
        deviceConfigs.flatMap(configs -> Optional.ofNullable(configs.get(deviceId)));
    DeviceConfig effectiveConfig = rawIndividualConfig.orElse(DeviceConfig.getDefaultInstance());
    if (labConfig.isPresent()
        && configPusherHelper.isSectionRestricted(
            HostConfigSection.DEVICE_CONFIG, labConfig.get())) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId, effectiveConfig, REASON_CONFIG_PUSHER, UpdateError.Code.VALIDATION_ERROR));
    }
    if (deviceConfigs.isEmpty()) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              DeviceConfig.getDefaultInstance(),
              REASON_DEVICE_CONFIG_LOAD_FAILED,
              UpdateError.Code.UNKNOWN));
    }
    return LoadedDevice.candidate(deviceId, rawIndividualConfig);
  }

  private ListenableFuture<ImmutableList<DeviceEvaluation>> checkPermissions(
      ImmutableList<LoadedDevice> loaded, UniverseScope universe, Optional<String> username) {
    return inWaves(loaded, device -> checkPermission(device, universe, username));
  }

  private ListenableFuture<DeviceEvaluation> checkPermission(
      LoadedDevice device, UniverseScope universe, Optional<String> username) {
    if (device.readOnly().isPresent()) {
      return immediateFuture(device.readOnly().get());
    }
    String deviceId = device.deviceId();
    DeviceEvaluation writable =
        DeviceEvaluation.createWritable(deviceId, device.rawIndividualConfig());
    if (!environment.isGoogleInternal()) {
      return immediateFuture(writable);
    }
    DeviceEvaluation noPermission =
        DeviceEvaluation.createReadOnly(
            deviceId,
            writable.effectiveDeviceConfig(),
            REASON_NO_PERMISSION,
            UpdateError.Code.PERMISSION_DENIED);
    if (username.isEmpty()) {
      return immediateFuture(noPermission);
    }
    return FluentFuture.from(
            iamPermissionChecker.canConfigDevice(
                deviceId, writable.effectiveDeviceConfig(), universe))
        .transform(hasPermission -> hasPermission ? writable : noPermission, executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to check IAM permission for device %s", deviceId);
              return noPermission;
            },
            executor);
  }

  private static ImmutableList<LoadedDevice> readOnlyForAll(
      ImmutableList<String> ids, String reason, UpdateError.Code code) {
    return ids.stream()
        .map(
            id ->
                LoadedDevice.readOnly(
                    DeviceEvaluation.createReadOnly(
                        id, DeviceConfig.getDefaultInstance(), reason, code)))
        .collect(toImmutableList());
  }

  /**
   * Applies {@code call} to every item, starting each wave of at most {@link #MAX_CONCURRENT_CALLS}
   * items only after the previous wave completes. Results keep the order of {@code items}.
   */
  private static <T, R> ListenableFuture<ImmutableList<R>> inWaves(
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
