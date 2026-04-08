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
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceInfoLookupHelperTest {

  private static final String DEVICE_ID = "test_device_id";
  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabInfoProvider labInfoProvider;
  @Captor private ArgumentCaptor<GetLabInfoRequest> labInfoRequestCaptor;

  @Test
  public void lookUpDeviceInfoAsync_success() throws Exception {
    DeviceInfo expectedDeviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId(DEVICE_ID))
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
                                            .addDeviceInfo(expectedDeviceInfo)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoResponse));

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        DeviceInfoLookupHelper.lookUpDeviceInfoAsync(
            labInfoProvider, DEVICE_ID, SELF_UNIVERSE, directExecutor());

    assertThat(deviceInfoFuture.get()).isEqualTo(expectedDeviceInfo);
    verify(labInfoProvider).getLabInfoAsync(labInfoRequestCaptor.capture(), eq(SELF_UNIVERSE));
    GetLabInfoRequest capturedRequest = labInfoRequestCaptor.getValue();

    assertThat(capturedRequest.getLabQuery().hasDeviceViewRequest()).isTrue();
    assertThat(capturedRequest.getLabQuery().getDeviceViewRequest()).isEqualToDefaultInstance();
  }

  @Test
  public void lookUpDeviceInfoAsync_deviceNotFound_throwsException() throws Exception {
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder().setDeviceView(DeviceView.getDefaultInstance()))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoResponse));

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        DeviceInfoLookupHelper.lookUpDeviceInfoAsync(
            labInfoProvider, DEVICE_ID, SELF_UNIVERSE, directExecutor());

    ExecutionException exception = assertThrows(ExecutionException.class, deviceInfoFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RuntimeException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().contains("Device not found");
  }
}
