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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.protobuf.util.Timestamps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceHeaderBuilderTest {

  private final DeviceHeaderBuilder builder = new DeviceHeaderBuilder();

  @Test
  public void buildDeviceHeaderInfo_basicFields() {
    DeviceInfo deviceInfo =
        createBaseDeviceInfo()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId("device_id_1")
                    .setLabLocator(
                        LabLocator.newBuilder().setHostName("host_1").setIp("192.168.1.1")))
            .build();

    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(deviceInfo);

    assertThat(header.getId()).isEqualTo("device_id_1");
    assertThat(header.getHost().getName()).isEqualTo("host_1");
    assertThat(header.getHost().getIp()).isEqualTo("192.168.1.1");
  }

  @Test
  public void buildQuarantineInfo_notQuarantined() {
    DeviceInfo deviceInfo = createBaseDeviceInfo().build();

    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(deviceInfo);

    assertThat(header.getQuarantine().getIsQuarantined()).isFalse();
    assertThat(header.getQuarantine().hasExpiry()).isFalse();
    assertThat(header.getActions().getQuarantine().getTooltip()).contains("Quarantine");
  }

  @Test
  public void buildQuarantineInfo_tempDimension() {
    DeviceInfo deviceInfo =
        createBaseDeviceInfo()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))
                            .setExpireTimestampMs(1234567890L)))
            .build();

    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(deviceInfo);

    assertThat(header.getQuarantine().getIsQuarantined()).isTrue();
    assertThat(header.getQuarantine().getExpiry()).isEqualTo(Timestamps.fromMillis(1234567890L));
    assertThat(header.getActions().getQuarantine().getTooltip()).contains("Unquarantine");
  }

  @Test
  public void buildQuarantineInfo_requiredDimension() {
    DeviceInfo deviceInfo =
        createBaseDeviceInfo()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))))
            .build();

    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(deviceInfo);

    assertThat(header.getQuarantine().getIsQuarantined()).isTrue();
    assertThat(header.getQuarantine().hasExpiry()).isFalse();
  }

  @Test
  public void buildDeviceActions_screenshot() {
    // 1. Enabled: Alive + Supported
    DeviceInfo enabled =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("screenshot_able")
                                    .setValue("true"))))
            .build();
    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(enabled);
    assertThat(header.getActions().getScreenshot().getEnabled()).isTrue();
    assertThat(header.getActions().getScreenshot().getVisible()).isTrue();
    assertThat(header.getActions().getScreenshot().getTooltip()).contains("Take a screenshot");

    // 2. Disabled: Missing
    DeviceInfo missing = enabled.toBuilder().setDeviceStatus(DeviceStatus.MISSING).build();
    assertThat(builder.buildDeviceHeaderInfo(missing).getActions().getScreenshot().getEnabled())
        .isFalse();

    // 3. Disabled: Not supported
    DeviceInfo notSupported = createBaseDeviceInfo().build();
    assertThat(
            builder.buildDeviceHeaderInfo(notSupported).getActions().getScreenshot().getEnabled())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_logcat() {
    // 1. Enabled: AndroidRealDevice + Alive + Not Shared
    DeviceInfo enabled =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();
    DeviceHeaderInfo header = builder.buildDeviceHeaderInfo(enabled);
    assertThat(header.getActions().getLogcat().getEnabled()).isTrue();
    assertThat(header.getActions().getLogcat().getVisible()).isTrue();
    assertThat(header.getActions().getLogcat().getTooltip()).contains("Get device logcat");

    // 2. Disabled: Missing
    DeviceInfo missing = enabled.toBuilder().setDeviceStatus(DeviceStatus.MISSING).build();
    assertThat(builder.buildDeviceHeaderInfo(missing).getActions().getLogcat().getEnabled())
        .isFalse();

    // 3. Disabled: Shared
    DeviceInfo shared =
        enabled.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("shared"))))
            .build();
    assertThat(builder.buildDeviceHeaderInfo(shared).getActions().getLogcat().getEnabled())
        .isFalse();

    // 4. Disabled: Testbed
    DeviceInfo testbed =
        createBaseDeviceInfo()
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("TestbedDevice"))
            .build();
    assertThat(builder.buildDeviceHeaderInfo(testbed).getActions().getLogcat().getEnabled())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_flash() {
    // 1. Enabled: AndroidRealDevice + Idle
    DeviceInfo enabled =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();
    assertThat(
            builder.buildDeviceHeaderInfo(enabled).getActions().getFlash().getState().getEnabled())
        .isTrue();
    assertThat(
            builder
                .buildDeviceHeaderInfo(enabled)
                .getActions()
                .getFlash()
                .getParams()
                .getDeviceType())
        .isEqualTo("AndroidRealDevice");
    // Explicitly check enabled tooltip
    assertThat(
            builder.buildDeviceHeaderInfo(enabled).getActions().getFlash().getState().getTooltip())
        .contains("Flash the device");

    // 2. Enabled: AndroidFlashableDevice + Idle
    DeviceInfo flashable =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidFlashableDevice"))
            .build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(flashable)
                .getActions()
                .getFlash()
                .getState()
                .getEnabled())
        .isTrue();

    // 3. Disabled: Not Idle
    DeviceInfo busy = enabled.toBuilder().setDeviceStatus(DeviceStatus.BUSY).build();
    assertThat(builder.buildDeviceHeaderInfo(busy).getActions().getFlash().getState().getEnabled())
        .isFalse();
    assertThat(builder.buildDeviceHeaderInfo(busy).getActions().getFlash().getState().getTooltip())
        .contains("wait for device idle");

    // 4. Disabled: Shared
    DeviceInfo shared =
        enabled.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("shared"))))
            .build();
    assertThat(
            builder.buildDeviceHeaderInfo(shared).getActions().getFlash().getState().getEnabled())
        .isFalse();
    assertThat(
            builder.buildDeviceHeaderInfo(shared).getActions().getFlash().getState().getTooltip())
        .contains("satellite lab Android devices");

    // 5. Disabled: TestBed
    DeviceInfo testbed =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("TestbedDevice"))
            .build();
    assertThat(
            builder.buildDeviceHeaderInfo(testbed).getActions().getFlash().getState().getEnabled())
        .isFalse();
    assertThat(
            builder.buildDeviceHeaderInfo(testbed).getActions().getFlash().getState().getTooltip())
        .contains("satellite lab Android devices");
  }

  @Test
  public void buildQuarantineInfo_tempDimension_partialMatch() {
    // 1. Match name "quarantined" but value "false"
    DeviceInfo nameMatchOnly =
        createBaseDeviceInfo()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("false"))))
            .build();
    assertThat(builder.buildDeviceHeaderInfo(nameMatchOnly).getQuarantine().getIsQuarantined())
        .isFalse();

    // 2. Match value "true" but name "other"
    DeviceInfo valueMatchOnly =
        createBaseDeviceInfo()
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder().setName("other").setValue("true"))))
            .build();
    assertThat(builder.buildDeviceHeaderInfo(valueMatchOnly).getQuarantine().getIsQuarantined())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_isSharedDevice_checkValues() {
    // 1. Required dim "pool" = "other" -> Not Shared
    // If logic was anyMatch(d -> true), this would mistakenly be identified as shared because
    // the list is not empty.
    DeviceInfo notShared =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("pool").setValue("other"))))
            .build();
    // Verify indirect effect: Logcat is enabled (it's disabled for shared devices)
    assertThat(builder.buildDeviceHeaderInfo(notShared).getActions().getLogcat().getEnabled())
        .isTrue();
  }

  @Test
  public void buildDeviceActions_flash_visibility() {
    // 1. Neither AndroidRealDevice nor AndroidFlashableDevice -> Invisible
    // If logic was flashVisible = true, this would fail.
    DeviceInfo invisible =
        createBaseDeviceInfo()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("OtherDevice"))
            .build();

    assertThat(
            builder
                .buildDeviceHeaderInfo(invisible)
                .getActions()
                .getFlash()
                .getState()
                .getVisible())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_remoteControl() {
    // 1. Enabled: AcidDevice (AndroidRealDevice) + Idle + Normal
    DeviceInfo enabled =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(enabled)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isTrue();

    // 2. Disabled: Not Idle
    DeviceInfo busy = enabled.toBuilder().setDeviceStatus(DeviceStatus.BUSY).build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(busy)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
    assertThat(
            builder
                .buildDeviceHeaderInfo(busy)
                .getActions()
                .getRemoteControl()
                .getState()
                .getTooltip())
        .contains("wait for device idle");

    // 3. Disabled: FailedDevice
    DeviceInfo failed =
        enabled.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder().addType("AndroidRealDevice").addType("FailedDevice"))
            .build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(failed)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
    assertThat(
            builder
                .buildDeviceHeaderInfo(failed)
                .getActions()
                .getRemoteControl()
                .getState()
                .getTooltip())
        .contains("FailedDevice");

    // 4. Disabled: Mac Host
    DeviceInfo macHost =
        enabled.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder()
                                    .setName("host_os")
                                    .setValue("Mac OS"))))
            .build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(macHost)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
    assertThat(
            builder
                .buildDeviceHeaderInfo(macHost)
                .getActions()
                .getRemoteControl()
                .getState()
                .getTooltip())
        .contains("Linux OS");

    // 5. Disabled: Not AndroidRealDevice
    DeviceInfo nonAndroid =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("IosRealDevice"))
            .build();
    assertThat(
            builder
                .buildDeviceHeaderInfo(nonAndroid)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
    assertThat(
            builder
                .buildDeviceHeaderInfo(nonAndroid)
                .getActions()
                .getRemoteControl()
                .getState()
                .getTooltip())
        .contains("only supported on Android real devices");
  }

  @Test
  public void buildDeviceActions_remoteControl_abnormalDevice() {
    DeviceInfo abnormal =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .addType("AbnormalTestbedDevice"))
            .build();

    assertThat(
            builder
                .buildDeviceHeaderInfo(abnormal)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_remoteControl_macHostInRequiredDim() {
    DeviceInfo macHost =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("AndroidRealDevice")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder()
                                    .setName("host_os")
                                    .setValue("Mac OS"))))
            .build();

    assertThat(
            builder
                .buildDeviceHeaderInfo(macHost)
                .getActions()
                .getRemoteControl()
                .getState()
                .getEnabled())
        .isFalse();
  }

  @Test
  public void buildDeviceActions_screenshot_supportedInRequiredDim() {
    DeviceInfo enabled =
        createBaseDeviceInfo()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addRequiredDimension(
                                DeviceDimension.newBuilder()
                                    .setName("screenshot_able")
                                    .setValue("true"))))
            .build();
    assertThat(builder.buildDeviceHeaderInfo(enabled).getActions().getScreenshot().getEnabled())
        .isTrue();
  }

  private DeviceInfo.Builder createBaseDeviceInfo() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(
            DeviceLocator.newBuilder()
                .setId("device_id")
                .setLabLocator(LabLocator.newBuilder().setHostName("host_name").setIp("1.2.3.4")))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
        .setDeviceCondition(DeviceCondition.getDefaultInstance());
  }
}
