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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetHostWifiConfigsResponse.HostWifiConfigEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateHostWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateHostWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ClearDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigChangeStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.TargetDeviceWifi;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
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
public final class BatchHostWifiConfigHandlersTest {

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Bind @Mock private Environment environment;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private BatchGetHostWifiConfigsHandler batchGetHostWifiConfigsHandler;
  @Inject private BatchUpdateHostWifiConfigsHandler batchUpdateHostWifiConfigsHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(true);
    when(configPusherHelper.isSectionRestricted(any(), any())).thenReturn(false);
    when(environment.isGoogleInternal()).thenReturn(false);
    when(environment.isAts()).thenReturn(true);
    when(configurationProvider.getLabConfig(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.empty())));
    when(configurationProvider.updateLabConfig(
            anyString(), any(LabConfig.class), any(UniverseScope.class)))
        .thenReturn(immediateVoidFuture());
  }

  @Test
  @SuppressWarnings("deprecation") // Exercises fallback to BasicDeviceConfig.default_wifi.
  public void batchGetHostWifiConfigs_step1InitialLoad_extractsLabWifiAndFallback()
      throws Exception {
    LabConfig host1Config =
        LabConfig.newBuilder()
            .setHostName("host-1")
            .setWifiConfig(
                WifiConfig.newBuilder()
                    .setSsid("ats-lab-wifi")
                    .setPsk("secret")
                    .setScanSsid(true)
                    .build())
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("legacy-wifi").build())
                    .build())
            .build();
    LabConfig host2Config =
        LabConfig.newBuilder()
            .setHostName("host-2")
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("legacy-only").build())
                    .build())
            .build();
    when(configurationProvider.getLabConfig("host-1", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(host1Config))));
    when(configurationProvider.getLabConfig("host-2", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(host2Config))));

    BatchGetHostWifiConfigsRequest request =
        BatchGetHostWifiConfigsRequest.newBuilder()
            .addHostNames("host-1")
            .addHostNames("host-2")
            .addHostNames("host-1")
            .build();

    BatchGetHostWifiConfigsResponse response =
        batchGetHostWifiConfigsHandler
            .batchGetHostWifiConfigs(request, SELF_UNIVERSE, Optional.empty())
            .get();

    assertThat(response.getEntriesList())
        .containsExactly(
            HostWifiConfigEntry.newBuilder()
                .setHostName("host-1")
                .setCurrentWifi(
                    DeviceWifi.newBuilder()
                        .setSsid("ats-lab-wifi")
                        .setPsk("secret")
                        .setScanSsid(true))
                .setStatus(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WRITABLE)
                .build(),
            HostWifiConfigEntry.newBuilder()
                .setHostName("host-2")
                .setCurrentWifi(DeviceWifi.newBuilder().setSsid("legacy-only"))
                .setStatus(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WRITABLE)
                .build())
        .inOrder();
  }

  @Test
  public void batchGetHostWifiConfigs_step2Review_classifiesThreeStatesInInternalMode()
      throws Exception {
    when(environment.isGoogleInternal()).thenReturn(true);
    when(environment.isAts()).thenReturn(false);

    LabConfig matchingHost =
        LabConfig.newBuilder()
            .setHostName("host-match")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("target-ssid").setPsk("psk").build())
            .build();
    LabConfig updatingHost =
        LabConfig.newBuilder()
            .setHostName("host-update")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("old-ssid").build())
            .build();
    LabConfig pusherHost = LabConfig.newBuilder().setHostName("host-pusher").build();
    LabConfig deniedHost = LabConfig.newBuilder().setHostName("host-denied").build();

    when(configurationProvider.getLabConfig("host-match", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(matchingHost))));
    when(configurationProvider.getLabConfig("host-update", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(updatingHost))));
    when(configurationProvider.getLabConfig("host-pusher", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(pusherHost))));
    when(configurationProvider.getLabConfig("host-denied", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(deniedHost))));

    when(configPusherHelper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG, pusherHost))
        .thenReturn(true);
    when(iamPermissionChecker.canConfigHost("host-match", SELF_UNIVERSE))
        .thenReturn(immediateFuture(true));
    when(iamPermissionChecker.canConfigHost("host-update", SELF_UNIVERSE))
        .thenReturn(immediateFuture(true));
    when(iamPermissionChecker.canConfigHost("host-denied", SELF_UNIVERSE))
        .thenReturn(immediateFuture(false));

    BatchGetHostWifiConfigsRequest request =
        BatchGetHostWifiConfigsRequest.newBuilder()
            .addHostNames("host-match")
            .addHostNames("host-update")
            .addHostNames("host-pusher")
            .addHostNames("host-denied")
            .setTarget(
                TargetDeviceWifi.newBuilder()
                    .setWifi(DeviceWifi.newBuilder().setSsid("target-ssid").setPsk("psk")))
            .build();

    BatchGetHostWifiConfigsResponse response =
        batchGetHostWifiConfigsHandler
            .batchGetHostWifiConfigs(request, SELF_UNIVERSE, Optional.of("operator"))
            .get();

    assertThat(response.getEntriesCount()).isEqualTo(4);
    assertThat(response.getEntries(0).getStatus())
        .isEqualTo(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_ALREADY_MATCHES);
    assertThat(response.getEntries(1).getStatus())
        .isEqualTo(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_WILL_UPDATE);
    assertThat(response.getEntries(2).getStatus())
        .isEqualTo(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(2).getReadOnlyReason()).isEqualTo("Managed by Config Pusher");
    assertThat(response.getEntries(3).getStatus())
        .isEqualTo(HostConfigChangeStatus.HOST_CONFIG_CHANGE_STATUS_READ_ONLY);
    assertThat(response.getEntries(3).getReadOnlyReason()).isEqualTo("No permission");
  }

  @Test
  public void batchGetHostWifiConfigs_exceedsBatchLimit_throwsInvalidArgument() {
    BatchGetHostWifiConfigsRequest.Builder reqBuilder = BatchGetHostWifiConfigsRequest.newBuilder();
    IntStream.rangeClosed(0, BatchHostConfigHelper.MAX_BATCH_HOSTS)
        .forEach(index -> reqBuilder.addHostNames("host-" + index));

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () ->
                batchGetHostWifiConfigsHandler
                    .batchGetHostWifiConfigs(reqBuilder.build(), SELF_UNIVERSE, Optional.empty())
                    .get());
    assertThat(ex).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(((FeServiceException) ex.getCause()).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  @SuppressWarnings("deprecation") // Verifies clearing legacy BasicDeviceConfig.default_wifi.
  public void batchUpdateHostWifiConfigs_atsUpdatesLabWifiAndClearsLegacyDefaultWifi()
      throws Exception {
    LabConfig existingHost1 =
        LabConfig.newBuilder()
            .setHostName("host-1")
            .addMonitoredDeviceUuid("device-1")
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .addOwner("admin")
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("legacy-ssid").build())
                    .build())
            .build();
    when(configurationProvider.getLabConfig("host-1", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(existingHost1))));
    when(configurationProvider.getLabConfig("host-2", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.empty())));

    BatchUpdateHostWifiConfigsRequest request =
        BatchUpdateHostWifiConfigsRequest.newBuilder()
            .addHostNames("host-1")
            .addHostNames("host-2")
            .setTarget(
                TargetDeviceWifi.newBuilder()
                    .setWifi(
                        DeviceWifi.newBuilder()
                            .setSsid("new-ats-wifi")
                            .setPsk("new-psk")
                            .setScanSsid(true)))
            .build();

    BatchUpdateHostWifiConfigsResponse response =
        batchUpdateHostWifiConfigsHandler
            .batchUpdateHostWifiConfigs(request, SELF_UNIVERSE, Optional.empty())
            .get();

    assertThat(response.getErrorsMap()).isEmpty();

    ArgumentCaptor<LabConfig> host1Captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider)
        .updateLabConfig(eq("host-1"), host1Captor.capture(), eq(SELF_UNIVERSE));
    LabConfig updatedHost1 = host1Captor.getValue();
    assertThat(updatedHost1.getWifiConfig().getSsid()).isEqualTo("new-ats-wifi");
    assertThat(updatedHost1.getWifiConfig().getPsk()).isEqualTo("new-psk");
    assertThat(updatedHost1.getWifiConfig().getScanSsid()).isTrue();
    assertThat(updatedHost1.getDefaultDeviceConfig().hasDefaultWifi()).isFalse();
    assertThat(updatedHost1.getDefaultDeviceConfig().getOwnerList()).containsExactly("admin");
    assertThat(updatedHost1.getMonitoredDeviceUuidList()).containsExactly("device-1");

    ArgumentCaptor<LabConfig> host2Captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider)
        .updateLabConfig(eq("host-2"), host2Captor.capture(), eq(SELF_UNIVERSE));
    assertThat(host2Captor.getValue().getHostName()).isEqualTo("host-2");
    assertThat(host2Captor.getValue().getWifiConfig().getSsid()).isEqualTo("new-ats-wifi");
  }

  @Test
  public void batchUpdateHostWifiConfigs_clearWifiAndPartialFailureIsolation() throws Exception {
    LabConfig host1 =
        LabConfig.newBuilder()
            .setHostName("host-ok")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("wifi-a").build())
            .build();
    LabConfig host2 =
        LabConfig.newBuilder()
            .setHostName("host-fail")
            .setWifiConfig(WifiConfig.newBuilder().setSsid("wifi-b").build())
            .build();
    LabConfig host3 = LabConfig.newBuilder().setHostName("host-pusher").build();

    when(configurationProvider.getLabConfig("host-ok", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(host1))));
    when(configurationProvider.getLabConfig("host-fail", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(host2))));
    when(configurationProvider.getLabConfig("host-pusher", SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(host3))));
    when(configPusherHelper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG, host3))
        .thenReturn(true);
    when(configurationProvider.updateLabConfig(eq("host-fail"), any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFailedFuture(new IllegalStateException("Storage timeout")));

    BatchUpdateHostWifiConfigsRequest request =
        BatchUpdateHostWifiConfigsRequest.newBuilder()
            .addHostNames("host-ok")
            .addHostNames("host-fail")
            .addHostNames("host-pusher")
            .setTarget(
                TargetDeviceWifi.newBuilder().setClearWifi(ClearDeviceWifi.getDefaultInstance()))
            .build();

    BatchUpdateHostWifiConfigsResponse response =
        batchUpdateHostWifiConfigsHandler
            .batchUpdateHostWifiConfigs(request, SELF_UNIVERSE, Optional.empty())
            .get();

    assertThat(response.getErrorsMap()).hasSize(2);
    assertThat(response.getErrorsMap().get("host-fail").getCode())
        .isEqualTo(UpdateError.Code.UNKNOWN);
    assertThat(response.getErrorsMap().get("host-fail").getMessage()).contains("Storage timeout");
    assertThat(response.getErrorsMap().get("host-pusher").getCode())
        .isEqualTo(UpdateError.Code.PERMISSION_DENIED);

    ArgumentCaptor<LabConfig> okCaptor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider)
        .updateLabConfig(eq("host-ok"), okCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(okCaptor.getValue().hasWifiConfig()).isFalse();
    verify(configurationProvider, never())
        .updateLabConfig(eq("host-pusher"), any(), eq(SELF_UNIVERSE));
  }

  @Test
  public void batchUpdateHostWifiConfigs_unsetTarget_throwsInvalidArgument() {
    BatchUpdateHostWifiConfigsRequest request =
        BatchUpdateHostWifiConfigsRequest.newBuilder().addHostNames("host-1").build();

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () ->
                batchUpdateHostWifiConfigsHandler
                    .batchUpdateHostWifiConfigs(request, SELF_UNIVERSE, Optional.empty())
                    .get());
    assertThat(ex).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(((FeServiceException) ex.getCause()).getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }
}
