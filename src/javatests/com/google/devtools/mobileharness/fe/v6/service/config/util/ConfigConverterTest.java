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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.DetectorSpecs;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.DetectorSpecs.ManekiDetectorSpecs;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.OverSshDevice;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.PermissionInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ManekiSpec;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.StabilitySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.WifiConfig;
import com.google.protobuf.Int32Value;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConfigConverterTest {

  @Test
  public void toFeDeviceConfig_fullMappings() {
    BasicDeviceConfig basicConfig =
        BasicDeviceConfig.newBuilder()
            .addOwner("owner1")
            .addExecutor("exec1")
            .setDefaultWifi(
                com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig
                    .newBuilder()
                    .setSsid("my-ssid")
                    .setPsk("my-psk")
                    .setScanSsid(true)
                    .build())
            .setCompositeDimension(
                DeviceCompositeDimension.newBuilder()
                    .addSupportedDimension(
                        DeviceDimension.newBuilder().setName("n1").setValue("v1").build())
                    .addRequiredDimension(
                        DeviceDimension.newBuilder().setName("n2").setValue("v2").build())
                    .build())
            .setMaxConsecutiveTest(Int32Value.of(10))
            .setMaxConsecutiveFail(Int32Value.of(5))
            .build();

    DeviceConfig feConfig = ConfigConverter.toFeDeviceConfig(basicConfig);

    assertThat(feConfig.getPermissions().getOwnersList()).containsExactly("owner1");
    assertThat(feConfig.getPermissions().getExecutorsList()).containsExactly("exec1");
    assertThat(feConfig.getWifi().getSsid()).isEqualTo("my-ssid");
    assertThat(feConfig.getWifi().getPsk()).isEqualTo("my-psk");
    assertThat(feConfig.getWifi().getScanSsid()).isTrue();
    assertThat(feConfig.getWifi().getType()).isEqualTo("custom");
    assertThat(feConfig.getDimensions().getSupportedList().get(0).getName()).isEqualTo("n1");
    assertThat(feConfig.getDimensions().getRequiredList().get(0).getName()).isEqualTo("n2");
    assertThat(feConfig.getSettings().getMaxConsecutiveTest()).isEqualTo(10);
    assertThat(feConfig.getSettings().getMaxConsecutiveFail()).isEqualTo(5);
  }

  @Test
  public void toFeDeviceConfig_emptyWifi_noneType() {
    BasicDeviceConfig basicConfig =
        BasicDeviceConfig.newBuilder()
            .setDefaultWifi(
                com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig
                    .newBuilder()
                    .setSsid("")
                    .build())
            .build();

    DeviceConfig feConfig = ConfigConverter.toFeDeviceConfig(basicConfig);

    assertThat(feConfig.getWifi().getType()).isEqualTo("none");
  }

  @Test
  public void toBasicDeviceConfig_fullMappings() {
    DeviceConfig feConfig =
        DeviceConfig.newBuilder()
            .setPermissions(PermissionInfo.newBuilder().addOwners("o1").addExecutors("e1").build())
            .setWifi(WifiConfig.newBuilder().setSsid("s1").setPsk("p1").setScanSsid(false).build())
            .setDimensions(
                DeviceConfig.Dimensions.newBuilder()
                    .addSupported(
                        com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension
                            .newBuilder()
                            .setName("sn")
                            .setValue("sv")
                            .build())
                    .addRequired(
                        com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension
                            .newBuilder()
                            .setName("rn")
                            .setValue("rv")
                            .build())
                    .build())
            .setSettings(
                StabilitySettings.newBuilder()
                    .setMaxConsecutiveTest(20)
                    .setMaxConsecutiveFail(3)
                    .build())
            .build();

    BasicDeviceConfig basicConfig = ConfigConverter.toBasicDeviceConfig(feConfig);

    assertThat(basicConfig.getOwnerList()).containsExactly("o1");
    assertThat(basicConfig.getExecutorList()).containsExactly("e1");
    assertThat(basicConfig.getDefaultWifi().getSsid()).isEqualTo("s1");
    assertThat(basicConfig.getCompositeDimension().getSupportedDimensionList().get(0).getName())
        .isEqualTo("sn");
    assertThat(basicConfig.getCompositeDimension().getRequiredDimensionList().get(0).getName())
        .isEqualTo("rn");
    assertThat(basicConfig.getMaxConsecutiveTest().getValue()).isEqualTo(20);
    assertThat(basicConfig.getMaxConsecutiveFail().getValue()).isEqualTo(3);
  }

  @Test
  public void toFeHostConfig_fullMappings() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("admin").build())
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(HostProperty.newBuilder().setKey("k1").setValue("v1").build())
                    .build())
            .addMonitoredDeviceUuid("u1")
            .addOverTcpIp("1.1.1.1")
            .addOverSsh(
                OverSshDevice.newBuilder()
                    .setIpAddress("2.2.2.2")
                    .setUsername("user")
                    .setPassword("pass")
                    .setSshDeviceType("android")
                    .build())
            .setDetectorSpecs(
                DetectorSpecs.newBuilder()
                    .setManekiDetectorSpecs(
                        ManekiDetectorSpecs.newBuilder()
                            .addManekiAndroidDeviceDiscoverySpec(
                                ManekiDetectorSpecs.ManekiAndroidDeviceDiscoverySpec.newBuilder()
                                    .setMacAddress("mac1")
                                    .build())
                            .build())
                    .build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getPermissions().getHostAdminsList()).containsExactly("admin");
    assertThat(feConfig.getHostPropertiesList().get(0).getKey()).isEqualTo("k1");
    assertThat(feConfig.getDeviceDiscovery().getMonitoredDeviceUuidsList()).containsExactly("u1");
    assertThat(feConfig.getDeviceDiscovery().getOverTcpIpsList()).containsExactly("1.1.1.1");
    assertThat(feConfig.getDeviceDiscovery().getOverSshDevicesList().get(0).getIpAddress())
        .isEqualTo("2.2.2.2");
    assertThat(feConfig.getDeviceDiscovery().getManekiSpecsList().get(0).getType())
        .isEqualTo("android");
  }

  @Test
  public void toFeManekiSpecs_allTypes() {
    DetectorSpecs specs =
        DetectorSpecs.newBuilder()
            .setManekiDetectorSpecs(
                ManekiDetectorSpecs.newBuilder()
                    .addManekiAndroidDeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiAndroidDeviceDiscoverySpec.newBuilder()
                            .setMacAddress("m1")
                            .build())
                    .addManekiRokuDeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiRokuDeviceDiscoverySpec.newBuilder()
                            .setMacAddress("m2")
                            .build())
                    .addManekiRdkDeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiRdkDeviceDiscoverySpec.newBuilder()
                            .setMacAddress("m3")
                            .build())
                    .addManekiRaspberryPiDeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiRaspberryPiDeviceDiscoverySpec.newBuilder()
                            .setMacAddress("m4")
                            .build())
                    .addManekiPs4DeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiPs4DeviceDiscoverySpec.newBuilder()
                            .setWinMacAddress("m5")
                            .build())
                    .addManekiPs5DeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiPs5DeviceDiscoverySpec.newBuilder()
                            .setWinMacAddress("m6")
                            .build())
                    .addManekiGenericDeviceDiscoverySpec(
                        ManekiDetectorSpecs.ManekiGenericDeviceDiscoverySpec.newBuilder()
                            .setMacAddress("m7")
                            .build())
                    .build())
            .build();

    HostConfig feConfig =
        ConfigConverter.toFeHostConfig(LabConfig.newBuilder().setDetectorSpecs(specs).build());
    List<ManekiSpec> manekiSpecs = feConfig.getDeviceDiscovery().getManekiSpecsList();

    assertThat(manekiSpecs).hasSize(7);
    assertThat(manekiSpecs.get(0).getType()).isEqualTo("android");
    assertThat(manekiSpecs.get(1).getType()).isEqualTo("roku");
    assertThat(manekiSpecs.get(2).getType()).isEqualTo("rdk");
    assertThat(manekiSpecs.get(3).getType()).isEqualTo("raspberry_pi");
    assertThat(manekiSpecs.get(4).getType()).isEqualTo("ps4");
    assertThat(manekiSpecs.get(5).getType()).isEqualTo("ps5");
    assertThat(manekiSpecs.get(6).getType()).isEqualTo("generic");
  }

  @Test
  public void toInternalDetectorSpecs_allTypes() {
    ImmutableList<ManekiSpec> feSpecs =
        ImmutableList.of(
            ManekiSpec.newBuilder().setType("android").setMacAddress("m1").build(),
            ManekiSpec.newBuilder().setType("roku").setMacAddress("m2").build(),
            ManekiSpec.newBuilder().setType("rdk").setMacAddress("m3").build(),
            ManekiSpec.newBuilder().setType("raspberry_pi").setMacAddress("m4").build(),
            ManekiSpec.newBuilder().setType("ps4").setMacAddress("m5").build(),
            ManekiSpec.newBuilder().setType("ps5").setMacAddress("m6").build(),
            ManekiSpec.newBuilder().setType("generic").setMacAddress("m7").build(),
            ManekiSpec.newBuilder().setType("unknown").setMacAddress("m8").build());

    DetectorSpecs specs = ConfigConverter.toInternalDetectorSpecs(feSpecs);
    ManekiDetectorSpecs maneki = specs.getManekiDetectorSpecs();

    assertThat(maneki.getManekiAndroidDeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiRokuDeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiRdkDeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiRaspberryPiDeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiPs4DeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiPs5DeviceDiscoverySpecCount()).isEqualTo(1);
    assertThat(maneki.getManekiGenericDeviceDiscoverySpecCount()).isEqualTo(1);
  }

  @Test
  public void toFeHostConfig_extractsHostAdminsFromDefaultDeviceConfig() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder().addOwner("admin1").addOwner("admin2").build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getPermissions().getHostAdminsList()).containsExactly("admin1", "admin2");
  }

  @Test
  public void toFeHostConfig_detectsSharedMode_fromPropertyPresence() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder()
                            .setKey("device_config_mode")
                            .setValue("host")
                            .build())
                    .build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.SHARED);
  }

  @Test
  public void toFeHostConfig_detectsPerDeviceMode_fromPropertyAbsence() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("other").setValue("val").build())
                    .build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.PER_DEVICE);
  }

  @Test
  public void toFeHostConfig_detectsPerDeviceMode_whenValueNotHost() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder()
                            .setKey("device_config_mode")
                            .setValue("something_else")
                            .build())
                    .build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.PER_DEVICE);
  }

  @Test
  public void toFeHostConfig_detectsPerDeviceMode_whenKeyNotDeviceConfigMode() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("other_key").setValue("host").build())
                    .build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.getDeviceConfigMode()).isEqualTo(DeviceConfigMode.PER_DEVICE);
  }

  @Test
  public void toFeHostConfig_convertsDefaultDeviceConfig() {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("owner1").build())
            .build();

    HostConfig feConfig = ConfigConverter.toFeHostConfig(labConfig);

    assertThat(feConfig.hasDeviceConfig()).isTrue();
    assertThat(feConfig.getDeviceConfig().getPermissions().getOwnersList())
        .containsExactly("owner1");
  }
}
