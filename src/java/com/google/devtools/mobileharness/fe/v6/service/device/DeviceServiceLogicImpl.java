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

package com.google.devtools.mobileharness.fe.v6.service.device;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.GetDeviceOverviewHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHealthinessStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceRecoveryTaskStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestResultStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthinessStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RecoveryTaskStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RemoteControlRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RemoteControlResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestResultStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Common implementation of {@link DeviceServiceLogic}. */
@Singleton
public final class DeviceServiceLogicImpl implements DeviceServiceLogic {

  private final GetDeviceOverviewHandler getDeviceOverviewHandler;

  @Inject
  DeviceServiceLogicImpl(GetDeviceOverviewHandler getDeviceOverviewHandler) {
    this.getDeviceOverviewHandler = getDeviceOverviewHandler;
  }

  @Override
  public ListenableFuture<DeviceOverviewPageData> getDeviceOverview(
      GetDeviceOverviewRequest request) {
    return getDeviceOverviewHandler.getDeviceOverview(request);
  }

  // Methods for other RPCs (GetDeviceHealthinessStats, etc.)
  @Override
  public ListenableFuture<HealthinessStats> getDeviceHealthinessStats(
      GetDeviceHealthinessStatsRequest request) {
    // TODO: Implement this method.
    return immediateFuture(HealthinessStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<TestResultStats> getDeviceTestResultStats(
      GetDeviceTestResultStatsRequest request) {
    // TODO: Implement this method.
    return immediateFuture(TestResultStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<RecoveryTaskStats> getDeviceRecoveryTaskStats(
      GetDeviceRecoveryTaskStatsRequest request) {
    // TODO: Implement this method.
    return immediateFuture(RecoveryTaskStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<TakeScreenshotResponse> takeScreenshot(TakeScreenshotRequest request) {
    // TODO: Implement this method.
    return immediateFuture(TakeScreenshotResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<GetLogcatResponse> getLogcat(GetLogcatRequest request) {
    // TODO: Implement this method.
    return immediateFuture(GetLogcatResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<QuarantineDeviceResponse> quarantineDevice(
      QuarantineDeviceRequest request) {
    // TODO: Implement this method.
    return immediateFuture(QuarantineDeviceResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<UnquarantineDeviceResponse> unquarantineDevice(
      UnquarantineDeviceRequest request) {
    // TODO: Implement this method.
    return immediateFuture(UnquarantineDeviceResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<RemoteControlResponse> remoteControl(RemoteControlRequest request) {
    // TODO: Implement this method.
    return immediateFuture(RemoteControlResponse.getDefaultInstance());
  }
}
