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

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HostInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.protobuf.util.Timestamps;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for DeviceHeaderInfo. */
@Singleton
public class DeviceHeaderInfoBuilder {

  private final FlashButtonBuilder flashButtonBuilder;
  private final LogcatButtonBuilder logcatButtonBuilder;
  private final QuarantineButtonBuilder quarantineButtonBuilder;
  private final ScreenshotButtonBuilder screenshotButtonBuilder;
  private final ConfigurationButtonBuilder configurationButtonBuilder;
  private final RemoteControlButtonBuilder remoteControlButtonBuilder;

  @Inject
  DeviceHeaderInfoBuilder(
      FlashButtonBuilder flashButtonBuilder,
      LogcatButtonBuilder logcatButtonBuilder,
      QuarantineButtonBuilder quarantineButtonBuilder,
      ScreenshotButtonBuilder screenshotButtonBuilder,
      ConfigurationButtonBuilder configurationButtonBuilder,
      RemoteControlButtonBuilder remoteControlButtonBuilder) {
    this.flashButtonBuilder = flashButtonBuilder;
    this.logcatButtonBuilder = logcatButtonBuilder;
    this.quarantineButtonBuilder = quarantineButtonBuilder;
    this.screenshotButtonBuilder = screenshotButtonBuilder;
    this.configurationButtonBuilder = configurationButtonBuilder;
    this.remoteControlButtonBuilder = remoteControlButtonBuilder;
  }

  /** Builds DeviceHeaderInfo based on device info and configs. */
  public DeviceHeaderInfo buildDeviceHeaderInfo(
      DeviceInfo deviceInfo,
      Optional<DeviceConfig> unusedDeviceConfigOpt,
      Optional<LabConfig> unusedLabConfigOpt,
      UniverseScope universe) {
    Optional<TempDimension> quarantineDim =
        deviceInfo.getDeviceCondition().getTempDimensionList().stream()
            .filter(
                dim ->
                    dim.getDimension().getName().equals("quarantined")
                        && Ascii.toLowerCase(dim.getDimension().getValue()).equals("true"))
            .findFirst();

    QuarantineInfo.Builder quarantineInfoBuilder = QuarantineInfo.newBuilder();
    if (quarantineDim.isPresent()) {
      quarantineInfoBuilder.setIsQuarantined(true);
      long expireMs = quarantineDim.get().getExpireTimestampMs();
      if (expireMs > 0) {
        quarantineInfoBuilder.setExpiry(Timestamps.fromMillis(expireMs));
      }
    } else {
      quarantineInfoBuilder.setIsQuarantined(false);
    }

    return DeviceHeaderInfo.newBuilder()
        .setId(deviceInfo.getDeviceLocator().getId())
        .setHost(
            HostInfo.newBuilder()
                .setName(deviceInfo.getDeviceLocator().getLabLocator().getHostName())
                .setIp(deviceInfo.getDeviceLocator().getLabLocator().getIp()))
        .setQuarantine(quarantineInfoBuilder.build())
        // TODO: Fill device actions.
        .setActions(
            DeviceActions.newBuilder()
                .setScreenshot(screenshotButtonBuilder.build(deviceInfo, universe))
                .setLogcat(logcatButtonBuilder.build(deviceInfo, universe))
                .setFlash(flashButtonBuilder.build(deviceInfo, universe))
                .setRemoteControl(remoteControlButtonBuilder.build(deviceInfo, universe))
                .setQuarantine(quarantineButtonBuilder.build(deviceInfo, universe))
                .setConfiguration(configurationButtonBuilder.build(deviceInfo, universe)))
        .build();
  }
}
