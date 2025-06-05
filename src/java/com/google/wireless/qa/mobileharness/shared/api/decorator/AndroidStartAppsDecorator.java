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
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Driver decorator for starting applications on Android device. */
@DecoratorAnnotation(help = "For starting apps. ")
public class AndroidStartAppsDecorator extends BaseDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help =
          "The applications(intents) to start before going into the core driver. "
              + "This param must be formatted as gson "
              + "(https://github.com/google/gson/blob/master/UserGuide.md). "
              + "example: [\"-a android.intent.action.VIEW -d content://contacts/people/1\", "
              + "\"-c android.intent.category.APP_CONTACTS\"]")
  public static final String PARAM_START_APPS = "start_apps";

  private final AndroidProcessUtil androidProcessUtil;

  /** Util for sending progress report messages. */
  private final TestMessageUtil testMessageUtil;

  private final Clock clock;

  public AndroidStartAppsDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidProcessUtil(),
        new TestMessageUtil(),
        Clock.systemUTC());
  }

  @VisibleForTesting
  AndroidStartAppsDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidProcessUtil androidProcessUtil,
      TestMessageUtil testMessageUtil,
      Clock clock) {
    super(decoratedDriver, testInfo);
    this.androidProcessUtil = androidProcessUtil;
    this.testMessageUtil = testMessageUtil;
    this.clock = clock;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Instant startTime = clock.instant();

    JobInfo jobInfo = testInfo.jobInfo();
    String deviceId = getDevice().getDeviceId();
    String inputParam = jobInfo.params().get(PARAM_START_APPS);

    sendProgressReportMessage(testInfo, "Start apps");

    // Parses intents.
    Gson gson = new Gson();
    List<String> intents = gson.fromJson(inputParam, new TypeToken<List<String>>() {}.getType());

    // Starts apps.
    if (intents != null) {
      for (String intent : intents) {
        try {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Start application: %s\n on device: %s", intent, deviceId);
          androidProcessUtil.startApplication(deviceId, intent);
        } catch (MobileHarnessException e) {
          testInfo.warnings().addAndLog(e, logger);
          throw e;
        }
      }
    }

    Instant endTime = clock.instant();
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
          testInfo.locator().getId(),
          ImmutableMap.of(
              "namespace",
              TestMessageUtil.MH_NAMESPACE_PREFIX + "decorator:AndroidStartAppsDecorator",
              "type",
              "progress_report",
              "time",
              "pre_run",
              "progress",
              progress));
    } catch (MobileHarnessException e) {
      testInfo.warnings().addAndLog(e, logger);
    }
  }
}
