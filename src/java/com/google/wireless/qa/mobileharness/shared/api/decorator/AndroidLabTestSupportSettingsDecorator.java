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

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.labtestsupport.util.LabTestSupportHelper;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
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

  private final ApkInstaller apkInstaller;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final LabTestSupportHelper labTestSupportHelper;

  @Inject
  AndroidLabTestSupportSettingsDecorator(
      Driver decorated,
      TestInfo testInfo,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      LabTestSupportHelper labTestSupportHelper) {
    super(decorated, testInfo);
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.labTestSupportHelper = labTestSupportHelper;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidLabTestSupportSettingsDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);
    String labTestSupportApkPath = testInfo.jobInfo().files().getSingle(TAG_LAB_TEST_SUPPORT_APK);

    apkInstaller.installApkIfNotExist(
        getDevice(),
        ApkInstallArgs.builder()
            .setApkPath(labTestSupportApkPath)
            .setGrantPermissions(true)
            .build(),
        testInfo.log());

    int deviceSdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    if (spec.getDisableSmartLockForPasswordsAndFastPair()
        && !labTestSupportHelper.disableSmartLockForPasswordsAndFastPair(
            deviceId, deviceSdkVersion)) {
      throw new MobileHarnessException(
          AndroidErrorId
              .ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_DISABLE_SMART_LOCK_FOR_PASSWORDS_AND_FAST_PAIR_ERROR,
          String.format(
              "Failed to disable the features of \"smart lock for passwords\" and \"fast pair with"
                  + " smartwatches/headphones\" on device %s",
              deviceId));
    }

    getDecorated().run(testInfo);
  }
}
