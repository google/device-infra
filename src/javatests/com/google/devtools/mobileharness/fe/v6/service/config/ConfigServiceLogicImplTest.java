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

package com.google.devtools.mobileharness.fe.v6.service.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.FakeBatchFleet;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse.CandidateDimensionKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsResponse.DeviceDimensionConfigEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ClearDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ClearDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse.CandidateDimensionValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDimensionValues;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ConfigServiceLogicImplTest {

  private static final UniverseScope SELF = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private WifiCredentialsStore wifiCredentialsStore;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private Environment environment;

  private ConfigServiceLogicImpl configServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(SELF);
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(true);
    when(environment.isGoogleInternal()).thenReturn(true);
    configServiceLogicImpl =
        Guice.createInjector(BoundFieldModule.of(this)).getInstance(ConfigServiceLogicImpl.class);
  }

  @Test
  public void getDeviceConfig_invalidUniverse_fails() {
    GetDeviceConfigRequest request =
        GetDeviceConfigRequest.newBuilder().setId("device").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> configServiceLogicImpl.getDeviceConfig(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void unlockHostProperties_success() throws Exception {
    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName("host").setUniverse("self").build();
    LabConfig existingConfig = LabConfig.newBuilder().setHostName("host").build();

    when(configurationProvider.getLabConfig(eq("host"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(existingConfig))));
    when(configPusherHelper.unlockHostProperties(any(LabConfig.Builder.class)))
        .thenAnswer(
            invocation -> {
              LabConfig.Builder builder = invocation.getArgument(0);
              builder.setHostName("host-modified"); // simulate modification
              return true;
            });
    when(configurationProvider.updateLabConfig(
            eq("host"), any(LabConfig.class), any(UniverseScope.class)))
        .thenReturn(immediateVoidFuture());

    UnlockHostPropertiesResponse response =
        configServiceLogicImpl.unlockHostProperties(request).get();

    assertThat(response.getSuccess()).isTrue();
    verify(configurationProvider)
        .updateLabConfig(eq("host"), any(LabConfig.class), any(UniverseScope.class));
  }

  @Test
  public void checkDeviceConfigPermission_delegatesToHandler() throws Exception {
    CheckDeviceConfigPermissionRequest request =
        CheckDeviceConfigPermissionRequest.newBuilder().setId("device").setUniverse("self").build();
    when(iamPermissionChecker.canConfigDevice(eq("device"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(true));

    CheckDeviceConfigPermissionResponse response =
        configServiceLogicImpl.checkDeviceConfigPermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
    assertThat(response.getUserName()).isEqualTo("user");
  }

  @Test
  public void checkHostConfigPermission_delegatesToHandler() throws Exception {
    CheckHostConfigPermissionRequest request =
        CheckHostConfigPermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("self")
            .build();
    when(iamPermissionChecker.canConfigHost(eq("host"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(true));

    CheckHostConfigPermissionResponse response =
        configServiceLogicImpl.checkHostConfigPermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
    assertThat(response.getUserName()).isEqualTo("user");
  }

  @Test
  public void unlockHostProperties_invalidUniverse_fails() {
    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName("host").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> configServiceLogicImpl.unlockHostProperties(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void batchConfigRpcs_delegateToHandlers() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(true);
    when(environment.isAts()).thenReturn(false);
    DeviceConfig dev1Config =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("shared"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("team").setValue("a")))
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("LabWifi").setPsk("psk")))
            .build();
    DeviceInfo fleetDevice =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(dev1Config.getBasicConfig().getCompositeDimension()))
            .build();
    new FakeBatchFleet(labInfoProvider, configurationProvider, iamPermissionChecker, SELF)
        .install()
        .addDevice("dev1", "host1", Optional.of(dev1Config))
        .allowConfig("dev1", true)
        .setFleetQueryResponse(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setLabView(
                            LabView.newBuilder()
                                .addLabData(
                                    LabData.newBuilder()
                                        .setDeviceList(
                                            DeviceList.newBuilder().addDeviceInfo(fleetDevice)))))
                .build());
    when(wifiCredentialsStore.read())
        .thenReturn(
            immediateFuture(
                WifiCredentialStore.newBuilder()
                    .addEntries(WifiCredentialEntry.newBuilder().setSsid("GoogleGuest"))
                    .build()));
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    assertThat(
            configServiceLogicImpl
                .getConfigurableDimensions(GetConfigurableDimensionsRequest.getDefaultInstance())
                .get()
                .getDimensions(0)
                .getDimensionName())
        .isEqualTo("pool");

    assertThat(
            configServiceLogicImpl
                .batchGetConfigurableDimensionKeys(
                    BatchGetConfigurableDimensionKeysRequest.newBuilder()
                        .addDeviceIds("dev1")
                        .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                        .build())
                .get()
                .getCandidateKeys(0))
        .isEqualTo(
            CandidateDimensionKey.newBuilder()
                .setDimensionName("team")
                .setSelectedDeviceCount(1)
                .setFleetDeviceCount(1)
                .build());

    assertThat(
            configServiceLogicImpl
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder()
                        .setDimensionName("pool")
                        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                        .build())
                .get()
                .getCandidateValuesList())
        .containsExactly(
            CandidateDimensionValue.newBuilder().addValues("shared").setDeviceCount(1).build());

    DeviceDimensionConfigEntry dimensionEntry =
        configServiceLogicImpl
            .batchGetDeviceDimensionConfigs(
                BatchGetDeviceDimensionConfigsRequest.newBuilder()
                    .addDeviceIds("dev1")
                    .setDimensionName("pool")
                    .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                    .build(),
                Optional.of("user"))
            .get()
            .getEntries(0);
    assertThat(dimensionEntry.getValuesList()).containsExactly("shared");
    assertThat(dimensionEntry.getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE);

    assertThat(
            configServiceLogicImpl
                .batchUpdateDeviceDimensionConfigs(
                    BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
                        .addDeviceIds("dev1")
                        .setDimensionName("pool")
                        .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                        .setTarget(
                            TargetDimensionValues.newBuilder()
                                .setClearValues(ClearDimensionValues.getDefaultInstance()))
                        .build(),
                    Optional.of("user"))
                .get()
                .getErrorsMap())
        .isEmpty();

    assertThat(
            configServiceLogicImpl
                .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance())
                .get()
                .getCandidateWifis(0)
                .getWifi()
                .getSsid())
        .isEqualTo("GoogleGuest");

    assertThat(
            configServiceLogicImpl
                .batchGetDeviceWifiConfigs(
                    BatchGetDeviceWifiConfigsRequest.newBuilder().addDeviceIds("dev1").build(),
                    Optional.of("user"))
                .get()
                .getEntries(0)
                .getCurrentWifi())
        .isEqualTo(DeviceWifi.newBuilder().setSsid("LabWifi").setPsk("psk").build());

    assertThat(
            configServiceLogicImpl
                .batchUpdateDeviceWifiConfigs(
                    BatchUpdateDeviceWifiConfigsRequest.newBuilder()
                        .addDeviceIds("dev1")
                        .setTarget(
                            TargetDeviceWifi.newBuilder()
                                .setClearWifi(ClearDeviceWifi.getDefaultInstance()))
                        .build(),
                    Optional.of("user"))
                .get()
                .getErrorsMap())
        .isEmpty();

    @SuppressWarnings("unchecked") // Safe cast for generic Map ArgumentCaptor.
    ArgumentCaptor<Map<String, DeviceConfig>> writes = ArgumentCaptor.forClass(Map.class);
    verify(configurationProvider, times(2)).updateDeviceConfigs(writes.capture(), eq(SELF));
    assertThat(
            writes
                .getAllValues()
                .get(0)
                .get("dev1")
                .getBasicConfig()
                .getCompositeDimension()
                .getRequiredDimensionList())
        .isEmpty();
    assertThat(writes.getAllValues().get(1).get("dev1").getBasicConfig().hasDefaultWifi())
        .isFalse();
  }

  @Test
  public void batchConfigRpcs_invalidUniverse_fails() {
    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .getConfigurableDimensions(
                    GetConfigurableDimensionsRequest.newBuilder().setUniverse("invalid").build())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .batchGetConfigurableDimensionKeys(
                    BatchGetConfigurableDimensionKeysRequest.newBuilder()
                        .setUniverse("invalid")
                        .build())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .getDimensionValueSuggestions(
                    GetDimensionValueSuggestionsRequest.newBuilder().setUniverse("invalid").build())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .batchGetDeviceDimensionConfigs(
                    BatchGetDeviceDimensionConfigsRequest.newBuilder()
                        .setUniverse("invalid")
                        .build(),
                    Optional.empty())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .batchUpdateDeviceDimensionConfigs(
                    BatchUpdateDeviceDimensionConfigsRequest.newBuilder()
                        .setUniverse("invalid")
                        .build(),
                    Optional.empty())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .getWifiSuggestions(
                    GetWifiSuggestionsRequest.newBuilder().setUniverse("invalid").build())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .batchGetDeviceWifiConfigs(
                    BatchGetDeviceWifiConfigsRequest.newBuilder().setUniverse("invalid").build(),
                    Optional.empty())
                .get());
    assertInvalidUniverse(
        () ->
            configServiceLogicImpl
                .batchUpdateDeviceWifiConfigs(
                    BatchUpdateDeviceWifiConfigsRequest.newBuilder().setUniverse("invalid").build(),
                    Optional.empty())
                .get());
  }

  private static void assertInvalidUniverse(ThrowingRunnable runnable) {
    ExecutionException ex = assertThrows(ExecutionException.class, runnable);
    assertThat(ex).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
