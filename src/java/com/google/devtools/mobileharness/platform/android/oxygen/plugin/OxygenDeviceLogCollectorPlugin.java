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

package com.google.devtools.mobileharness.platform.android.oxygen.plugin;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.OxygenDevice;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Plugin for collecting logs from OxygenDevice before the test ends. */
@Plugin(type = PluginType.LAB)
public final class OxygenDeviceLogCollectorPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Subscribe
  public void onTestEnding(LocalTestEndingEvent event) throws InterruptedException {
    TestInfo testInfo = event.getTest();
    for (Device device : event.getLocalDevices().values()) {
      if (device instanceof OxygenDevice oxygenDevice) {
        logger.atInfo().log("Found OxygenDevice %s, pulling logs...", device.getDeviceId());
        try {
          oxygenDevice.pullOxygenLogs(testInfo);
        } catch (MobileHarnessException e) {
          testInfo.warnings().addAndLog(e, logger);
        }
      }
    }
  }
}
