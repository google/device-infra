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

package com.google.devtools.mobileharness.infra.ats.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.server.proto.ConditionedDeviceConfigProto.Condition;
import com.google.devtools.mobileharness.infra.ats.server.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfig;
import com.google.devtools.mobileharness.infra.ats.server.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfigs;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceConfigUtilTest {
  @Test
  public void filterConditionedDeviceConfigsByDevice_success() {
    ConditionedDeviceConfigs conditionedDeviceConfigs =
        ConditionedDeviceConfigs.newBuilder()
            .addConditionedDeviceConfigs(
                ConditionedDeviceConfig.newBuilder()
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("type_device")
                            .setValueRegex("AndroidRealDevice"))
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("dimension_model")
                            .setValueRegex("Pixel 9 Pro")))
            .addConditionedDeviceConfigs(
                ConditionedDeviceConfig.newBuilder()
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("type_device")
                            .setValueRegex("AndroidRealDevice"))
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("dimension_model")
                            .setValueRegex("Pixel 9 Pro XL")))
            .build();

    Device device = new AndroidRealDevice("1234567890");
    device.info().deviceTypes().add("AndroidRealDevice");
    device.addDimension("model", "Pixel 9 Pro");
    device.addDimension("pool", "shared");
    device.addDimension("host_name", "host1");

    assertThat(
            DeviceConfigUtil.filterConditionedDeviceConfigsByDevice(
                conditionedDeviceConfigs, device))
        .containsExactly(
            ConditionedDeviceConfig.newBuilder()
                .addCondition(
                    Condition.newBuilder().setKey("type_device").setValueRegex("AndroidRealDevice"))
                .addCondition(
                    Condition.newBuilder().setKey("dimension_model").setValueRegex("Pixel 9 Pro"))
                .build());
  }

  @Test
  public void filterConditionedDeviceConfigsByDevice_valueRegex_success() {
    ConditionedDeviceConfigs conditionedDeviceConfigs =
        ConditionedDeviceConfigs.newBuilder()
            .addConditionedDeviceConfigs(
                ConditionedDeviceConfig.newBuilder()
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("type_device")
                            .setValueRegex("AndroidRealDevice"))
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("dimension_model")
                            .setValueRegex("Pixel 8.*")))
            .addConditionedDeviceConfigs(
                ConditionedDeviceConfig.newBuilder()
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("type_device")
                            .setValueRegex("AndroidRealDevice"))
                    .addCondition(
                        Condition.newBuilder()
                            .setKey("dimension_model")
                            .setValueRegex("Pixel 9.*")))
            .build();

    Device device = new AndroidRealDevice("1234567890");
    device.info().deviceTypes().add("AndroidRealDevice");
    device.addDimension("model", "Pixel 9 Pro");
    device.addDimension("pool", "shared");
    device.addDimension("host_name", "host1");

    assertThat(
            DeviceConfigUtil.filterConditionedDeviceConfigsByDevice(
                conditionedDeviceConfigs, device))
        .containsExactly(
            ConditionedDeviceConfig.newBuilder()
                .addCondition(
                    Condition.newBuilder().setKey("type_device").setValueRegex("AndroidRealDevice"))
                .addCondition(
                    Condition.newBuilder().setKey("dimension_model").setValueRegex("Pixel 9.*"))
                .build());
  }
}
