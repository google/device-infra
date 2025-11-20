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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DimensionSourceGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
public final class GetDeviceOverviewHandlerTest {

  private static final String DEVICE_ID = "test_device_id";
  private static final String UNIVERSE = "test_universe";
  private static final String HOST_NAME = "test_host.google.com";

  private static final GetDeviceOverviewRequest DEFAULT_REQUEST =
      GetDeviceOverviewRequest.newBuilder().setId(DEVICE_ID).setUniverse(UNIVERSE).build();

  private static final DeviceDimension DIMENSION_DETECTED_SUPPORTED =
      DeviceDimension.newBuilder().setName("detected_supported").setValue("val1").build();
  private static final DeviceDimension DIMENSION_DETECTED_REQUIRED =
      DeviceDimension.newBuilder().setName("detected_required").setValue("val2").build();
  private static final DeviceDimension DIMENSION_CONFIG_SUPPORTED =
      DeviceDimension.newBuilder().setName("config_supported").setValue("val3").build();
  private static final DeviceDimension DIMENSION_CONFIG_REQUIRED =
      DeviceDimension.newBuilder().setName("config_required").setValue("val4").build();

  private static final DeviceInfo DEFAULT_DEVICE_INFO =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder()
                  .setId(DEVICE_ID)
                  .setLabLocator(LabLocator.newBuilder().setHostName(HOST_NAME)))
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(DIMENSION_DETECTED_SUPPORTED)
                          .addSupportedDimension(DIMENSION_CONFIG_SUPPORTED)
                          .addRequiredDimension(DIMENSION_DETECTED_REQUIRED)
                          .addRequiredDimension(DIMENSION_CONFIG_REQUIRED)))
          .build();

  private static final GetLabInfoResponse DEFAULT_LAB_INFO_RESPONSE =
      GetLabInfoResponse.newBuilder()
          .setLabQueryResult(
              LabQueryResult.newBuilder()
                  .setDeviceView(
                      DeviceView.newBuilder()
                          .setGroupedDevices(
                              GroupedDevices.newBuilder()
                                  .setDeviceList(
                                      DeviceList.newBuilder().addDeviceInfo(DEFAULT_DEVICE_INFO)))))
          .build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoStub labInfoStub;
  @Bind @Mock private ConfigurationProvider configurationProvider;

  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetDeviceOverviewHandler getDeviceOverviewHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    // Default mock behaviors
    when(labInfoStub.getLabInfoAsync(any(GetLabInfoRequest.class)))
        .thenReturn(immediateFuture(DEFAULT_LAB_INFO_RESPONSE));
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
  }

  @Test
  public void getDeviceOverview_success() throws Exception {
    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    DeviceOverview response = responseFuture.get();
    assertThat(response.getId()).isEqualTo(DEVICE_ID);
    assertThat(response.getHost().getName()).isEqualTo(HOST_NAME);

    verify(labInfoStub).getLabInfoAsync(any(GetLabInfoRequest.class));
    verify(configurationProvider).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_cacheHit() throws Exception {
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // First call
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // Second call

    verify(labInfoStub).getLabInfoAsync(any(GetLabInfoRequest.class));
    verify(configurationProvider).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_forceRefresh() throws Exception {
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // First call
    getDeviceOverviewHandler
        .getDeviceOverview(DEFAULT_REQUEST.toBuilder().setForceRefresh(true).build())
        .get(); // Second call with force refresh

    verify(labInfoStub, times(2)).getLabInfoAsync(any(GetLabInfoRequest.class));
    verify(configurationProvider, times(2)).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider, times(2)).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_labInfoStubFails() throws Exception {
    when(labInfoStub.getLabInfoAsync(any(GetLabInfoRequest.class)))
        .thenReturn(immediateFailedFuture(new StatusRuntimeException(Status.DEADLINE_EXCEEDED)));

    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    ExecutionException e = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(e).hasCauseThat().isInstanceOf(StatusRuntimeException.class);
  }

  @Test
  public void getDeviceOverview_deviceConfigFails() throws Exception {
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFailedFuture(new RuntimeException("Config error")));

    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    ExecutionException e = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(e).hasCauseThat().isInstanceOf(RuntimeException.class);
  }

  @Test
  public void getDeviceOverview_dimensions_onlyDetected() throws Exception {
    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get();

    ImmutableMap<String, DimensionSourceGroup> expectedSupported =
        ImmutableMap.of(
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_supported")
                        .setValue("val1"))
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_supported")
                        .setValue("val3"))
                .build());
    ImmutableMap<String, DimensionSourceGroup> expectedRequired =
        ImmutableMap.of(
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_required")
                        .setValue("val2"))
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_required")
                        .setValue("val4"))
                .build());

    assertThat(response.getDimensions().getSupportedMap())
        .containsExactlyEntriesIn(expectedSupported);
    assertThat(response.getDimensions().getRequiredMap())
        .containsExactlyEntriesIn(expectedRequired);
  }

  @Test
  public void getDeviceOverview_dimensions_withDeviceConfig() throws Exception {
    DeviceConfig deviceConfig =
        DeviceConfig.newBuilder()
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(DIMENSION_CONFIG_SUPPORTED)
                            .addRequiredDimension(DIMENSION_CONFIG_REQUIRED)))
            .build();
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.of(deviceConfig)));

    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get();

    ImmutableMap<String, DimensionSourceGroup> expectedSupported =
        ImmutableMap.of(
            "From Device Config",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_supported")
                        .setValue("val3"))
                .build(),
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_supported")
                        .setValue("val1"))
                .build());
    ImmutableMap<String, DimensionSourceGroup> expectedRequired =
        ImmutableMap.of(
            "From Device Config",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_required")
                        .setValue("val4"))
                .build(),
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_required")
                        .setValue("val2"))
                .build());

    assertThat(response.getDimensions().getSupportedMap())
        .containsExactlyEntriesIn(expectedSupported);
    assertThat(response.getDimensions().getRequiredMap())
        .containsExactlyEntriesIn(expectedRequired);
  }

  @Test
  public void getDeviceOverview_dimensions_withHostConfig() throws Exception {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("device_config_mode").setValue("host")))
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(DIMENSION_CONFIG_SUPPORTED)
                            .addRequiredDimension(DIMENSION_CONFIG_REQUIRED)))
            .build();
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.of(labConfig)));
    // DeviceConfig should be ignored even if present
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.of(DeviceConfig.getDefaultInstance())));

    ListenableFuture<DeviceOverview> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get();

    ImmutableMap<String, DimensionSourceGroup> expectedSupported =
        ImmutableMap.of(
            "From Host Config",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_supported")
                        .setValue("val3"))
                .build(),
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_supported")
                        .setValue("val1"))
                .build());
    ImmutableMap<String, DimensionSourceGroup> expectedRequired =
        ImmutableMap.of(
            "From Host Config",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("config_required")
                        .setValue("val4"))
                .build(),
            "Detected by OmniLab",
            DimensionSourceGroup.newBuilder()
                .addDimensions(
                    com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension
                        .newBuilder()
                        .setName("detected_required")
                        .setValue("val2"))
                .build());

    assertThat(response.getDimensions().getSupportedMap())
        .containsExactlyEntriesIn(expectedSupported);
    assertThat(response.getDimensions().getRequiredMap())
        .containsExactlyEntriesIn(expectedRequired);
    // Verify DeviceConfig future was cancelled
    verify(configurationProvider).getDeviceConfig(any(), any());
  }
}
