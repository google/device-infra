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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchUpdateDeviceDimensionConfigs RPC. */
@Singleton
public final class BatchUpdateDeviceDimensionConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchDeviceConfigHelper batchDeviceConfigHelper;
  private final ConfigurationProvider configurationProvider;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  BatchUpdateDeviceDimensionConfigsHandler(
      BatchDeviceConfigHelper batchDeviceConfigHelper,
      ConfigurationProvider configurationProvider,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.batchDeviceConfigHelper = batchDeviceConfigHelper;
    this.configurationProvider = configurationProvider;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<BatchUpdateDeviceDimensionConfigsResponse>
      batchUpdateDeviceDimensionConfigs(
          BatchUpdateDeviceDimensionConfigsRequest request,
          UniverseScope universe,
          Optional<String> username) {
    logger.atInfo().log("BatchUpdateDeviceDimensionConfigsRequest: %s", request);

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      if (request.getDeviceIdsList().isEmpty()) {
        return immediateFailedFuture(e);
      }
      return immediateFuture(
          buildAllFailedResponse(
              request.getDeviceIdsList(), UpdateError.Code.VALIDATION_ERROR, e.getMessage()));
    }

    Optional<ImmutableList<String>> targetValuesOpt = parseTargetValues(request);
    if (targetValuesOpt.isEmpty()) {
      return immediateFailedFuture(
          FeServiceException.invalidArgument(
              "Target dimension values must be set (clearValues or valueList)."));
    }

    String dimensionName = BatchDeviceConfigUtil.cleanDimensionName(request.getDimensionName());
    DimensionList dimensionList = request.getDimensionList();
    Optional<String> invalidReason =
        BatchDeviceConfigUtil.checkDimensionTarget(dimensionName, dimensionList)
            .or(() -> BatchDeviceConfigHelper.checkDeviceIdCount(request.getDeviceIdsList()));
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    ImmutableList<String> targetValues = targetValuesOpt.get();

    return FluentFuture.from(
            batchDeviceConfigHelper.evaluateDevices(request.getDeviceIdsList(), universe, username))
        .transformAsync(
            evaluations ->
                applyDimensionUpdates(
                    evaluations, dimensionName, dimensionList, targetValues, universe),
            executor);
  }

  private ListenableFuture<BatchUpdateDeviceDimensionConfigsResponse> applyDimensionUpdates(
      List<DeviceEvaluation> evaluations,
      String dimensionName,
      DimensionList dimensionList,
      List<String> targetValues,
      UniverseScope universe) {
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
        continue;
      }

      DeviceConfig existingConfig =
          evaluation
              .rawIndividualConfig()
              .orElseGet(() -> DeviceConfig.newBuilder().setUuid(deviceId).build());
      DeviceCompositeDimension updatedComposite =
          BatchDeviceConfigUtil.replaceDimensionValues(
              existingConfig.getBasicConfig().getCompositeDimension(),
              dimensionName,
              dimensionList,
              targetValues);
      BasicDeviceConfig updatedBasicConfig =
          existingConfig.getBasicConfig().toBuilder()
              .setCompositeDimension(updatedComposite)
              .build();
      DeviceConfig updatedDeviceConfig =
          existingConfig.toBuilder().setUuid(deviceId).setBasicConfig(updatedBasicConfig).build();
      configsToUpdate.put(deviceId, updatedDeviceConfig);
    }

    if (configsToUpdate.isEmpty()) {
      return immediateFuture(
          BatchUpdateDeviceDimensionConfigsResponse.newBuilder().putAllErrors(errors).build());
    }

    return FluentFuture.from(
            configurationProvider.updateDeviceConfigs(
                ImmutableMap.copyOf(configsToUpdate), universe))
        .transform(
            backendErrors -> {
              backendErrors.forEach(
                  (deviceId, errMsg) ->
                      errors.put(
                          deviceId,
                          UpdateError.newBuilder()
                              .setCode(UpdateError.Code.UNKNOWN)
                              .setMessage(
                                  isNullOrEmpty(errMsg)
                                      ? "Failed to update device configuration."
                                      : errMsg)
                              .build()));
              return BatchUpdateDeviceDimensionConfigsResponse.newBuilder()
                  .putAllErrors(errors)
                  .build();
            },
            executor)
        .catching(
            Throwable.class,
            t -> {
              logger.atWarning().withCause(t).log(
                  "BatchUpdateDeviceDimensionConfigs backend call failed");
              String fallbackMsg =
                  !isNullOrEmpty(t.getMessage())
                      ? t.getMessage()
                      : "Failed to update device configuration.";
              for (String deviceId : configsToUpdate.keySet()) {
                errors.put(
                    deviceId,
                    UpdateError.newBuilder()
                        .setCode(UpdateError.Code.UNKNOWN)
                        .setMessage(fallbackMsg)
                        .build());
              }
              return BatchUpdateDeviceDimensionConfigsResponse.newBuilder()
                  .putAllErrors(errors)
                  .build();
            },
            executor);
  }

  private static Optional<ImmutableList<String>> parseTargetValues(
      BatchUpdateDeviceDimensionConfigsRequest request) {
    TargetDimensionValues target = request.getTarget();
    return switch (target.getTargetCase()) {
      case CLEAR_VALUES -> Optional.of(ImmutableList.of());
      case VALUE_LIST ->
          Optional.of(
              target.getValueList().getValuesList().stream()
                  .filter(v -> !v.isEmpty())
                  .collect(toImmutableList()));
      case TARGET_NOT_SET -> Optional.empty();
    };
  }

  private static BatchUpdateDeviceDimensionConfigsResponse buildAllFailedResponse(
      List<String> deviceIds, UpdateError.Code code, String message) {
    UpdateError error = UpdateError.newBuilder().setCode(code).setMessage(message).build();
    return BatchUpdateDeviceDimensionConfigsResponse.newBuilder()
        .putAllErrors(deviceIds.stream().distinct().collect(toImmutableMap(id -> id, id -> error)))
        .build();
  }
}
