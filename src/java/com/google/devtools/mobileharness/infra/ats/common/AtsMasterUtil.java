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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import javax.inject.Inject;

/** Utility class for Mobile Harness master. */
public class AtsMasterUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceQuerier deviceQuerier;

  @Inject
  AtsMasterUtil(DeviceQuerier deviceQuerier) {
    this.deviceQuerier = deviceQuerier;
  }

  /**
   * Queries Android devices from master.
   *
   * <p>Note that the missing devices are filtered out in the device query.
   */
  public ImmutableList<DeviceInfo> queryAndroidDevicesFromMaster()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
      logger.atInfo().log(
          "Device statuses from master: %s",
          queryResult.getDeviceInfoList().stream()
              .map(deviceInfo -> deviceInfo.getId() + ": " + deviceInfo.getStatus())
              .collect(toImmutableList()));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .filter(
            deviceInfo ->
                !Ascii.equalsIgnoreCase(deviceInfo.getStatus(), Device.DeviceStatus.MISSING.name()))
        .collect(toImmutableList());
  }
}
