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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchUpdateDeviceDimensionConfigs RPC. */
@Singleton
public final class BatchUpdateDeviceDimensionConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchDeviceConfigHelper batchDeviceConfigHelper;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;
  private final ListeningExecutorService executor;

  @Inject
  BatchUpdateDeviceDimensionConfigsHandler(
      BatchDeviceConfigHelper batchDeviceConfigHelper,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      Environment environment,
      ListeningExecutorService executor) {
    this.batchDeviceConfigHelper = batchDeviceConfigHelper;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
    this.executor = executor;
  }

  public ListenableFuture<BatchUpdateDeviceDimensionConfigsResponse>
      batchUpdateDeviceDimensionConfigs(
          BatchUpdateDeviceDimensionConfigsRequest request, Optional<String> username) {
    logger.atInfo().log(
        "BatchUpdateDeviceDimensionConfigsRequest: %d devices, dimension=%s, list=%s",
        request.getDevicesCount(), request.getDimensionName(), request.getDimensionList());

    try {
      configServiceCapabilityFactory.create(UniverseScope.SELF).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(FeServiceException.failedPrecondition(e.getMessage()));
    }

    String dimensionName = BatchDeviceConfigUtil.cleanDimensionName(request.getDimensionName());
    DimensionList dimensionList = request.getDimensionList();
    ParsedTarget parsedTarget = parseTargetValues(dimensionName, request.getTarget());
    if (parsedTarget.error().isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(parsedTarget.error().get()));
    }

    Optional<String> invalidReason =
        BatchDeviceConfigUtil.checkDimensionTarget(
                dimensionName, dimensionList, environment.isAts())
            .or(() -> BatchDeviceConfigHelper.checkDeviceTargetCount(request.getDevicesList()));
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }

    ImmutableList<String> targetValues = parsedTarget.values();

    return FluentFuture.from(
            batchDeviceConfigHelper.evaluateDevices(request.getDevicesList(), username))
        .transformAsync(
            evaluations ->
                applyDimensionUpdates(evaluations, dimensionName, dimensionList, targetValues),
            executor);
  }

  private ListenableFuture<BatchUpdateDeviceDimensionConfigsResponse> applyDimensionUpdates(
      List<DeviceEvaluation> evaluations,
      String dimensionName,
      DimensionList dimensionList,
      List<String> targetValues) {
    return FluentFuture.from(
            batchDeviceConfigHelper.commitDeviceConfigUpdates(
                evaluations,
                existingConfig -> {
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
                  return existingConfig.toBuilder().setBasicConfig(updatedBasicConfig).build();
                },
                "Failed to update device configuration."))
        .transform(
            errors ->
                BatchUpdateDeviceDimensionConfigsResponse.newBuilder().putAllErrors(errors).build(),
            executor);
  }

  private record ParsedTarget(ImmutableList<String> values, Optional<String> error) {
    static ParsedTarget valid(ImmutableList<String> values) {
      return new ParsedTarget(values, Optional.empty());
    }

    static ParsedTarget invalid(String error) {
      return new ParsedTarget(ImmutableList.of(), Optional.of(error));
    }
  }

  private static ParsedTarget parseTargetValues(
      String dimensionName, TargetDimensionValues target) {
    return switch (target.getTargetCase()) {
      case CLEAR_VALUES -> ParsedTarget.valid(ImmutableList.of());
      case VALUE_LIST -> {
        ImmutableList<String> normalized =
            BatchDeviceConfigUtil.normalizeValues(target.getValueList().getValuesList());
        if (normalized.isEmpty()) {
          yield ParsedTarget.invalid(
              "Dimension valueList must contain at least one non-blank value; use clearValues to"
                  + " clear.");
        }
        Optional<String> valueError =
            BatchDeviceConfigUtil.checkDimensionValues(dimensionName, normalized);
        yield valueError.isPresent()
            ? ParsedTarget.invalid(valueError.get())
            : ParsedTarget.valid(normalized);
      }
      case TARGET_NOT_SET ->
          ParsedTarget.invalid("Target dimension values must be set (clearValues or valueList).");
    };
  }
}
