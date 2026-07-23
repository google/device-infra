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

package com.google.devtools.mobileharness.infra.controller.test.util.atsjitemulatorlogpuller;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidJitEmulator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;

/** Lab plugin to pull CVD logs early during TestEndingEvent. */
@Plugin(type = Plugin.PluginType.LAB)
public class AtsJitEmulatorLogPullerPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Subscribe
  public void onTestEnding(LocalTestEndingEvent event)
      throws MobileHarnessException, SkipTestException, InterruptedException {
    for (Device device : event.getLocalDevices().values()) {
      if (device instanceof AndroidJitEmulator androidJitEmulator) {
        logger.atInfo().log(
            "Detected AndroidJitEmulator device: %s. Pulling CVD logs early.",
            device.getDeviceId());
        androidJitEmulator.pullCvdLogs(event.getTest());
        logger.atInfo().log("Successfully pulled CVD logs early for %s.", device.getDeviceId());
      }
    }
  }
}
