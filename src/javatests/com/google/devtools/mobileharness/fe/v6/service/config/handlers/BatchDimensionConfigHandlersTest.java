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
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceTarget;
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
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DeviceDimensionSummary;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsResponse;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.inject.Guice;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
import java.util.Arrays;
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

  private static final UniverseScope SELF_UNIVERSE = UniverseScope.SELF;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlags flags = new SetFlags();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
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
    flags.set("fe_connect_to_config_server", "true");
    Guice.createInjector(
            BoundFieldModule.of(this),
            new FactoryModuleBuilder().build(ConfigServiceCapabilityFactory.class))
        .injectMembers(this);
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
            .getConfigurableDimensions(GetConfigurableDimensionsRequest.getDefaultInstance())
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
            .getConfigurableDimensions(GetConfigurableDimensionsRequest.getDefaultInstance())
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
  public void getConfigurableDimensions_disabledConfigServerInAts_returnsEnabledFalse()
      throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    flags.set("fe_connect_to_config_server", "false");

    GetConfigurableDimensionsResponse response =
        getConfigurableDimensionsHandler
            .getConfigurableDimensions(GetConfigurableDimensionsRequest.getDefaultInstance())
            .get();
    assertThat(response.getEnabled()).isFalse();
    assertThat(response.getDimensionsList()).isEmpty();
    assertThat(response.getAllowCustomDimensions()).isFalse();
  }

  @Test
  public void batchGetConfigurableDimensionKeys_internalSupported_usesGetAllDeviceDimensions()
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

    GetAllDeviceDimensionsResponse allDimensions =
        GetAllDeviceDimensionsResponse.newBuilder()
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("model").setDeviceCount(100))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("pool").setDeviceCount(50))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("label").setDeviceCount(2))
            .addDimensions(
                DeviceDimensionSummary.newBuilder().setName("dim::label").setDeviceCount(5))
            .addDimensions(
                DeviceDimensionSummary.newBuilder().setName("custom_team_tag").setDeviceCount(1))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("unused").setDeviceCount(0))
            .build();
    when(labInfoProvider.getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE))
        .thenReturn(immediateFuture(allDimensions));

    // Include an unrecognizable device ID alongside dev1; only valid UUIDs hit getDeviceConfigs.
    BatchGetConfigurableDimensionKeysRequest request =
        BatchGetConfigurableDimensionKeysRequest.newBuilder()
            .addDevices(target("dev1", "host1"))
            .addDevices(target("unrecognizable device", "host1"))
            .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
            .build();

    BatchGetConfigurableDimensionKeysResponse response =
        batchGetConfigurableDimensionKeysHandler.batchGetConfigurableDimensionKeys(request).get();

    verify(labInfoProvider)
        .getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE);
    verify(labInfoProvider, never()).getLabInfoAsync(any(), any());

    // "pool" (curated required-only) and "dim::label" (invalid ':' key) are excluded from
    // DIMENSION_LIST_SUPPORTED fleet counts without double-counting "label".
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
  public void batchGetConfigurableDimensionKeys_internalRequiredAtsDefaultsAndValidation()
      throws Exception {
    // 1. Internal REQUIRED with GetAllDeviceDimensions excludes supported-only curated keys
    // ("label", "cluster") while keeping custom keys and curated "pool".
    GetAllDeviceDimensionsResponse requiredDimensions =
        GetAllDeviceDimensionsResponse.newBuilder()
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("label").setDeviceCount(10))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("cluster").setDeviceCount(8))
            .addDimensions(
                DeviceDimensionSummary.newBuilder().setName("custom_req").setDeviceCount(1))
            .build();
    when(labInfoProvider.getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE))
        .thenReturn(immediateFuture(requiredDimensions));

    BatchGetConfigurableDimensionKeysResponse reqResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build())
            .get();
    assertThat(reqResponse.getCandidateKeysList()).hasSize(2);
    assertThat(reqResponse.getCandidateKeys(0).getDimensionName()).isEqualTo("custom_req");
    assertThat(reqResponse.getCandidateKeys(0).getFleetDeviceCount()).isEqualTo(1);
    assertThat(reqResponse.getCandidateKeys(1).getDimensionName()).isEqualTo("pool");
    assertThat(reqResponse.getCandidateKeys(1).getFleetDeviceCount()).isEqualTo(0);
    verify(labInfoProvider)
        .getAllDeviceDimensionsAsync(
            GetAllDeviceDimensionsRequest.getDefaultInstance(), SELF_UNIVERSE);

    // 2. ATS mode returns ONLY ATS curated keys (never leaking 1P defaults or non-curated custom
    // keys present in stored ATS DeviceConfigs)
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    BatchGetConfigurableDimensionKeysResponse atsSupportedEmpty =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                    .build())
            .get();
    assertThat(
            atsSupportedEmpty.getCandidateKeysList().stream()
                .map(
                    BatchGetConfigurableDimensionKeysResponse.CandidateDimensionKey
                        ::getDimensionName))
        .containsExactly("recovery", "skip_suw_app")
        .inOrder();

    DeviceConfig atsConfig =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("ats_pool"))
                            .addRequiredDimension(
                                DeviceDimension.newBuilder()
                                    .setName("non_curated_custom")
                                    .setValue("v1"))))
            .build();
    when(configurationProvider.getDeviceConfigs(ImmutableList.of("dev1"), SELF_UNIVERSE))
        .thenReturn(immediateFuture(ImmutableList.of(atsConfig)));
    BatchGetConfigurableDimensionKeysResponse atsPopulatedResponse =
        batchGetConfigurableDimensionKeysHandler
            .batchGetConfigurableDimensionKeys(
                BatchGetConfigurableDimensionKeysRequest.newBuilder()
                    .addDevices(target("dev1", "host1"))
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build())
            .get();
    assertThat(atsPopulatedResponse.getCandidateKeysList()).hasSize(2);
    assertThat(atsPopulatedResponse.getCandidateKeys(0).getDimensionName()).isEqualTo("pool");
    assertThat(atsPopulatedResponse.getCandidateKeys(0).getSelectedDeviceCount()).isEqualTo(1);
    assertThat(atsPopulatedResponse.getCandidateKeys(1).getDimensionName())
        .isEqualTo("persist_test_harness");

    // 3. Unspecified dimensionList and >1000 devices -> INVALID_ARGUMENT
    assertInvalidArgument(
        batchGetConfigurableDimensionKeysHandler.batchGetConfigurableDimensionKeys(
            BatchGetConfigurableDimensionKeysRequest.getDefaultInstance()),
        "Dimension list must be");
    assertInvalidArgument(
        batchGetConfigurableDimensionKeysHandler.batchGetConfigurableDimensionKeys(
            BatchGetConfigurableDimensionKeysRequest.newBuilder()
                .addAllDevices(tooManyTargets())
                .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                .build()),
        "at most 1000 devices");

    // 4. Disabled config server in ATS -> FAILED_PRECONDITION
    flags.set("fe_connect_to_config_server", "false");
    assertFailedPrecondition(
        batchGetConfigurableDimensionKeysHandler.batchGetConfigurableDimensionKeys(
            BatchGetConfigurableDimensionKeysRequest.newBuilder()
                .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                .build()));
  }

  @Test
  public void getDimensionValueSuggestions_preFiltersByDimensionAndRanksByDeviceCount()
      throws Exception {
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
                                                        DeviceLocator.newBuilder().setId("dev3")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev4"))
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addSupportedDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("pool")
                                                                            .setValue(
                                                                                "supported_only")))))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev5"))
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("pool")
                                                                            .setValue("Shared")))))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev6"))
                                                    .setDeviceFeature(
                                                        DeviceFeature.newBuilder()
                                                            .setCompositeDimension(
                                                                DeviceCompositeDimension
                                                                    .newBuilder()
                                                                    .addRequiredDimension(
                                                                        DeviceDimension.newBuilder()
                                                                            .setName("pool")
                                                                            .setValue(
                                                                                "Dedicated")))))))))
            .build();
    fleet.install().setFleetQueryResponse(labInfoResponse);

    GetDimensionValueSuggestionsRequest request =
        GetDimensionValueSuggestionsRequest.newBuilder()
            .setDimensionName("pool")
            .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
            .build();

    GetDimensionValueSuggestionsResponse response =
        getDimensionValueSuggestionsHandler.getDimensionValueSuggestions(request).get();

    ArgumentCaptor<GetLabInfoRequest> reqCaptor = ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(reqCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(reqCaptor.getValue().getPage().getLimit()).isEqualTo(0);
    assertThat(reqCaptor.getValue().getUseRealtimeData()).isFalse();
    DeviceMatchCondition filterCondition =
        reqCaptor.getValue().getLabQuery().getFilter().getDeviceFilter().getDeviceMatchCondition(0);
    assertThat(filterCondition.getDimensionMatchCondition().getCondition().getKey())
        .isEqualTo("pool");
    assertThat(
            filterCondition
                .getDimensionMatchCondition()
                .getCondition()
                .getValueCondition()
                .getLengthMatch()
                .getGreaterThanOrEqual()
                .getMinValue())
        .isEqualTo(1);
    assertThat(
            reqCaptor
                .getValue()
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getRequiredDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("pool");

    // dev3 has no pool value, dev4 carries pool only in supported_dimension, dev5's "Shared"
    // stays case-distinct from "shared", and equal-count suggestions ("Dedicated" and "Shared")
    // break ties lexicographically.
    assertThat(response.getCandidateValuesList()).hasSize(3);
    assertThat(response.getCandidateValues(0).getValuesList()).containsExactly("shared");
    assertThat(response.getCandidateValues(0).getDeviceCount()).isEqualTo(2);
    assertThat(response.getCandidateValues(1).getValuesList()).containsExactly("Dedicated");
    assertThat(response.getCandidateValues(1).getDeviceCount()).isEqualTo(1);
    assertThat(response.getCandidateValues(2).getValuesList()).containsExactly("Shared");
    assertThat(response.getCandidateValues(2).getDeviceCount()).isEqualTo(1);
  }

  @Test
  public void getDimensionValueSuggestions_handlesEmptyNameSupportedListAtsAndValidation()
      throws Exception {
    // 1. Empty dimension name returns INVALID_ARGUMENT without touching backends
    assertInvalidArgument(
        getDimensionValueSuggestionsHandler.getDimensionValueSuggestions(
            GetDimensionValueSuggestionsRequest.getDefaultInstance()),
        "Dimension name must not be empty");
    verify(labInfoProvider, never()).getLabInfoAsync(any(), any());

    // 2. Internal SUPPORTED with missing LabView
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    assertThat(
            getDimensionValueSuggestionsHandler
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder()
                        .setDimensionName("label")
                        .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                        .build())
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

    // 3. ATS mode with populated fleet and curated policy check
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
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
                                                        DeviceLocator.newBuilder().setId("dev1")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("unrecognizable device")))
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
                    .build())
            .get();
    assertThat(atsResp.getCandidateValuesList()).hasSize(1);
    assertThat(atsResp.getCandidateValues(0).getValuesList()).containsExactly("fastboot");

    // Non-curated dimension in ATS -> INVALID_ARGUMENT
    assertInvalidArgument(
        getDimensionValueSuggestionsHandler.getDimensionValueSuggestions(
            GetDimensionValueSuggestionsRequest.newBuilder()
                .setDimensionName("label")
                .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                .build()),
        "not configurable in ATS");

    // Unspecified dimensionList -> INVALID_ARGUMENT
    assertInvalidArgument(
        getDimensionValueSuggestionsHandler.getDimensionValueSuggestions(
            GetDimensionValueSuggestionsRequest.newBuilder().setDimensionName("pool").build()),
        "Dimension list must be");

    // 4. Disabled config server in ATS -> FAILED_PRECONDITION
    flags.set("fe_connect_to_config_server", "false");
    assertFailedPrecondition(
        getDimensionValueSuggestionsHandler.getDimensionValueSuggestions(
            GetDimensionValueSuggestionsRequest.newBuilder()
                .setDimensionName("pool")
                .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                .build()));
  }

  @Test
  public void batchGetDeviceDimensionConfigs_computesThreeStateReviewStatusesAndHostNames()
      throws Exception {
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
                Optional.of("user1"))
            .get();

    assertThat(response.getEntriesList()).hasSize(4);
    assertThat(response.getEntries(0).getDeviceId()).isEqualTo("dev_update");
    assertThat(response.getEntries(0).getHostName()).isEqualTo("host1");
    assertThat(response.getEntries(0).getValuesList()).containsExactly("shared");
    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
    assertThat(response.getEntries(1).getDeviceId()).isEqualTo("dev_match");
    assertThat(response.getEntries(1).getHostName()).isEqualTo("host1");
    assertThat(response.getEntries(1).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);
    assertThat(response.getEntries(2).getDeviceId()).isEqualTo("dev_host_managed");
    assertThat(response.getEntries(2).getHostName()).isEqualTo("host_shared");
    assertThat(response.getEntries(2).getValuesList()).containsExactly("host_pool");
    assertThat(response.getEntries(2).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(2).getReadOnlyReason())
        .isEqualTo("Managed by host shared config");
    assertThat(response.getEntries(3).getDeviceId()).isEqualTo("dev_no_perm");
    assertThat(response.getEntries(3).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(3).getReadOnlyReason()).isEqualTo("No permission");

    verify(iamPermissionChecker)
        .canConfigDevice("dev_update", poolConfig("dev_update", "shared"), SELF_UNIVERSE);
    verify(iamPermissionChecker, never()).canConfigDevice(any(), any(UniverseScope.class));
    verify(labInfoProvider).getLabInfoAsync(any(), eq(SELF_UNIVERSE));
    verify(configurationProvider).getDeviceConfigs(any(), eq(SELF_UNIVERSE));
    verify(configurationProvider).getLabConfig("host1", SELF_UNIVERSE);
    verify(configurationProvider).getLabConfig("host_shared", SELF_UNIVERSE);
  }

  @Test
  public void batchGetDeviceDimensionConfigs_orderInsensitiveAndCaseSensitiveValueComparison()
      throws Exception {
    DeviceConfig devReordered =
        DeviceConfig.newBuilder()
            .setUuid("dev_reordered")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("label").setValue("beta"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("label").setValue("alpha"))))
            .build();
    DeviceConfig devCaseMismatch =
        DeviceConfig.newBuilder()
            .setUuid("dev_case_mismatch")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("label").setValue("Alpha"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("label").setValue("beta"))))
            .build();
    fleet
        .install()
        .addDevice("dev_reordered", "host1", Optional.of(devReordered))
        .addDevice("dev_case_mismatch", "host1", Optional.of(devCaseMismatch))
        .allowConfig("dev_reordered", true)
        .allowConfig("dev_case_mismatch", true);

    BatchGetDeviceDimensionConfigsResponse response =
        batchGetDeviceDimensionConfigsHandler
            .batchGetDeviceDimensionConfigs(
                BatchGetDeviceDimensionConfigsRequest.newBuilder()
                    .addDevices(target("dev_reordered", "host1"))
                    .addDevices(target("dev_case_mismatch", "host1"))
                    .setDimensionName("label")
                    .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                    .setTarget(
                        TargetDimensionValues.newBuilder()
                            .setValueList(
                                DimensionValueList.newBuilder()
                                    .addValues("alpha")
                                    .addValues("beta")))
                    .build(),
                Optional.of("user1"))
            .get();

    assertThat(response.getEntriesList()).hasSize(2);
    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);
    assertThat(response.getEntries(1).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
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
                poolGetRequest("dev_writable").build(), Optional.of("user1"))
            .get();
    assertThat(configReadFailed.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(configReadFailed.getEntries(0).getReadOnlyReason())
        .isEqualTo("Failed to load device configuration");

    fleet.failLabInfo();
    assertThat(
            batchGetDeviceDimensionConfigsHandler
                .batchGetDeviceDimensionConfigs(
                    poolGetRequest("dev_writable").build(), Optional.of("user1"))
                .get()
                .getEntries(0)
                .getReadOnlyReason())
        .isEqualTo("Failed to load device information");
  }

  @Test
  public void batchGetDeviceDimensionConfigs_emptyUsernameAtsAndDuplicateTargets()
      throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(poolConfig("dev1", "shared")))
        .allowConfig("dev1", true);

    // Empty username in 1P is never writable.
    assertThat(
            batchGetDeviceDimensionConfigsHandler
                .batchGetDeviceDimensionConfigs(poolGetRequest("dev1").build(), Optional.empty())
                .get()
                .getEntries(0)
                .getReadOnlyReason())
        .isEqualTo("No permission");

    // ATS allows empty username; duplicate targets collapse into one entry.
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
                Optional.empty())
            .get();
    assertThat(clearResp.getEntriesList()).hasSize(1);
    assertThat(clearResp.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
  }

  @Test
  public void batchGetDeviceDimensionConfigs_rejectsInvalidRequestsAndDisabledConfigServer()
      throws Exception {
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("model").build(), Optional.of("user1")),
        "automatically detected by OmniLab");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("  ").build(), Optional.of("user1")),
        "must not be empty");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").setDimensionName("bad key").build(), Optional.of("user1")),
        "Invalid dimension name");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").clearDimensionList().build(), Optional.of("user1")),
        "Dimension list must be");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest()
                .addDevices(target("dev1"))
                .setTarget(
                    TargetDimensionValues.newBuilder()
                        .setValueList(DimensionValueList.newBuilder().addValues("  ")))
                .build(),
            Optional.of("user1")),
        "at least one non-blank value");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1")
                .setTarget(
                    TargetDimensionValues.newBuilder()
                        .setValueList(DimensionValueList.newBuilder().addValues("regex:.*")))
                .build(),
            Optional.of("user1")),
        "must not start with \"regex:\"");
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest().addAllDevices(tooManyTargets()).build(), Optional.of("user1")),
        "at most 1000 devices");

    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    assertInvalidArgument(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1")
                .setDimensionName("label")
                .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                .build(),
            Optional.empty()),
        "not configurable in ATS");

    flags.set("fe_connect_to_config_server", "false");
    assertFailedPrecondition(
        batchGetDeviceDimensionConfigsHandler.batchGetDeviceDimensionConfigs(
            poolGetRequest("dev1").build(), Optional.empty()));
  }

  @Test
  // BasicDeviceConfig.default_wifi is the active storage field for device Wi-Fi configuration.
  @SuppressWarnings("deprecation")
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
                poolUpdateRequest("dedicated", "dev1", "dev2"), Optional.of("user1"))
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
                poolUpdateRequest("dedicated", "dev1"), Optional.of("user1"))
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
                poolUpdateRequest("dedicated", "dev_new"), Optional.of("user1"))
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
            .addDevices(target("dev1", "host1"))
            .setDimensionName("pool")
            .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
            .setTarget(clearTarget)
            .build();

    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearTarget().build(), Optional.of("user1")),
        "Target dimension values must be set");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder()
                .setTarget(
                    TargetDimensionValues.newBuilder()
                        .setValueList(DimensionValueList.newBuilder().addValues("   ")))
                .build(),
            Optional.of("user1")),
        "at least one non-blank value");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder()
                .setTarget(
                    TargetDimensionValues.newBuilder()
                        .setValueList(DimensionValueList.newBuilder().addValues("exclude")))
                .build(),
            Optional.of("user1")),
        "must not be \"exclude\"");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("").build(), Optional.of("user1")),
        "must not be empty");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("model").clearDevices().build(),
            Optional.of("user1")),
        "automatically detected by OmniLab");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().setDimensionName("invalid space").clearDevices().build(),
            Optional.of("user1")),
        "Invalid dimension name");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearDimensionList().build(), Optional.of("user1")),
        "Dimension list must be");
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder().clearDevices().addAllDevices(tooManyTargets()).build(),
            Optional.of("user1")),
        "at most 1000 devices");

    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    assertInvalidArgument(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            valid.toBuilder()
                .setDimensionName("pool")
                .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                .build(),
            Optional.empty()),
        "not configurable in ATS");

    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());
  }

  @Test
  public void batchUpdateDeviceDimensionConfigs_handlesReadOnlyCollidingIdsAndDisabledConfigServer()
      throws Exception {
    fleet
        .install()
        .addDevice("dup", "host1", Optional.of(poolConfig("dup", "shared")))
        .addDevice("dup", "host_shared", Optional.empty())
        .addDevice("dev1", "host1", Optional.of(poolConfig("dev1", "shared")))
        .setLabConfig("host_shared", hostManagedLab("host_shared", "host_pool"))
        .allowConfig("dup", true)
        .allowConfig("dev1", true);

    // Colliding deviceId where a sibling host in LabInfo is read-only never mutates the shared
    // DeviceConfig, even when Step 3 sends only the standalone host target.
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
                        .addDevices(target("dup", "host1"))
                        .setDimensionName("pool")
                        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                        .setTarget(
                            TargetDimensionValues.newBuilder()
                                .setValueList(
                                    DimensionValueList.newBuilder().addValues("dedicated")))
                        .build(),
                    Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dup")
                .getMessage())
        .isEqualTo("Managed by host shared config on colliding host host_shared");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());

    // Backend per-device error with an empty message.
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of("dev1", "")));
    assertThat(
            batchUpdateDeviceDimensionConfigsHandler
                .batchUpdateDeviceDimensionConfigs(
                    poolUpdateRequest("dedicated", "dev1"), Optional.of("user1"))
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
                    poolUpdateRequest("dedicated", "dev1"), Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("Backend unavailable");

    // Disabled config server in ATS rejects with FAILED_PRECONDITION.
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    flags.set("fe_connect_to_config_server", "false");
    assertFailedPrecondition(
        batchUpdateDeviceDimensionConfigsHandler.batchUpdateDeviceDimensionConfigs(
            poolUpdateRequest("dedicated", "dev1"), Optional.empty()));
  }

  private static DeviceTarget target(String deviceId) {
    return DeviceTarget.newBuilder().setDeviceId(deviceId).build();
  }

  private static DeviceTarget target(String deviceId, String hostName) {
    return DeviceTarget.newBuilder().setDeviceId(deviceId).setHostName(hostName).build();
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
        .addAllDevices(
            Arrays.stream(deviceIds).map(BatchDimensionConfigHandlersTest::target).toList())
        .setDimensionName("pool")
        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED);
  }

  private static BatchUpdateDeviceDimensionConfigsRequest poolUpdateRequest(
      String value, String... deviceIds) {
    return BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
        .addAllDevices(
            Arrays.stream(deviceIds).map(BatchDimensionConfigHandlersTest::target).toList())
        .setDimensionName("pool")
        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
        .setTarget(
            TargetDimensionValues.newBuilder()
                .setValueList(DimensionValueList.newBuilder().addValues(value)))
        .build();
  }

  private static ImmutableList<DeviceTarget> tooManyTargets() {
    return IntStream.rangeClosed(0, BatchDeviceConfigHelper.MAX_BATCH_DEVICE_IDS)
        .mapToObj(i -> target("dev" + i, "host1"))
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
    assertThat(((FeServiceException) e.getCause()).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(e).hasCauseThat().hasMessageThat().contains(messageFragment);
  }

  private static void assertFailedPrecondition(Future<?> future) {
    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(((FeServiceException) e.getCause()).getCode())
        .isEqualTo(Status.Code.FAILED_PRECONDITION);
  }
}
