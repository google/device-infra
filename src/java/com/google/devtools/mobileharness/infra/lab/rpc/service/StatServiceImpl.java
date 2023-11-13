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

package com.google.devtools.mobileharness.infra.lab.rpc.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.lab.proto.Stat.Device;
import com.google.wireless.qa.mobileharness.lab.proto.Stat.DeviceStatusDuration;
import com.google.wireless.qa.mobileharness.lab.proto.Stat.Lab;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetDeviceStatRequest;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetDeviceStatResponse;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetLabStatRequest;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetLabStatResponse;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
import com.google.wireless.qa.mobileharness.shared.controller.stat.LabStat;
import com.google.wireless.qa.mobileharness.shared.controller.stat.StatManager;
import com.google.wireless.qa.mobileharness.shared.controller.stat.TestStat;
import java.util.Map.Entry;

/**
 * The service logic class for providing various statistic data of the current lab server. It is
 * used to create RPC services for Stubby and gRPC.
 */
public class StatServiceImpl {

  /** Statistic data of the current lab server. */
  private final LabStat labStat;

  public StatServiceImpl() {
    this(StatManager.getInstance().getOrCreateLabStat(LabLocator.LOCALHOST.ip()));
  }

  @VisibleForTesting
  StatServiceImpl(LabStat labStat) {
    this.labStat = labStat;
  }

  public GetLabStatResponse getLabStat(@SuppressWarnings("unused") GetLabStatRequest req) {
    Lab.Builder labProto = Lab.newBuilder();
    labProto.setStartTime(labStat.getStartTime());
    ImmutableMap<String, DeviceStat> deviceStats = labStat.getDeviceStats();
    for (Entry<String, DeviceStat> entry : deviceStats.entrySet()) {
      labProto.addDeviceStat(convertDeviceStat(entry.getKey(), entry.getValue()));
    }
    return GetLabStatResponse.newBuilder().setLabStat(labProto.build()).build();
  }

  public GetDeviceStatResponse getDeviceStat(GetDeviceStatRequest req)
      throws MobileHarnessException {
    String deviceId = req.getDeviceId();
    DeviceStat deviceStat = getDeviceStat(deviceId);
    return GetDeviceStatResponse.newBuilder()
        .setDeviceStat(convertDeviceStat(deviceId, deviceStat))
        .build();
  }

  /**
   * Finds the {@code DeviceStat} of the given device ID.
   *
   * @throws MobileHarnessException if failed to find the device
   */
  private DeviceStat getDeviceStat(String deviceId) throws MobileHarnessException {
    DeviceStat deviceStat = labStat.getDeviceStat(deviceId);
    if (deviceStat == null) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_RPC_GET_DEVICE_STAT_DEVICE_NOT_FOUND,
          "Can not find device [" + deviceId + "] in current lab server");
    }
    return deviceStat;
  }

  /**
   * Converts the internal {@link DeviceStat} object to proto buffer version without the test
   * history.
   */
  private Device convertDeviceStat(String deviceId, DeviceStat deviceStat) {
    Device.Builder deviceProto = Device.newBuilder();
    deviceProto.setId(deviceId);

    TestStat historicalTestStat = deviceStat.getHistoricalTestStat();
    deviceProto.setTotalTestNum(historicalTestStat.totalTestCount());
    deviceProto.setFirstShowUpTime(deviceStat.getFirstShowUpTime());
    deviceProto.setLastShowUpTime(deviceStat.getLastShowUpTime());
    deviceProto.setLastReadyTime(deviceStat.getLastReadyTime());
    deviceProto.setLastDieTime(deviceStat.getLastDieTime());

    long detectDeviceIntervalMs = Flags.instance().detectDeviceIntervalSec.getNonNull() * 1000L;
    for (Entry<DeviceStatus, Long> statusCount : deviceStat.getStatusCounts().entrySet()) {
      deviceProto.addStatusDuration(
          DeviceStatusDuration.newBuilder()
              .setStatus(statusCount.getKey())
              .setMs(detectDeviceIntervalMs * statusCount.getValue())
              .build());
    }
    for (Entry<DeviceStatus, Long> statusCount : deviceStat.getLastStatusCounts().entrySet()) {
      deviceProto.addLastStatusDuration(
          DeviceStatusDuration.newBuilder()
              .setStatus(statusCount.getKey())
              .setMs(detectDeviceIntervalMs * statusCount.getValue())
              .build());
    }
    return deviceProto.build();
  }
}
