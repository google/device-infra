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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidLabTestSupportSettingsSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLabTestSupportSettingsDecoratorSpec;
import javax.inject.Inject;

/**
 * Decorator to set phenotype flags to turn on/off specific features on the device via
 * LabTestSupport.
 */
@DecoratorAnnotation(
    help =
        "Decorator to set phenotype flags to turn on/off specific features on the device via"
            + " LabTestSupport.")
public class AndroidLabTestSupportSettingsDecorator extends BaseDecorator
    implements AndroidLabTestSupportSettingsSpec,
        SpecConfigable<AndroidLabTestSupportSettingsDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ApkInstaller unusedApkInstaller;
  private final AndroidInstrumentationUtil unusedAndroidInstrumentationUtil;

  @Inject
  AndroidLabTestSupportSettingsDecorator(
      Driver decorated,
      TestInfo testInfo,
      ApkInstaller apkInstaller,
      AndroidInstrumentationUtil androidInstrumentationUtil) {
    super(decorated, testInfo);
    this.unusedApkInstaller = apkInstaller;
    this.unusedAndroidInstrumentationUtil = androidInstrumentationUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Running AndroidLabTestSupportSettingsDecorator...");
    String deviceId = getDevice().getDeviceId();
    AndroidLabTestSupportSettingsDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);
    logger.atInfo().log("AndroidLabTestSupportSettingsDecoratorSpec: %s", spec);

    String labTestSupportApkPath = testInfo.jobInfo().files().getSingle(TAG_LAB_TEST_SUPPORT_APK);
    logger.atInfo().log("LabTestSupport APK path: %s", labTestSupportApkPath);

    getDecorated().run(testInfo);
  }
}
