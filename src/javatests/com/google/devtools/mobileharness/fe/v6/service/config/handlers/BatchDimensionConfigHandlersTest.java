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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ClearDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionValueList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
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
public final class BatchDimensionConfigHandlersTest {

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind @Mock private Environment environment;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private GetConfigurableDimensionsHandler getConfigurableDimensionsHandler;

  @Inject private BatchGetConfigurableDimensionKeysHandler batchGetConfigurableDimensionKeysHandler;

  @Inject private GetDimensionValueSuggestionsHandler getDimensionValueSuggestionsHandler;
  @Inject private BatchGetDeviceDimensionConfigsHandler batchGetDeviceDimensionConfigsHandler;

  @Inject private BatchUpdateDeviceDimensionConfigsHandler batchUpdateDeviceDimensionConfigsHandler;

  private FakeBatchFleet fleet;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(true);
    when(environment.isGoogleInternal()).thenReturn(true);
    when(environment.isAts()).thenReturn(false);
    fleet =
        new FakeBatchFleet(
            labInfoProvider, configurationProvider, iamPermissionChecker, SELF_UNIVERSE);
  }

  @Test
  public void getConfigurableDimensions_internal1p_returnsCuratedAndAllowsCustom()
      throws Exception {
    GetConfigurableDimensionsResponse response =
        getConfigurableDimensionsHandler
            .getConfigurableDimensions(
                GetConfigurableDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE)
            .get();

    assertThat(response.getEnabled()).isTrue();
    assertThat(response.getAllowCustomDimensions()).isTrue();
    assertThat(response.getDimensionsList()).hasSize(3);
    assertThat(response.getDimensions(0).getDimensionName()).isEqualTo("pool");
    assertThat(response.getDimensions(0).getDimensionList())
        .isEqualTo(DimensionList.DIMENSION_LIST_REQUIRED);
    assertThat(response.getDimensions(1).getDimensionName()).isEqualTo("label");
    assertThat(response.getDimensions(2).getDimensionName()).isEqualTo("cluster");
  }

  @Test
  public void getConfigurableDimensions_ats_returnsFourFixedAndDisallowsCustom() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);

    GetConfigurableDimensionsResponse response =
        getConfigurableDimensionsHandler
            .getConfigurableDimensions(
                GetConfigurableDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE)
            .get();

    assertThat(response.getEnabled()).isTrue();
    assertThat(response.getAllowCustomDimensions()).isFalse();
    assertThat(response.getDimensionsList()).hasSize(4);
    assertThat(response.getDimensions(0).getDimensionName()).isEqualTo("recovery");
    assertThat(response.getDimensions(1).getDimensionName()).isEqualTo("skip_suw_app");
    assertThat(response.getDimensions(2).getDimensionName()).isEqualTo("pool");
    assertThat(response.getDimensions(3).getDimensionName()).isEqualTo("persist_test_harness");
  }

  @Test
  public void getConfigurableDimensions_disabledUniverse_returnsEnabledFalse() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    GetConfigurableDimensionsResponse response =
        getConfigurableDimensionsHandler
            .getConfigurableDimensions(
                GetConfigurableDimensionsRequest.getDefaultInstance(),
                new UniverseScope.RoutedUniverse("ats"))
            .get();

    assertThat(response.getEnabled()).isFalse();
    assertThat(response.getDimensionsList()).isEmpty();
    assertThat(response.getAllowCustomDimensions()).isFalse();
  }

  @Test
  public void batchGetConfigurableDimensionKeys_internalSupported_ranksSelectedFirstAndExcludesSys()
      throws Exception {
    DeviceConfig dev1Config =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("custom_team_tag")
                                    .setValue("team_a"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("model").setValue("Pixel 8"))))
            .build();
    when(configurationProvider.getDeviceConfigs(ImmutableList.of("dev1"), SELF_UNIVERSE))
        .thenReturn(immediateFuture(ImmutableList.of(dev1Config)));

    GetLabInfoResponse labInfoWithSupported =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addSupportedDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("model")
                                                                            .setValue("Pixel 8"))
                                                                    .addSupportedDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("label")
                                                                            .setValue("presubmit"))
                                                                    .addSupportedDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName(
                                                                                "custom_team_tag")
                                                                            .setValue("team_a")))))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addSupportedDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("label")
                                                                            .setValue("postsubmit"))
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName(
                                                                                "custom_req_only")
                                                                            .setValue("v1")))))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoWithSupported));

    BatchGetConfigurableDimensionKeysRequest request =
        BatchGetConfigurableDimensionKeysRequest.newBuilder()
            .addDeviceIds("dev1")
            .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
            .build();

    BatchGetConfigurableDimensionKeysResponse response =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(request, SELF_UNIVERSE)
            .get();

    ArgumentCaptor<GetLabInfoRequest> supportedMaskCaptor =
        ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(supportedMaskCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(
            supportedMaskCaptor
                .getValue()
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getFieldMask()
                .getPathsList())
        .containsExactly("device_feature.composite_dimension.supported_dimension");

    assertThat(response.getSystemDimensionNamesList()).contains("model");
    assertThat(response.getCandidateKeysList()).hasSize(3);
    assertThat(response.getCandidateKeys(0).getDimensionName()).isEqualTo("custom_team_tag");
    assertThat(response.getCandidateKeys(0).getSelectedDeviceCount()).isEqualTo(1);
    assertThat(response.getCandidateKeys(0).getFleetDeviceCount()).isEqualTo(1);
    assertThat(response.getCandidateKeys(1).getDimensionName()).isEqualTo("label");
    assertThat(response.getCandidateKeys(1).getSelectedDeviceCount()).isEqualTo(0);
    assertThat(response.getCandidateKeys(1).getFleetDeviceCount()).isEqualTo(2);
    assertThat(response.getCandidateKeys(2).getDimensionName()).isEqualTo("cluster");
    assertThat(response.getCandidateKeys(2).getSelectedDeviceCount()).isEqualTo(0);
    assertThat(response.getCandidateKeys(2).getFleetDeviceCount()).isEqualTo(0);
  }

  @Test
  public void batchGetConfigurableDimensionKeys_internalRequiredAndAtsAndUnavailable()
      throws Exception {
    // 1. Internal REQUIRED with LabView and empty selectedIds
    GetLabInfoResponse labInfoWithRequired =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("custom_req")
                                                                            .setValue("v1")))))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoWithRequired));

    BatchGetConfigurableDimensionKeysResponse reqResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build(),
                SELF_UNIVERSE)
            .get();
    assertThat(reqResponse.getCandidateKeysList()).hasSize(2);
    assertThat(reqResponse.getCandidateKeys(0).getDimensionName()).isEqualTo("custom_req");
    assertThat(reqResponse.getCandidateKeys(0).getFleetDeviceCount()).isEqualTo(1);
    assertThat(reqResponse.getCandidateKeys(1).getDimensionName()).isEqualTo("pool");
    assertThat(reqResponse.getCandidateKeys(1).getFleetDeviceCount()).isEqualTo(0);

    ArgumentCaptor<GetLabInfoRequest> reqKeysCaptor =
        ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(reqKeysCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(reqKeysCaptor.getValue().getPage().getLimit()).isEqualTo(0);
    assertThat(reqKeysCaptor.getValue().getUseRealtimeData()).isFalse();

    // 2. Internal REQUIRED without LabView
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    BatchGetConfigurableDimensionKeysResponse emptyLabViewResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build(),
                SELF_UNIVERSE)
            .get();
    assertThat(emptyLabViewResponse.getCandidateKeysList()).hasSize(1);
    assertThat(emptyLabViewResponse.getCandidateKeys(0).getDimensionName()).isEqualTo("pool");

    // 3. ATS mode with empty fleet and with populated fleet
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    BatchGetConfigurableDimensionKeysResponse atsEmptyResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build(),
                SELF_UNIVERSE)
            .get();
    assertThat(atsEmptyResponse.getCandidateKeysList()).hasSize(1);

    DeviceConfig atsConfig =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("ats_pool"))))
            .build();
    when(configurationProvider.getDeviceConfigs(ImmutableList.of("dev1"), SELF_UNIVERSE))
        .thenReturn(immediateFuture(ImmutableList.of(atsConfig)));
    BatchGetConfigurableDimensionKeysResponse atsPopulatedResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .addDeviceIds("dev1")
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build(),
                SELF_UNIVERSE)
            .get();
    assertThat(atsPopulatedResponse.getCandidateKeys(0).getDimensionName()).isEqualTo("pool");
    assertThat(atsPopulatedResponse.getCandidateKeys(0).getSelectedDeviceCount()).isEqualTo(1);

    // 4. Unavailable universe throws
    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    assertThrows(
        ExecutionException.class,
        () ->
            batchGetConfigurableDimensionKeysHandler
                .batchGetConfigurableDimensionKeys(
                    BatchGetConfigurableDimensionKeysRequest.getDefaultInstance(), SELF_UNIVERSE)
                .get());
  }

  @Test
  public void getDimensionValueSuggestions_aggregatesAndRanksByDeviceCount() throws Exception {
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev1"))
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("pool")
                                                                            .setValue("shared")))))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev2"))
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("pool")
                                                                            .setValue("shared")))))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("dev3")))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoResponse));

    GetDimensionValueSuggestionsRequest request =
        GetDimensionValueSuggestionsRequest.newBuilder()
            .setDimensionName("pool")
            .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
            .build();

    GetDimensionValueSuggestionsResponse response =
        getDimensionValueSuggestionsHandler
            .getDimensionValueSuggestions(request, SELF_UNIVERSE)
            .get();

    ArgumentCaptor<GetLabInfoRequest> reqCaptor = ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(reqCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(reqCaptor.getValue().getPage().getLimit()).isEqualTo(0);
    assertThat(reqCaptor.getValue().getUseRealtimeData()).isFalse();
    assertThat(
            reqCaptor
                .getValue()
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getRequiredDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("pool");

    // dev3 has no pool value; an empty value-set is never a suggestion.
    assertThat(response.getCandidateValuesList()).hasSize(1);
    assertThat(response.getCandidateValues(0).getValuesList()).containsExactly("shared");
    assertThat(response.getCandidateValues(0).getDeviceCount()).isEqualTo(2);
  }

  @Test
  public void getDimensionValueSuggestions_handlesEmptyNameSupportedListAtsAndUnavailable()
      throws Exception {
    // 1. Empty dimension name
    assertThat(
            getDimensionValueSuggestionsHandler
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
                .get()
                .getCandidateValuesList())
        .isEmpty();

    // 2. Internal SUPPORTED with missing LabView
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    assertThat(
            getDimensionValueSuggestionsHandler
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder()
                        .setDimensionName("label")
                        .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                        .build(),
                    SELF_UNIVERSE)
                .get()
                .getCandidateValuesList())
        .isEmpty();

    ArgumentCaptor<GetLabInfoRequest> reqCaptor = ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(reqCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(
            reqCaptor
                .getValue()
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getSupportedDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("label");

    // 3. ATS mode with empty fleet and with populated fleet
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    assertThat(
            getDimensionValueSuggestionsHandler
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder()
                        .setDimensionName("recovery")
                        .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                        .build(),
                    SELF_UNIVERSE)
                .get()
                .getCandidateValuesList())
        .isEmpty();

    GetLabInfoResponse atsLabInfo =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev1")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("dev2")))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(atsLabInfo));
    DeviceConfig dev1Cfg =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("recovery")
                                    .setValue("fastboot"))))
            .build();
    when(configurationProvider.getDeviceConfigs(ImmutableList.of("dev1", "dev2"), SELF_UNIVERSE))
        .thenReturn(immediateFuture(ImmutableList.of(dev1Cfg)));

    GetDimensionValueSuggestionsResponse atsResp =
        getDimensionValueSuggestionsHandler
            .getDimensionValueSuggestions(
                GetDimensionValueSuggestionsRequest.newBuilder()
                    .setDimensionName("recovery")
                    .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                    .build(),
                SELF_UNIVERSE)
            .get();
    assertThat(atsResp.getCandidateValuesList()).hasSize(1);
    assertThat(atsResp.getCandidateValues(0).getValuesList()).containsExactly("fastboot");

    // 4. Unavailable universe throws
    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    assertThrows(
        ExecutionException.class,
        () ->
            getDimensionValueSuggestionsHandler
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder()
                        .setDimensionName("pool")
                        .build(),
                    SELF_UNIVERSE)
                .get());
  }

  @Test
  public void batchGetDeviceDimensionConfigs_computesThreeStateReviewStatuses() throws Exception {
    fleet
        .install()
        .addDevice("dev_update", "host1", Optional.of(poolConfig("dev_update", "shared")))
        .addDevice("dev_match", "host1", Optional.of(poolConfig("dev_match", "dedicated")))
        .addDevice("dev_host_managed", "host_shared", Optional.empty())
        .addDevice("dev_no_perm", "host1", Optional.of(poolConfig("dev_no_perm", "shared")))
        .setLabConfig("host_shared", hostManagedLab("host_shared", "host_pool"))
        .allowConfig("dev_update", true)
        .allowConfig("dev_match", true)
        .allowConfig("dev_no_perm", false);

    BatchGetDeviceDimensionConfigsResponse response =
        batchGetDeviceDimensionConfigsHandler
            .batchGetDeviceDimensionConfigs(
                poolGetRequest("dev_update", "dev_match", "dev_host_managed", "dev_no_perm")
                    .setTarget(
                        TargetDimensionValues.newBuilder()
                            .setValueList(DimensionValueList.newBuilder().addValues("dedicated")))
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();

    assertThat(response.getEntriesList()).hasSize(4);
    assertThat(response.getEntries(0).getDeviceId()).isEqualTo("dev_update");
    assertThat(response.getEntries(0).getValuesList()).containsExactly("shared");
    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
    assertThat(response.getEntries(1).getDeviceId()).isEqualTo("dev_match");
    assertThat(response.getEntries(1).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);
    assertThat(response.getEntries(2).getDeviceId()).isEqualTo("dev_host_managed");
    assertThat(response.getEntries(2).getValuesList()).containsExactly("host_pool");
    assertThat(response.getEntries(2).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(2).getReadOnlyReason())
        .isEqualTo("Managed by host shared config");
    assertThat(response.getEntries(3).getDeviceId()).isEqualTo("dev_no_perm");
    assertThat(response.getEntries(3).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(3).getReadOnlyReason()).isEqualTo("No permission");

    // The permission check reuses the loaded config instead of loading the device again.
    verify(iamPermissionChecker)
        .canConfigDevice("dev_update", poolConfig("dev_update", "shared"), SELF_UNIVERSE);
    verify(iamPermissionChecker, never()).canConfigDevice(any(), any(UniverseScope.class));
    // One lab info query and one device config read serve the whole batch.
    verify(labInfoProvider).getLabInfoAsync(any(), eq(SELF_UNIVERSE));
    verify(configurationProvider).getDeviceConfigs(any(), eq(SELF_UNIVERSE));
    verify(configurationProvider).getLabConfig("host1", SELF_UNIVERSE);
    verify(configurationProvider).getLabConfig("host_shared", SELF_UNIVERSE);
  }

  @Test
  public void batchGetDeviceDimensionConfigs_reportsLoadFailuresAsReadOnly() throws Exception {
    fleet
        .install()
        .addDevice("dev_writable", "host1", Optional.of(poolConfig("dev_writable", "shared")))
        .addDevice("dev_host_down", "host_broken", Optional.of(poolConfig("dev_host_down", "x")))
        .addDevice("dev_iam_error", "host1", Optional.of(poolConfig("dev_iam_error", "shared")))
        .failLabConfig("host_broken")
        .allowConfig("dev_writable", true)
        .failPermissionCheck("dev_iam_error");

    BatchGetDeviceDimensionConfigsResponse response =
        batchGetDeviceDimensionConfigsHandler
            .batchGetDeviceDimensionConfigs(
                poolGetRequest("dev_writable", "dev_missing", "dev_host_down", "dev_iam_error")
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();

    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE);
    assertThat(response.getEntries(1).getReadOnlyReason()).isEqualTo("Device not found");
    assertThat(response.getEntries(2).getReadOnlyReason())
        .isEqualTo("Failed to load host configuration");
    assertThat(response.getEntries(3).getReadOnlyReason()).isEqualTo("No permission");

    fleet.failDeviceConfigs();
    BatchGetDeviceDimensionConfigsResponse configReadFailed =
        batchGetDeviceDimensionConfigsHandler
            .batchGetDeviceDimensionConfigs(
                poolGetRequest("dev_writable").build(), SELF_UNIVERSE, Optional.of("user1"))
            .get();
    assertThat(configReadFailed.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(configReadFailed.getEntries(0).getReadOnlyReason())
        .isEqualTo("Failed to load device configuration");

    fleet.failLabInfo();
    assertThat(
            batchGetDeviceDimensionConfigsHandler
                .batchGetDeviceDimensionConfigs(
                    poolGetRequest("dev_writable").build(), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getEntries(0)
                .getReadOnlyReason())
        .isEqualTo("Failed to load device information");

    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);
    assertThat(
            batchGetDeviceDimensionConfigsHandler
                .batchGetDeviceDimensionConfigs(
                    poolGetRequest("dev_writable").build(), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getEntries(0)
                .getReadOnlyReason())
        .isEqualTo("Configuration is not supported for this device");
  }

  @Test
  public void batchGetDeviceDimensionConfigs_emptyUsernameAtsAndDuplicateIds() throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(poolConfig("dev1", "shared")))
        .allowConfig("dev1", true);

    // Empty username in 1P is never writable.
    assertThat(
            batchGetDeviceDimensionConfigsHandler
                .batchGetDeviceDimensionConfigs(
                    poolGetRequest("dev1").build(), SELF_UNIVERSE, Optional.empty())
                .get()
                .getEntries(0)
                .getReadOnlyReason())
        .isEqualTo("No permission");

    // ATS skips IAM; duplicate IDs collapse into one entry.
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    BatchGetDeviceDimensionConfigsResponse clearResp =
        batchGetDeviceDimensionConfigsHandler
            .batchGetDeviceDimensionConfigs(
                poolGetRequest("dev1", "dev1")
                    .setTarget(
                        TargetDimensionValues.newBuilder()
                            .setClearValues(ClearDimensionValues.getDefaultInstance()))
                    .build(),
                SELF_UNIVERSE,
                Optional.empty())
            .get();
    assertThat(clearResp.getEntriesList()).hasSize(1);
    assertThat(clearResp.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
  }

  @Test
  public void batchGetDeviceDimensionConfigs_rejectsInvalidRequests() throws Exception {
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("model").build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "automatically detected by OmniLab");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("  ").build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "must not be empty");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("bad key").build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "Invalid dimension name");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").clearDimensionList().build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "Dimension list must be");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest().addAllDeviceIds(tooManyDeviceIds()).build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "at most 1000 devices");

    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    ExecutionException unavailable =
        assertThrows(
            ExecutionException.class,
            () ->
                batchGetDeviceDimensionConfigsHandler
                    .batchGetDeviceDimensionConfigs(
                        poolGetRequest("dev1").build(), SELF_UNIVERSE, Optional.of("user1"))
                    .get());
    assertThat(unavailable).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_updatesWritableAndReportsReadOnlyError()
      throws Exception {
    DeviceConfig dev1Config =
        poolConfig("dev1", "shared").toBuilder()
            .setBasicConfig(
                poolConfig("dev1", "shared").getBasicConfig().toBuilder()
                    .addOwner("owner_a")
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("LabWifi")))
            .build();
    LabConfig restrictedLab = LabConfig.newBuilder().setHostName("host2").build();
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(dev1Config))
        .addDevice("dev2", "host2", Optional.of(poolConfig("dev2", "shared")))
        .setLabConfig("host2", restrictedLab)
        .allowConfig("dev1", true);
    when(configPusherHelper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG, restrictedLab))
        .thenReturn(true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    BatchUpdateDeviceDimensionConfigsResponse response =
        batchUpdateDeviceDimensionConfigsHandler
            .batchUpdateDeviceDimensionConfigs(
                poolUpdateRequest("dedicated", "dev1", "dev2"), SELF_UNIVERSE, Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap())
        .containsExactly(
            "dev2",
            UpdateError.newBuilder()
                .setCode(UpdateError.Code.VALIDATION_ERROR)
                .setMessage("Managed by Config Pusher")
                .build());
    DeviceConfig written = captureSingleWrite().get("dev1");
    assertThat(written.getBasicConfig().getCompositeDimension().getRequiredDimensionList())
        .containsExactly(
            DeviceDimension.newBuilder().setName("pool").setValue("dedicated").build());
    assertThat(written.getBasicConfig().getOwnerList()).containsExactly("owner_a");
    assertThat(written.getBasicConfig().getDefaultWifi().getSsid()).isEqualTo("LabWifi");
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_configReadFailure_neverOverwritesDevice()
      throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(poolConfig("dev1", "shared")))
        .allowConfig("dev1", true)
        .failDeviceConfigs();

    BatchUpdateDeviceDimensionConfigsResponse response =
        batchUpdateDeviceDimensionConfigsHandler
            .batchUpdateDeviceDimensionConfigs(
                poolUpdateRequest("dedicated", "dev1"), SELF_UNIVERSE, Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap().get("dev1").getMessage())
        .isEqualTo("Failed to load device configuration");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_deviceWithoutStoredConfig_writesNewConfig()
      throws Exception {
    fleet.install().addDevice("dev_new", "host1", Optional.empty()).allowConfig("dev_new", true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    BatchUpdateDeviceDimensionConfigsResponse response =
        batchUpdateDeviceDimensionConfigsHandler
            .batchUpdateDeviceDimensionConfigs(
                poolUpdateRequest("dedicated", "dev_new"), SELF_UNIVERSE, Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap()).isEmpty();
    assertThat(captureSingleWrite()).containsExactly("dev_new", poolConfig("dev_new", "dedicated"));
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_rejectsInvalidRequests() throws Exception {
    TargetDimensionValues clearTarget =
        TargetDimensionValues.newBuilder()
            .setClearValues(ClearDimensionValues.getDefaultInstance())
            .build();
    BatchUpdateDeviceDimensionConfigsRequest valid =
        BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
            .addDeviceIds("dev1")
            .setDimensionName("pool")
            .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
            .setTarget(clearTarget)
            .build();

    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearTarget().build(), SELF_UNIVERSE, Optional.of("user1")),
        "Target dimension values must be set");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearTarget().clearDeviceIds().build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "Target dimension values must be set");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("").build(), SELF_UNIVERSE, Optional.of("user1")),
        "must not be empty");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("model").clearDeviceIds().build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "automatically detected by OmniLab");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("invalid space").clearDeviceIds().build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "Invalid dimension name");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearDimensionList().build(), SELF_UNIVERSE, Optional.of("user1")),
        "Dimension list must be");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearDeviceIds().addAllDeviceIds(tooManyDeviceIds()).build(),
            SELF_UNIVERSE,
            Optional.of("user1")),
        "at most 1000 devices");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_handlesReadOnlyAndBackendFailures()
      throws Exception {
    fleet
        .install()
        .addDevice("dev_ro", "host_shared", Optional.empty())
        .addDevice("dev1", "host1", Optional.of(poolConfig("dev1", "shared")))
        .setLabConfig("host_shared", hostManagedLab("host_shared", "host_pool"))
        .allowConfig("dev1", true);

    // All devices read-only: nothing is written.
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    poolUpdateRequest("dedicated", "dev_ro"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev_ro")
                .getMessage())
        .isEqualTo("Managed by host shared config");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());

    // Backend per-device error with an empty message.
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of("dev1", "")));
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    poolUpdateRequest("dedicated", "dev1"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("Failed to update device configuration.");

    // Backend RPC failure.
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFailedFuture(new RuntimeException("Backend unavailable")));
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    poolUpdateRequest("dedicated", "dev1"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("Backend unavailable");

    // Unavailable universe (with devices and without devices).
    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    poolUpdateRequest("dedicated", "dev1"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("unavailable");
    ExecutionException unavailableEx =
        assertThrows(
            ExecutionException.class,
            () ->
                batchUpdateDeviceDimensionConfigsHandler
                    .batchUpdateDeviceDimensionConfigs(
                        poolUpdateRequest("dedicated"), SELF_UNIVERSE, Optional.of("user1"))
                    .get());
    assertThat(unavailableEx).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }

  private static DeviceConfig poolConfig(String deviceId, String poolValue) {
    return DeviceConfig.newBuilder()
        .setUuid(deviceId)
        .setBasicConfig(
            BasicDeviceConfig.newBuilder()
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addRequiredDimension(
                            DeviceDimension.newBuilder().setName("pool").setValue(poolValue))))
        .build();
  }

  private static LabConfig hostManagedLab(String hostName, String poolValue) {
    return LabConfig.newBuilder()
        .setHostName(hostName)
        .setHostProperties(
            HostProperties.newBuilder()
                .addHostProperty(
                    HostProperty.newBuilder().setKey("device_config_mode").setValue("host")))
        .setDefaultDeviceConfig(poolConfig("", poolValue).getBasicConfig())
        .build();
  }

  private static BatchGetDeviceDimensionConfigsRequest.Builder poolGetRequest(String... deviceIds) {
    return BatchGetDeviceDimensionConfigsRequest.newBuilder()
        .addAllDeviceIds(ImmutableList.copyOf(deviceIds))
        .setDimensionName("pool")
        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED);
  }

  private static BatchUpdateDeviceDimensionConfigsRequest poolUpdateRequest(
      String value, String... deviceIds) {
    return BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
        .addAllDeviceIds(ImmutableList.copyOf(deviceIds))
        .setDimensionName("pool")
        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
        .setTarget(
            TargetDimensionValues.newBuilder()
                .setValueList(DimensionValueList.newBuilder().addValues(value)))
        .build();
  }

  private static ImmutableList<String> tooManyDeviceIds() {
    return IntStream.rangeClosed(0, BatchDeviceConfigHelper.MAX_BATCH_DEVICE_IDS)
        .mapToObj(i -> "dev" + i)
        .collect(toImmutableList());
  }

  private Map<String, DeviceConfig> captureSingleWrite() {
    @SuppressWarnings("unchecked") // Safe cast for generic Map ArgumentCaptor.
    ArgumentCaptor<Map<String, DeviceConfig>> captor = ArgumentCaptor.forClass(Map.class);
    verify(configurationProvider).updateDeviceConfigs(captor.capture(), eq(SELF_UNIVERSE));
    return captor.getValue();
  }

  private static void assertInvalidArgument(Future<?> future, String messageFragment) {
    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains(messageFragment);
  }
}
