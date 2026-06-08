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

/*
 * Copyright 2026 Google LLC
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

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builder for {@link DeviceActions}. */
@Singleton
public class DeviceActionsBuilder {

  private final FlashButtonBuilder flashButtonBuilder;
  private final LogcatButtonBuilder logcatButtonBuilder;
  private final QuarantineButtonBuilder quarantineButtonBuilder;
  private final ScreenshotButtonBuilder screenshotButtonBuilder;
  private final ConfigurationButtonBuilder configurationButtonBuilder;
  private final RemoteControlButtonBuilder remoteControlButtonBuilder;
  private final DeviceDecommissionButtonBuilder decommissionButtonBuilder;

  @Inject
  DeviceActionsBuilder(
      FlashButtonBuilder flashButtonBuilder,
      LogcatButtonBuilder logcatButtonBuilder,
      QuarantineButtonBuilder quarantineButtonBuilder,
      ScreenshotButtonBuilder screenshotButtonBuilder,
      ConfigurationButtonBuilder configurationButtonBuilder,
      RemoteControlButtonBuilder remoteControlButtonBuilder,
      DeviceDecommissionButtonBuilder decommissionButtonBuilder) {
    this.flashButtonBuilder = flashButtonBuilder;
    this.logcatButtonBuilder = logcatButtonBuilder;
    this.quarantineButtonBuilder = quarantineButtonBuilder;
    this.screenshotButtonBuilder = screenshotButtonBuilder;
    this.configurationButtonBuilder = configurationButtonBuilder;
    this.remoteControlButtonBuilder = remoteControlButtonBuilder;
    this.decommissionButtonBuilder = decommissionButtonBuilder;
  }

  /** Builds DeviceActions based on device info. */
  public DeviceActions buildDeviceActions(DeviceInfo deviceInfo, UniverseScope universe) {
    return DeviceActions.newBuilder()
        .setScreenshot(screenshotButtonBuilder.build(deviceInfo, universe))
        .setLogcat(logcatButtonBuilder.build(deviceInfo, universe))
        .setFlash(flashButtonBuilder.build(deviceInfo, universe))
        .setRemoteControl(remoteControlButtonBuilder.build(deviceInfo, universe))
        .setQuarantine(quarantineButtonBuilder.build(deviceInfo, universe))
        .setConfiguration(configurationButtonBuilder.build(deviceInfo, universe))
        .setDecommission(decommissionButtonBuilder.build(deviceInfo, universe))
        .build();
  }
}
