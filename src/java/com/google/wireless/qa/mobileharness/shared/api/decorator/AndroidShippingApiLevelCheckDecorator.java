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
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidShippingApiLevelCheckDecoratorSpec;
import javax.inject.Inject;

/** Decorator to check if the device's shipping API level meets the required minimum. */
@DecoratorAnnotation(
    help =
        "Decorator to skip the test if the device's shipping API level is lower than the"
            + " required one.")
public class AndroidShippingApiLevelCheckDecorator extends BaseDecorator
    implements SpecConfigable<AndroidShippingApiLevelCheckDecoratorSpec> {

  @VisibleForTesting
  static final ImmutableList<String> SYSTEM_SHIPPING_API_LEVEL_PROPS =
      ImmutableList.of("ro.product.first_api_level", "ro.build.version.sdk");

  @VisibleForTesting
  static final ImmutableList<String> VENDOR_API_LEVEL_PROPS =
      ImmutableList.of("ro.board.api_level", "ro.board.first_api_level");

  @VisibleForTesting
  static final ImmutableList<String> VSR_VENDOR_API_LEVEL_PROPS =
      ImmutableList.of("ro.vendor.api_level");

  private static final int VALUE_NOT_FOUND = -1;

  private final AndroidAdbUtil androidAdbUtil;

  @Inject
  AndroidShippingApiLevelCheckDecorator(
      Driver decorated, TestInfo testInfo, AndroidAdbUtil androidAdbUtil) {
    super(decorated, testInfo);
    this.androidAdbUtil = androidAdbUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidShippingApiLevelCheckDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    boolean shouldRun = true;
    if (spec.getMinApiLevel() > 0) {
      shouldRun = checkShippingApiLevel(testInfo, deviceId, spec.getMinApiLevel());
    }
    if (shouldRun && spec.getVsrMinApiLevel() > 0) {
      int vsrMinApiLevel = spec.getVsrMinApiLevel();
      if (vsrMinApiLevel > 34 && vsrMinApiLevel < 202404) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SHIPPING_API_LEVEL_CHECK_DECORATOR_INVALID_VSR_MIN_API_LEVEL,
            "vsr-min-api-level must have YYYYMM format if it has a value greater than 34, but has "
                + vsrMinApiLevel);
      }
      shouldRun = checkVsrApiLevel(testInfo, deviceId, vsrMinApiLevel);
    }
    if (shouldRun && spec.getVendorMinApiLevel() > 0) {
      int vendorMinApiLevel = spec.getVendorMinApiLevel();
      if (vendorMinApiLevel < 202404) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SHIPPING_API_LEVEL_CHECK_DECORATOR_INVALID_VENDOR_MIN_API_LEVEL,
            "vendor-min-api-level must have YYYYMM format greater than or equal to 202404, but has "
                + vendorMinApiLevel);
      }
      shouldRun = checkVendorApiLevel(testInfo, deviceId, vendorMinApiLevel);
    }

    if (shouldRun) {
      getDecorated().run(testInfo);
    }
  }

  private boolean checkShippingApiLevel(TestInfo testInfo, String deviceId, int minApiLevel)
      throws MobileHarnessException, InterruptedException {
    String shippingApiLevel = androidAdbUtil.getProperty(deviceId, SYSTEM_SHIPPING_API_LEVEL_PROPS);
    int shippingApiLevelInt =
        shippingApiLevel.isEmpty() ? VALUE_NOT_FOUND : Integer.parseInt(shippingApiLevel);
    if (shippingApiLevelInt < minApiLevel) {
      skipTest(
          testInfo,
          String.format(
              "Skipping test because %s: %d on %s is less than %d.",
              SYSTEM_SHIPPING_API_LEVEL_PROPS, shippingApiLevelInt, deviceId, minApiLevel));
      return false;
    }
    return true;
  }

  private boolean checkVsrApiLevel(TestInfo testInfo, String deviceId, int vsrMinApiLevel)
      throws MobileHarnessException, InterruptedException {
    // Android T or newer defines "ro.vendor.api_level".
    String vsrApiLevel = androidAdbUtil.getProperty(deviceId, VSR_VENDOR_API_LEVEL_PROPS);
    if (!vsrApiLevel.isEmpty()) {
      int vsrApiLevelInt = Integer.parseInt(vsrApiLevel);
      if (vsrApiLevelInt < vsrMinApiLevel) {
        skipTest(
            testInfo,
            String.format(
                "Skipping test because %s: %d on device %s is less than %d.",
                VSR_VENDOR_API_LEVEL_PROPS, vsrApiLevelInt, deviceId, vsrMinApiLevel));
        return false;
      } else {
        return true;
      }
    }
    // For older devices, fallback to check both shipping api level and vendor api level.
    return checkShippingApiLevel(testInfo, deviceId, vsrMinApiLevel)
        && checkVendorApiLevel(testInfo, deviceId, vsrMinApiLevel);
  }

  private boolean checkVendorApiLevel(TestInfo testInfo, String deviceId, int vendorMinApiLevel)
      throws MobileHarnessException, InterruptedException {
    String vendorApiLevel = androidAdbUtil.getProperty(deviceId, VENDOR_API_LEVEL_PROPS);
    int vendorApiLevelInt =
        vendorApiLevel.isEmpty() ? VALUE_NOT_FOUND : Integer.parseInt(vendorApiLevel);
    if (vendorApiLevelInt < vendorMinApiLevel) {
      skipTest(
          testInfo,
          String.format(
              "Skipping test because %s: %d on device %s is less than %d.",
              VENDOR_API_LEVEL_PROPS, vendorApiLevelInt, deviceId, vendorMinApiLevel));
      return false;
    }
    return true;
  }

  private void skipTest(TestInfo testInfo, String reason) {
    MobileHarnessException error =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_SHIPPING_API_LEVEL_CHECK_DECORATOR_API_LEVEL_TOO_LOW, reason);
    testInfo.resultWithCause().setNonPassing(TestResult.SKIP, error);
    testInfo.getRootTest().resultWithCause().setNonPassing(TestResult.SKIP, error);
  }
}
