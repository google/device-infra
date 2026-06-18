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
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Decorator for displaying device info and preventing orientation switch by installing and starting
 * the Bootstrap app.
 */
@DecoratorAnnotation(
    help =
        "Decorator for displaying device info and preventing orientation switch by installing and"
            + " starting the Bootstrap app.")
public class AndroidDisplayDeviceInfoDecorator extends BaseDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BACKDROP_APK_RES_PATH =
      "/com/google/testing/helium/utp/android/companion/backdrop/backdrop.apk";
  private static final String BACKDROP_PKG =
      "com.google.testing.helium.utp.android.companion.backdrop";
  private static final String BACKDROP_INTENT = "-n " + BACKDROP_PKG + "/.BackdropActivity";

  private final AndroidProcessUtil androidProcessUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;

  @Inject
  AndroidDisplayDeviceInfoDecorator(
      Driver decorated,
      TestInfo testInfo,
      AndroidProcessUtil androidProcessUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil) {
    super(decorated, testInfo);
    this.androidProcessUtil = androidProcessUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws InterruptedException, MobileHarnessException {
    String serial = getDevice().getDeviceId();

    String apkPath;
    try {
      Optional<String> externalResFile = resUtil.getExternalResourceFile(BACKDROP_APK_RES_PATH);
      if (externalResFile.isPresent()) {
        apkPath = externalResFile.get();
      } else {
        apkPath =
            resUtil.getResourceFile(AndroidDisplayDeviceInfoDecorator.class, BACKDROP_APK_RES_PATH);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DISPLAY_DEVICE_INFO_DECORATOR_BOOTSTRAP_APK_NOT_FOUND,
          "Failed to find the bootstrap app apk.",
          e);
    }

    try {
      testInfo.log().atInfo().alsoTo(logger).log("Install bootstrap app to device %s", serial);
      apkInstaller.installApkIfNotExist(
          getDevice(), ApkInstallArgs.builder().addApkPaths(apkPath).build(), testInfo.log());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DISPLAY_DEVICE_INFO_DECORATOR_BOOTSTRAP_INSTALL_ERROR,
          "Failed to install the bootstrap app.",
          e);
    }

    try {
      testInfo.log().atInfo().alsoTo(logger).log("Start bootstrap app on device %s", serial);
      androidProcessUtil.startApplication(serial, BACKDROP_INTENT);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DISPLAY_DEVICE_INFO_DECORATOR_BOOTSTRAP_START_ERROR,
          "Failed to start the bootstrap app.",
          e);
    }

    try {
      getDecorated().run(testInfo);
    } finally {
      apkInstaller.uninstallApk(getDevice(), BACKDROP_PKG, true, testInfo.log());
    }
  }
}
