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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.PermissionInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.WifiConfig;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
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
public final class UpdateDeviceConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private Environment environment;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private UpdateDeviceConfigHandler updateDeviceConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapability.isUniverseSupported(any())).thenReturn(true);
    when(environment.isAts()).thenReturn(false);
  }

  @Test
  public void updateDeviceConfig_unsupportedUniverse_returnsError() throws Exception {
    when(configServiceCapability.isUniverseSupported("unsupported")).thenReturn(false);
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId("test_device")
            .setUniverse("unsupported")
            .build();

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    assertThat(response.getError().getMessage()).contains("not currently supported");
  }

  @Test
  public void updateDeviceConfig_success() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    DeviceConfig feConfig =
        DeviceConfig.newBuilder()
            .setWifi(WifiConfig.newBuilder().setSsid("new_ssid").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.WIFI)
            .build();

    Device.DeviceConfig existingConfig =
        Device.DeviceConfig.newBuilder()
            .setUuid(deviceId)
            .setBasicConfig(
                BasicDeviceConfig.newBuilder().addAllOwner(ImmutableList.of("owner1")).build())
            .build();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<Device.DeviceConfig> captor = ArgumentCaptor.forClass(Device.DeviceConfig.class);
    verify(configurationProvider).updateDeviceConfig(eq(deviceId), captor.capture(), eq(universe));
    Device.DeviceConfig updatedConfig = captor.getValue();
    assertThat(updatedConfig.getBasicConfig().getOwnerList()).containsExactly("owner1");
    assertThat(updatedConfig.getBasicConfig().getDefaultWifi().getSsid()).isEqualTo("new_ssid");
  }

  @Test
  public void updateDeviceConfig_ats_invalidSection_fail() throws Exception {
    when(environment.isAts()).thenReturn(true);
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId("test_device")
            .setUniverse("google_1p")
            .setSection(DeviceConfigSection.PERMISSIONS)
            .build();

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getMessage())
        .contains("Configuration section 'PERMISSIONS' is not supported in the ATS environment.");
  }

  @Test
  public void updateDeviceConfig_ats_allSection_onlyUpdatesAtsSections() throws Exception {
    when(environment.isAts()).thenReturn(true);
    String deviceId = "test_device";
    String universe = "google_1p";
    DeviceConfig feConfig =
        DeviceConfig.newBuilder()
            .setWifi(WifiConfig.newBuilder().setSsid("new_ssid").build())
            .setPermissions(PermissionInfo.newBuilder().addOwners("new_owner").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.ALL)
            .build();

    Device.DeviceConfig existingConfig =
        Device.DeviceConfig.newBuilder()
            .setUuid(deviceId)
            .setBasicConfig(
                BasicDeviceConfig.newBuilder().addAllOwner(ImmutableList.of("owner1")).build())
            .build();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<Device.DeviceConfig> captor = ArgumentCaptor.forClass(Device.DeviceConfig.class);
    verify(configurationProvider).updateDeviceConfig(eq(deviceId), captor.capture(), eq(universe));
    Device.DeviceConfig updatedConfig = captor.getValue();
    // Owners should NOT be updated in ATS when section is ALL
    assertThat(updatedConfig.getBasicConfig().getOwnerList()).containsExactly("owner1");
    // Wifi SHOULD be updated
    assertThat(updatedConfig.getBasicConfig().getDefaultWifi().getSsid()).isEqualTo("new_ssid");
  }

  @Test
  public void updateDeviceConfig_hostManagedDevice_returnsError() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setSection(DeviceConfigSection.WIFI)
            .build();

    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig
                        .getDefaultInstance(),
                    ManagementMode.HOST_MANAGED,
                    Optional.empty(),
                    Optional.empty())));

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    assertThat(response.getError().getMessage()).contains("host-managed device");
  }

  @Test
  public void updateDeviceConfig_selfLockout_detected() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setPermissions(PermissionInfo.newBuilder().addOwners("other_user").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.PERMISSIONS)
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(groupMembershipProvider.isMemberOfAny("user1", ImmutableList.of("other_user")))
        .thenReturn(immediateFuture(false));

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.of("user1")).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.SELF_LOCKOUT_DETECTED);
  }

  @Test
  public void updateDeviceConfig_selfLockout_override_success() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setPermissions(PermissionInfo.newBuilder().addOwners("other_user").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.PERMISSIONS)
            .setOptions(
                com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateOptions
                    .newBuilder()
                    .setOverrideSelfLockout(true)
                    .build())
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.of("user1")).get();

    assertThat(response.getSuccess()).isTrue();
  }

  @Test
  public void updateDeviceConfig_stabilitySection_success() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setSettings(
                com.google.devtools.mobileharness.fe.v6.service.proto.config.StabilitySettings
                    .newBuilder()
                    .setMaxConsecutiveTest(10)
                    .setMaxConsecutiveFail(5)
                    .build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.STABILITY)
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<Device.DeviceConfig> captor = ArgumentCaptor.forClass(Device.DeviceConfig.class);
    verify(configurationProvider).updateDeviceConfig(eq(deviceId), captor.capture(), eq(universe));
    Device.DeviceConfig updatedConfig = captor.getValue();
    assertThat(updatedConfig.getBasicConfig().getMaxConsecutiveTest())
        .isEqualTo(Int32Value.newBuilder().setValue(10).build());
    assertThat(updatedConfig.getBasicConfig().getMaxConsecutiveFail())
        .isEqualTo(Int32Value.newBuilder().setValue(5).build());
  }

  @Test
  public void updateDeviceConfig_dimensions_success() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setDimensions(
                com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig
                    .ConfigDimensions.newBuilder()
                    .addSupported(
                        com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension
                            .newBuilder()
                            .setName("label")
                            .setValue("value")
                            .build()))
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.DIMENSIONS)
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<Device.DeviceConfig> captor = ArgumentCaptor.forClass(Device.DeviceConfig.class);
    verify(configurationProvider).updateDeviceConfig(eq(deviceId), captor.capture(), eq(universe));
    Device.DeviceConfig updatedConfig = captor.getValue();
    assertThat(updatedConfig.getBasicConfig().getCompositeDimension().getSupportedDimensionList())
        .hasSize(1);
    assertThat(
            updatedConfig
                .getBasicConfig()
                .getCompositeDimension()
                .getSupportedDimension(0)
                .getName())
        .isEqualTo("label");
  }

  @Test
  public void updateDeviceConfig_selfLockout_userInOwners_returnsSuccess() throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setPermissions(PermissionInfo.newBuilder().addOwners("user1").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.PERMISSIONS)
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.of("user1")).get();

    assertThat(response.getSuccess()).isTrue();
  }

  @Test
  public void updateDeviceConfig_selfLockout_notPermissionsSection_returnsSuccess()
      throws Exception {
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setWifi(WifiConfig.newBuilder().setSsid("ssid").build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.WIFI)
            .build();

    Device.DeviceConfig existingConfig = Device.DeviceConfig.getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.of("user1")).get();

    assertThat(response.getSuccess()).isTrue();
  }

  @Test
  public void updateDeviceConfig_nonAts_sectionAll_success() throws Exception {
    when(environment.isAts()).thenReturn(false);
    String deviceId = "test_device";
    String universe = "google_1p";
    com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig feConfig =
        com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig.newBuilder()
            .setWifi(WifiConfig.newBuilder().setSsid("new_ssid").build())
            .setPermissions(PermissionInfo.newBuilder().addOwners("new_owner").build())
            .setSettings(
                com.google.devtools.mobileharness.fe.v6.service.proto.config.StabilitySettings
                    .newBuilder()
                    .setMaxConsecutiveTest(20)
                    .build())
            .build();
    UpdateDeviceConfigRequest request =
        UpdateDeviceConfigRequest.newBuilder()
            .setId(deviceId)
            .setUniverse(universe)
            .setConfig(feConfig)
            .setSection(DeviceConfigSection.ALL)
            .build();

    com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig existingConfig =
        com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig
            .getDefaultInstance();
    when(deviceDataLoader.loadDeviceData(deviceId, universe))
        .thenReturn(
            immediateFuture(
                DeviceData.create(
                    DeviceInfo.getDefaultInstance(),
                    existingConfig,
                    ManagementMode.PER_DEVICE,
                    Optional.empty(),
                    Optional.of(existingConfig))));
    when(configurationProvider.getDeviceConfig(deviceId, universe))
        .thenReturn(immediateFuture(Optional.of(existingConfig)));
    when(configurationProvider.updateDeviceConfig(eq(deviceId), any(), eq(universe)))
        .thenReturn(immediateVoidFuture());

    UpdateDeviceConfigResponse response =
        updateDeviceConfigHandler.updateDeviceConfig(request, Optional.empty()).get();

    assertThat(response.getSuccess()).isTrue();
    ArgumentCaptor<com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig>
        captor =
            ArgumentCaptor.forClass(
                com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig.class);
    verify(configurationProvider).updateDeviceConfig(eq(deviceId), captor.capture(), eq(universe));
    com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig updatedConfig =
        captor.getValue();
    assertThat(updatedConfig.getBasicConfig().getOwnerList()).containsExactly("new_owner");
    assertThat(updatedConfig.getBasicConfig().getDefaultWifi().getSsid()).isEqualTo("new_ssid");
    assertThat(updatedConfig.getBasicConfig().getMaxConsecutiveTest())
        .isEqualTo(Int32Value.newBuilder().setValue(20).build());
  }
}
