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
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceDiscoverySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUpdateScope;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
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
  private final ListeningExecutorService executor;

  @Inject
  UpdateHostConfigHandler(
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      GroupMembershipProvider groupMembershipProvider,
      Environment environment,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.groupMembershipProvider = groupMembershipProvider;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<UpdateHostConfigResponse> updateHostConfig(
      UpdateHostConfigRequest request, Optional<String> username) {
    logger.atInfo().log("Updating host config for %s", request.getHostName());

    try {
      validateRequest(request);
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

    return FluentFuture.from(performSelfLockoutCheck(request, username))
        .transformAsync(
            optResponse ->
                optResponse.isPresent()
                    ? immediateFuture(optResponse.get())
                    : saveUpdatedConfig(request),
            executor);
  }

  private void validateRequest(UpdateHostConfigRequest request) {
    configServiceCapabilityFactory.create(request.getUniverse()).checkConfigServiceAvailability();

    if (environment.isAts()) {
      switch (request.getScope().getSection()) {
        case DEVICE_CONFIG_MODE, DEVICE_CONFIG -> {}
        default ->
            throw new IllegalArgumentException(
                String.format(
                    "Configuration section '%s' is not supported in the ATS environment.",
                    request.getScope().getSection()));
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
      UpdateHostConfigRequest request) {
    return Futures.transformAsync(
        configurationProvider.getLabConfig(request.getHostName(), request.getUniverse()),
        existingConfigOpt -> {
          LabConfig.Builder builder =
              existingConfigOpt.isPresent()
                  ? existingConfigOpt.get().toBuilder()
                  : LabConfig.newBuilder().setHostName(request.getHostName());

          updateLabConfigBuilder(builder, request.getConfig(), request.getScope());

          return Futures.transform(
              configurationProvider.updateLabConfig(
                  request.getHostName(), builder.build(), request.getUniverse()),
              unused -> UpdateHostConfigResponse.newBuilder().setSuccess(true).build(),
              executor);
        },
        executor);
  }

  private ListenableFuture<Boolean> isSelfLockout(
      UpdateHostConfigRequest request, Optional<String> username) {
    if (request.getOptions().getOverrideSelfLockout() || username.isEmpty()) {
      return immediateFuture(false);
    }

    switch (request.getScope().getSection()) {
      case HOST_PERMISSIONS, HOST_CONFIG_SECTION_UNSPECIFIED, UNRECOGNIZED -> {}
      default -> {
        return immediateFuture(false);
      }
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

  private void updateLabConfigBuilder(
      LabConfig.Builder builder, HostConfig incoming, HostConfigUpdateScope scope) {
    switch (scope.getSection()) {
      case DEVICE_CONFIG_MODE -> updateDeviceConfigMode(builder, incoming.getDeviceConfigMode());
      case HOST_PERMISSIONS -> updateHostPermissions(builder, incoming);
      case HOST_PROPERTIES -> updateHostProperties(builder, incoming);
      case DEVICE_CONFIG ->
          builder.setDefaultDeviceConfig(
              updateBasicConfig(
                  builder.getDefaultDeviceConfig(),
                  ConfigConverter.toBasicDeviceConfig(incoming.getDeviceConfig()),
                  scope.getDeviceConfigSection()));
      case DEVICE_DISCOVERY -> updateDeviceDiscovery(builder, incoming.getDeviceDiscovery());
      case HOST_CONFIG_SECTION_UNSPECIFIED, UNRECOGNIZED -> {
        // Update all sections
        updateDeviceConfigMode(builder, incoming.getDeviceConfigMode());
        updateHostPermissions(builder, incoming);
        updateHostProperties(builder, incoming);
        builder.setDefaultDeviceConfig(
            updateBasicConfig(
                builder.getDefaultDeviceConfig(),
                ConfigConverter.toBasicDeviceConfig(incoming.getDeviceConfig()),
                scope.getDeviceConfigSection()));
        updateDeviceDiscovery(builder, incoming.getDeviceDiscovery());
      }
    }
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
    if (!discovery.getManekiSpecsList().isEmpty()) {
      builder.setDetectorSpecs(
          ConfigConverter.toInternalDetectorSpecs(discovery.getManekiSpecsList()));
    }
  }

  private BasicDeviceConfig updateBasicConfig(
      BasicDeviceConfig current, BasicDeviceConfig incoming, DeviceConfigSection section) {
    BasicDeviceConfig.Builder builder = current.toBuilder();

    DeviceConfigSection sectionToUpdate = section;
    if (environment.isAts()) {
      switch (section) {
        case WIFI, DIMENSIONS, ALL -> {}
        default -> sectionToUpdate = DeviceConfigSection.DEVICE_CONFIG_SECTION_UNKNOWN;
      }
    }

    switch (sectionToUpdate) {
      case PERMISSIONS -> {
        builder
            .clearOwner()
            .addAllOwner(incoming.getOwnerList())
            .clearExecutor()
            .addAllExecutor(incoming.getExecutorList());
      }
      case WIFI -> builder.setDefaultWifi(incoming.getDefaultWifi());
      case DIMENSIONS -> builder.setCompositeDimension(incoming.getCompositeDimension());
      case STABILITY -> {
        builder
            .setMaxConsecutiveTest(incoming.getMaxConsecutiveTest())
            .setMaxConsecutiveFail(incoming.getMaxConsecutiveFail());
      }
      case ALL -> {
        if (environment.isAts()) {
          builder
              .setDefaultWifi(incoming.getDefaultWifi())
              .setCompositeDimension(incoming.getCompositeDimension());
        } else {
          // Update everything in BasicDeviceConfig
          builder
              .clearOwner()
              .addAllOwner(incoming.getOwnerList())
              .clearExecutor()
              .addAllExecutor(incoming.getExecutorList())
              .setDefaultWifi(incoming.getDefaultWifi())
              .setCompositeDimension(incoming.getCompositeDimension())
              .setMaxConsecutiveTest(incoming.getMaxConsecutiveTest())
              .setMaxConsecutiveFail(incoming.getMaxConsecutiveFail());
        }
      }
      default -> {}
    }
    return builder.build();
  }
}
