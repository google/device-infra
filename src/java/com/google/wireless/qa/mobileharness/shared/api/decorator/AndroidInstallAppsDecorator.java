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

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.step.android.InstallApkStep;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.InstallApkStepSpec;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

/** Decorator for installing apks on Android real devices/emulators. */
@DecoratorAnnotation(
    help =
        "For installing apks. "
            + "Use this decorator \"outside\" of the other decorators that rely on the apks. "
            + "See the http://go/mh-tr")
public class AndroidInstallAppsDecorator extends BaseDecorator
    implements SpecConfigable<InstallApkStepSpec> {

  @StepAnnotation private final InstallApkStep installApkStep;

  /**
   * @deprecated Use {@code InstallApkStep.PARAM_SKIP_GMS_DOWNGRADE} instead.
   */
  @Deprecated
  public static final String PARAM_SKIP_GMS_DOWNGRADE = InstallApkStep.PARAM_SKIP_GMS_DOWNGRADE;

  /**
   * @deprecated Use {@code InstallApkStep.PARAM_CLEAR_GMS_APP_DATA} instead.
   */
  @Deprecated
  public static final String PARAM_CLEAR_GMS_APP_DATA = InstallApkStep.PARAM_CLEAR_GMS_APP_DATA;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Util for sending progress report messages. */
  private final TestMessageUtil testMessageUtil;

  @Inject
  AndroidInstallAppsDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      InstallApkStep installApkStep,
      TestMessageUtil testMessageUtil) {
    super(decoratedDriver, testInfo);
    this.installApkStep = installApkStep;
    this.testMessageUtil = testMessageUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Instant startTime = Instant.now();
    // Installs APKs.
    sendProgressReportMessage(testInfo, "Install apks");
    try {
      InstallApkStepSpec spec = testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());
      installApkStep.installBuildApks(getDevice(), testInfo, spec);
    } catch (MobileHarnessException e) {
      // Proper TestResult will be set by isInstallFailure.
      installApkStep.isInstallFailure(e, testInfo);
      // Throw out the exception so Mobly adapter can capture this failure. b/73964982.
      throw e;
    }

    Instant endTime = Instant.now();
    long runTimeMs = Duration.between(startTime, endTime).toMillis();
    testInfo
        .properties()
        .add(
            PropertyName.Test.PREFIX_DECORATOR_RUN_TIME_MS + getClass().getSimpleName(),
            Long.toString(runTimeMs));

    getDecorated().run(testInfo);
  }

  /** Sends the progress report message. */
  private void sendProgressReportMessage(TestInfo testInfo, String progress) {
    try {
      testMessageUtil.sendMessageToTest(
          testInfo,
          ImmutableMap.of(
              "namespace",
              TestMessageUtil.MH_NAMESPACE_PREFIX + "decorator:AndroidInstallAppsDecorator",
              "type",
              "progress_report",
              "time",
              "pre_run",
              "progress",
              progress));
    } catch (MobileHarnessException e) {
      testInfo.errors().addAndLog(e, logger);
    }
  }
}
