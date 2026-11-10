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

import static com.google.common.base.Strings.isNullOrEmpty;
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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceTarget;
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
import com.google.protobuf.FieldMask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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

  /** Maximum number of distinct device targets accepted by one batch request. */
  public static final int MAX_BATCH_DEVICE_IDS = 1000;

  /** Maximum number of lab config reads or permission checks in flight at once. */
  static final int MAX_CONCURRENT_CALLS = 16;

  private static final FieldMask DEVICE_LOCATOR_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths("device_locator.id")
          .addPaths("device_locator.lab_locator.host_name")
          .build();

  static final String REASON_DEVICE_NOT_FOUND = "Device not found";
  static final String REASON_INVALID_DEVICE_ID = "Invalid device ID";
  static final String REASON_DEVICE_INFO_LOAD_FAILED = "Failed to load device information";
  static final String REASON_HOST_CONFIG_LOAD_FAILED = "Failed to load host configuration";
  static final String REASON_DEVICE_CONFIG_LOAD_FAILED = "Failed to load device configuration";
  static final String REASON_HOST_MANAGED = "Managed by host shared config";
  static final String REASON_CONFIG_PUSHER = "Managed by Config Pusher";
  static final String REASON_NO_PERMISSION = "No permission";

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final ConfigPusherHelper configPusherHelper;
  private final IamPermissionChecker iamPermissionChecker;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchDeviceConfigHelper(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      ConfigPusherHelper configPusherHelper,
      IamPermissionChecker iamPermissionChecker,
      Environment environment,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.configPusherHelper = configPusherHelper;
    this.iamPermissionChecker = iamPermissionChecker;
    this.environment = environment;
    this.executor = executor;
  }

  /** Loaded device configuration data and writability evaluation for a single device target. */
  @AutoValue
  public abstract static class DeviceEvaluation {
    public abstract String deviceId();

    public abstract String hostName();

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
        String deviceId, String hostName, Optional<DeviceConfig> rawIndividualConfig) {
      return new AutoValue_BatchDeviceConfigHelper_DeviceEvaluation(
          deviceId,
          hostName,
          rawIndividualConfig.orElse(DeviceConfig.getDefaultInstance()),
          rawIndividualConfig,
          true,
          "",
          UpdateError.Code.CODE_UNSPECIFIED);
    }

    static DeviceEvaluation createReadOnly(
        String deviceId,
        String hostName,
        DeviceConfig effectiveDeviceConfig,
        String reason,
        UpdateError.Code code) {
      return new AutoValue_BatchDeviceConfigHelper_DeviceEvaluation(
          deviceId, hostName, effectiveDeviceConfig, Optional.empty(), false, reason, code);
    }
  }

  private record LabDevice(String deviceId, String hostName) {}

  /** Configuration state of one device target before the permission check. */
  private record LoadedDevice(
      String deviceId,
      String hostName,
      Optional<DeviceEvaluation> readOnly,
      Optional<DeviceConfig> rawIndividualConfig) {

    static LoadedDevice readOnly(DeviceEvaluation evaluation) {
      return new LoadedDevice(
          evaluation.deviceId(), evaluation.hostName(), Optional.of(evaluation), Optional.empty());
    }

    static LoadedDevice candidate(
        String deviceId, String hostName, Optional<DeviceConfig> rawIndividualConfig) {
      return new LoadedDevice(deviceId, hostName, Optional.empty(), rawIndividualConfig);
    }
  }

  /**
   * Returns normalized {@code devices} (with trimmed {@code host_name}) without duplicates, in
   * first-occurrence order.
   */
  public static ImmutableList<DeviceTarget> distinctTargets(List<DeviceTarget> devices) {
    return devices.stream()
        .map(target ->
            DeviceTarget.newBuilder()
                .setDeviceId(target.getDeviceId())
                .setHostName(target.getHostName().trim())
                .build())
        .distinct()
        .collect(toImmutableList());
  }

  /**
   * Returns distinct {@code device_id}s from {@code devices} that meet {@code DeviceConfigService}
   * device UUID rules.
   */
  public static ImmutableList<String> extractDistinctValidDeviceUuids(List<DeviceTarget> devices) {
    return devices.stream()
        .map(DeviceTarget::getDeviceId)
        .filter(BatchDeviceConfigUtil::isValidDeviceUuid)
        .distinct()
        .collect(toImmutableList());
  }

  /**
   * Returns the reason {@code devices} cannot be processed in one batch request, or empty when it
   * holds at most {@link #MAX_BATCH_DEVICE_IDS} distinct targets.
   */
  public static Optional<String> checkDeviceTargetCount(List<DeviceTarget> devices) {
    int count = distinctTargets(devices).size();
    return count > MAX_BATCH_DEVICE_IDS
        ? Optional.of(
            String.format(
                "A batch request accepts at most %d devices, got %d.", MAX_BATCH_DEVICE_IDS, count))
        : Optional.empty();
  }

  /**
   * Evaluates all distinct {@code devices} in request order.
   *
   * <p>Callers validate service availability and that {@code devices} holds at most {@link
   * #MAX_BATCH_DEVICE_IDS} distinct targets.
   */
  public ListenableFuture<ImmutableList<DeviceEvaluation>> evaluateDevices(
      List<DeviceTarget> devices, Optional<String> username) {
    ImmutableList<DeviceTarget> targets = distinctTargets(devices);
    if (targets.isEmpty()) {
      return immediateFuture(ImmutableList.of());
    }

    ImmutableList<String> queryIds =
        targets.stream()
            .map(DeviceTarget::getDeviceId)
            .filter(id -> !id.isEmpty())
            .distinct()
            .collect(toImmutableList());
    ImmutableList<String> configIds = extractDistinctValidDeviceUuids(targets);

    ListenableFuture<Optional<ImmutableList<LabDevice>>> labDevicesFuture =
        loadLabDevices(queryIds);
    ListenableFuture<Optional<ImmutableMap<String, DeviceConfig>>> deviceConfigsFuture =
        loadDeviceConfigs(configIds);

    return FluentFuture.from(
            Futures.whenAllSucceed(labDevicesFuture, deviceConfigsFuture)
                .callAsync(
                    () ->
                        loadDevices(
                            targets,
                            Futures.getDone(labDevicesFuture),
                            Futures.getDone(deviceConfigsFuture)),
                    executor))
        .transformAsync(loaded -> checkPermissions(loaded, username), executor);
  }

  /**
   * Commits mutated {@link DeviceConfig}s for all writable {@code evaluations} and returns the
   * combined per-device {@link UpdateError} map (read-only evaluations plus backend write errors).
   *
   * <p>Because the response error map and {@code DeviceConfigService} storage are keyed by {@code
   * deviceId}, any read-only evaluation for a {@code deviceId} blocks committing that {@code
   * deviceId} across all requested targets so a {@code deviceId} reported in {@code errors} is
   * never also mutated in storage.
   */
  public ListenableFuture<ImmutableMap<String, UpdateError>> commitDeviceConfigUpdates(
      List<DeviceEvaluation> evaluations,
      UnaryOperator<DeviceConfig> configMutator,
      String defaultErrorMessage) {
    Map<String, UpdateError> errors = new LinkedHashMap<>();
    Map<String, DeviceConfig> configsToUpdate = new LinkedHashMap<>();

    for (DeviceEvaluation evaluation : evaluations) {
      String deviceId = evaluation.deviceId();
      if (!evaluation.writable()) {
        errors.put(
            deviceId,
            UpdateError.newBuilder()
                .setCode(evaluation.errorCode())
                .setMessage(evaluation.readOnlyReason())
                .build());
        configsToUpdate.remove(deviceId);
        continue;
      }
      if (errors.containsKey(deviceId)) {
        continue;
      }

      DeviceConfig existingConfig =
          evaluation
              .rawIndividualConfig()
              .orElseGet(() -> DeviceConfig.newBuilder().setUuid(deviceId).build());
      DeviceConfig mutatedConfig = configMutator.apply(existingConfig);
      configsToUpdate.put(deviceId, mutatedConfig.toBuilder().setUuid(deviceId).build());
    }

    if (configsToUpdate.isEmpty()) {
      return immediateFuture(ImmutableMap.copyOf(errors));
    }

    return FluentFuture.from(
            configurationProvider.updateDeviceConfigs(
                ImmutableMap.copyOf(configsToUpdate), UniverseScope.SELF))
        .transform(
            backendErrors -> {
              backendErrors.forEach((deviceId, errMsg) ->
                  errors.put(
                      deviceId,
                      UpdateError.newBuilder()
                          .setCode(UpdateError.Code.UNKNOWN)
                          .setMessage(isNullOrEmpty(errMsg) ? defaultErrorMessage : errMsg)
                          .build()));
              return ImmutableMap.copyOf(errors);
            },
            executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log("Batch updateDeviceConfigs backend call failed");
              String fallbackMsg =
                  t.getMessage() != null && !t.getMessage().isEmpty()
                      ? t.getMessage()
                      : defaultErrorMessage;
              for (String deviceId : configsToUpdate.keySet()) {
                errors.put(
                    deviceId,
                    UpdateError.newBuilder()
                        .setCode(UpdateError.Code.UNKNOWN)
                        .setMessage(fallbackMsg)
                        .build());
              }
              return ImmutableMap.copyOf(errors);
            },
            executor);
  }

  /** Loads matching lab devices by UUID, or returns empty when the lookup fails. */
  private ListenableFuture<Optional<ImmutableList<LabDevice>>> loadLabDevices(
      ImmutableList<String> queryIds) {
    if (queryIds.isEmpty()) {
      return immediateFuture(Optional.of(ImmutableList.of()));
    }
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
                                                                    .addAllExpected(queryIds)))))))
                    .setDeviceViewRequest(LabQuery.DeviceViewRequest.getDefaultInstance())
                    .setMask(
                        LabQuery.Mask.newBuilder()
                            .setDeviceInfoMask(
                                DeviceInfoMask.newBuilder()
                                    .setFieldMask(DEVICE_LOCATOR_FIELD_MASK))))
            .build();
    return FluentFuture.from(labInfoProvider.getLabInfoAsync(request, UniverseScope.SELF))
        .transform(response -> Optional.of(extractLabDevices(response)), executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "Failed to load device information for %d devices in batch evaluation",
                  queryIds.size());
              return Optional.empty();
            },
            executor);
  }

  private static ImmutableList<LabDevice> extractLabDevices(GetLabInfoResponse response) {
    return response
        .getLabQueryResult()
        .getDeviceView()
        .getGroupedDevices()
        .getDeviceList()
        .getDeviceInfoList()
        .stream()
        .filter(DeviceInfo::hasDeviceLocator)
        .map(info ->
            new LabDevice(
                info.getDeviceLocator().getId(),
                info.getDeviceLocator().getLabLocator().getHostName()))
        .collect(toImmutableList());
  }

  /** Maps each valid device UUID to its stored DeviceConfig, or returns empty when read fails. */
  private ListenableFuture<Optional<ImmutableMap<String, DeviceConfig>>> loadDeviceConfigs(
      ImmutableList<String> configIds) {
    if (configIds.isEmpty()) {
      return immediateFuture(Optional.of(ImmutableMap.of()));
    }
    return FluentFuture.from(configurationProvider.getDeviceConfigs(configIds, UniverseScope.SELF))
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
                  "Failed to load device configs for %d devices in batch evaluation",
                  configIds.size());
              return Optional.empty();
            },
            executor);
  }

  private ListenableFuture<ImmutableList<LoadedDevice>> loadDevices(
      ImmutableList<DeviceTarget> targets,
      Optional<ImmutableList<LabDevice>> labDevices,
      Optional<ImmutableMap<String, DeviceConfig>> deviceConfigs) {
    if (labDevices.isEmpty()) {
      return immediateFuture(
          readOnlyForAll(targets, REASON_DEVICE_INFO_LOAD_FAILED, UpdateError.Code.UNKNOWN));
    }
    ImmutableList<LabDevice> allLabDevices = labDevices.get();
    ImmutableList<Optional<LabDevice>> matchedByTarget =
        targets.stream()
            .map(target -> findMatchingLabDevice(target, allLabDevices))
            .collect(toImmutableList());
    ImmutableList<String> distinctHosts =
        allLabDevices.stream()
            .map(LabDevice::hostName)
            .filter(host -> !host.isEmpty())
            .distinct()
            .collect(toImmutableList());
    return Futures.transform(
        inWaves(distinctHosts, this::loadLabConfig),
        labConfigs -> {
          ImmutableMap<String, ConfigResult<LabConfig>> labConfigByHost =
              IntStream.range(0, distinctHosts.size())
                  .boxed()
                  .collect(toImmutableMap(distinctHosts::get, labConfigs::get));
          return IntStream.range(0, targets.size())
              .mapToObj(i ->
                  resolveDevice(
                      targets.get(i),
                      matchedByTarget.get(i),
                      allLabDevices,
                      labConfigByHost,
                      deviceConfigs))
              .collect(toImmutableList());
        },
        executor);
  }

  private static Optional<LabDevice> findMatchingLabDevice(
      DeviceTarget target, ImmutableList<LabDevice> labDevices) {
    String deviceId = target.getDeviceId();
    if (deviceId.isEmpty()) {
      return Optional.empty();
    }
    String requestedHost = target.getHostName();
    return labDevices.stream()
        .filter(labDevice ->
            labDevice.deviceId().equals(deviceId)
                && (requestedHost.isEmpty() || labDevice.hostName().equals(requestedHost)))
        .findFirst();
  }

  private ListenableFuture<ConfigResult<LabConfig>> loadLabConfig(String hostName) {
    return Futures.catching(
        configurationProvider.getLabConfig(hostName, UniverseScope.SELF),
        Throwable.class,
        t -> {
          logger.atWarning().withCause(t).log("Failed to fetch LabConfig for %s", hostName);
          return ConfigResult.unavailable();
        },
        executor);
  }

  private LoadedDevice resolveDevice(
      DeviceTarget target,
      Optional<LabDevice> matchedLabDevice,
      ImmutableList<LabDevice> allLabDevices,
      ImmutableMap<String, ConfigResult<LabConfig>> labConfigByHost,
      Optional<ImmutableMap<String, DeviceConfig>> deviceConfigs) {
    String deviceId = target.getDeviceId();
    if (matchedLabDevice.isEmpty()) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              target.getHostName(),
              DeviceConfig.getDefaultInstance(),
              REASON_DEVICE_NOT_FOUND,
              UpdateError.Code.VALIDATION_ERROR));
    }
    String resolvedHost = matchedLabDevice.get().hostName();
    ConfigResult<LabConfig> labConfigResult =
        resolvedHost.isEmpty()
            ? ConfigResult.available(Optional.empty())
            : labConfigByHost.get(resolvedHost);
    if (!labConfigResult.isAvailable()) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              resolvedHost,
              DeviceConfig.getDefaultInstance(),
              REASON_HOST_CONFIG_LOAD_FAILED,
              UpdateError.Code.UNKNOWN));
    }
    Optional<LabConfig> labConfig = labConfigResult.config();
    if (labConfig.isPresent() && DeviceDataLoader.isHostManaged(labConfig.get())) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              resolvedHost,
              DeviceConfig.newBuilder()
                  .setUuid(deviceId)
                  .setBasicConfig(labConfig.get().getDefaultDeviceConfig())
                  .build(),
              REASON_HOST_MANAGED,
              UpdateError.Code.VALIDATION_ERROR));
    }
    if (!BatchDeviceConfigUtil.isValidDeviceUuid(deviceId)) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              resolvedHost,
              DeviceConfig.getDefaultInstance(),
              REASON_INVALID_DEVICE_ID,
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
              deviceId,
              resolvedHost,
              effectiveConfig,
              REASON_CONFIG_PUSHER,
              UpdateError.Code.VALIDATION_ERROR));
    }
    Optional<DeviceEvaluation> siblingRestriction =
        checkCollidingHostRestrictions(
            deviceId, resolvedHost, effectiveConfig, allLabDevices, labConfigByHost);
    if (siblingRestriction.isPresent()) {
      return LoadedDevice.readOnly(siblingRestriction.get());
    }
    if (deviceConfigs.isEmpty()) {
      return LoadedDevice.readOnly(
          DeviceEvaluation.createReadOnly(
              deviceId,
              resolvedHost,
              DeviceConfig.getDefaultInstance(),
              REASON_DEVICE_CONFIG_LOAD_FAILED,
              UpdateError.Code.UNKNOWN));
    }
    return LoadedDevice.candidate(deviceId, resolvedHost, rawIndividualConfig);
  }

  /**
   * Checks whether any other host reporting {@code deviceId} restricts per-device configuration.
   * Because {@code DeviceConfigService} keys device configs globally by {@code deviceId}, writing a
   * shared {@code DeviceConfig} when a colliding host is host-managed, Config-Pusher-managed, or
   * has an unreadable host config is unsafe even when only the standalone host target is requested.
   */
  private Optional<DeviceEvaluation> checkCollidingHostRestrictions(
      String deviceId,
      String resolvedHost,
      DeviceConfig effectiveConfig,
      ImmutableList<LabDevice> allLabDevices,
      ImmutableMap<String, ConfigResult<LabConfig>> labConfigByHost) {
    for (LabDevice sibling : allLabDevices) {
      if (!sibling.deviceId().equals(deviceId)
          || sibling.hostName().isEmpty()
          || sibling.hostName().equals(resolvedHost)) {
        continue;
      }
      ConfigResult<LabConfig> siblingConfigResult = labConfigByHost.get(sibling.hostName());
      if (siblingConfigResult == null || !siblingConfigResult.isAvailable()) {
        return Optional.of(
            DeviceEvaluation.createReadOnly(
                deviceId,
                resolvedHost,
                effectiveConfig,
                String.format(
                    "%s on colliding host %s", REASON_HOST_CONFIG_LOAD_FAILED, sibling.hostName()),
                UpdateError.Code.UNKNOWN));
      }
      Optional<LabConfig> siblingConfig = siblingConfigResult.config();
      if (siblingConfig.isPresent() && DeviceDataLoader.isHostManaged(siblingConfig.get())) {
        return Optional.of(
            DeviceEvaluation.createReadOnly(
                deviceId,
                resolvedHost,
                effectiveConfig,
                String.format("%s on colliding host %s", REASON_HOST_MANAGED, sibling.hostName()),
                UpdateError.Code.VALIDATION_ERROR));
      }
      if (siblingConfig.isPresent()
          && configPusherHelper.isSectionRestricted(
              HostConfigSection.DEVICE_CONFIG, siblingConfig.get())) {
        return Optional.of(
            DeviceEvaluation.createReadOnly(
                deviceId,
                resolvedHost,
                effectiveConfig,
                String.format("%s on colliding host %s", REASON_CONFIG_PUSHER, sibling.hostName()),
                UpdateError.Code.VALIDATION_ERROR));
      }
    }
    return Optional.empty();
  }

  private ListenableFuture<ImmutableList<DeviceEvaluation>> checkPermissions(
      ImmutableList<LoadedDevice> loaded, Optional<String> username) {
    return inWaves(loaded, device -> checkPermission(device, username));
  }

  private ListenableFuture<DeviceEvaluation> checkPermission(
      LoadedDevice device, Optional<String> username) {
    if (device.readOnly().isPresent()) {
      return immediateFuture(device.readOnly().get());
    }
    String deviceId = device.deviceId();
    String hostName = device.hostName();
    DeviceEvaluation writable =
        DeviceEvaluation.createWritable(deviceId, hostName, device.rawIndividualConfig());
    DeviceEvaluation noPermission =
        DeviceEvaluation.createReadOnly(
            deviceId,
            hostName,
            writable.effectiveDeviceConfig(),
            REASON_NO_PERMISSION,
            UpdateError.Code.PERMISSION_DENIED);
    if (environment.isGoogleInternal() && username.isEmpty()) {
      return immediateFuture(noPermission);
    }
    return FluentFuture.from(
            iamPermissionChecker.canConfigDevice(
                deviceId, writable.effectiveDeviceConfig(), UniverseScope.SELF))
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
      ImmutableList<DeviceTarget> targets, String reason, UpdateError.Code code) {
    return targets.stream()
        .map(target ->
            LoadedDevice.readOnly(
                DeviceEvaluation.createReadOnly(
                    target.getDeviceId(),
                    target.getHostName(),
                    DeviceConfig.getDefaultInstance(),
                    reason,
                    code)))
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
