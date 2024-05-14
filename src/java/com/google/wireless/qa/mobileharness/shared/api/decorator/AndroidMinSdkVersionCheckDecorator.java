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
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidMinSdkVersionCheckDecoratorSpec;
import javax.inject.Inject;

/** Decorator to check if the device's SDK version is supported. */
@DecoratorAnnotation(
    help =
        "Decorator to skip the test if the device's SDK version is lower than the required"
            + " minSdkVersion.")
public class AndroidMinSdkVersionCheckDecorator extends BaseDecorator
    implements SpecConfigable<AndroidMinSdkVersionCheckDecoratorSpec> {

  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  @Inject
  AndroidMinSdkVersionCheckDecorator(
      Driver decorated, TestInfo testInfo, AndroidSystemSettingUtil androidSystemSettingUtil) {
    super(decorated, testInfo);

    this.androidSystemSettingUtil = androidSystemSettingUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidMinSdkVersionCheckDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    int minSdkVersion = spec.getMinSdkVersion();

    if (androidSystemSettingUtil.getDeviceSdkVersion(deviceId) < minSdkVersion) {
      MobileHarnessException error =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_MIN_SDK_VERSION_DECORATOR_SDK_VERSION_TOO_LOW,
              String.format(
                  "Device %s has SDK version %s, which is lower than the required minSdkVersion %s",
                  deviceId, androidSystemSettingUtil.getDeviceSdkVersion(deviceId), minSdkVersion));
      testInfo.resultWithCause().setNonPassing(TestResult.SKIP, error);
      testInfo.getRootTest().resultWithCause().setNonPassing(TestResult.SKIP, error);
      throw error;
    }

    try {
      getDecorated().run(testInfo);
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw ErrorModelConverter.upgradeMobileHarnessException(e);
    }
  }
}
