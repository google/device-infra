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

import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import javax.inject.Inject;

/** Post processing operations for AndroidRoboTest. */
public class PostProcessor {
  private static final String CRAWLER_PKG = "androidx.test.tools.crawler";
  private static final String STUB_CRAWLER_PKG = "androidx.test.tools.crawler.stubapp";

  private final ApkInstaller apkInstaller;

  @Inject
  PostProcessor(ApkInstaller apkInstaller) {
    this.apkInstaller = apkInstaller;
  }

  /** Uninstall app packages installed as part of AndroidRoboTest. */
  public void uninstallApks(TestInfo testInfo, Device device, AndroidRoboTestSpec spec)
      throws InterruptedException {
    var crawlerPackageId = spec.hasCrawlerPackageId() ? spec.getCrawlerPackageId() : CRAWLER_PKG;
    var crawlerStubPackageId =
        spec.hasStubAppPackageId() ? spec.getStubAppPackageId() : STUB_CRAWLER_PKG;
    apkInstaller.uninstallApk(device, crawlerPackageId, true, testInfo.log());
    apkInstaller.uninstallApk(device, crawlerStubPackageId, true, testInfo.log());
  }
}
