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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.api.spec.TestbedDeviceSpec;
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
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("model").setValue("pixel 5").build()))
            .addSubDeviceDimension(
                SubDeviceDimensions.SubDeviceDimension.newBuilder()
                    .setDeviceId("sub_device_2")
                    .addDeviceDimension(
                        StrPair.newBuilder()
                            .setName(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY)
                            .setValue("DisconnectedDevice"))
                    .addDeviceDimension(
                        StrPair.newBuilder().setName("uuid").setValue("abc-123").build()))
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
}
