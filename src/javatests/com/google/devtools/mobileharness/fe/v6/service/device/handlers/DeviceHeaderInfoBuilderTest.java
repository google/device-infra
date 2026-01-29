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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HostInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineInfo;
import com.google.inject.Guice;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceHeaderInfoBuilderTest {

  private static final String DEVICE_ID = "test_device_id";
  private static final String HOST_NAME = "test_host.google.com";
  private static final String IP = "192.168.1.1";

  @Inject private DeviceHeaderInfoBuilder deviceHeaderInfoBuilder;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void buildDeviceHeaderInfo_quarantined() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(DEVICE_ID)
                    .setLabLocator(LabLocator.newBuilder().setHostName(HOST_NAME).setIp(IP)))
            .setDeviceCondition(
                DeviceCondition.newBuilder()
                    .addTempDimension(
                        TempDimension.newBuilder()
                            .setDimension(
                                DeviceDimension.newBuilder()
                                    .setName("quarantined")
                                    .setValue("true"))))
            .build();

    DeviceHeaderInfo expectedHeaderInfo =
        DeviceHeaderInfo.newBuilder()
            .setId(DEVICE_ID)
            .setHost(HostInfo.newBuilder().setName(HOST_NAME).setIp(IP))
            .setQuarantine(QuarantineInfo.newBuilder().setIsQuarantined(true))
            .setActions(
                DeviceActions.newBuilder()
                    .setScreenshot(ActionButtonState.getDefaultInstance())
                    .setLogcat(ActionButtonState.getDefaultInstance())
                    .setFlash(FlashActionButtonState.getDefaultInstance())
                    .setRemoteControl(ActionButtonState.getDefaultInstance())
                    .setQuarantine(ActionButtonState.getDefaultInstance()))
            .build();

    assertThat(
            deviceHeaderInfoBuilder.buildDeviceHeaderInfo(
                deviceInfo, Optional.empty(), Optional.empty()))
        .isEqualTo(expectedHeaderInfo);
  }

  @Test
  public void buildDeviceHeaderInfo_notQuarantined() {
    DeviceInfo deviceInfo =
        DeviceInfo.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(DEVICE_ID)
                    .setLabLocator(LabLocator.newBuilder().setHostName(HOST_NAME).setIp(IP)))
            .build();

    DeviceHeaderInfo expectedHeaderInfo =
        DeviceHeaderInfo.newBuilder()
            .setId(DEVICE_ID)
            .setHost(HostInfo.newBuilder().setName(HOST_NAME).setIp(IP))
            .setQuarantine(QuarantineInfo.newBuilder().setIsQuarantined(false))
            .setActions(
                DeviceActions.newBuilder()
                    .setScreenshot(ActionButtonState.getDefaultInstance())
                    .setLogcat(ActionButtonState.getDefaultInstance())
                    .setFlash(FlashActionButtonState.getDefaultInstance())
                    .setRemoteControl(ActionButtonState.getDefaultInstance())
                    .setQuarantine(ActionButtonState.getDefaultInstance()))
            .build();

    assertThat(
            deviceHeaderInfoBuilder.buildDeviceHeaderInfo(
                deviceInfo, Optional.empty(), Optional.empty()))
        .isEqualTo(expectedHeaderInfo);
  }
}
