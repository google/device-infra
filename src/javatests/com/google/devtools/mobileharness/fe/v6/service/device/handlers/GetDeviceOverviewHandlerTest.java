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
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;
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
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DimensionSourceGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HostInfo;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
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
  private static final Instant NOW = Instant.parse("2025-11-21T10:00:00Z");
  private static final Timestamp NOW_TIMESTAMP = toProtoTimestamp(NOW);
  private static final Timestamp ONE_HOUR_AGO_TIMESTAMP = toProtoTimestamp(NOW.minusSeconds(3601));
  private static final Timestamp HALF_HOUR_AGO_TIMESTAMP =
      toProtoTimestamp(NOW.minus(Duration.ofMinutes(30)));

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
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addType("AndroidRealDevice")
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(DIMENSION_DETECTED_SUPPORTED)
                          .addSupportedDimension(DIMENSION_CONFIG_SUPPORTED)
                          .addRequiredDimension(DIMENSION_DETECTED_REQUIRED)
                          .addRequiredDimension(DIMENSION_CONFIG_REQUIRED)))
          .setDeviceCondition(DeviceCondition.newBuilder().setLastHealthyTime(NOW_TIMESTAMP))
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

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private DeviceHeaderInfoBuilder deviceHeaderInfoBuilder;

  @Bind private ListeningExecutorService executorService = newDirectExecutorService();
  @Bind private InstantSource instantSource = InstantSource.fixed(NOW);

  @Inject private GetDeviceOverviewHandler getDeviceOverviewHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    // Default mock behaviors
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any()))
        .thenReturn(immediateFuture(DEFAULT_LAB_INFO_RESPONSE));
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(deviceHeaderInfoBuilder.buildDeviceHeaderInfo(any(), any(), any()))
        .thenReturn(
            DeviceHeaderInfo.newBuilder()
                .setId(DEVICE_ID)
                .setHost(HostInfo.newBuilder().setName(HOST_NAME))
                .build());
  }

  private void mockDeviceInfo(DeviceInfo deviceInfo) {
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any()))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setDeviceView(
                                DeviceView.newBuilder()
                                    .setGroupedDevices(
                                        GroupedDevices.newBuilder()
                                            .setDeviceList(
                                                DeviceList.newBuilder()
                                                    .addDeviceInfo(deviceInfo)))))
                    .build()));
  }

  @Test
  public void getDeviceOverview_success() throws Exception {
    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    DeviceOverviewPageData response = responseFuture.get();
    assertThat(response.getOverview().getId()).isEqualTo(DEVICE_ID);
    assertThat(response.getOverview().getHost().getName()).isEqualTo(HOST_NAME);
    assertThat(response.getOverview().hasHealthAndActivity()).isTrue();
    assertThat(response.getHeaderInfo())
        .isEqualTo(
            DeviceHeaderInfo.newBuilder()
                .setId(DEVICE_ID)
                .setHost(HostInfo.newBuilder().setName(HOST_NAME))
                .build());

    verify(labInfoProvider).getLabInfoAsync(any(GetLabInfoRequest.class), eq(UNIVERSE));
    verify(configurationProvider).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_cacheHit() throws Exception {
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // First call
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // Second call

    verify(labInfoProvider).getLabInfoAsync(any(GetLabInfoRequest.class), eq(UNIVERSE));
    verify(configurationProvider).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_forceRefresh() throws Exception {
    getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST).get(); // First call
    getDeviceOverviewHandler
        .getDeviceOverview(DEFAULT_REQUEST.toBuilder().setForceRefresh(true).build())
        .get(); // Second call with force refresh

    verify(labInfoProvider, times(2)).getLabInfoAsync(any(GetLabInfoRequest.class), eq(UNIVERSE));
    verify(configurationProvider, times(2)).getDeviceConfig(eq(DEVICE_ID), eq(UNIVERSE));
    verify(configurationProvider, times(2)).getLabConfig(eq(HOST_NAME), eq(UNIVERSE));
  }

  @Test
  public void getDeviceOverview_labInfoProviderFails() throws Exception {
    when(labInfoProvider.getLabInfoAsync(any(GetLabInfoRequest.class), any()))
        .thenReturn(immediateFailedFuture(new StatusRuntimeException(Status.DEADLINE_EXCEEDED)));

    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    ExecutionException e = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(e).hasCauseThat().isInstanceOf(StatusRuntimeException.class);
  }

  @Test
  public void getDeviceOverview_deviceConfigFails() throws Exception {
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFailedFuture(new RuntimeException("Config error")));

    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);

    ExecutionException e = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(e).hasCauseThat().isInstanceOf(RuntimeException.class);
  }

  @Test
  public void getDeviceOverview_dimensions_onlyDetected() throws Exception {
    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get().getOverview();

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

    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get().getOverview();

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

    ListenableFuture<DeviceOverviewPageData> responseFuture =
        getDeviceOverviewHandler.getDeviceOverview(DEFAULT_REQUEST);
    DeviceOverview response = responseFuture.get().getOverview();

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

  // Tests for HealthAndActivityInfo

  @Test
  public void healthAndActivity_inService_idle() throws Exception {
    mockDeviceInfo(DEFAULT_DEVICE_INFO.toBuilder().setDeviceStatus(DeviceStatus.IDLE).build());
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("In Service (Idle)");
    assertThat(info.getSubtitle()).isEqualTo("The device is healthy and ready for new tasks.");
    assertThat(info.getState()).isEqualTo(HealthState.IN_SERVICE_IDLE);
    assertThat(info.getDeviceStatus().getStatus()).isEqualTo("IDLE");
    assertThat(info.getDeviceStatus().getIsCritical()).isFalse();
    assertThat(info.getDeviceTypesList())
        .containsExactly(
            DeviceType.newBuilder().setType("AndroidRealDevice").setIsAbnormal(false).build());
    assertThat(info.getLastInServiceTime()).isEqualTo(NOW_TIMESTAMP);
  }

  @Test
  public void healthAndActivity_inService_busy() throws Exception {
    mockDeviceInfo(DEFAULT_DEVICE_INFO.toBuilder().setDeviceStatus(DeviceStatus.BUSY).build());
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("In Service (Busy)");
    assertThat(info.getSubtitle()).isEqualTo("The device is healthy and currently running a task.");
    assertThat(info.getState()).isEqualTo(HealthState.IN_SERVICE_BUSY);
    assertThat(info.getCurrentTask().getType()).isEqualTo("Test");
  }

  @Test
  public void healthAndActivity_quarantinedIdle() throws Exception {
    DeviceInfo quarantinedDevice =
        DEFAULT_DEVICE_INFO.toBuilder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceCondition(
                DEFAULT_DEVICE_INFO.getDeviceCondition().toBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))))
            .build();
    mockDeviceInfo(quarantinedDevice);
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("Quarantined");
    assertThat(info.getState()).isEqualTo(HealthState.OUT_OF_SERVICE_NEEDS_FIXING);
    assertThat(info.getDeviceStatus().getIsCritical()).isTrue();
    assertThat(info.getDiagnostics().getDiagnosis())
        .contains("Device has been manually quarantined");
  }

  @Test
  public void healthAndActivity_outOfService_recovering() throws Exception {
    DeviceInfo recoveringDevice =
        DEFAULT_DEVICE_INFO.toBuilder()
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DEFAULT_DEVICE_INFO.getDeviceFeature().toBuilder()
                    .clearType()
                    .addType("AndroidRealDevice")
                    .addType("DisconnectedDevice"))
            .setDeviceCondition(
                DEFAULT_DEVICE_INFO.getDeviceCondition().toBuilder()
                    .setLastHealthyTime(HALF_HOUR_AGO_TIMESTAMP))
            .build();
    mockDeviceInfo(recoveringDevice);
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("Out of Service (Recovering)");
    assertThat(info.getState()).isEqualTo(HealthState.OUT_OF_SERVICE_RECOVERING);
    assertThat(info.getDeviceStatus().getIsCritical()).isFalse();
    assertThat(info.getDeviceTypesList())
        .containsExactly(
            DeviceType.newBuilder().setType("AndroidRealDevice").setIsAbnormal(false).build(),
            DeviceType.newBuilder().setType("DisconnectedDevice").setIsAbnormal(true).build());
    assertThat(info.getCurrentTask().getType()).isEqualTo("Recovery Task");
  }

  @Test
  public void healthAndActivity_outOfService_tempMaint_init() throws Exception {
    DeviceInfo initDevice =
        DEFAULT_DEVICE_INFO.toBuilder()
            .setDeviceStatus(DeviceStatus.INIT)
            .setDeviceCondition(
                DEFAULT_DEVICE_INFO.getDeviceCondition().toBuilder()
                    .setLastHealthyTime(HALF_HOUR_AGO_TIMESTAMP))
            .build();
    mockDeviceInfo(initDevice);
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("Out of Service (may be temporary)");
    assertThat(info.getState()).isEqualTo(HealthState.OUT_OF_SERVICE_TEMP_MAINT);
    assertThat(info.getDeviceStatus().getIsCritical()).isFalse();
    assertThat(info.getDiagnostics().getDiagnosis()).contains("INIT");
  }

  @Test
  public void healthAndActivity_outOfService_needsFixing_missing() throws Exception {
    DeviceInfo missingDevice =
        DEFAULT_DEVICE_INFO.toBuilder()
            .setDeviceStatus(DeviceStatus.MISSING)
            .setDeviceFeature(
                DEFAULT_DEVICE_INFO.getDeviceFeature().toBuilder()
                    .clearType()
                    .addType("AndroidRealDevice")
                    .addType("DisconnectedDevice"))
            .setDeviceCondition(
                DEFAULT_DEVICE_INFO.getDeviceCondition().toBuilder()
                    .setLastHealthyTime(ONE_HOUR_AGO_TIMESTAMP))
            .build();
    mockDeviceInfo(missingDevice);
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("Out of Service (Needs Fixing)");
    assertThat(info.getState()).isEqualTo(HealthState.OUT_OF_SERVICE_NEEDS_FIXING);
    assertThat(info.getDeviceStatus().getIsCritical()).isTrue();
    assertThat(info.getDiagnostics().getDiagnosis()).contains("MISSING");
    assertThat(info.getDiagnostics().getDiagnosis()).contains("DisconnectedDevice");
    assertThat(info.getDiagnostics().getExplanation()).contains("stopped sending heartbeats");
    assertThat(info.getDiagnostics().getSuggestedAction()).contains("Check device power");
  }

  @Test
  public void healthAndActivity_outOfService_needsFixing_noTypes() throws Exception {
    DeviceInfo noTypeDevice =
        DEFAULT_DEVICE_INFO.toBuilder()
            .setDeviceStatus(DeviceStatus.FAILED)
            .setDeviceFeature(DEFAULT_DEVICE_INFO.getDeviceFeature().toBuilder().clearType())
            .setDeviceCondition(
                DEFAULT_DEVICE_INFO.getDeviceCondition().toBuilder()
                    .setLastHealthyTime(ONE_HOUR_AGO_TIMESTAMP))
            .build();
    mockDeviceInfo(noTypeDevice);
    HealthAndActivityInfo info =
        getDeviceOverviewHandler
            .getDeviceOverview(DEFAULT_REQUEST)
            .get()
            .getOverview()
            .getHealthAndActivity();

    assertThat(info.getTitle()).isEqualTo("Out of Service (Needs Fixing)");
    assertThat(info.getState()).isEqualTo(HealthState.OUT_OF_SERVICE_NEEDS_FIXING);
    assertThat(info.getDeviceStatus().getIsCritical()).isTrue();
    assertThat(info.getDiagnostics().getDiagnosis()).contains("no type detected");
    assertThat(info.getDiagnostics().getDiagnosis()).contains("FAILED");
  }
}
