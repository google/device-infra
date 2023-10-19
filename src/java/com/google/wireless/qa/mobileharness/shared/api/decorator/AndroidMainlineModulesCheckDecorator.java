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
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidMainlineModulesCheckDecoratorSpec;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/** Decorator for managing whether to run the test based on device preloaded mainline modules. */
@DecoratorAnnotation(
    help =
        "Decorator for managing whether to run the test based on device preloaded mainline modules."
            + " See AndroidMainlineModulesCheckDecoratorSpec for more details.")
public class AndroidMainlineModulesCheckDecorator extends BaseDecorator
    implements SpecConfigable<AndroidMainlineModulesCheckDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String GO_APEX_PREFIX = "com.google.android.go.";

  private final AndroidPackageManagerUtil androidPackageManagerUtil;

  @Inject
  AndroidMainlineModulesCheckDecorator(
      Driver decorated, TestInfo testInfo, AndroidPackageManagerUtil androidPackageManagerUtil) {
    super(decorated, testInfo);
    this.androidPackageManagerUtil = androidPackageManagerUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    AndroidMainlineModulesCheckDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    boolean skipTest = true;
    try {
      skipTest = ifSkipTest(spec, testInfo, deviceId);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Failed to check if test should be skipped on device %s. Skip it by default: %s",
              deviceId, MoreThrowables.shortDebugString(e, 0));
    }

    if (skipTest) {
      MobileHarnessException error =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_MAINLINE_MODULES_CHECK_DECORATOR_SKIP_TEST,
              String.format(
                  "None of %s is active on device %s or error happened, skipping the test",
                  spec.getMainlineModulePackageNameList(), deviceId));
      testInfo.resultWithCause().setNonPassing(TestResult.SKIP, error);
      return;
    } else {
      try {
        getDecorated().run(testInfo);
      } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
        throw ErrorModelConverter.upgradeMobileHarnessException(e);
      }
    }
  }

  private boolean ifSkipTest(
      AndroidMainlineModulesCheckDecoratorSpec spec, TestInfo testInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    if (spec.getMainlineModulePackageNameList().isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No mainline module specified for device %s. Running the test.", deviceId);
      return false;
    }

    Set<PackageInfo> activeApexes =
        androidPackageManagerUtil.listApexPackageInfos(
            UtilArgs.builder().setSerial(deviceId).build());

    boolean modulePreloaded = false;
    Set<String> mainlineModulePackageNames = new HashSet<>(spec.getMainlineModulePackageNameList());
    for (PackageInfo pkg : activeApexes) {
      String pkgName = pkg.packageName();
      pkgName = pkgName.startsWith(GO_APEX_PREFIX) ? pkgName.replace(".go.", ".") : pkgName;
      if (mainlineModulePackageNames.contains(pkgName)) {
        modulePreloaded = true;
        break;
      }
    }
    if (!modulePreloaded) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Skipping the test because none of %s is active on device %s.",
              mainlineModulePackageNames, deviceId);
      return true;
    }
    return false;
  }
}
