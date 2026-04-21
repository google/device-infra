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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.util.Timestamps;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class QuarantineDeviceHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private JobSyncStub jobSyncStub;
  @Bind private ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private Environment environment;
  @Bind @Mock private InstantSource instantSource;

  @Inject private QuarantineDeviceHandler quarantineDeviceHandler;

  @Before
  public void setUp() {
    when(environment.isGoogleInternal()).thenReturn(true);
    when(instantSource.instant()).thenReturn(Instant.ofEpochMilli(1000000000L));
    when(jobSyncStub.upsertDeviceTempRequiredDimensionsAsync(any(), any(Boolean.class)))
        .thenReturn(
            immediateFuture(UpsertDeviceTempRequiredDimensionsResponse.getDefaultInstance()));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void quarantineDevice_success() throws Exception {
    String deviceId = "device_id";
    String hostName = "host_name";
    String universe = "google_1p";
    QuarantineDeviceRequest request =
        QuarantineDeviceRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setEndTime(Timestamps.fromSeconds(1086400))
            .build();

    DeviceLocator deviceLocator =
        DeviceLocator.newBuilder()
            .setId(deviceId)
            .setLabLocator(LabLocator.newBuilder().setHostName(hostName))
            .build();

    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(deviceLocator))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));

    QuarantineDeviceResponse response =
        quarantineDeviceHandler.quarantineDevice(request, UniverseScope.SELF).get();

    assertThat(response)
        .isEqualTo(
            QuarantineDeviceResponse.newBuilder()
                .setQuarantineExpiry(Timestamps.fromSeconds(1000000 + 24 * 3600))
                .build());

    ArgumentCaptor<UpsertDeviceTempRequiredDimensionsRequest> upsertRequestCaptor =
        ArgumentCaptor.forClass(UpsertDeviceTempRequiredDimensionsRequest.class);
    verify(jobSyncStub)
        .upsertDeviceTempRequiredDimensionsAsync(upsertRequestCaptor.capture(), eq(true));

    UpsertDeviceTempRequiredDimensionsRequest upsertRequest = upsertRequestCaptor.getValue();
    assertThat(upsertRequest.getDeviceLocator()).isEqualTo(deviceLocator);
    assertThat(upsertRequest.getTempRequiredDimensionList()).hasSize(1);
    assertThat(upsertRequest.getTempRequiredDimension(0).getName()).isEqualTo("quarantined");
    assertThat(upsertRequest.getTempRequiredDimension(0).getValue()).isEqualTo("true");
    assertThat(upsertRequest.getDurationMs()).isEqualTo(24 * 60 * 60 * 1000L);
  }

  @Test
  public void quarantineDevice_customDuration_success() throws Exception {
    String deviceId = "device_id";
    String hostName = "host_name";
    String universe = "google_1p";
    QuarantineDeviceRequest request =
        QuarantineDeviceRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setEndTime(Timestamps.fromSeconds(1172800))
            .build();

    DeviceLocator deviceLocator =
        DeviceLocator.newBuilder()
            .setId(deviceId)
            .setLabLocator(LabLocator.newBuilder().setHostName(hostName))
            .build();

    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(deviceLocator))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(labInfoResponse));

    QuarantineDeviceResponse response =
        quarantineDeviceHandler.quarantineDevice(request, UniverseScope.SELF).get();

    assertThat(response)
        .isEqualTo(
            QuarantineDeviceResponse.newBuilder()
                .setQuarantineExpiry(Timestamps.fromSeconds(1000000 + 48 * 3600))
                .build());

    ArgumentCaptor<UpsertDeviceTempRequiredDimensionsRequest> upsertRequestCaptor =
        ArgumentCaptor.forClass(UpsertDeviceTempRequiredDimensionsRequest.class);
    verify(jobSyncStub)
        .upsertDeviceTempRequiredDimensionsAsync(upsertRequestCaptor.capture(), eq(true));

    UpsertDeviceTempRequiredDimensionsRequest upsertRequest = upsertRequestCaptor.getValue();
    assertThat(upsertRequest.getDurationMs()).isEqualTo(48 * 60 * 60 * 1000L);
  }

  @Test
  public void quarantineDevice_pastEndTime_fails() throws Exception {
    QuarantineDeviceRequest request =
        QuarantineDeviceRequest.newBuilder()
            .setId("device_id")
            .setEndTime(Timestamps.fromSeconds(900000))
            .build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> quarantineDeviceHandler.quarantineDevice(request, UniverseScope.SELF).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("End time must be in the future");
  }

  @Test
  public void quarantineDevice_notInternal_fails() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    QuarantineDeviceRequest request = QuarantineDeviceRequest.getDefaultInstance();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> quarantineDeviceHandler.quarantineDevice(request, UniverseScope.SELF).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Unsupported environment");
  }

  @Test
  public void quarantineDevice_routedUniverse_fails() throws Exception {
    QuarantineDeviceRequest request = QuarantineDeviceRequest.getDefaultInstance();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                quarantineDeviceHandler
                    .quarantineDevice(request, new UniverseScope.RoutedUniverse("remote_id"))
                    .get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Unsupported universe");
  }
}
