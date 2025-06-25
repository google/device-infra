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

import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.testcomponents.TestComponentsDirUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.util.MoblyPostTestProcessor;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import javax.inject.Inject;

/**
 * Driver for running Mobly tests packaged in AOSP and distributed via the Android Build.
 *
 * @deprecated Use MoblyAospTest instead.
 */
@Deprecated
@DriverAnnotation(
    help = "For running Mobly tests packaged in AOSP and distributed via the Android Build.")
public class MoblyAospPackageTest extends MoblyAospTest {

  @Inject
  MoblyAospPackageTest(
      Device device,
      TestInfo testInfo,
      MoblyAospTestSetupUtil setupUtil,
      ResUtil resUtil,
      TestComponentsDirUtil testComponentsDirUtil,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil,
      MoblyPostTestProcessor postTestProcessor) {
    super(
        device,
        testInfo,
        setupUtil,
        resUtil,
        testComponentsDirUtil,
        commandExecutor,
        localFileUtil,
        postTestProcessor);
  }
}
