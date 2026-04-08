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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetTestbedConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestbedConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetTestbedConfig RPC. */
@Singleton
public final class GetTestbedConfigHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceDataLoader deviceDataLoader;
  private final TestbedConfigBuilder testbedConfigBuilder;

  @Inject
  GetTestbedConfigHandler(
      DeviceDataLoader deviceDataLoader, TestbedConfigBuilder testbedConfigBuilder) {
    this.deviceDataLoader = deviceDataLoader;
    this.testbedConfigBuilder = testbedConfigBuilder;
  }

  public ListenableFuture<TestbedConfig> getTestbedConfig(
      GetTestbedConfigRequest request, UniverseScope universe) {
    String deviceId = request.getId();
    Preconditions.checkArgument(!deviceId.isEmpty(), "Device ID cannot be empty");

    return Futures.transform(
        deviceDataLoader.loadDeviceData(deviceId, universe),
        deviceData -> {
          logger.atFine().log("Building testbed config for %s", deviceId);
          return testbedConfigBuilder.buildTestbedConfig(deviceId, deviceData);
        },
        directExecutor());
  }
}
