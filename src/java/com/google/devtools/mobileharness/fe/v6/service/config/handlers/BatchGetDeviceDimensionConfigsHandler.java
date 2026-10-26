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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.BatchDeviceConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsResponse.DeviceDimensionConfigEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the BatchGetDeviceDimensionConfigs RPC. */
@Singleton
public final class BatchGetDeviceDimensionConfigsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchDeviceConfigHelper batchDeviceConfigHelper;
  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final ListeningExecutorService executor;

  @Inject
  BatchGetDeviceDimensionConfigsHandler(
      BatchDeviceConfigHelper batchDeviceConfigHelper,
      ConfigServiceCapabilityFactory configServiceCapabilityFactory,
      ListeningExecutorService executor) {
    this.batchDeviceConfigHelper = batchDeviceConfigHelper;
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.executor = executor;
  }

  public ListenableFuture<BatchGetDeviceDimensionConfigsResponse> batchGetDeviceDimensionConfigs(
      BatchGetDeviceDimensionConfigsRequest request,
      UniverseScope universe,
      Optional<String> username) {
    logger.atInfo().log("BatchGetDeviceDimensionConfigsRequest: %s", request);

    try {
      configServiceCapabilityFactory.create(universe).checkConfigServiceAvailability();
    } catch (UnsupportedOperationException e) {
      return immediateFailedFuture(e);
    }

    String dimensionName = BatchDeviceConfigUtil.cleanDimensionName(request.getDimensionName());
    DimensionList dimensionList = request.getDimensionList();
    Optional<String> invalidReason =
        BatchDeviceConfigUtil.checkDimensionTarget(dimensionName, dimensionList)
            .or(() -> BatchDeviceConfigHelper.checkDeviceIdCount(request.getDeviceIdsList()));
    if (invalidReason.isPresent()) {
      return immediateFailedFuture(FeServiceException.invalidArgument(invalidReason.get()));
    }
    Optional<ImmutableList<String>> targetCanon = parseTargetCanonicalValues(request);

    return Futures.transform(
        batchDeviceConfigHelper.evaluateDevices(request.getDeviceIdsList(), universe, username),
        evaluations -> {
          ImmutableList<DeviceDimensionConfigEntry> entries =
              evaluations.stream()
                  .map(
                      evaluation ->
                          buildEntry(evaluation, dimensionName, dimensionList, targetCanon))
                  .collect(toImmutableList());
          return BatchGetDeviceDimensionConfigsResponse.newBuilder().addAllEntries(entries).build();
        },
        executor);
  }

  private static DeviceDimensionConfigEntry buildEntry(
      DeviceEvaluation evaluation,
      String dimensionName,
      DimensionList dimensionList,
      Optional<ImmutableList<String>> targetCanon) {
    ImmutableList<String> currentValues =
        BatchDeviceConfigUtil.getDimensionValues(
            evaluation.effectiveDeviceConfig().getBasicConfig(), dimensionName, dimensionList);

    DeviceConfigChangeStatus status;
    if (!evaluation.writable()) {
      status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY;
    } else if (targetCanon.isEmpty()) {
      status = DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE;
    } else {
      ImmutableList<String> currentCanon = BatchDeviceConfigUtil.canonicalizeValues(currentValues);
      status =
          currentCanon.equals(targetCanon.get())
              ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
              : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE;
    }

    return DeviceDimensionConfigEntry.newBuilder()
        .setDeviceId(evaluation.deviceId())
        .addAllValues(currentValues)
        .setStatus(status)
        .setReadOnlyReason(evaluation.readOnlyReason())
        .build();
  }

  private static Optional<ImmutableList<String>> parseTargetCanonicalValues(
      BatchGetDeviceDimensionConfigsRequest request) {
    TargetDimensionValues target = request.getTarget();
    return switch (target.getTargetCase()) {
      case CLEAR_VALUES -> Optional.of(ImmutableList.of());
      case VALUE_LIST ->
          Optional.of(
              BatchDeviceConfigUtil.canonicalizeValues(target.getValueList().getValuesList()));
      case TARGET_NOT_SET -> Optional.empty();
    };
  }
}
