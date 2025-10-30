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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHealthinessStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceRecoveryTaskStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestResultStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthinessStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RecoveryTaskStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestResultStats;
import javax.inject.Inject;

/** Common implementation of {@link DeviceServiceLogic}. */
public final class DeviceServiceLogicImpl implements DeviceServiceLogic {

  @Inject
  DeviceServiceLogicImpl() {}

  @Override
  public ListenableFuture<DeviceOverview> getDeviceOverview(GetDeviceOverviewRequest request) {
    // TODO: Implement this method.
    return immediateFuture(DeviceOverview.newBuilder().setId(request.getId()).build());
  }

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
}
