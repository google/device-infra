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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import javax.inject.Inject;

/** Driver for running Android Robo Tests using the UTP Android Robo Driver. */
@DriverAnnotation(
    help =
        "Driver to run Android Robo tests on real devices/emulators using "
            + "the UTP AndroidRoboDriver.")
@TestAnnotation(required = false, help = "Crawls the app. No specific test to execute.")
public class AndroidRoboTest extends BaseDriver {
  private static final String UTP_LAUNCHER_RESOURCE_PATH =
      "/com/google/testing/platform/launcher/launcher_with_protobuf_deploy.jar";
  private static final String UTP_MAIN_RESOURCE_PATH =
      "/com/google/testing/platform/main/main_deploy.jar";

  private static final String DEVICE_PROVIDER_RESOURCE_PATH =
      "/com/google/testing/platform/runtime/android/provider/local/local_android_device_provider_java_binary_deploy.jar";

  private static final String ANDROID_ROBO_DRIVER_RESOURCE_PATH =
      "/com/google/testing/helium/utp/android/driver/robo/android_robo_driver_deploy.jar";

  private final ResUtil resourcesUtil;

  @Inject
  AndroidRoboTest(Device device, TestInfo testInfo, ResUtil resUtil) {
    super(device, testInfo);
    this.resourcesUtil = resUtil;
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
    String driverPath =
        resourcesUtil.getResourceFile(getClass(), ANDROID_ROBO_DRIVER_RESOURCE_PATH);
    String providerPath = resourcesUtil.getResourceFile(getClass(), DEVICE_PROVIDER_RESOURCE_PATH);
    String launcherPath = resourcesUtil.getResourceFile(getClass(), UTP_LAUNCHER_RESOURCE_PATH);
    String mainPath = resourcesUtil.getResourceFile(getClass(), UTP_MAIN_RESOURCE_PATH);
    testInfo.log().atInfo().alsoTo(logger).log("Driver Path: %s", driverPath);
    testInfo.log().atInfo().alsoTo(logger).log("Provider Path: %s", providerPath);
    testInfo.log().atInfo().alsoTo(logger).log("Launcher Path: %s", launcherPath);
    testInfo.log().atInfo().alsoTo(logger).log("Main Path: %s", mainPath);
    testInfo.resultWithCause().setPass();
  }
}
