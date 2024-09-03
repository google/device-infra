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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.DeviceBuildInfo;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsDeviceCompatibilityCheckerTest {

  private static final String DEVICE_ID_1 = "DEVICE_ID_1";
  private static final String DEVICE_ID_2 = "DEVICE_ID_2";

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private TestLocator testLocator;
  @Mock private Log testLog;
  @Mock private Api atInfo;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidAdbInternalUtil androidAdbInternalUtil;
  @Mock private LocalTestStartedEvent event;
  @Mock private Allocation allocation;

  private Timing timing;
  private Properties properties;
  private XtsDeviceCompatibilityChecker xtsDeviceCompatibilityChecker;

  @Before
  public void setUp() throws Exception {
    timing = new Timing();
    properties = new Properties(timing);
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(testLog);
    when(testLog.atInfo()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(testInfo.locator()).thenReturn(testLocator);
    when(event.getTest()).thenReturn(testInfo);
    when(event.getAllocation()).thenReturn(allocation);
    when(allocation.getAllDeviceLocators())
        .thenReturn(
            ImmutableList.of(
                new DeviceLocator(DEVICE_ID_1, null), new DeviceLocator(DEVICE_ID_2, null)));
    when(androidAdbInternalUtil.getDeviceSerialsByState(/* deviceState= */ null))
        .thenReturn(ImmutableSet.of(DEVICE_ID_1, DEVICE_ID_2));

    xtsDeviceCompatibilityChecker =
        new XtsDeviceCompatibilityChecker(androidAdbUtil, androidAdbInternalUtil);
  }

  @Test
  public void deviceBuildFingerprintUnset_skipChecking() throws Exception {
    xtsDeviceCompatibilityChecker.onTestStarted(event);
    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isFalse();
  }

  @Test
  public void deviceBuildFingerprintNotMatchPrevSession_throwsSkipTestException() throws Exception {
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    properties.add(Job.IS_RUN_RETRY, "true");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, "build_fingerprint_different");

    assertThat(
            assertThrows(
                    SkipTestException.class,
                    () -> xtsDeviceCompatibilityChecker.onTestStarted(event))
                .errorId())
        .isEqualTo(
            AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILD_NOT_MATCH_RETRY_PREV_SESSION);
    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isTrue();
  }

  @Test
  public void deviceBuildFingerprintMatchPrevSession() throws Exception {
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    properties.add(Job.IS_RUN_RETRY, "true");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, "build_fingerprint");

    xtsDeviceCompatibilityChecker.onTestStarted(event);

    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isFalse();
  }

  @Test
  public void deviceBuildFingerprintUnalteredMatched() throws Exception {
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    properties.add(Job.IS_RUN_RETRY, "true");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, "build_fingerprint_changed");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED, "build_fingerprint");
    properties.add(Job.PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT, "vendor_build_fingerprint");

    xtsDeviceCompatibilityChecker.onTestStarted(event);

    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isFalse();
  }

  @Test
  public void deviceVendorBuildFingerprintNotMatched_throwsSkipTestException() throws Exception {
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    properties.add(Job.IS_RUN_RETRY, "true");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, "build_fingerprint_changed");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED, "build_fingerprint");
    properties.add(
        Job.PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT, "vendor_build_fingerprint_changed");

    assertThat(
            assertThrows(
                    SkipTestException.class,
                    () -> xtsDeviceCompatibilityChecker.onTestStarted(event))
                .errorId())
        .isEqualTo(
            AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILD_NOT_MATCH_RETRY_PREV_SESSION);
    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isTrue();
  }

  @Test
  public void deviceBuildFingerprintUnalteredNotMatched_throwsSkipTestException() throws Exception {
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.FINGERPRINT.getPropName())))
        .thenReturn("build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_1, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    when(androidAdbUtil.getProperty(
            DEVICE_ID_2, ImmutableList.of(DeviceBuildInfo.VENDOR_FINGERPRINT.getPropName())))
        .thenReturn("vendor_build_fingerprint");
    properties.add(Job.IS_RUN_RETRY, "true");
    properties.add(Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, "build_fingerprint");
    properties.add(
        Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED, "build_fingerprint_original");
    properties.add(Job.PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT, "vendor_build_fingerprint");

    assertThat(
            assertThrows(
                    SkipTestException.class,
                    () -> xtsDeviceCompatibilityChecker.onTestStarted(event))
                .errorId())
        .isEqualTo(
            AndroidErrorId.XTS_DEVICE_COMPAT_CHECKER_DEVICE_BUILD_NOT_MATCH_RETRY_PREV_SESSION);
    assertThat(properties.getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)).isTrue();
  }
}
