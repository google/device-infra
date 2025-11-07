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

package com.google.devtools.mobileharness.platform.android.labtestsupport.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import java.time.Duration;

/**
 * Utility class for operating LabTestSupport.
 *
 * <p>Must install the LabTestSupport APK on the Android devices before calling methods in this
 * class.
 */
public class LabTestSupportHelper {

  @VisibleForTesting static final String ALLOW_LTS_PROP_NAME = "debug.gms.phenotype.allow_lts";

  @VisibleForTesting
  static final String LAB_TEST_SUPPORT_PACKAGE = "com.google.android.apps.mtaas.labtestsupport";

  @VisibleForTesting
  static final String LTS_CONFIG_PHENOTYPE_FLAGS_INSTRUMENTATION_RUNNER_NAME =
      LAB_TEST_SUPPORT_PACKAGE + ".ConfigurePhenotypeFlagsInstrumentation";

  @VisibleForTesting
  static final String LTS_ENABLE_ADS_DEBUG_LOGGING_INSTRUMENTATION_RUNNER_NAME =
      LAB_TEST_SUPPORT_PACKAGE + ".EnableAdsDebugLoggingInstrumentation";

  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  public LabTestSupportHelper() {
    this(new AndroidAdbUtil(), new AndroidInstrumentationUtil());
  }

  @VisibleForTesting
  LabTestSupportHelper(
      AndroidAdbUtil androidAdbUtil, AndroidInstrumentationUtil androidInstrumentationUtil) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
  }

  /**
   * Disables the features of "smart lock for passwords" and "fast pair with
   * smartwatches/headphones" on the device.
   *
   * @param serial the serial number of the device.
   * @return true if the command is executed successfully.
   */
  public boolean disableSmartLockForPasswordsAndFastPair(String serial, int deviceSdkVersion)
      throws MobileHarnessException, InterruptedException {
    try {
      return runLtsInstrumentation(
          serial, deviceSdkVersion, LTS_CONFIG_PHENOTYPE_FLAGS_INSTRUMENTATION_RUNNER_NAME);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.LAB_TEST_SUPPORT_DISABLE_SMART_LOCK_FOR_PASSWORDS_AND_FAST_PAIR_ERROR,
          String.format(
              "Failed to disable the features of \"smart lock for passwords\" and \"fast pair with"
                  + " smartwatches/headphones\" on device %s",
              serial),
          e);
    }
  }

  public boolean enableAdsDebuggingLogging(String serial, int deviceSdkVersion)
      throws MobileHarnessException, InterruptedException {
    try {
      return runLtsInstrumentation(
          serial, deviceSdkVersion, LTS_ENABLE_ADS_DEBUG_LOGGING_INSTRUMENTATION_RUNNER_NAME);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.LAB_TEST_SUPPORT_ENABLE_ADS_DEBUG_LOGGING_ERROR,
          String.format("Failed to enable ads debugging logging on device %s", serial),
          e);
    }
  }

  private boolean runLtsInstrumentation(String serial, int deviceSdkVersion, String runnerName)
      throws MobileHarnessException, InterruptedException {
    // This property is checked by the gms phenotype service.
    androidAdbUtil.setProperty(serial, ALLOW_LTS_PROP_NAME, "true");
    String instrumentOutput =
        androidInstrumentationUtil.instrument(
            serial,
            deviceSdkVersion,
            AndroidInstrumentationSetting.create(
                LAB_TEST_SUPPORT_PACKAGE,
                runnerName,
                /* className= */ null,
                /* otherOptions= */ null,
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
    return instrumentOutput.contains("success=true");
  }
}
