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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the UpdateDeviceConfig RPC. */
@Singleton
public final class UpdateDeviceConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigurationProvider configurationProvider;
  private final DeviceDataLoader deviceDataLoader;
  private final GroupMembershipProvider groupMembershipProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  UpdateDeviceConfigHandler(
      ConfigurationProvider configurationProvider,
      DeviceDataLoader deviceDataLoader,
      GroupMembershipProvider groupMembershipProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.deviceDataLoader = deviceDataLoader;
    this.groupMembershipProvider = groupMembershipProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<UpdateDeviceConfigResponse> updateDeviceConfig(
      UpdateDeviceConfigRequest request, Optional<String> username) {
    logger.atInfo().log("Updating device config for %s", request.getId());

    try {
      validateRequest(request);
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      return immediateFuture(
          UpdateDeviceConfigResponse.newBuilder()
              .setSuccess(false)
              .setError(
                  UpdateError.newBuilder()
                      .setCode(UpdateError.Code.VALIDATION_ERROR)
                      .setMessage(e.getMessage()))
              .build());
    }

    BasicDeviceConfig incoming = ConfigConverter.toBasicDeviceConfig(request.getConfig());

    return FluentFuture.from(
            deviceDataLoader.loadDeviceData(request.getId(), request.getUniverse()))
        .transformAsync(this::checkHostManaged, executor)
        .transformAsync(
            optResponse ->
                optResponse.isPresent()
                    ? immediateFuture(optResponse)
                    : performSelfLockoutCheck(request, incoming, username),
            executor)
        .transformAsync(
            optResponse ->
                optResponse.isPresent()
                    ? immediateFuture(optResponse.get())
                    : saveUpdatedConfig(request, incoming),
            executor);
  }

  private void validateRequest(UpdateDeviceConfigRequest request) {
    configServiceCapabilityFactory.create(request.getUniverse()).checkConfigServiceAvailability();

    if (environment.isAts()) {
      switch (request.getSection()) {
        case WIFI, DIMENSIONS, ALL -> {}
        default ->
            throw new IllegalArgumentException(
                String.format(
                    "Configuration section '%s' is not supported in the ATS environment.",
                    request.getSection()));
      }
    }
  }

  private ListenableFuture<Optional<UpdateDeviceConfigResponse>> checkHostManaged(
      DeviceData deviceData) {
    if (deviceData.isHostManaged()) {
      return immediateFuture(
          Optional.of(
              UpdateDeviceConfigResponse.newBuilder()
                  .setSuccess(false)
                  .setError(
                      UpdateError.newBuilder()
                          .setCode(UpdateError.Code.VALIDATION_ERROR)
                          .setMessage(
                              "Cannot update configuration for host-managed device. Please"
                                  + " update the host configuration instead."))
                  .build()));
    }
    return immediateFuture(Optional.empty());
  }

  private ListenableFuture<Optional<UpdateDeviceConfigResponse>> performSelfLockoutCheck(
      UpdateDeviceConfigRequest request, BasicDeviceConfig incoming, Optional<String> endUser) {
    return Futures.transform(
        isSelfLockout(request, incoming, endUser),
        isSelfLockout -> {
          if (isSelfLockout) {
            return Optional.of(
                UpdateDeviceConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setError(
                        UpdateError.newBuilder()
                            .setCode(UpdateError.Code.SELF_LOCKOUT_DETECTED)
                            .setMessage(
                                "Self-lockout detected. You are removing yourself from the"
                                    + " owners list. Use 'override_self_lockout' to proceed"
                                    + " if this is intentional."))
                    .build());
          }
          return Optional.empty();
        },
        executor);
  }

  private ListenableFuture<UpdateDeviceConfigResponse> saveUpdatedConfig(
      UpdateDeviceConfigRequest request, BasicDeviceConfig incoming) {
    return Futures.transformAsync(
        configurationProvider.getDeviceConfig(request.getId(), request.getUniverse()),
        existingConfigOpt -> {
          DeviceConfig.Builder configToUpdate =
              existingConfigOpt.isPresent()
                  ? existingConfigOpt.get().toBuilder()
                  : DeviceConfig.newBuilder().setUuid(request.getId());

          BasicDeviceConfig updatedBasicConfig =
              updateBasicConfig(configToUpdate.getBasicConfig(), request, incoming);
          configToUpdate.setBasicConfig(updatedBasicConfig);

          return Futures.transform(
              configurationProvider.updateDeviceConfig(
                  request.getId(), configToUpdate.build(), request.getUniverse()),
              unused -> UpdateDeviceConfigResponse.newBuilder().setSuccess(true).build(),
              executor);
        },
        executor);
  }

  private BasicDeviceConfig updateBasicConfig(
      BasicDeviceConfig current, UpdateDeviceConfigRequest request, BasicDeviceConfig incoming) {
    BasicDeviceConfig.Builder builder = current.toBuilder();

    switch (request.getSection()) {
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

  private ListenableFuture<Boolean> isSelfLockout(
      UpdateDeviceConfigRequest request, BasicDeviceConfig incoming, Optional<String> username) {
    if (request.getOptions().getOverrideSelfLockout() || username.isEmpty()) {
      return immediateFuture(false);
    }

    // Check if permissions are being updated
    switch (request.getSection()) {
      case PERMISSIONS, ALL, DEVICE_CONFIG_SECTION_UNKNOWN, UNRECOGNIZED -> {}
      default -> {
        return immediateFuture(false);
      }
    }

    List<String> newOwners = incoming.getOwnerList();
    String user = username.get();

    if (newOwners.contains(user)) {
      return immediateFuture(false);
    }

    return Futures.transform(
        groupMembershipProvider.isMemberOfAny(user, newOwners),
        (Boolean isMember) -> !isMember,
        executor);
  }
}
