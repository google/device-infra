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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.MasterStubAnnotation.StubbyStub;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for quarantining a device. */
@Singleton
public final class QuarantineDeviceHandler {

  private final LabInfoProvider labInfoProvider;
  private final JobSyncStub jobSyncStub;
  private final ListeningExecutorService executor;
  private final Environment environment;
  private final InstantSource instantSource;

  @Inject
  QuarantineDeviceHandler(
      LabInfoProvider labInfoProvider,
      @StubbyStub JobSyncStub jobSyncStub,
      ListeningExecutorService executor,
      Environment environment,
      InstantSource instantSource) {
    this.labInfoProvider = labInfoProvider;
    this.jobSyncStub = jobSyncStub;
    this.executor = executor;
    this.environment = environment;
    this.instantSource = instantSource;
  }

  public ListenableFuture<QuarantineDeviceResponse> quarantineDevice(
      QuarantineDeviceRequest request, UniverseScope universe) {
    String deviceId = request.getId();

    if (!environment.isGoogleInternal()) {
      return immediateFailedFuture(new IllegalArgumentException("Unsupported environment"));
    }

    if (!(universe instanceof UniverseScope.SelfUniverse)) {
      return immediateFailedFuture(
          new IllegalArgumentException("Unsupported universe: " + universe));
    }

    if (!request.hasEndTime()) {
      return immediateFailedFuture(new IllegalArgumentException("End time must be specified"));
    }

    Instant expireTime =
        Instant.ofEpochSecond(request.getEndTime().getSeconds(), request.getEndTime().getNanos());

    // TODO: Update the `UpsertDeviceTempRequiredDimensionsRequest` to use the expire time
    // directly.
    Instant now = instantSource.instant();
    long durationMs = Duration.between(now, expireTime).toMillis();

    if (durationMs <= 0) {
      return immediateFailedFuture(new IllegalArgumentException("End time must be in the future"));
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
                  .setDurationMs(durationMs)
                  .build();

          return transform(
              jobSyncStub.upsertDeviceTempRequiredDimensionsAsync(
                  upsertRequest, environment.isGoogleInternal()),
              unused ->
                  QuarantineDeviceResponse.newBuilder()
                      .setQuarantineExpiry(
                          Timestamp.newBuilder()
                              .setSeconds(expireTime.getEpochSecond())
                              .setNanos(expireTime.getNano())
                              .build())
                      .build(),
              executor);
        },
        executor);
  }
}
