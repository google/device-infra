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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.PermissionInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceDiscoverySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUpdateScope;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostPermissions;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ManekiSpec;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.StabilitySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateOptions;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.WifiConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Int32Value;
import java.util.Optional;
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
public final class UpdateHostConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind @Mock private Environment environment;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private UpdateHostConfigHandler updateHostConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapabilityFactory.create(anyString())).thenReturn(configServiceCapability);
    when(environment.isAts()).thenReturn(false);
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.updateLabConfig(any(), any(), any()))
        .thenReturn(immediateVoidFuture());
  }

  @Test
  public void updateHostConfig_unsupportedUniverse_fail() throws Exception {
    String universe = "unsupported";
    doThrow(new UnsupportedOperationException("Universe not currently supported"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder().setHostName("host").setUniverse(universe).build();

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    assertThat(response.getError().getMessage()).contains("Universe not currently supported");
  }

  @Test
  public void updateHostConfig_success() throws Exception {
    String hostName = "test_host";
    String universe = "google_1p";
    HostConfig feConfig =
        HostConfig.newBuilder()
            .addHostProperties(
                HostProperty.newBuilder().setKey("new_key").setValue("new_value").build())
            .build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PROPERTIES)
                    .build())
            .build();

    LabConfig existingConfig =
        LabConfig.newBuilder()
            .setHostName(hostName)
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        Lab.HostProperty.newBuilder()
                            .setKey("old_key")
                            .setValue("old_value")
                            .build())
                    .build())
            .build();
    when(configurationProvider.getLabConfig(hostName, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateLabConfig(eq(hostName), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), eq(universe));
    assertThat(captor.getValue().getHostProperties())
        .isEqualTo(
            HostProperties.newBuilder()
                .addHostProperty(
                    Lab.HostProperty.newBuilder().setKey("new_key").setValue("new_value").build())
                .build());
  }

  @Test
  public void updateHostConfig_atsEnvironment_unsupportedSection_fail() throws Exception {
    when(environment.isAts()).thenReturn(true);
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("test_host")
            .setUniverse("oss")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PROPERTIES)
                    .build())
            .build();

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getMessage())
        .contains(
            "Configuration section 'HOST_PROPERTIES' is not supported in the ATS environment.");
  }

  @Test
  public void updateHostConfig_atsEnvironment_deviceConfigSection_success() throws Exception {
    when(environment.isAts()).thenReturn(true);
    String hostName = "test_host";
    String universe = "oss";
    HostConfig feConfig =
        HostConfig.newBuilder()
            .setDeviceConfig(
                DeviceConfig.newBuilder()
                    .setWifi(WifiConfig.newBuilder().setSsid("new_wifi").build())
                    .setDimensions(
                        DeviceConfig.Dimensions.newBuilder()
                            .addSupported(
                                DeviceDimension.newBuilder()
                                    .setName("dimension_name")
                                    .setValue("new_dimension")
                                    .build())
                            .build())
                    .setPermissions(PermissionInfo.newBuilder().addOwners("new_owner").build())
                    .build())
            .build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.ALL)
                    .build())
            .build();

    when(configurationProvider.getLabConfig(hostName, universe))
        .thenReturn(
            immediateFuture(Optional.of(LabConfig.newBuilder().setHostName(hostName).build())));
    when(configurationProvider.updateLabConfig(eq(hostName), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), eq(universe));
    LabConfig updatedConfig = captor.getValue();
    BasicDeviceConfig deviceConfig = updatedConfig.getDefaultDeviceConfig();
    assertThat(deviceConfig.getDefaultWifi().getSsid()).isEqualTo("new_wifi");
    assertThat(deviceConfig.getCompositeDimension().toString()).contains("new_dimension");
    // In ATS, ALL only updates WIFI and DIMENSIONS
    assertThat(deviceConfig.getOwnerList()).isEmpty();
  }

  @Test
  public void updateHostConfig_atsEnvironment_unsupportedDeviceConfigSection_ignored()
      throws Exception {
    when(environment.isAts()).thenReturn(true);
    String hostName = "test_host";
    String universe = "oss";
    HostConfig feConfig =
        HostConfig.newBuilder()
            .setDeviceConfig(
                DeviceConfig.newBuilder()
                    .setPermissions(PermissionInfo.newBuilder().addOwners("new_owner").build())
                    .build())
            .build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.PERMISSIONS)
                    .build())
            .build();

    LabConfig existingConfig =
        LabConfig.newBuilder()
            .setHostName(hostName)
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("old_owner").build())
            .build();
    when(configurationProvider.getLabConfig(hostName, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateLabConfig(eq(hostName), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), eq(universe));
    LabConfig updatedConfig = captor.getValue();
    // PERMISSIONS is not supported in ATS for DeviceConfig, so it should be ignored (not updated)
    assertThat(updatedConfig.getDefaultDeviceConfig().getOwnerList()).containsExactly("old_owner");
  }

  @Test
  public void updateHostConfig_selfLockout_fail() throws Exception {
    String hostName = "test_host";
    String user = "test_user";
    HostConfig feConfig =
        HostConfig.newBuilder()
            .setPermissions(HostPermissions.newBuilder().addHostAdmins("other_user").build())
            .build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PERMISSIONS)
                    .build())
            .build();

    when(groupMembershipProvider.isMemberOfAny(eq(user), any())).thenReturn(immediateFuture(false));

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.of(user)).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.SELF_LOCKOUT_DETECTED);
  }

  @Test
  public void updateHostConfig_selfLockout_override_success() throws Exception {
    String hostName = "test_host";
    String user = "test_user";
    HostConfig feConfig =
        HostConfig.newBuilder()
            .setPermissions(HostPermissions.newBuilder().addHostAdmins("other_user").build())
            .build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PERMISSIONS)
                    .build())
            .setOptions(UpdateOptions.newBuilder().setOverrideSelfLockout(true).build())
            .build();

    UpdateHostConfigResponse response =
        updateHostConfigHandler.updateHostConfig(request, Optional.of(user)).get();

    assertThat(response.getSuccess()).isTrue();
    verify(configurationProvider).updateLabConfig(eq(hostName), any(), any());
  }

  @Test
  public void updateHostConfig_deviceConfig_mode_success() throws Exception {
    String hostName = "test_host";
    HostConfig feConfig =
        HostConfig.newBuilder().setDeviceConfigMode(DeviceConfigMode.SHARED).build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG_MODE)
                    .build())
            .build();

    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), any());
    assertThat(captor.getValue().getHostProperties().getHostPropertyList())
        .contains(
            Lab.HostProperty.newBuilder().setKey("device_config_mode").setValue("host").build());
  }

  @Test
  public void updateHostConfig_deviceDiscovery_success() throws Exception {
    String hostName = "test_host";
    DeviceDiscoverySettings discovery =
        DeviceDiscoverySettings.newBuilder().addMonitoredDeviceUuids("uuid1").build();
    HostConfig feConfig = HostConfig.newBuilder().setDeviceDiscovery(discovery).build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_DISCOVERY)
                    .build())
            .build();

    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), any());
    assertThat(captor.getValue().getMonitoredDeviceUuidList()).containsExactly("uuid1");
  }

  @Test
  public void updateHostConfig_deviceConfig_stability_success() throws Exception {
    String hostName = "test_host";
    DeviceConfig feDeviceConfig =
        DeviceConfig.newBuilder()
            .setWifi(WifiConfig.getDefaultInstance())
            .setSettings(
                StabilitySettings.newBuilder()
                    .setMaxConsecutiveFail(5)
                    .setMaxConsecutiveTest(10)
                    .build())
            .build();
    HostConfig feConfig = HostConfig.newBuilder().setDeviceConfig(feDeviceConfig).build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.STABILITY)
                    .build())
            .build();

    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), any());
    assertThat(captor.getValue().getDefaultDeviceConfig().getMaxConsecutiveFail())
        .isEqualTo(Int32Value.of(5));
    assertThat(captor.getValue().getDefaultDeviceConfig().getMaxConsecutiveTest())
        .isEqualTo(Int32Value.of(10));
  }

  @Test
  public void updateHostConfig_deviceConfig_all_nonAts_success() throws Exception {
    when(environment.isAts()).thenReturn(false);
    String hostName = "host";
    LabConfig existingLabConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("old_owner").build())
            .build();
    when(configurationProvider.getLabConfig(eq(hostName), any()))
        .thenReturn(immediateFuture(Optional.of(existingLabConfig)));

    DeviceConfig feDeviceConfig =
        DeviceConfig.newBuilder()
            .setPermissions(PermissionInfo.newBuilder().addOwners("new_owner").build())
            .setWifi(WifiConfig.newBuilder().setSsid("wifi").build())
            .setSettings(StabilitySettings.newBuilder().setMaxConsecutiveTest(10).build())
            .build();
    HostConfig feConfig = HostConfig.newBuilder().setDeviceConfig(feDeviceConfig).build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.ALL)
                    .build())
            .build();

    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), any());
    BasicDeviceConfig result = captor.getValue().getDefaultDeviceConfig();
    assertThat(result.getOwnerList()).containsExactly("new_owner");
    assertThat(result.getDefaultWifi().getSsid()).isEqualTo("wifi");
    assertThat(result.getMaxConsecutiveTest().getValue()).isEqualTo(10);
  }

  @Test
  public void updateHostConfig_deviceDiscovery_detailed_success() throws Exception {
    String hostName = "host";
    LabConfig existingLabConfig = LabConfig.getDefaultInstance();
    when(configurationProvider.getLabConfig(eq(hostName), any()))
        .thenReturn(immediateFuture(Optional.of(existingLabConfig)));

    DeviceDiscoverySettings discovery =
        DeviceDiscoverySettings.newBuilder()
            .addOverSshDevices(
                DeviceDiscoverySettings.OverSshDevice.newBuilder()
                    .setIpAddress("1.2.3.4")
                    .setUsername("admin")
                    .setPassword("pass")
                    .build())
            .addManekiSpecs(
                ManekiSpec.newBuilder()
                    .setType("android")
                    .setMacAddress("00:11:22:33:44:55")
                    .build())
            .build();
    HostConfig feConfig = HostConfig.newBuilder().setDeviceDiscovery(discovery).build();
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setConfig(feConfig)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_DISCOVERY)
                    .build())
            .build();

    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq(hostName), captor.capture(), any());
    LabConfig result = captor.getValue();
    assertThat(result.getOverSshCount()).isEqualTo(1);
    assertThat(result.getOverSsh(0).getIpAddress()).isEqualTo("1.2.3.4");
    assertThat(
            result
                .getDetectorSpecs()
                .getManekiDetectorSpecs()
                .getManekiAndroidDeviceDiscoverySpecCount())
        .isEqualTo(1);
    assertThat(
            result
                .getDetectorSpecs()
                .getManekiDetectorSpecs()
                .getManekiAndroidDeviceDiscoverySpec(0)
                .getMacAddress())
        .isEqualTo("00:11:22:33:44:55");
  }

  @Test
  public void updateHostConfig_isSelfLockout_nonPermissionSection_success() throws Exception {
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PROPERTIES)
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.of("user")).get();
    verify(configurationProvider).updateLabConfig(eq("host"), any(), any());
  }

  @Test
  public void updateHostConfig_isSelfLockout_userInAdmins_success() throws Exception {
    String user = "user";
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PERMISSIONS)
                    .build())
            .setConfig(
                HostConfig.newBuilder()
                    .setPermissions(HostPermissions.newBuilder().addHostAdmins(user).build())
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.of(user)).get();
    verify(configurationProvider).updateLabConfig(eq("host"), any(), any());
  }

  @Test
  public void updateHostConfig_unspecifiedSection_success() throws Exception {
    String hostName = "host";
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName(hostName)
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_CONFIG_SECTION_UNSPECIFIED)
                    .build())
            .setConfig(
                HostConfig.newBuilder()
                    .setDeviceConfigMode(DeviceConfigMode.SHARED)
                    .addHostProperties(HostProperty.newBuilder().setKey("k").setValue("v").build())
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();
    verify(configurationProvider).updateLabConfig(eq(hostName), any(), any());
  }

  @Test
  public void updateHostConfig_updateHostPermissions_noPermissionsInConfig_success()
      throws Exception {
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.HOST_PERMISSIONS)
                    .build())
            .setConfig(HostConfig.getDefaultInstance())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();
    verify(configurationProvider).updateLabConfig(eq("host"), any(), any());
  }

  @Test
  public void updateHostConfig_deviceConfig_permissions_success() throws Exception {
    when(environment.isAts()).thenReturn(false);
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.PERMISSIONS)
                    .build())
            .setConfig(
                HostConfig.newBuilder()
                    .setDeviceConfig(
                        DeviceConfig.newBuilder()
                            .setPermissions(PermissionInfo.newBuilder().addOwners("owner1").build())
                            .build())
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq("host"), captor.capture(), any());
    assertThat(captor.getValue().getDefaultDeviceConfig().getOwnerList()).containsExactly("owner1");
  }

  @Test
  public void updateHostConfig_deviceConfig_wifi_success() throws Exception {
    when(environment.isAts()).thenReturn(false);
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.WIFI)
                    .build())
            .setConfig(
                HostConfig.newBuilder()
                    .setDeviceConfig(
                        DeviceConfig.newBuilder()
                            .setWifi(WifiConfig.newBuilder().setSsid("ssid").build())
                            .build())
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq("host"), captor.capture(), any());
    assertThat(captor.getValue().getDefaultDeviceConfig().getDefaultWifi().getSsid())
        .isEqualTo("ssid");
  }

  @Test
  public void updateHostConfig_deviceConfig_dimensions_success() throws Exception {
    when(environment.isAts()).thenReturn(false);
    UpdateHostConfigRequest request =
        UpdateHostConfigRequest.newBuilder()
            .setHostName("host")
            .setScope(
                HostConfigUpdateScope.newBuilder()
                    .setSection(HostConfigSection.DEVICE_CONFIG)
                    .setDeviceConfigSection(DeviceConfigSection.DIMENSIONS)
                    .build())
            .setConfig(
                HostConfig.newBuilder()
                    .setDeviceConfig(
                        DeviceConfig.newBuilder()
                            .setDimensions(
                                DeviceConfig.Dimensions.newBuilder()
                                    .addSupported(
                                        DeviceDimension.newBuilder()
                                            .setName("n")
                                            .setValue("v")
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
    updateHostConfigHandler.updateHostConfig(request, Optional.empty()).get();
    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider).updateLabConfig(eq("host"), captor.capture(), any());
    assertThat(captor.getValue().getDefaultDeviceConfig().getCompositeDimension().toString())
        .contains("v");
  }
}
