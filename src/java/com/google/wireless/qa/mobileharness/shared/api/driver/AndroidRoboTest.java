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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import javax.inject.Inject;

/** Driver for running Android Robo Tests using the UTP Android Robo Driver. */
@DriverAnnotation(
    help =
        "Driver to run Android Robo tests on real devices/emulators using "
            + "the UTP AndroidRoboDriver.")
@TestAnnotation(required = false, help = "Crawls the app. No specific test to execute.")
public class AndroidRoboTest extends BaseDriver implements SpecConfigable<AndroidRoboTestSpec> {

  private final Aapt aapt;
  private final Adb adb;

  @Inject
  AndroidRoboTest(Device device, TestInfo testInfo, Adb adb, Aapt aapt) {
    super(device, testInfo);
    this.aapt = aapt;
    this.adb = adb;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Running Android Robo Driver on %s.", this.getDevice().getDeviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Job Info: %s", this.getTest().jobInfo());

    testInfo.resultWithCause().setPass();
  }
}
