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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.Math.min;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Helper class for ATS applications to create job config. */
public final class CreateJobConfigUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";

  private final DeviceQuerier deviceQuerier;

  public CreateJobConfigUtil(DeviceQuerier deviceQuerier) {
    this.deviceQuerier = deviceQuerier;
  }

  /**
   * Gets a list of SubDeviceSpec for the job. One SubDeviceSpec maps to one subdevice used for
   * running the job as the job may need multiple devices to run the test.
   */
  public ImmutableList<SubDeviceSpec> getSubDeviceSpecList(
      List<String> passedInDeviceSerials, int shardCount)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> allAndroidDevices = getAllAndroidDevices();
    logger.atInfo().log("All android devices: %s", allAndroidDevices);
    if (passedInDeviceSerials.isEmpty()) {
      return pickAndroidOnlineDevices(allAndroidDevices, shardCount);
    }

    ArrayList<String> existingPassedInDeviceSerials = new ArrayList<>();
    passedInDeviceSerials.forEach(
        serial -> {
          if (allAndroidDevices.contains(serial)) {
            existingPassedInDeviceSerials.add(serial);
          } else {
            logger.atInfo().log("Passed in device serial [%s] is not detected, skipped.", serial);
          }
        });
    if (existingPassedInDeviceSerials.isEmpty()) {
      logger.atInfo().log("None of passed in devices exist [%s], skipped.", passedInDeviceSerials);
      return ImmutableList.of();
    }
    return existingPassedInDeviceSerials.stream()
        .map(
            serial ->
                SubDeviceSpec.newBuilder()
                    .setType(ANDROID_REAL_DEVICE_TYPE)
                    .setDimensions(StringMap.newBuilder().putContent("serial", serial))
                    .build())
        .collect(toImmutableList());
  }

  private ImmutableList<SubDeviceSpec> pickAndroidOnlineDevices(
      Set<String> allAndroidOnlineDevices, int shardCount) {
    if (shardCount <= 1 && !allAndroidOnlineDevices.isEmpty()) {
      return ImmutableList.of(SubDeviceSpec.newBuilder().setType(ANDROID_REAL_DEVICE_TYPE).build());
    }
    int numOfNeededDevices = min(allAndroidOnlineDevices.size(), shardCount);
    ImmutableList.Builder<SubDeviceSpec> deviceSpecList = ImmutableList.builder();
    for (int i = 0; i < numOfNeededDevices; i++) {
      deviceSpecList.add(SubDeviceSpec.newBuilder().setType(ANDROID_REAL_DEVICE_TYPE).build());
    }
    return deviceSpecList.build();
  }

  private ImmutableSet<String> getAllAndroidDevices()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .map(DeviceInfo::getId)
        .collect(toImmutableSet());
  }
}
