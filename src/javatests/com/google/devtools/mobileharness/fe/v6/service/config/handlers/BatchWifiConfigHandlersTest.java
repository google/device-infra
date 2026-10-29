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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ClearDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
public final class BatchWifiConfigHandlersTest {

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private WifiCredentialsStore wifiCredentialsStore;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind @Mock private Environment environment;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private GetWifiSuggestionsHandler getWifiSuggestionsHandler;
  @Inject private BatchGetDeviceWifiConfigsHandler batchGetDeviceWifiConfigsHandler;
  @Inject private BatchUpdateDeviceWifiConfigsHandler batchUpdateDeviceWifiConfigsHandler;

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
  public void getWifiSuggestions_internal1p_returnsCuratedPresets() throws Exception {
    WifiCredentialStore store =
        WifiCredentialStore.newBuilder()
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("GoogleGuest").setPsk(""))
            .addEntries(WifiCredentialEntry.newBuilder().setSsid("GoogleGuestPSK").setPsk("secret"))
            .build();
    when(wifiCredentialsStore.read()).thenReturn(immediateFuture(store));

    GetWifiSuggestionsResponse response =
        getWifiSuggestionsHandler
            .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
            .get();

    assertThat(response.getCandidateWifisList()).hasSize(2);
    assertThat(response.getCandidateWifis(0).getWifi().getSsid()).isEqualTo("GoogleGuest");
    assertThat(response.getCandidateWifis(0).getWifi().getScanSsid()).isFalse();
    assertThat(response.getCandidateWifis(0).hasCuratedPreset()).isTrue();
    assertThat(response.getCandidateWifis(1).getWifi().getSsid()).isEqualTo("GoogleGuestPSK");
    assertThat(response.getCandidateWifis(1).getWifi().getPsk()).isEqualTo("secret");
    assertThat(response.getCandidateWifis(1).getWifi().getScanSsid()).isFalse();
    assertThat(response.getCandidateWifis(1).hasCuratedPreset()).isTrue();
  }

  @Test
  public void getWifiSuggestions_ats_aggregatesAndRanksByDeviceCount() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);

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
                                                        DeviceLocator.newBuilder().setId("dev1")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev2")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("dev3")))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(labInfoResponse));

    DeviceConfig cfg1 =
        DeviceConfig.newBuilder()
            .setUuid("dev1")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("LabWifiA").setPsk("pskA")))
            .build();
    DeviceConfig cfg2 =
        DeviceConfig.newBuilder()
            .setUuid("dev2")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("LabWifiA").setPsk("pskA")))
            .build();
    DeviceConfig cfg3 =
        DeviceConfig.newBuilder()
            .setUuid("dev3")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(
                        WifiConfig.newBuilder()
                            .setSsid("LabWifiB")
                            .setPsk("pskB")
                            .setScanSsid(true)))
            .build();
    when(configurationProvider.getDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableList.of(cfg1, cfg2, cfg3)));

    GetWifiSuggestionsResponse response =
        getWifiSuggestionsHandler
            .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
            .get();

    assertThat(response.getCandidateWifisList()).hasSize(2);
    assertThat(response.getCandidateWifis(0).getWifi().getSsid()).isEqualTo("LabWifiA");
    assertThat(response.getCandidateWifis(0).getFleetInUse().getDeviceCount()).isEqualTo(2);
    assertThat(response.getCandidateWifis(1).getWifi().getSsid()).isEqualTo("LabWifiB");
    assertThat(response.getCandidateWifis(1).getWifi().getScanSsid()).isTrue();
    assertThat(response.getCandidateWifis(1).getFleetInUse().getDeviceCount()).isEqualTo(1);
  }

  @Test
  public void getWifiSuggestions_emptyAtsFleet_returnsEmpty() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    when(labInfoProvider.getLabInfoAsync(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));

    assertThat(
            getWifiSuggestionsHandler
                .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
                .get()
                .getCandidateWifisList())
        .isEmpty();
    verify(configurationProvider, never()).getDeviceConfigs(any(), any());
  }

  @Test
  public void getWifiSuggestions_configServiceUnavailable_readsNothing() throws Exception {
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(false);

    // 1P would read the curated store; ATS would aggregate the fleet. Neither runs.
    assertThat(
            getWifiSuggestionsHandler
                .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
                .get()
                .getCandidateWifisList())
        .isEmpty();
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    assertThat(
            getWifiSuggestionsHandler
                .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance(), SELF_UNIVERSE)
                .get()
                .getCandidateWifisList())
        .isEmpty();

    verifyNoInteractions(wifiCredentialsStore, labInfoProvider, configurationProvider);
  }

  @Test
  public void batchGetDeviceWifiConfigs_computesThreeStateReviewStatuses() throws Exception {
    fleet
        .install()
        .addDevice("dev_update", "host1", Optional.of(wifiConfig("dev_update", "OldWifi", false)))
        .addDevice("dev_match", "host1", Optional.of(wifiConfig("dev_match", "NewWifi", true)))
        .addDevice("dev_readonly", "host_shared", Optional.empty())
        .setLabConfig("host_shared", hostManagedLab("host_shared"))
        .allowConfig("dev_update", true)
        .allowConfig("dev_match", true);

    BatchGetDeviceWifiConfigsResponse response =
        batchGetDeviceWifiConfigsHandler
            .batchGetDeviceWifiConfigs(
                BatchGetDeviceWifiConfigsRequest.newBuilder()
                    .addDeviceIds("dev_update")
                    .addDeviceIds("dev_match")
                    .addDeviceIds("dev_readonly")
                    .setTarget(
                        TargetDeviceWifi.newBuilder()
                            .setWifi(
                                DeviceWifi.newBuilder()
                                    .setSsid("NewWifi")
                                    .setPsk("NewWifi-psk")
                                    .setScanSsid(true)))
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();

    assertThat(response.getEntriesList()).hasSize(3);
    assertThat(response.getEntries(0).getDeviceId()).isEqualTo("dev_update");
    assertThat(response.getEntries(0).getCurrentWifi().getSsid()).isEqualTo("OldWifi");
    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
    assertThat(response.getEntries(1).getDeviceId()).isEqualTo("dev_match");
    assertThat(response.getEntries(1).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);
    assertThat(response.getEntries(2).getDeviceId()).isEqualTo("dev_readonly");
    assertThat(response.getEntries(2).getCurrentWifi().getSsid()).isEqualTo("HostWifi");
    assertThat(response.getEntries(2).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(2).getReadOnlyReason())
        .isEqualTo("Managed by host shared config");
  }

  @Test
  public void batchGetDeviceWifiConfigs_handlesUnsetClearAndUnconfiguredDevices() throws Exception {
    fleet
        .install()
        .addDevice("dev_with_wifi", "host1", Optional.of(wifiConfig("dev_with_wifi", "Old", false)))
        .addDevice("dev_no_wifi", "host1", Optional.empty())
        .allowConfig("dev_with_wifi", true)
        .allowConfig("dev_no_wifi", true);

    // 1. Target unset -> WRITABLE
    assertThat(
            batchGetDeviceWifiConfigsHandler
                .batchGetDeviceWifiConfigs(
                    BatchGetDeviceWifiConfigsRequest.newBuilder()
                        .addDeviceIds("dev_no_wifi")
                        .build(),
                    SELF_UNIVERSE,
                    Optional.of("user1"))
                .get()
                .getEntries(0)
                .getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE);

    // 2. Target with blank SSID -> WRITABLE
    assertThat(
            batchGetDeviceWifiConfigsHandler
                .batchGetDeviceWifiConfigs(
                    BatchGetDeviceWifiConfigsRequest.newBuilder()
                        .addDeviceIds("dev_no_wifi")
                        .setTarget(
                            TargetDeviceWifi.newBuilder()
                                .setWifi(DeviceWifi.newBuilder().setSsid("   ")))
                        .build(),
                    SELF_UNIVERSE,
                    Optional.of("user1"))
                .get()
                .getEntries(0)
                .getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE);

    // 3. Target CLEAR_WIFI -> WILL_UPDATE for configured, ALREADY_MATCHES for unconfigured
    BatchGetDeviceWifiConfigsResponse clearResp =
        batchGetDeviceWifiConfigsHandler
            .batchGetDeviceWifiConfigs(
                BatchGetDeviceWifiConfigsRequest.newBuilder()
                    .addDeviceIds("dev_with_wifi")
                    .addDeviceIds("dev_no_wifi")
                    .setTarget(
                        TargetDeviceWifi.newBuilder()
                            .setClearWifi(ClearDeviceWifi.getDefaultInstance()))
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();
    assertThat(clearResp.getEntries(0).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE);
    assertThat(clearResp.getEntries(1).getStatus())
        .isEqualTo(DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);

    // 4. Too many devices
    ExecutionException tooMany =
        assertThrows(
            ExecutionException.class,
            () ->
                batchGetDeviceWifiConfigsHandler
                    .batchGetDeviceWifiConfigs(
                        BatchGetDeviceWifiConfigsRequest.newBuilder()
                            .addAllDeviceIds(tooManyDeviceIds())
                            .build(),
                        SELF_UNIVERSE,
                        Optional.of("user1"))
                    .get());
    assertThat(tooMany).hasCauseThat().isInstanceOf(FeServiceException.class);

    // 5. Unavailable universe throws
    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    assertThrows(
        ExecutionException.class,
        () ->
            batchGetDeviceWifiConfigsHandler
                .batchGetDeviceWifiConfigs(
                    BatchGetDeviceWifiConfigsRequest.getDefaultInstance(),
                    SELF_UNIVERSE,
                    Optional.of("user1"))
                .get());
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_setsTrimmedWifiAndPreservesOtherFields()
      throws Exception {
    DeviceConfig existing =
        wifiConfig("dev1", "OldWifi", false).toBuilder()
            .setBasicConfig(
                wifiConfig("dev1", "OldWifi", false).getBasicConfig().toBuilder()
                    .addOwner("owner_a"))
            .build();
    fleet.install().addDevice("dev1", "host1", Optional.of(existing)).allowConfig("dev1", true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    BatchUpdateDeviceWifiConfigsResponse response =
        batchUpdateDeviceWifiConfigsHandler
            .batchUpdateDeviceWifiConfigs(
                BatchUpdateDeviceWifiConfigsRequest.newBuilder()
                    .addDeviceIds("dev1")
                    .setTarget(
                        TargetDeviceWifi.newBuilder()
                            .setWifi(
                                DeviceWifi.newBuilder()
                                    .setSsid("  NewWifi  ")
                                    .setPsk("newPsk")
                                    .setScanSsid(true)))
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap()).isEmpty();
    BasicDeviceConfig written = captureSingleWrite().get("dev1").getBasicConfig();
    assertThat(written.getDefaultWifi())
        .isEqualTo(
            WifiConfig.newBuilder().setSsid("NewWifi").setPsk("newPsk").setScanSsid(true).build());
    assertThat(written.getOwnerList()).containsExactly("owner_a");
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_clearsWifi() throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(wifiConfig("dev1", "OldWifi", false)))
        .allowConfig("dev1", true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    BatchUpdateDeviceWifiConfigsResponse response =
        batchUpdateDeviceWifiConfigsHandler
            .batchUpdateDeviceWifiConfigs(
                BatchUpdateDeviceWifiConfigsRequest.newBuilder()
                    .addDeviceIds("dev1")
                    .setTarget(
                        TargetDeviceWifi.newBuilder()
                            .setClearWifi(ClearDeviceWifi.getDefaultInstance()))
                    .build(),
                SELF_UNIVERSE,
                Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap()).isEmpty();
    assertThat(captureSingleWrite().get("dev1").getBasicConfig().hasDefaultWifi()).isFalse();
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_deviceWithoutStoredConfig_writesNewConfig()
      throws Exception {
    fleet.install().addDevice("dev_new", "host1", Optional.empty()).allowConfig("dev_new", true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));

    batchUpdateDeviceWifiConfigsHandler
        .batchUpdateDeviceWifiConfigs(
            wifiUpdateRequest("dev_new"), SELF_UNIVERSE, Optional.of("user1"))
        .get();

    assertThat(captureSingleWrite())
        .containsExactly(
            "dev_new",
            DeviceConfig.newBuilder()
                .setUuid("dev_new")
                .setBasicConfig(
                    BasicDeviceConfig.newBuilder()
                        .setDefaultWifi(WifiConfig.newBuilder().setSsid("NewWifi").setPsk("psk")))
                .build());
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_configReadFailure_neverOverwritesDevice()
      throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(wifiConfig("dev1", "OldWifi", false)))
        .allowConfig("dev1", true)
        .failDeviceConfigs();

    BatchUpdateDeviceWifiConfigsResponse response =
        batchUpdateDeviceWifiConfigsHandler
            .batchUpdateDeviceWifiConfigs(
                wifiUpdateRequest("dev1"), SELF_UNIVERSE, Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap().get("dev1").getMessage())
        .isEqualTo("Failed to load device configuration");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_surfacesBackendPerDeviceError() throws Exception {
    fleet
        .install()
        .addDevice("dev1", "host1", Optional.of(wifiConfig("dev1", "OldWifi", false)))
        .allowConfig("dev1", true);
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of("dev1", "Spanner commit failed")));

    BatchUpdateDeviceWifiConfigsResponse response =
        batchUpdateDeviceWifiConfigsHandler
            .batchUpdateDeviceWifiConfigs(
                wifiUpdateRequest("dev1"), SELF_UNIVERSE, Optional.of("user1"))
            .get();

    assertThat(response.getErrorsMap().get("dev1").getCode()).isEqualTo(UpdateError.Code.UNKNOWN);
    assertThat(response.getErrorsMap().get("dev1").getMessage()).isEqualTo("Spanner commit failed");
  }

  @Test
  public void batchUpdateDeviceWifiConfigs_handlesValidationReadOnlyAndFailureBranches()
      throws Exception {
    // 1. Missing target, blank SSID or too many devices are always INVALID_ARGUMENT.
    assertInvalidArgument(
        BatchUpdateDeviceWifiConfigsRequest.newBuilder().addDeviceIds("dev1").build());
    assertInvalidArgument(
        BatchUpdateDeviceWifiConfigsRequest.newBuilder()
            .addDeviceIds("dev1")
            .setTarget(TargetDeviceWifi.newBuilder().setWifi(DeviceWifi.newBuilder().setSsid("  ")))
            .build());
    assertInvalidArgument(BatchUpdateDeviceWifiConfigsRequest.getDefaultInstance());
    assertInvalidArgument(
        wifiUpdateRequest("dev1").toBuilder()
            .clearDeviceIds()
            .addAllDeviceIds(tooManyDeviceIds())
            .build());

    // 2. All devices read-only
    fleet
        .install()
        .addDevice("dev_ro", "host_shared", Optional.empty())
        .addDevice("dev1", "host1", Optional.of(wifiConfig("dev1", "OldWifi", false)))
        .setLabConfig("host_shared", hostManagedLab("host_shared"))
        .allowConfig("dev1", true);
    assertThat(
            batchUpdateDeviceWifiConfigsHandler
                .batchUpdateDeviceWifiConfigs(
                    wifiUpdateRequest("dev_ro"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev_ro")
                .getMessage())
        .isEqualTo("Managed by host shared config");
    verify(configurationProvider, never()).updateDeviceConfigs(any(), any());

    // 3. RPC exception on updateDeviceConfigs
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFailedFuture(new RuntimeException("RPC failed")));
    assertThat(
            batchUpdateDeviceWifiConfigsHandler
                .batchUpdateDeviceWifiConfigs(
                    wifiUpdateRequest("dev1"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("RPC failed");

    // 4. Unavailable universe (with devices and without devices)
    doThrow(new UnsupportedOperationException("unavailable"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    assertThat(
            batchUpdateDeviceWifiConfigsHandler
                .batchUpdateDeviceWifiConfigs(
                    wifiUpdateRequest("dev1"), SELF_UNIVERSE, Optional.of("user1"))
                .get()
                .getErrorsMap()
                .get("dev1")
                .getMessage())
        .isEqualTo("unavailable");
    ExecutionException unavailableEx =
        assertThrows(
            ExecutionException.class,
            () ->
                batchUpdateDeviceWifiConfigsHandler
                    .batchUpdateDeviceWifiConfigs(
                        wifiUpdateRequest(), SELF_UNIVERSE, Optional.of("user1"))
                    .get());
    assertThat(unavailableEx).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }

  private static DeviceConfig wifiConfig(String deviceId, String ssid, boolean scanSsid) {
    return DeviceConfig.newBuilder()
        .setUuid(deviceId)
        .setBasicConfig(
            BasicDeviceConfig.newBuilder()
                .setDefaultWifi(
                    WifiConfig.newBuilder()
                        .setSsid(ssid)
                        .setPsk(ssid + "-psk")
                        .setScanSsid(scanSsid)))
        .build();
  }

  private static LabConfig hostManagedLab(String hostName) {
    return LabConfig.newBuilder()
        .setHostName(hostName)
        .setHostProperties(
            HostProperties.newBuilder()
                .addHostProperty(
                    HostProperty.newBuilder().setKey("device_config_mode").setValue("host")))
        .setDefaultDeviceConfig(wifiConfig("", "HostWifi", false).getBasicConfig())
        .build();
  }

  private static BatchUpdateDeviceWifiConfigsRequest wifiUpdateRequest(String... deviceIds) {
    return BatchUpdateDeviceWifiConfigsRequest.newBuilder()
        .addAllDeviceIds(ImmutableList.copyOf(deviceIds))
        .setTarget(
            TargetDeviceWifi.newBuilder()
                .setWifi(DeviceWifi.newBuilder().setSsid("NewWifi").setPsk("psk")))
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

  private void assertInvalidArgument(BatchUpdateDeviceWifiConfigsRequest request) {
    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                batchUpdateDeviceWifiConfigsHandler
                    .batchUpdateDeviceWifiConfigs(request, SELF_UNIVERSE, Optional.of("user1"))
                    .get());
    assertThat(e).hasCauseThat().isInstanceOf(FeServiceException.class);
  }
}
