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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.TestSuiteVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.DeviceBuildInfo;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteVersionUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** A lab plugin to ensure allocated devices are compatible with the xTS test suite. */
@Plugin(type = PluginType.LAB)
public final class XtsDeviceCompatibilityChecker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;

  @Inject
  XtsDeviceCompatibilityChecker(
      AndroidAdbUtil androidAdbUtil, AndroidAdbInternalUtil androidAdbInternalUtil) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
  }

  @Subscribe
  public void onTestStarted(LocalTestStartedEvent event)
      throws SkipTestException, MobileHarnessException, InterruptedException {
    TestInfo testInfo = event.getTest();
    JobInfo jobInfo = testInfo.jobInfo();
    ImmutableList<String> deviceSerials = getAllocatedAndroidDeviceSerials(event);

    if (Flags.enableXtsDeviceCompatibilityCheck.getNonNull()) {
      validateDeviceToolCompatibility(deviceSerials, testInfo);
    }

    if (jobInfo.properties().getBoolean(Job.IS_RUN_RETRY).orElse(false)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Validating device build fingerprint for retry %s", testInfo.locator().getName());
      validateDeviceBuildFingerprintMatchPrevSession(
          deviceSerials,
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

    if (jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Validating device build fingerprint for non TF test %s",
              testInfo.locator().getName());
      validateDeviceBuildFingerprintsTheSame(deviceSerials, testInfo);
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
        String deviceBuildFingerprint = getDeviceBuildFingerprint(serial);
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
          String deviceVendorBuildFingerprint = getDeviceVendorBuildFingerprint(serial);
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

  private void validateDeviceBuildFingerprintsTheSame(
      ImmutableList<String> deviceSerials, TestInfo testInfo)
      throws InterruptedException, SkipTestException {
    if (deviceSerials.size() < 2) {
      return;
    }

    try {
      String deviceBuildFingerprint = getDeviceBuildFingerprint(deviceSerials.get(0));
      String deviceVendorBuildFingerprint = getDeviceVendorBuildFingerprint(deviceSerials.get(0));
      for (int i = 1; i < deviceSerials.size(); i++) {
        String serial = deviceSerials.get(i);
        String deviceBuildFingerprintToCompare = getDeviceBuildFingerprint(serial);
        if (!Ascii.equalsIgnoreCase(deviceBuildFingerprint, deviceBuildFingerprintToCompare)) {
          setSkipCollectingNonTfReports(testInfo.jobInfo());
          throw SkipTestException.create(
              String.format(
                  "Device build fingerprints should be the same for multi-device tests."
                      + " Found [%s: %s] and [%s: %s]. Skipping test [%s]",
                  deviceSerials.get(0),
                  deviceBuildFingerprint,
                  serial,
                  deviceBuildFingerprintToCompare,
                  testInfo.locator().getName()),
              DesiredTestResult.SKIP,
              AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILDS_NOT_THE_SAME);
        }
        String deviceVendorBuildFingerprintToCompare = getDeviceVendorBuildFingerprint(serial);
        if (!Ascii.equalsIgnoreCase(
            deviceVendorBuildFingerprint, deviceVendorBuildFingerprintToCompare)) {
          setSkipCollectingNonTfReports(testInfo.jobInfo());
          throw SkipTestException.create(
              String.format(
                  "Device vendor build fingerprints should be the same for multi-device tests. "
                      + "Found [%s: %s] and [%s: %s]. Skipping test [%s]",
                  deviceSerials.get(0),
                  deviceVendorBuildFingerprint,
                  serial,
                  deviceVendorBuildFingerprintToCompare,
                  testInfo.locator().getName()),
              DesiredTestResult.SKIP,
              AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILDS_NOT_THE_SAME);
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

  private String getDeviceBuildFingerprint(String serial)
      throws MobileHarnessException, InterruptedException {
    return androidAdbUtil.getProperty(serial, DeviceBuildInfo.FINGERPRINT.getPropNames()).trim();
  }

  private String getDeviceVendorBuildFingerprint(String serial)
      throws MobileHarnessException, InterruptedException {
    return androidAdbUtil
        .getProperty(serial, DeviceBuildInfo.VENDOR_FINGERPRINT.getPropNames())
        .trim();
  }

  private Optional<String> getAnyDeviceSdkVersion(
      ImmutableList<String> deviceSerials, TestInfo testInfo) throws InterruptedException {
    for (String serial : deviceSerials) {
      try {
        String sdkVersion =
            androidAdbUtil.getProperty(serial, DeviceBuildInfo.VERSION_SDK_FULL.getPropNames());
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Device %s SDK version: %s for compatibility check", serial, sdkVersion);
        return Optional.of(sdkVersion);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to get or parse device SDK version for %s: %s", serial, e.getMessage());
      }
    }
    return Optional.empty();
  }

  private void validateDeviceToolCompatibility(
      ImmutableList<String> deviceSerials, TestInfo testInfo)
      throws SkipTestException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    Optional<String> toolVersionStr = jobInfo.properties().getOptional(Job.XTS_SUITE_VERSION);
    Optional<String> sdkVersionStr = getAnyDeviceSdkVersion(deviceSerials, testInfo);
    if (toolVersionStr.isEmpty() || sdkVersionStr.isEmpty()) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Unknown tool version [%s] or device SDK version [%s], skipping compatibility check.",
              toolVersionStr.orElse("unknown"), sdkVersionStr.orElse("unknown"));
      return; // Cannot check if tool or device SDK version is unknown
    }

    TestSuiteVersion toolVersion;
    TestSuiteVersion deviceSdkVersion;
    try {
      toolVersion = TestSuiteVersionUtil.parse(toolVersionStr.get());
      // Full SDK version is "major(.minor)" format, e.g. "35", "36", "36.1", etc.
      deviceSdkVersion = TestSuiteVersionUtil.parse(sdkVersionStr.get());
    } catch (IllegalArgumentException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Failed to parse tool version [%s] or SDK version [%s], skipping compatibility check:"
                  + " %s",
              toolVersionStr.get(), sdkVersionStr.get(), e.getMessage());
      return;
    }

    if (!isCompatible(toolVersion, deviceSdkVersion)) {
      setSkipCollectingNonTfReports(jobInfo);
      throw SkipTestException.create(
          String.format(
              "Device SDK version [%s] is incompatible with tools version"
                  + " [%s]. Skipping test.",
              sdkVersionStr.get(), toolVersionStr.get()),
          DesiredTestResult.SKIP,
          AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_AND_TOOL_INCOMPATIBLE);
    }
  }

  private static boolean isCompatible(
      TestSuiteVersion toolVersion, TestSuiteVersion deviceSdkVersion) {
    // Skip the check for Android 12 and older devices.
    if (deviceSdkVersion.getMajor() <= 32) {
      return true;
    }
    // Tool version is the Android release version (e.g. 15/16/16.1)
    // Device SDK version is the Android SDK version (e.g. 35/36/36.1)
    return toolVersion.getMajor() == (deviceSdkVersion.getMajor() - 20)
        && toolVersion.getMinor() == deviceSdkVersion.getMinor();
  }
}
