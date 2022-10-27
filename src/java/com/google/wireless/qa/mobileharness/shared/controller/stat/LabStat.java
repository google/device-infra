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

package com.google.wireless.qa.mobileharness.shared.controller.stat;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * Statistic data of the current lab server. All statistic data provided is defined in:
 * //java/com/google/wireless/qa/mobileharness/lab/proto/stat.proto
 *
 * @author derekchen@google.com (Derek Chen)
 */
public class LabStat {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Start time in millisecondes of the current lab server. */
  private final long startTime;

  /** Statistic data of any devices ever show up in the current lab server. */
  private final ConcurrentMap<String, DeviceStat> deviceStats =
      new ConcurrentHashMap<String, DeviceStat>();

  public LabStat() {
    this(Clock.systemUTC());
  }

  /** Constructor for test only. */
  @VisibleForTesting
  LabStat(Clock clock) {
    startTime = clock.millis();
  }

  /** Returns the start time in milliseconds of the current lab server. */
  public long getStartTime() {
    return startTime;
  }

  /**
   * Gets existing {@code DeviceStat} according to the device ID.
   *
   * @return the existing {@code DeviceStat} of the device, or null if never exist
   */
  @Nullable
  public DeviceStat getDeviceStat(String deviceId) {
    String deviceControlId = deviceId;
    if (DeviceIdManager.getInstance().containsUuid(deviceId)) {
      deviceControlId =
          DeviceIdManager.getInstance().getDeviceIdFromUuid(deviceId).get().controlId();
    }
    return deviceStats.get(deviceControlId);
  }

  /**
   * Gets {@code DeviceStat} according to the device ID. If not exists, creates one.
   *
   * @return the existing or newly-created {@code DeviceStat} of the device
   */
  public DeviceStat getOrCreateDeviceStat(String deviceId) {
    DeviceStat newDeviceStat = new DeviceStat();
    DeviceStat deviceStat = deviceStats.putIfAbsent(deviceId, newDeviceStat);
    if (deviceStat == null) {
      logger.atInfo().log("New DeviceStat created for %s", deviceId);
      deviceStat = newDeviceStat;
    }
    return deviceStat;
  }

  /** Returns a immutable copy of the device <ID, DeviceStat> mapping. */
  public ImmutableMap<String, DeviceStat> getDeviceStats() {
    return ImmutableMap.copyOf(deviceStats);
  }
}
