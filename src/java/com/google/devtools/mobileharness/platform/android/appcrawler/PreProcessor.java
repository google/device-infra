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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import javax.inject.Inject;

/** PreProcessor class to handle operations before the crawl. */
public class PreProcessor {
  private final ApkInstaller apkInstaller;

  @Inject
  PreProcessor(ApkInstaller apkInstaller) {
    this.apkInstaller = apkInstaller;
  }

  /** Install apks needed for the test. */
  public void installApks(TestInfo testInfo, Device device, AndroidRoboTestSpec spec)
      throws MobileHarnessException, InterruptedException {
    apkInstaller.installApk(device, setupInstallable(spec.getCrawlerApk(), true), testInfo.log());
    apkInstaller.installApk(
        device, setupInstallable(spec.getCrawlerStubApk(), true), testInfo.log());
  }

  private ApkInstallArgs setupInstallable(String path, boolean grantRuntimePermissions) {
    return ApkInstallArgs.builder()
        .setApkPath(path)
        .setSkipIfCached(true)
        .setGrantPermissions(grantRuntimePermissions)
        .build();
  }
}
