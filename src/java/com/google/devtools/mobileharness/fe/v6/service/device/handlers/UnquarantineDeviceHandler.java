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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.Futures.transformAsync;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.MasterStubAnnotation.StubbyStub;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for unquarantining a device. */
@Singleton
public final class UnquarantineDeviceHandler {

  // Short duration to make the quarantine expire.
  private static final Duration UNQUARANTINE_DURATION = Duration.ofMinutes(1);

  private final LabInfoProvider labInfoProvider;
  private final JobSyncStub jobSyncStub;
  private final ListeningExecutorService executor;
  private final Environment environment;

  @Inject
  UnquarantineDeviceHandler(
      LabInfoProvider labInfoProvider,
      @StubbyStub JobSyncStub jobSyncStub,
      ListeningExecutorService executor,
      Environment environment) {
    this.labInfoProvider = labInfoProvider;
    this.jobSyncStub = jobSyncStub;
    this.executor = executor;
    this.environment = environment;
  }

  public ListenableFuture<UnquarantineDeviceResponse> unquarantineDevice(
      UnquarantineDeviceRequest request, UniverseScope universe) {
    String deviceId = request.getId();

    if (!environment.isGoogleInternal()) {
      return immediateFailedFuture(new IllegalArgumentException("Unsupported environment"));
    }

    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          new IllegalArgumentException("Unsupported universe: " + universe));
    }

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        DeviceInfoLookupHelper.lookUpDeviceInfoAsync(labInfoProvider, deviceId, universe, executor);

    return transformAsync(
        deviceInfoFuture,
        deviceInfo -> {
          UpsertDeviceTempRequiredDimensionsRequest upsertRequest =
              UpsertDeviceTempRequiredDimensionsRequest.newBuilder()
                  .setDeviceLocator(deviceInfo.getDeviceLocator())
                  .addTempRequiredDimension(
                      DeviceDimension.newBuilder().setName("quarantined").setValue("true").build())
                  .setDurationMs(UNQUARANTINE_DURATION.toMillis())
                  .build();

          return transform(
              jobSyncStub.upsertDeviceTempRequiredDimensionsAsync(
                  upsertRequest, environment.isGoogleInternal()),
              unused -> UnquarantineDeviceResponse.getDefaultInstance(),
              executor);
        },
        executor);
  }
}
