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

package com.google.devtools.mobileharness.fe.v6.service.shared;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.NetworkInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.api.spec.TestbedDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Device.SubDeviceDimensions;
import java.util.Base64;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubDeviceInfoListFactoryTest {

  @Inject private SubDeviceInfoListFactory subDeviceInfoListFactory;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void create_withValidSubDeviceDimensions_success() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("AndroidRealDevice"))
                    .addDeviceDimension(StrPair.newBuilder().setName("model").setValue("pixel 5"))
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("release_version").setValue("11"))
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("battery_level").setValue("85"))
                    .addDeviceDimension(StrPair.newBuilder().setName("wifi_rssi").setValue("-50"))
                    .addDeviceDimension(StrPair.newBuilder().setName("internet").setValue("true"))
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(Dimension.Name.DEVICE_SUPPORTS_MORETO.name())
                            .setValue("true")))
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_2")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("DisconnectedDevice"))
                    .addDeviceDimension(StrPair.newBuilder().setName("uuid").setValue("abc-123")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result)
        .containsExactly(
            SubDeviceInfo.newBuilder()
                .setId("sub_device_1")
                .addTypes(DeviceType.newBuilder().setType("AndroidRealDevice").setIsAbnormal(false))
                .addDimensions(
                    DeviceDimension.newBuilder()
                        .setName("mh_device_type")
                        .setValue("AndroidRealDevice"))
                .addDimensions(DeviceDimension.newBuilder().setName("model").setValue("pixel 5"))
                .addDimensions(
                    DeviceDimension.newBuilder().setName("release_version").setValue("11"))
                .addDimensions(DeviceDimension.newBuilder().setName("battery_level").setValue("85"))
                .addDimensions(DeviceDimension.newBuilder().setName("wifi_rssi").setValue("-50"))
                .addDimensions(DeviceDimension.newBuilder().setName("internet").setValue("true"))
                .addDimensions(
                    DeviceDimension.newBuilder().setName("DEVICE_SUPPORTS_MORETO").setValue("true"))
                .setModel("pixel 5")
                .setVersion("11")
                .setBatteryLevel(85)
                .setNetwork(NetworkInfo.newBuilder().setWifiRssi(-50).setHasInternet(true))
                .build(),
            SubDeviceInfo.newBuilder()
                .setId("sub_device_2")
                .addTypes(DeviceType.newBuilder().setType("DisconnectedDevice").setIsAbnormal(true))
                .addDimensions(
                    DeviceDimension.newBuilder()
                        .setName("mh_device_type")
                        .setValue("DisconnectedDevice"))
                .addDimensions(DeviceDimension.newBuilder().setName("uuid").setValue("abc-123"))
                .build());
  }

  @Test
  public void create_noSubDeviceDimensionKey_returnsEmpty() {
    ImmutableMap<String, String> dimensions = ImmutableMap.of("some_other_key", "some_value");

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result).isEmpty();
  }

  @Test
  public void create_invalidBase64_returnsEmpty() {
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, "invalid-base64");

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result).isEmpty();
  }

  @Test
  public void create_invalidProtoBytes_returnsEmpty() {
    String encodedInvalidProto = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedInvalidProto);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result).isEmpty();
  }

  @Test
  public void create_emptySubDeviceListInProto_returnsEmpty() {
    SubDeviceDimensions subDeviceDimensions = SubDeviceDimensions.getDefaultInstance();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result).isEmpty();
  }

  @Test
  public void create_withInvalidIntegerDimensions_usesDefaultValues() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("battery_level").setValue("not-an-int"))
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("wifi_rssi").setValue("invalid")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getBatteryLevel()).isEqualTo(-1);
    assertThat(result.get(0).getNetwork().getWifiRssi()).isEqualTo(0);
  }

  @Test
  public void create_withSoftwareVersionFallback_success() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("software_version").setValue("12")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result.get(0).getVersion()).isEqualTo("12");
  }

  @Test
  public void create_withDuplicateDimensionKeys_usesLastValue() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(StrPair.newBuilder().setName("model").setValue("pixel 4"))
                    .addDeviceDimension(StrPair.newBuilder().setName("model").setValue("pixel 5")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result.get(0).getModel()).isEqualTo("pixel 5");
  }

  @Test
  public void create_withAllAbnormalTypes_isAbnormalTrue() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("UnauthorizedDevice")))
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_2")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("PreMaturedDevice")))
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_3")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("DemotedDevice")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result.get(0).getTypes(0).getIsAbnormal()).isTrue();
    assertThat(result.get(1).getTypes(0).getIsAbnormal()).isTrue();
    assertThat(result.get(2).getTypes(0).getIsAbnormal()).isTrue();
  }

  @Test
  public void create_withOnlyWifiRssi_success() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(StrPair.newBuilder().setName("wifi_rssi").setValue("-60")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result.get(0).hasNetwork()).isTrue();
    assertThat(result.get(0).getNetwork().getWifiRssi()).isEqualTo(-60);
    assertThat(result.get(0).getNetwork().getHasInternet()).isFalse();
  }

  @Test
  public void create_withOnlyInternet_success() {
    SubDeviceDimensions subDeviceDimensions =
        SubDeviceDimensions.newBuilder()
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_1")
                    .addDeviceDimension(StrPair.newBuilder().setName("internet").setValue("true")))
            .build();
    String encodedSubDeviceDimensions =
        Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
    ImmutableMap<String, String> dimensions =
        ImmutableMap.of(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY, encodedSubDeviceDimensions);

    ImmutableList<SubDeviceInfo> result = subDeviceInfoListFactory.create(dimensions);

    assertThat(result.get(0).hasNetwork()).isTrue();
    assertThat(result.get(0).getNetwork().getHasInternet()).isTrue();
    assertThat(result.get(0).getNetwork().getWifiRssi()).isEqualTo(0);
  }
}
