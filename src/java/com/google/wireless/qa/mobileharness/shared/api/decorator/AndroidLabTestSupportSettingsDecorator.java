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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.labtestsupport.util.LabTestSupportHelper;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidLabTestSupportSettingsSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLabTestSupportSettingsDecoratorSpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
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
  private final LocalFileUtil localFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;

  @Inject
  AndroidLabTestSupportSettingsDecorator(
      Driver decorated,
      TestInfo testInfo,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      LabTestSupportHelper labTestSupportHelper,
      LocalFileUtil localFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil) {
    super(decorated, testInfo);
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.labTestSupportHelper = labTestSupportHelper;
    this.localFileUtil = localFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidLabTestSupportSettingsDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);
    String labTestSupportApkPath = testInfo.jobInfo().files().getSingle(TAG_LAB_TEST_SUPPORT_APK);
    int deviceSdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);

    apkInstaller.installApkIfNotExist(
        getDevice(),
        ApkInstallArgs.builder()
            .setApkPath(labTestSupportApkPath)
            .setGrantPermissions(true)
            .build(),
        testInfo.log());

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

    if (spec.getEnableAdsDebugLogging()
        && !labTestSupportHelper.enableAdsDebuggingLogging(deviceId, deviceSdkVersion)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_ENABLE_ADS_DEBUG_LOGGING_ERROR,
          String.format("Failed to enable ads debugging logging on device %s", deviceId));
    }

    // Apply signature masquerading if both signature file and target package are specified.
    if (spec.hasSignatureFile() && spec.hasSignatureMasqueradingPackageId()) {
      // Read and check signature file
      var signature = localFileUtil.readFile(spec.getSignatureFile());
      if (signature.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId
                .ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_APPLY_SIGNATURE_MASQUERADING_ERROR,
            "Signature file empty.");
      }
      var signatureHash = computeSha1(signature);
      // Get package UID
      // Underlying method is supported only on API level >= 26
      var targetPackageUid =
          getUidOfPackage(deviceId, deviceSdkVersion, spec.getSignatureMasqueradingPackageId());
      if (targetPackageUid.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId
                .ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_APPLY_SIGNATURE_MASQUERADING_ERROR,
            String.format(
                "Failed to apply signature masquerading on device %s. Target package UID absent.",
                deviceId));
      }

      // Apply lab test support signature masquerading
      if (!labTestSupportHelper.applySignatureMasquerading(
          deviceId, deviceSdkVersion, targetPackageUid.get(), signatureHash)) {
        throw new MobileHarnessException(
            AndroidErrorId
                .ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_APPLY_SIGNATURE_MASQUERADING_ERROR,
            String.format(
                "Failed to apply signature masquerading on device %s. Instrumentation failed",
                deviceId));
      }
    }
    getDecorated().run(testInfo);
  }

  private Optional<Integer> getUidOfPackage(
      String deviceId, int deviceSdkVersion, String packageName)
      throws InterruptedException, MobileHarnessException {
    var allUids = androidPackageManagerUtil.listPackagesWithUid(deviceId, deviceSdkVersion);
    for (var entry : allUids.entrySet()) {
      if (entry.getValue().equals(packageName)) {
        return Optional.of(entry.getKey());
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  String computeSha1(String signature) throws MobileHarnessException {
    var signatureBytes = BaseEncoding.base16().lowerCase().decode(signature);
    try {
      var digest = MessageDigest.getInstance("SHA-1");
      var sha1Hash = digest.digest(signatureBytes);
      return BaseEncoding.base16().lowerCase().encode(sha1Hash);
    } catch (NoSuchAlgorithmException e) {
      throw new MobileHarnessException(
          AndroidErrorId
              .ANDROID_LAB_TEST_SUPPORT_SETTINGS_DECORATOR_APPLY_SIGNATURE_MASQUERADING_ERROR,
          "Error computing SHA-1 hash for supplied signature.",
          e);
    }
  }
}
