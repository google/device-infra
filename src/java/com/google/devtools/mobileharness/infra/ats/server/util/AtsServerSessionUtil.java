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

package com.google.devtools.mobileharness.infra.ats.server.util;

import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import javax.inject.Inject;

/** Utility class for Ats Server Session. */
public class AtsServerSessionUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DeviceQuerier deviceQuerier;
  private final NetworkUtil networkUtil;

  @Inject
  AtsServerSessionUtil(DeviceQuerier deviceQuerier, NetworkUtil networkUtil) {
    this.deviceQuerier = deviceQuerier;
    this.networkUtil = networkUtil;
  }

  /**
   * Checks if the current OLCS instance is running on the same host as the lab.
   *
   * @return true if the current OLCS instance is running on the same host as the lab.
   */
  public boolean isLocalMode() throws InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to query device");
      return false;
    }
    String olcsHostName = "";
    try {
      olcsHostName = networkUtil.getLocalHostName();
    } catch (MobileHarnessException ignored) {
      return false;
    }
    if (olcsHostName.isEmpty()) {
      return false;
    }
    for (DeviceInfo deviceInfo : queryResult.getDeviceInfoList()) {
      String labHostName =
          deviceInfo.getDimensionList().stream()
              .filter(
                  dimension ->
                      dimension
                          .getName()
                          .equals(Ascii.toLowerCase(Dimension.Name.HOST_NAME.name())))
              .findFirst()
              .map(DeviceQuery.Dimension::getValue)
              .orElse("");
      if (!labHostName.equals(olcsHostName)) {
        return false;
      }
    }
    return true;
  }
}
