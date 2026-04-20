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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceInfoUtilTest {

  @Test
  public void getDimensions_working() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("dim1").setValue("val1"))
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("dim2").setValue("val2"))))
            .build();

    ImmutableMap<String, String> dimensions = DeviceInfoUtil.getDimensions(deviceInfo);

    assertThat(dimensions).containsExactly("dim1", "val1", "dim2", "val2");
  }

  @Test
  public void getDimensions_empty_success() {
    DeviceInfo deviceInfo = DeviceInfo.getDefaultInstance();

    ImmutableMap<String, String> dimensions = DeviceInfoUtil.getDimensions(deviceInfo);

    assertThat(dimensions).isEmpty();
  }

  @Test
  public void getDimensions_duplicateKeys_mergesValues() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("dim1").setValue("val1"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("dim1").setValue("val2"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("dim1").setValue("val1"))))
            .build();

    ImmutableMap<String, String> dimensions = DeviceInfoUtil.getDimensions(deviceInfo);

    assertThat(dimensions).containsExactly("dim1", "val1,val2");
  }
}
