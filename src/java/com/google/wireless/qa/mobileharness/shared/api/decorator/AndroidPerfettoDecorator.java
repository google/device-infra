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
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * Runs perfetto at the start of each test and stops it at the end of each test.
 *
 * <p>Newer versions of perfetto (such as the one used by {@code Perfetto}) can run and wait for
 * Ctrl+c to stop tracing. This allows for unbounded tracing and precision control of when the trace
 * stops.
 *
 * <p>This decorator controls the {@code CommandFuture} representing the perfetto process and stops
 * it via a "\n" when the test actually finishes ({@link #run(TestInfo)}. Some time is permitted
 * after perfetto is told to finish to allow it to serialize the perfetto.html dump, which can take
 * some time to complete on larger traces. The user is advised to use {@link
 * PerfettoStep#PARAM_PERFETTO_TAGS} to limit the trace sizes and focus relevance.
 *
 * <p>This decorator includes 2 main running mode:
 *
 * <p>- start_perfetto_before_test_start mode
 *
 * <p>- start_perfetto_after_test_start mode
 */
@DecoratorAnnotation(
    help =
        "Runs Perfetto at the start of each test for a specified amount of time. "
            + "Includes the results of Perfetto as an html file in the test results.")
public class AndroidPerfettoDecorator extends BaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @StepAnnotation private final PerfettoStep perfettoStep;

  public static final String BEFORE_TEST_RUNNING_MODE = "start_perfetto_before_test_start";
  public static final String AFTER_TEST_RUNNING_MODE = "start_perfetto_after_test_finish";

  @ParamAnnotation(
      required = false,
      help =
          "Perfetto running mode, default: "
              + BEFORE_TEST_RUNNING_MODE
              + ". 'start_perfetto_before_test_start': Perfetto will start before test run in"
              + " async mode, and auto finish after test.\n"
              + "'start_perfetto_after_test_start': Perfetto will run in sync mode after test"
              + " finished, 'perfetto_running_time' need to be specified.\n"
              + "'start_perfetto_after_test_start' mode is created because perfetto have to run"
              + " after app starts and while app is running. The app is probably started from"
              + " direct commands (not from tests) in this case.")
  public static final String PARAM_PERFETTO_RUNNING_MODE = "perfetto_running_mode";

  private static final String OUTPUT_FILE_NAME_FORMAT = "perfetto.output";

  private final Clock clock;

  private final Sleeper sleeper;

  @Inject
  AndroidPerfettoDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      PerfettoStep perfettoStep,
      Clock clock,
      Sleeper sleeper) {
    super(decoratedDriver, testInfo);
    this.perfettoStep = perfettoStep;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  @Override
  public void run(final TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();

    String mode = jobInfo.params().get(PARAM_PERFETTO_RUNNING_MODE, BEFORE_TEST_RUNNING_MODE);
    File outputFile = new File(testInfo.getGenFileDir(), OUTPUT_FILE_NAME_FORMAT);

    if (AFTER_TEST_RUNNING_MODE.equals(mode)) {
      runAfterTest(testInfo, outputFile, deviceId);
    } else {
      runBeforeTest(testInfo, outputFile, deviceId);
    }
  }

  private void runAfterTest(final TestInfo testInfo, File outputFile, String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      getDecorated().run(testInfo);
    } finally {
      testInfo.log().atInfo().alsoTo(logger).log("Starting sync perfetto");
      try {
        String consoleOutput =
            perfettoStep.startSyncPerfetto(
                testInfo,
                deviceId,
                outputFile.getAbsolutePath(),
                null,
                () -> {
                  testInfo.log().atInfo().alsoTo(logger).log("[perfetto onTimeout] ");
                  testInfo
                      .errors()
                      .addAndLog(
                          new MobileHarnessException(
                              AndroidErrorId.ANDROID_PERFETTO_DECORATOR_SYNC_EXCEED_TIMEOUT_LIMIT,
                              "perfetto timeout"),
                          logger);
                },
                LineCallback.does(
                    line ->
                        testInfo.log().atInfo().alsoTo(logger).log("[perfetto output] %s", line)));
        testInfo.log().atInfo().log("Perfetto finished: %s", consoleOutput);
      } catch (MobileHarnessException e) {
        testInfo.errors().addAndLog(e, logger);
      }
    }
  }

  private void runBeforeTest(final TestInfo testInfo, File outputFile, String deviceId)
      throws MobileHarnessException, InterruptedException {
    // Run perfetto async, with a timeout equal to the test's remaining time (before timeout).
    // This is not how long perfetto is intended to run, however. Instead, perfetto is stopped
    // via "\n" on stdin (which is a new perfetto feature).
    testInfo.log().atInfo().alsoTo(logger).log("Starting async perfetto");
    Consumer<CommandResult> exitCallback =
        commandResult -> {
          if (commandResult.exitCode() == 0) {
            testInfo.log().atInfo().alsoTo(logger).log("[perfetto onSuccess]");
          } else {
            testInfo.log().atInfo().alsoTo(logger).log("[perfetto onError] %s", commandResult);
            testInfo
                .errors()
                .addAndLog(
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        AndroidErrorId.ANDROID_PERFETTO_DECORATOR_ASYNC_EXIT_WITH_ERRORS,
                        commandResult.toString()),
                    logger);
          }
        };
    CommandProcess perfettoProcess =
        perfettoStep.startAsyncPerfetto(
            testInfo,
            deviceId,
            outputFile.getAbsolutePath(),
            null,
            exitCallback,
            () -> {
              testInfo.log().atInfo().alsoTo(logger).log("[perfetto onTimeout]");
              testInfo
                  .errors()
                  .addAndLog(
                      new MobileHarnessException(
                          AndroidErrorId.ANDROID_PERFETTO_DECORATOR_ASYNC_EXCEED_TIMEOUT_LIMIT,
                          "perfetto timeout"),
                      logger);
            },
            LineCallback.does(
                line -> testInfo.log().atInfo().alsoTo(logger).log("[perfetto output] %s", line)));
    Instant asyncPerfettoStartTime = clock.instant();

    try {
      getDecorated().run(testInfo);
    } finally {
      Instant now = clock.instant();
      Instant earliestEndTime = asyncPerfettoStartTime.plusSeconds(5);
      if (now.isBefore(earliestEndTime)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Wait for a short while before stopping async perfetto");
        sleeper.sleep(Duration.between(now, earliestEndTime));
      }

      perfettoStep.stopAsyncPerfetto(perfettoProcess, testInfo);
    }
  }
}
