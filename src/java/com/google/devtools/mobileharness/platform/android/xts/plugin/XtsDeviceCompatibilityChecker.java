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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.DeviceBuildInfo;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.util.Set;

/** A lab plugin to ensure allocated devices are compatible with the xTS test suite. */
@Plugin(type = PluginType.LAB)
public final class XtsDeviceCompatibilityChecker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;

  public XtsDeviceCompatibilityChecker() {
    this(new AndroidAdbUtil(), new AndroidAdbInternalUtil());
  }

  @VisibleForTesting
  XtsDeviceCompatibilityChecker(
      AndroidAdbUtil androidAdbUtil, AndroidAdbInternalUtil androidAdbInternalUtil) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
  }

  /** Returns true if the job is enabled for the xTS device compatibility checker. */
  public static boolean isEnabled(JobInfo jobInfo) {
    return jobInfo.properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false)
        || jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false);
  }

  @Subscribe
  public void onTestStarted(LocalTestStartedEvent event)
      throws SkipTestException, MobileHarnessException, InterruptedException {
    TestInfo testInfo = event.getTest();
    JobInfo jobInfo = testInfo.jobInfo();

    if (jobInfo.properties().getBoolean(Job.IS_RUN_RETRY).orElse(false)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Validating device build fingerprint for test %s", testInfo.locator().getName());
      validateDeviceBuildFingerprintMatchPrevSession(
          getAllocatedAndroidDeviceSerials(event),
          testInfo,
          jobInfo.properties().getOptional(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT).orElse(""),
          jobInfo
              .properties()
              .getOptional(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED)
              .orElse(""),
          jobInfo
              .properties()
              .getOptional(Job.PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT)
              .orElse(""));
    }
  }

  /**
   * Followed the fingerprint check logic from {@code
   * com.android.compatibility.common.tradefed.targetprep.BuildFingerPrintPreparer}
   */
  private void validateDeviceBuildFingerprintMatchPrevSession(
      ImmutableList<String> deviceSerials,
      TestInfo testInfo,
      String prevSessionDeviceBuildFingerprint,
      String prevSessionDeviceBuildFingerprintUnaltered,
      String prevSessionDeviceVendorBuildFingerprint)
      throws SkipTestException, InterruptedException {
    if (deviceSerials.isEmpty()) {
      return;
    }

    if (prevSessionDeviceBuildFingerprint.isEmpty()) {
      // Skip checking device build fingerprint if the previous session's build fingerprint is
      // empty.
      return;
    }

    String prevSessionDeviceBuildFingerprintToCompare =
        prevSessionDeviceBuildFingerprintUnaltered.isEmpty()
            ? prevSessionDeviceBuildFingerprint
            : prevSessionDeviceBuildFingerprintUnaltered;

    try {
      for (String serial : deviceSerials) {
        String deviceBuildFingerprint =
            androidAdbUtil
                .getProperty(serial, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName()))
                .trim();
        if (!Ascii.equalsIgnoreCase(
            deviceBuildFingerprint, prevSessionDeviceBuildFingerprintToCompare)) {
          setSkipCollectingNonTfReports(testInfo.jobInfo());
          throw SkipTestException.create(
              String.format(
                  "Device %s build fingerprint [%s] must match the one in the retried session [%s]."
                      + " Skipping test [%s]",
                  serial,
                  deviceBuildFingerprint,
                  prevSessionDeviceBuildFingerprint,
                  testInfo.locator().getName()),
              DesiredTestResult.SKIP,
              AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILD_NOT_MATCH_RETRY_PREV_SESSION);
        }

        if (!prevSessionDeviceVendorBuildFingerprint.isEmpty()) {
          String deviceVendorBuildFingerprint =
              androidAdbUtil
                  .getProperty(
                      serial, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName()))
                  .trim();
          if (!Ascii.equalsIgnoreCase(
              deviceVendorBuildFingerprint, prevSessionDeviceVendorBuildFingerprint)) {
            setSkipCollectingNonTfReports(testInfo.jobInfo());
            throw SkipTestException.create(
                String.format(
                    "Device %s vendor build fingerprint [%s] must match the one in the retried"
                        + " session [%s]. Skipping test [%s]",
                    serial,
                    deviceVendorBuildFingerprint,
                    prevSessionDeviceVendorBuildFingerprint,
                    testInfo.locator().getName()),
                DesiredTestResult.SKIP,
                AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILD_NOT_MATCH_RETRY_PREV_SESSION);
          }
        }
      }
    } catch (MobileHarnessException e) {
      setSkipCollectingNonTfReports(testInfo.jobInfo());
      throw SkipTestException.create(
          String.format(
              "Error when checking device build fingerprint for test [%s]",
              testInfo.locator().getName()),
          DesiredTestResult.SKIP,
          AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_CHECK_DEVICE_BUILD_ERROR,
          e);
    }
  }

  private void setSkipCollectingNonTfReports(JobInfo jobInfo) {
    jobInfo.properties().add(Job.SKIP_COLLECTING_NON_TF_REPORTS, "true");
  }

  private ImmutableList<String> getAllocatedAndroidDeviceSerials(LocalTestStartedEvent event)
      throws MobileHarnessException, InterruptedException {
    Set<String> allAndroidDevices =
        androidAdbInternalUtil.getDeviceSerialsByState(/* deviceState= */ null);
    return event.getAllocation().getAllDeviceLocators().stream()
        .map(DeviceLocator::getSerial)
        .filter(allAndroidDevices::contains)
        .collect(toImmutableList());
  }
}
