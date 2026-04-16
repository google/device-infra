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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.GetDeviceHeaderInfoHandler;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.GetDeviceOverviewHandler;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.GetLogcatHandler;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.GetTestbedConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.QuarantineDeviceHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHealthinessStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceRecoveryTaskStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestResultStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetTestbedConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthinessStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RecoveryTaskStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestResultStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestbedConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Common implementation of {@link DeviceServiceLogic}. */
@Singleton
public final class DeviceServiceLogicImpl implements DeviceServiceLogic {

  private final GetDeviceHeaderInfoHandler getDeviceHeaderInfoHandler;
  private final GetDeviceOverviewHandler getDeviceOverviewHandler;
  private final GetLogcatHandler getLogcatHandler;
  private final GetTestbedConfigHandler getTestbedConfigHandler;
  private final UniverseFactory universeFactory;
  private final QuarantineDeviceHandler quarantineDeviceHandler;

  @Inject
  DeviceServiceLogicImpl(
      GetDeviceHeaderInfoHandler getDeviceHeaderInfoHandler,
      GetDeviceOverviewHandler getDeviceOverviewHandler,
      GetTestbedConfigHandler getTestbedConfigHandler,
      GetLogcatHandler getLogcatHandler,
      UniverseFactory universeFactory,
      QuarantineDeviceHandler quarantineDeviceHandler) {
    this.getDeviceHeaderInfoHandler = getDeviceHeaderInfoHandler;
    this.getDeviceOverviewHandler = getDeviceOverviewHandler;
    this.getTestbedConfigHandler = getTestbedConfigHandler;
    this.getLogcatHandler = getLogcatHandler;
    this.universeFactory = universeFactory;
    this.quarantineDeviceHandler = quarantineDeviceHandler;
  }

  @Override
  public ListenableFuture<DeviceOverviewPageData> getDeviceOverview(
      GetDeviceOverviewRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getDeviceOverviewHandler.getDeviceOverview(request, universe);
  }

  @Override
  public ListenableFuture<DeviceHeaderInfo> getDeviceHeaderInfo(
      GetDeviceHeaderInfoRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getDeviceHeaderInfoHandler.getDeviceHeaderInfo(request, universe);
  }

  // Methods for other RPCs (GetDeviceHealthinessStats, etc.)
  @Override
  public ListenableFuture<HealthinessStats> getDeviceHealthinessStats(
      GetDeviceHealthinessStatsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(HealthinessStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<TestResultStats> getDeviceTestResultStats(
      GetDeviceTestResultStatsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(TestResultStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<RecoveryTaskStats> getDeviceRecoveryTaskStats(
      GetDeviceRecoveryTaskStatsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(RecoveryTaskStats.getDefaultInstance());
  }

  @Override
  public ListenableFuture<TakeScreenshotResponse> takeScreenshot(TakeScreenshotRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(TakeScreenshotResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<GetLogcatResponse> getLogcat(GetLogcatRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getLogcatHandler.getLogcat(request, universe);
  }

  @Override
  public ListenableFuture<QuarantineDeviceResponse> quarantineDevice(
      QuarantineDeviceRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return quarantineDeviceHandler.quarantineDevice(request, universe);
  }

  @Override
  public ListenableFuture<UnquarantineDeviceResponse> unquarantineDevice(
      UnquarantineDeviceRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(UnquarantineDeviceResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<TestbedConfig> getTestbedConfig(GetTestbedConfigRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getTestbedConfigHandler.getTestbedConfig(request, universe);
  }
}
