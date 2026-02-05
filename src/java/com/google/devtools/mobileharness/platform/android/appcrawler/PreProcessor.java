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

package com.google.devtools.mobileharness.platform.android.appcrawler;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import javax.inject.Inject;

/** PreProcessor class to handle operations before the crawl. */
public class PreProcessor {

  private static final String CRAWLER_PKG = "androidx.test.tools.crawler";
  private final ApkInstaller apkInstaller;
  private final Adb adb;

  @Inject
  PreProcessor(ApkInstaller apkInstaller, Adb adb) {
    this.apkInstaller = apkInstaller;
    this.adb = adb;
  }

  /** Install apks needed for the test. */
  public void installApks(
      TestInfo testInfo, Device device, AndroidRoboTestSpec spec, String stubAppPath)
      throws MobileHarnessException, InterruptedException {
    apkInstaller.installApk(device, setupInstallable(spec.getCrawlerApk(), true), testInfo.log());
    addCrawlerToDeviceIdleWhitelist((AndroidDevice) device, spec);
    apkInstaller.installApk(device, setupInstallable(stubAppPath, true), testInfo.log());
  }

  private void addCrawlerToDeviceIdleWhitelist(AndroidDevice device, AndroidRoboTestSpec spec)
      throws InterruptedException, MobileHarnessException {
    // Not necessary for API level < 28.
    if (device.getSdkVersion() < 28) {
      return;
    }
    var crawlerPackageId = spec.hasCrawlerPackageId() ? spec.getCrawlerPackageId() : CRAWLER_PKG;
    var command = String.format("dumpsys deviceidle whitelist +%s", crawlerPackageId);
    var unused = adb.runShell(device.getDeviceId(), command);
  }

  private ApkInstallArgs setupInstallable(String path, boolean grantRuntimePermissions) {
    return ApkInstallArgs.builder()
        .setApkPath(path)
        .setSkipIfCached(true)
        .setGrantPermissions(grantRuntimePermissions)
        .build();
  }
}
