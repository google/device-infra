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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceHealthState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceSummary;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Instant;
import java.time.InstantSource;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class GetHostDeviceSummariesHandlerTest {

  private static final String HOST_NAME = "test.host";
  private static final String DEVICE_ID = "device_id";
  private static final GetHostDeviceSummariesRequest REQUEST =
      GetHostDeviceSummariesRequest.newBuilder().setHostName(HOST_NAME).build();
  private static final Instant NOW = Instant.parse("2025-11-21T10:00:00Z");

  private static final DeviceInfo DEVICE_INFO =
      DeviceInfo.newBuilder()
          .setDeviceLocator(DeviceLocator.newBuilder().setId(DEVICE_ID))
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addType("AndroidRealDevice")
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder().setName("model").setValue("pixel 6"))
                          .addRequiredDimension(
                              DeviceDimension.newBuilder().setName("pool").setValue("shared"))))
          .build();

  private static final GetLabInfoResponse LAB_INFO_RESPONSE =
      GetLabInfoResponse.newBuilder()
          .setLabQueryResult(
              LabQueryResult.newBuilder()
                  .setLabView(
                      LabView.newBuilder()
                          .addLabData(
                              LabData.newBuilder()
                                  .setDeviceList(
                                      DeviceList.newBuilder().addDeviceInfo(DEVICE_INFO)))))
          .build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();
  @Bind private InstantSource instantSource = InstantSource.fixed(NOW);

  @Inject private GetHostDeviceSummariesHandler getHostDeviceSummariesHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void getHostDeviceSummaries_success() throws Exception {
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(LAB_INFO_RESPONSE));

    GetHostDeviceSummariesResponse response =
        getHostDeviceSummariesHandler.getHostDeviceSummaries(REQUEST).get();

    assertThat(response.getDeviceSummariesList()).hasSize(1);
    DeviceSummary expectedDeviceSummary =
        DeviceSummary.newBuilder()
            .setId(DEVICE_ID)
            .setHealthState(
                DeviceHealthState.newBuilder()
                    .setHealth(HealthState.IN_SERVICE_IDLE)
                    .setTitle("In Service (Idle)")
                    .setTooltip("The device is healthy and ready for new tasks."))
            .addTypes(DeviceType.newBuilder().setType("AndroidRealDevice").setIsAbnormal(false))
            .setDeviceStatus(
                HealthAndActivityInfo.DeviceStatus.newBuilder()
                    .setStatus("IDLE")
                    .setIsCritical(false))
            .setRequiredDims("pool:shared")
            .setModel("pixel 6")
            .build();
    assertThat(response.getDeviceSummaries(0)).isEqualTo(expectedDeviceSummary);
  }

  @Test
  public void getHostDeviceSummaries_noDevices_returnsEmpty() throws Exception {
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));

    GetHostDeviceSummariesResponse response =
        getHostDeviceSummariesHandler.getHostDeviceSummaries(REQUEST).get();

    assertThat(response.getDeviceSummariesList()).isEmpty();
  }

  @Test
  public void getHostDeviceSummaries_multipleDevicesAndDimensions_success() throws Exception {
    DeviceInfo deviceInfoBusy =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device_id_2"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("IosRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("model").setValue("iphone10"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("sdk_version")
                                    .setValue("15"))))
            .build();
    GetLabInfoResponse labInfoResponseMultiple =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(DEVICE_INFO)
                                            .addDeviceInfo(deviceInfoBusy)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(labInfoResponseMultiple));

    GetHostDeviceSummariesResponse response =
        getHostDeviceSummariesHandler.getHostDeviceSummaries(REQUEST).get();

    assertThat(response.getDeviceSummariesList()).hasSize(2);
    assertThat(response.getDeviceSummaries(0).getId()).isEqualTo(DEVICE_ID);
    assertThat(response.getDeviceSummaries(1).getId()).isEqualTo("device_id_2");
    assertThat(response.getDeviceSummaries(1).getHealthState().getHealth())
        .isEqualTo(HealthState.IN_SERVICE_BUSY);
    assertThat(response.getDeviceSummaries(1).getLabel()).isEmpty();
    assertThat(response.getDeviceSummaries(1).getRequiredDims()).isEmpty();
    assertThat(response.getDeviceSummaries(1).getModel()).isEqualTo("iphone10");
    assertThat(response.getDeviceSummaries(1).getVersion()).isEqualTo("15");
  }
}
