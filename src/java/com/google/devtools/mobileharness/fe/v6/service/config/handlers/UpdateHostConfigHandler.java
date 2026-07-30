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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.OverSshDevice;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceDiscoverySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUpdateScope;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.protobuf.FieldMask;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the UpdateHostConfig RPC. */
@Singleton
public final class UpdateHostConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final GroupMembershipProvider groupMembershipProvider;
  private final Environment environment;
  private final ConfigPusherHelper configPusherHelper;
  private final ListeningExecutorService executor;

  @Inject
  UpdateHostConfigHandler(
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ConfigPusherHelper configPusherHelper,
      GroupMembershipProvider groupMembershipProvider,
      Environment environment,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.configPusherHelper = configPusherHelper;
    this.groupMembershipProvider = groupMembershipProvider;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<UpdateHostConfigResponse> updateHostConfig(
      UpdateHostConfigRequest request, UniverseScope universe, Optional<String> username) {
    logger.atInfo().log("Updating host config for %s", request.getHostName());

    try {
      validateRequest(request, universe);
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      return immediateFuture(
          UpdateHostConfigResponse.newBuilder()
              .setSuccess(false)
              .setError(
                  UpdateError.newBuilder()
                      .setCode(UpdateError.Code.VALIDATION_ERROR)
                      .setMessage(e.getMessage()))
              .build());
    }

    ListenableFuture<Optional<UpdateHostConfigResponse>> lockoutCheckFuture =
        performSelfLockoutCheck(request, username);
    ListenableFuture<ConfigResult<LabConfig>> existingConfigFuture =
        configurationProvider.getLabConfig(request.getHostName(), universe);

    return Futures.whenAllComplete(lockoutCheckFuture, existingConfigFuture)
        .callAsync(
            () -> {
              Optional<UpdateHostConfigResponse> lockoutResponse = lockoutCheckFuture.get();
              if (lockoutResponse.isPresent()) {
                return immediateFuture(lockoutResponse.get());
              }
              return saveUpdatedConfig(request, existingConfigFuture.get(), universe);
            },
            executor);
  }

  private void validateRequest(UpdateHostConfigRequest request, UniverseScope universe) {
    configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();

    if (environment.isAts() && request.getScope().hasUpdateMask()) {
      for (String path : request.getScope().getUpdateMask().getPathsList()) {
        if (!path.equals("device_config_mode")
            && !path.equals("device_config")
            && !path.startsWith("device_config.")) {
          throw new IllegalArgumentException(
              String.format(
                  "Configuration path '%s' is not supported in the ATS environment.", path));
        }
      }
    }
  }

  private ListenableFuture<Optional<UpdateHostConfigResponse>> performSelfLockoutCheck(
      UpdateHostConfigRequest request, Optional<String> username) {
    return Futures.transform(
        isSelfLockout(request, username),
        isSelfLockout -> {
          if (isSelfLockout) {
            return Optional.of(
                UpdateHostConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setError(
                        UpdateError.newBuilder()
                            .setCode(UpdateError.Code.SELF_LOCKOUT_DETECTED)
                            .setMessage(
                                "You cannot remove yourself from the host admins list without"
                                    + " overriding the self-lockout check."))
                    .build());
          }
          return Optional.empty();
        },
        executor);
  }

  private ListenableFuture<UpdateHostConfigResponse> saveUpdatedConfig(
      UpdateHostConfigRequest request,
      ConfigResult<LabConfig> labConfigResult,
      UniverseScope universe) {
    Optional<LabConfig> existingConfigOpt = labConfigResult.config();
    LabConfig.Builder builder =
        existingConfigOpt.isPresent()
            ? existingConfigOpt.get().toBuilder()
            : LabConfig.newBuilder().setHostName(request.getHostName());

    // If the host already exists and this is a partial update, we must validate that
    // the requested update sections are not restricted by Config Pusher.
    // For full updates, we bypass this fail-fast check to allow the backend to silently
    // skip restricted sections (safe merge) instead of rejecting the entire request.
    if (existingConfigOpt.isPresent()
        && !isFullUpdate(request.getScope())
        && request.getScope().hasUpdateMask()) {
      LabConfig existingConfig = existingConfigOpt.get();
      for (String path : request.getScope().getUpdateMask().getPathsList()) {
        HostConfigSection requestedSection = getSectionFromPath(path);
        if (requestedSection != HostConfigSection.HOST_CONFIG_SECTION_UNSPECIFIED) {
          try {
            configPusherHelper.validateUpdate(
                requestedSection, request.getConfig(), existingConfig);
          } catch (MobileHarnessException e) {
            return immediateFuture(
                UpdateHostConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setError(
                        UpdateError.newBuilder()
                            .setCode(UpdateError.Code.PERMISSION_DENIED)
                            .setMessage(e.getMessage()))
                    .build());
          }
        }
      }
    }

    updateLabConfigBuilder(builder, request.getConfig(), request.getScope());

    return FluentFuture.from(
            configurationProvider.updateLabConfig(request.getHostName(), builder.build(), universe))
        .transform(
            unused -> UpdateHostConfigResponse.newBuilder().setSuccess(true).build(), executor)
        .catching(
            Exception.class,
            e -> {
              UpdateHostConfigRequest sanitizedRequest = sanitizeRequest(request);
              logger.atWarning().log(
                  "Failed to save host configuration for %s. Request: %s",
                  request.getHostName(), sanitizedRequest);

              String errorMessage =
                  e.getMessage() != null && !e.getMessage().isEmpty()
                      ? e.getMessage()
                      : "Failed to save host configuration.";

              // If the error is due to invalid user IDs (e.g., misspelled LDAPs), we generate a
              // specific
              // error message indicating which section(s) likely contain the invalid IDs to help
              // the user fix it quickly.
              if (ConfigUtil.isInvalidUserError(e)) {
                errorMessage = getInvalidUserErrorMessage(request);
              }
              throw FeServiceException.internal(errorMessage, e);
            },
            executor);
  }

  private ListenableFuture<Boolean> isSelfLockout(
      UpdateHostConfigRequest request, Optional<String> username) {
    if (request.getOptions().getOverrideSelfLockout() || username.isEmpty()) {
      return immediateFuture(false);
    }

    if (!needsSelfLockoutCheck(request.getScope())) {
      return immediateFuture(false);
    }

    List<String> newHostAdmins = request.getConfig().getPermissions().getHostAdminsList();
    String user = username.get();

    if (newHostAdmins.contains(user)) {
      return immediateFuture(false);
    }

    return Futures.transform(
        groupMembershipProvider.isMemberOfAny(user, newHostAdmins),
        (Boolean isMember) -> !isMember,
        executor);
  }

  /**
   * Identifies whether the requested configuration scope changes could potentially lock out the
   * current user by modifying permissions.
   *
   * @param scope the update scope specifying which host configuration sections are changing.
   * @return true if a self-lockout check is required; false otherwise.
   */
  private boolean needsSelfLockoutCheck(HostConfigUpdateScope scope) {
    return !scope.hasUpdateMask() || isPathInFieldMask("permissions", scope.getUpdateMask());
  }

  /**
   * Identifies whether the update request represents a full update (either updating all paths
   * explicitly, or missing a mask entirely, fallback to old clients behaviour).
   */
  private boolean isFullUpdate(HostConfigUpdateScope scope) {
    if (!scope.hasUpdateMask()) {
      return true;
    }
    FieldMask mask = scope.getUpdateMask();
    return mask.getPathsList().contains("permissions")
        && mask.getPathsList().contains("device_config_mode")
        && mask.getPathsList().contains("device_config")
        && mask.getPathsList().contains("host_properties")
        && mask.getPathsList().contains("device_discovery");
  }

  private void updateLabConfigBuilder(
      LabConfig.Builder builder, HostConfig incoming, HostConfigUpdateScope scope) {
    LabConfig existingConfig = builder.build();

    FieldMask mask =
        scope.hasUpdateMask()
            ? scope.getUpdateMask()
            : FieldMask.newBuilder()
                .addPaths("permissions")
                .addPaths("device_config_mode")
                .addPaths("device_config")
                .addPaths("host_properties")
                .addPaths("device_discovery")
                // Sub-paths under device_config are implicitly matched by the parent path
                .build();

    if (isPathInFieldMask("device_config_mode", mask)
        && !configPusherHelper.isSectionRestricted(
            HostConfigSection.DEVICE_CONFIG_MODE, existingConfig)) {
      updateDeviceConfigMode(builder, incoming.getDeviceConfigMode());
    }

    if (isPathInFieldMask("permissions", mask)
        && !configPusherHelper.isSectionRestricted(
            HostConfigSection.HOST_PERMISSIONS, existingConfig)) {
      updateHostPermissions(builder, incoming);
    }

    if (isPathInFieldMask("host_properties", mask)
        && !configPusherHelper.isSectionRestricted(
            HostConfigSection.HOST_PROPERTIES, existingConfig)) {
      updateHostProperties(builder, incoming);
    }

    if (isPathInFieldMask("device_config", mask)
        && !configPusherHelper.isSectionRestricted(
            HostConfigSection.DEVICE_CONFIG, existingConfig)) {
      builder.setDefaultDeviceConfig(
          updateBasicConfig(
              builder.getDefaultDeviceConfig(),
              ConfigConverter.toBasicDeviceConfig(incoming.getDeviceConfig()),
              mask));
    }

    if (isPathInFieldMask("device_discovery", mask)
        && !configPusherHelper.isSectionRestricted(
            HostConfigSection.DEVICE_DISCOVERY, existingConfig)) {
      updateDeviceDiscovery(builder, incoming.getDeviceDiscovery());
    }
  }

  /** Maps a FieldMask path string to the corresponding HostConfigSection enum value. */
  private static HostConfigSection getSectionFromPath(String path) {
    if (path.equals("device_config_mode")) {
      return HostConfigSection.DEVICE_CONFIG_MODE;
    } else if (path.equals("permissions")) {
      return HostConfigSection.HOST_PERMISSIONS;
    } else if (path.equals("host_properties")) {
      return HostConfigSection.HOST_PROPERTIES;
    } else if (path.equals("device_config") || path.startsWith("device_config.")) {
      return HostConfigSection.DEVICE_CONFIG;
    } else if (path.equals("device_discovery")) {
      return HostConfigSection.DEVICE_DISCOVERY;
    }
    return HostConfigSection.HOST_CONFIG_SECTION_UNSPECIFIED;
  }

  /**
   * Helper that checks if a target configuration path is inside the update mask. Matches both exact
   * paths, sub-paths of the path, or parent paths.
   *
   * <p>Note: We cannot use standard protobuf FieldMaskUtil.isPathInFieldMask here because it only
   * supports matching parent paths in the mask (ancestor check), but does not return true if the
   * mask contains a sub-path of the checked path (descendant check).
   */
  private static boolean isPathInFieldMask(String path, FieldMask mask) {
    for (String maskPath : mask.getPathsList()) {
      if (maskPath.equals(path)
          || maskPath.startsWith(path + ".")
          || path.startsWith(maskPath + ".")) {
        return true;
      }
    }
    return false;
  }

  private void updateDeviceConfigMode(LabConfig.Builder builder, DeviceConfigMode mode) {
    HostProperties.Builder propertiesBuilder = builder.getHostProperties().toBuilder();
    // Use clearHostProperty and re-add all except device_config_mode to be safe
    ImmutableList<HostProperty> others =
        propertiesBuilder.getHostPropertyList().stream()
            .filter(p -> !p.getKey().equals("device_config_mode"))
            .collect(toImmutableList());
    propertiesBuilder.clearHostProperty().addAllHostProperty(others);

    if (mode == DeviceConfigMode.SHARED) {
      propertiesBuilder.addHostProperty(
          HostProperty.newBuilder().setKey("device_config_mode").setValue("host").build());
    }
    // PER_DEVICE is absence of the pair.
    builder.setHostProperties(propertiesBuilder.build());
  }

  private void updateHostPermissions(LabConfig.Builder builder, HostConfig incoming) {
    if (!incoming.hasPermissions()) {
      return;
    }

    // Sync host admins to default_device_config.owner
    builder
        .getDefaultDeviceConfigBuilder()
        .clearOwner()
        .addAllOwner(incoming.getPermissions().getHostAdminsList());
  }

  private void updateHostProperties(LabConfig.Builder builder, HostConfig incoming) {
    HostProperties existing = builder.getHostProperties();
    HostProperties.Builder newProperties = HostProperties.newBuilder();

    // Preserve device_config_mode pair from existing config as it's a "system" pair
    existing.getHostPropertyList().stream()
        .filter(p -> p.getKey().equals("device_config_mode"))
        .forEach(newProperties::addHostProperty);

    // Add all business properties from incoming
    incoming.getHostPropertiesList().stream()
        .map(p -> HostProperty.newBuilder().setKey(p.getKey()).setValue(p.getValue()).build())
        .forEach(newProperties::addHostProperty);

    builder.setHostProperties(newProperties.build());
  }

  private void updateDeviceDiscovery(LabConfig.Builder builder, DeviceDiscoverySettings discovery) {
    builder
        .clearMonitoredDeviceUuid()
        .addAllMonitoredDeviceUuid(discovery.getMonitoredDeviceUuidsList())
        .clearTestbedUuid()
        .addAllTestbedUuid(discovery.getTestbedUuidsList())
        .clearMiscDeviceUuid()
        .addAllMiscDeviceUuid(discovery.getMiscDeviceUuidsList())
        .clearOverTcpIp()
        .addAllOverTcpIp(discovery.getOverTcpIpsList())
        .clearOverSsh()
        .addAllOverSsh(
            discovery.getOverSshDevicesList().stream()
                .map(
                    d ->
                        OverSshDevice.newBuilder()
                            .setIpAddress(d.getIpAddress())
                            .setUsername(d.getUsername())
                            .setPassword(d.getPassword())
                            .setSshDeviceType(d.getSshDeviceType())
                            .build())
                .collect(toImmutableList()));
    if (discovery.getManekiSpecsList().isEmpty()) {
      builder.clearDetectorSpecs();
    } else {
      builder.setDetectorSpecs(
          ConfigConverter.toInternalDetectorSpecs(discovery.getManekiSpecsList()));
    }
  }

  private BasicDeviceConfig updateBasicConfig(
      BasicDeviceConfig current, BasicDeviceConfig incoming, FieldMask mask) {
    BasicDeviceConfig.Builder builder = current.toBuilder();

    if (!environment.isAts() && isPathInFieldMask("device_config.permissions", mask)) {
      builder
          .clearOwner()
          .addAllOwner(incoming.getOwnerList())
          .clearExecutor()
          .addAllExecutor(incoming.getExecutorList());
    }

    if (!environment.isAts() && isPathInFieldMask("device_config.settings", mask)) {
      builder
          .setMaxConsecutiveTest(incoming.getMaxConsecutiveTest())
          .setMaxConsecutiveFail(incoming.getMaxConsecutiveFail());
    }

    if (isPathInFieldMask("device_config.wifi", mask)) {
      updateDefaultWifi(builder, incoming);
    }

    if (isPathInFieldMask("device_config.dimensions", mask)) {
      builder.setCompositeDimension(incoming.getCompositeDimension());
    }

    return builder.build();
  }

  private static void updateDefaultWifi(
      BasicDeviceConfig.Builder builder, BasicDeviceConfig incoming) {
    if (incoming.hasDefaultWifi()) {
      builder.setDefaultWifi(incoming.getDefaultWifi());
    } else {
      builder.clearDefaultWifi();
    }
  }

  /**
   * Generates a detailed error message when an invalid user ID error occurs during host config
   * update.
   *
   * <p>To give a clearer error message to users, we inspect the {@link FieldMask} to determine
   * which specific sections (Host Permissions or Device Executors) were being updated, and point
   * the user to those sections.
   */
  private static String getInvalidUserErrorMessage(UpdateHostConfigRequest request) {
    // If no update mask is provided, it implies a full update (ALL scope), so we treat both
    // 'permissions' and 'device_config' as being updated to ensure all potential invalid ID
    // sections are covered in the error message.
    FieldMask mask =
        request.getScope().hasUpdateMask()
            ? request.getScope().getUpdateMask()
            : FieldMask.newBuilder().addPaths("permissions").addPaths("device_config").build();

    boolean updatingHostPermissions = isPathInFieldMask("permissions", mask);
    boolean updatingDevicePermissions = isPathInFieldMask("device_config.permissions", mask);

    String sections = "";
    if (updatingHostPermissions && updatingDevicePermissions) {
      sections = " in Host Permissions or Device Executors";
    } else if (updatingHostPermissions) {
      sections = " in Host Permissions";
    } else if (updatingDevicePermissions) {
      sections = " in Device Executors";
    }

    return String.format(
        "Failed to update config of host %s, please make sure the provided user"
            + " IDs%s are correct, or try again later.",
        request.getHostName(), sections);
  }

  private static UpdateHostConfigRequest sanitizeRequest(UpdateHostConfigRequest request) {
    if (!request.hasConfig()) {
      return request;
    }
    UpdateHostConfigRequest.Builder builder = request.toBuilder();
    if (builder.getConfig().getDeviceConfig().hasWifi()) {
      builder.getConfigBuilder().getDeviceConfigBuilder().getWifiBuilder().setPsk("******");
    }
    if (builder.getConfig().hasDeviceDiscovery()) {
      builder
          .getConfigBuilder()
          .getDeviceDiscoveryBuilder()
          .getOverSshDevicesBuilderList()
          .forEach(sshBuilder -> sshBuilder.setPassword("******"));
    }
    return builder.build();
  }
}
