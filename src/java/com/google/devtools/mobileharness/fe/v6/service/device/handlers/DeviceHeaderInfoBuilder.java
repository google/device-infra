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

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HostInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineInfo;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for DeviceHeaderInfo. */
@Singleton
public class DeviceHeaderInfoBuilder {

  private final FlashButtonBuilder flashButtonBuilder;
  private final LogcatButtonBuilder logcatButtonBuilder;

  @Inject
  DeviceHeaderInfoBuilder(
      FlashButtonBuilder flashButtonBuilder, LogcatButtonBuilder logcatButtonBuilder) {
    this.flashButtonBuilder = flashButtonBuilder;
    this.logcatButtonBuilder = logcatButtonBuilder;
  }

  /** Builds DeviceHeaderInfo based on device info and configs. */
  public DeviceHeaderInfo buildDeviceHeaderInfo(
      DeviceInfo deviceInfo,
      Optional<DeviceConfig> unusedDeviceConfigOpt,
      Optional<LabConfig> unusedLabConfigOpt) {
    return DeviceHeaderInfo.newBuilder()
        .setId(deviceInfo.getDeviceLocator().getId())
        .setHost(
            HostInfo.newBuilder()
                .setName(deviceInfo.getDeviceLocator().getLabLocator().getHostName())
                .setIp(deviceInfo.getDeviceLocator().getLabLocator().getIp()))
        .setQuarantine(
            QuarantineInfo.newBuilder()
                .setIsQuarantined(
                    deviceInfo.getDeviceCondition().getTempDimensionList().stream()
                        .anyMatch(
                            dim ->
                                dim.getDimension().getName().equals("quarantined")
                                    && dim.getDimension()
                                        .getValue()
                                        .toLowerCase(Locale.ROOT)
                                        .equals("true"))))
        // TODO: Fill device actions.
        .setActions(
            DeviceActions.newBuilder()
                .setScreenshot(ScreenshotButtonBuilder.build(deviceInfo))
                .setLogcat(logcatButtonBuilder.build(deviceInfo))
                .setFlash(flashButtonBuilder.build(deviceInfo))
                .setRemoteControl(RemoteControlButtonBuilder.build(deviceInfo))
                .setQuarantine(QuarantineButtonBuilder.build(deviceInfo)))
        .build();
  }
}
