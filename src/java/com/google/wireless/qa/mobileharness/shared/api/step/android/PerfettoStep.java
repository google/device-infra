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

package com.google.wireless.qa.mobileharness.shared.api.step.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.wireless.qa.mobileharness.shared.android.Perfetto;
import com.google.wireless.qa.mobileharness.shared.android.RunPerfettoArgs;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Utility to enable perfetto capture with user's input for driver and decorator. */
public class PerfettoStep {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final int DEFAULT_PERFETTO_BUFFER_SIZE = 32000;

  /** Command line switches to run Perfetto with. */
  @VisibleForTesting public static final String DEFAULT_PERFETTO_TAGS = "am";

  /** If perfetto_running_time is not specified, default perfetto time (10s) will be used. */
  @VisibleForTesting public static final Duration DEFAULT_PERFETTO_TIME = Duration.ofSeconds(10);

  @ParamAnnotation(
      required = false,
      help =
          "Buffer size in KB for perfetto capture. If not set, perfetto flag '-b' will not be set.")
  public static final String PARAM_PERFETTO_BUFFER_SIZE = "perfetto_buffer_size";

  @ParamAnnotation(
      required = false,
      help =
          "Duration (second) for perfetto capture. If not set, default "
              + "10 seconds will be used.")
  public static final String PARAM_PERFETTO_RUNNING_TIME = "perfetto_running_time";

  @ParamAnnotation(
      required = false,
      help =
          "Tags for perfetto capture. If not set, default ["
              + DEFAULT_PERFETTO_TAGS
              + "] will be applied during capture.")
  public static final String PARAM_PERFETTO_TAGS = "perfetto_tags";

  @ParamAnnotation(
      required = false,
      help =
          "Application name to monitor during perfetto capture. If not set, will leave it to"
              + " empty.")
  public static final String PARAM_PERFETTO_PACKAGE_NAME = "perfetto_package_name";

  @FileAnnotation(
      required = false,
      help =
          "The path to an optional perfetto config. You can create your own config at"
              + " https://ui.perfetto.dev/#!/record?p=buffers Note: Using config will make perfetto"
              + " ignore other params like time, size, buffer, package. Please specify the config"
              + " path as one of \"files\" in your mobile_test so that we can get it at runtime.")
  public static final String FILE_PERFETTO_CONFIG = "perfetto_config";

  /**
   * Time it takes for perfetto to shut down once it's been told to stop. The Perfetto process can
   * take some time to be activated by the OS in testing if the host machine is too busy.
   */
  public static final Duration DEFAULT_PERFETTO_SHUTDOWN_DURATION = Duration.ofMinutes(2);

  /** {@code Adb} only for perfetto argument. */
  private final Adb adb;

  /** {@code Perfetto} for collecting perfetto log. */
  private final Perfetto perfetto;

  /** Default buffer size for perfetto capture. */
  private int defaultBufferSize;

  /** Default tags for perfetto capture. */
  private String defaultTags;

  @Inject
  @VisibleForTesting
  PerfettoStep(Adb adb, Perfetto perfetto) {
    this.adb = adb;
    this.perfetto = perfetto;
    this.defaultBufferSize = DEFAULT_PERFETTO_BUFFER_SIZE;
    this.defaultTags = DEFAULT_PERFETTO_TAGS;
  }

  /**
   * Runs perfetto command synchronously. This method will parse user input and start perfetto.
   *
   * @param testInfo TestInfo structure
   * @param deviceId device serial number
   * @param outputFilePath HTML file path to output to
   * @param packageName enable app-level tracing for a comma-separated list of app package name
   * @param timeoutCallback callback for command timeout
   * @param outputCallback command callback for each line of output
   * @return the console output
   */
  @CanIgnoreReturnValue
  public String startSyncPerfetto(
      TestInfo testInfo,
      String deviceId,
      String outputFilePath,
      @Nullable String packageName,
      Runnable timeoutCallback,
      LineCallback outputCallback)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();

    RunPerfettoArgs.Builder builder = RunPerfettoArgs.builder();
    try {
      builder
          .setConfigFile(Optional.ofNullable(jobInfo.files().getSingle(FILE_PERFETTO_CONFIG)))
          .setTags(Optional.empty())
          .setTime(Duration.ZERO)
          .setBufferSizeKb(0);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Using Perfetto normal mode with config file, tags/time/buffer will be ignored");
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .withCause(e)
          .log("Error building Perfetto parameters.");
      String tags = jobInfo.params().get(PARAM_PERFETTO_TAGS, defaultTags);
      Duration time =
          Duration.ofSeconds(
              jobInfo
                  .params()
                  .getLong(PARAM_PERFETTO_RUNNING_TIME, DEFAULT_PERFETTO_TIME.toSeconds()));
      int bufferSize = jobInfo.params().getInt(PARAM_PERFETTO_BUFFER_SIZE, defaultBufferSize);

      builder
          .setTags(Optional.ofNullable(tags))
          .setTime(time)
          .setBufferSizeKb(bufferSize)
          .setConfigFile(Optional.empty());
    }
    String name = jobInfo.params().get(PARAM_PERFETTO_PACKAGE_NAME);
    if (!Strings.isNullOrEmpty(packageName)) {
      name = packageName;
    }

    return perfetto.runSyncPerfetto(
        builder
            .setAdbPath(Optional.ofNullable(adb.getAdbPath()))
            .setSerial(deviceId)
            .setTraceTimeout(testInfo.timer().remainingTimeJava())
            .setOutputPath(outputFilePath)
            .setPackageList(Optional.ofNullable(name))
            .setExitCallback(Optional.empty())
            .setTimeoutCallback(timeoutCallback)
            .setOutputCallback(outputCallback)
            .build());
  }

  /**
   * Start perfetto command asynchronously. This method will parse user input and start perfetto.
   *
   * @param testInfo TestInfo with parameters
   * @param deviceId device serial number
   * @param outputFilePath HTML file path to output to
   * @param packageName enable app-level tracing for a comma-separated list of app package name
   * @param resultCallback callback for command finished
   * @param timeoutCallback callback for command timeout
   * @param outputCallback command callback for each line of output
   * @return CommandProcess of the perfetto process
   */
  @CanIgnoreReturnValue
  public CommandProcess startAsyncPerfetto(
      TestInfo testInfo,
      String deviceId,
      String outputFilePath,
      @Nullable String packageName,
      Consumer<CommandResult> resultCallback,
      Runnable timeoutCallback,
      LineCallback outputCallback)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();

    RunPerfettoArgs.Builder builder = RunPerfettoArgs.builder();
    try {
      builder
          .setConfigFile(Optional.ofNullable(jobInfo.files().getSingle(FILE_PERFETTO_CONFIG)))
          .setTags(Optional.empty())
          .setBufferSizeKb(0);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Using Perfetto normal mode with config file, tags/time/buffer will be ignored");
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .withCause(e)
          .log("Did not find perfetto_config among files, using command line arguments.");
      String tags = jobInfo.params().get(PARAM_PERFETTO_TAGS, defaultTags);
      int bufferSize = jobInfo.params().getInt(PARAM_PERFETTO_BUFFER_SIZE, defaultBufferSize);

      builder
          .setTags(Optional.ofNullable(tags))
          .setBufferSizeKb(bufferSize)
          .setConfigFile(Optional.empty());
    }
    String name = jobInfo.params().get(PARAM_PERFETTO_PACKAGE_NAME);
    if (!Strings.isNullOrEmpty(packageName)) {
      name = packageName;
    }

    return perfetto.runAsyncPerfetto(
        builder
            .setAdbPath(Optional.ofNullable(adb.getAdbPath()))
            .setSerial(deviceId)
            .setTime(Duration.ZERO)
            .setTraceTimeout(testInfo.timer().remainingTimeJava())
            .setOutputPath(outputFilePath)
            .setPackageList(Optional.ofNullable(name))
            .setExitCallback(Optional.of(resultCallback))
            .setTimeoutCallback(timeoutCallback)
            .setOutputCallback(outputCallback)
            .build());
  }

  /**
   * Stop async perfetto command.
   *
   * @param perfettoProcess Process for an async perfetto command
   */
  public void stopAsyncPerfetto(CommandProcess perfettoProcess, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (perfettoProcess == null || !perfettoProcess.isAlive()) {
      // perfetto already stopped and killed, skip the check.
      return;
    }

    // Stop async perfetto capture.
    testInfo.log().atInfo().alsoTo(logger).log("Stopping perfetto gracefully...");
    try {
      // When run without a time (-t), perfetto runs until a command is given on stdin. We added a
      // switch in record_android_trace to listen for "StopTracing".
      perfettoProcess.stdinWriter().write("StopTracing\n");
      perfettoProcess.stdinWriter().flush();
      testInfo.log().atInfo().alsoTo(logger).log("finished flushing StopTracing to the script");
    } catch (IOException e) {
      perfettoProcess.kill();
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log(
              "Failed to write [%s] to stdin of command [%s], kill it",
              "StopTracing", perfettoProcess.command());
    }
    // Allow for perfetto to finish writing the dump, which can take a few seconds to
    // complete if the dump is very large.

    // Perfetto tool itself may take 10+s to start outputting streams.
    // So this check will most likely failed.
    try {
      perfettoProcess.await(DEFAULT_PERFETTO_SHUTDOWN_DURATION);
    } catch (TimeoutException e) {
      testInfo
          .warnings()
          .addAndLog(
              new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                  AndroidErrorId.ANDROID_PERFETTO_DECORATOR_ASYNC_EXCEED_TIMEOUT_LIMIT,
                  "Timeout waiting for Perfetto script to shutdown"),
              logger);
    }
    if (!perfettoProcess.isAlive()) {
      testInfo.log().atInfo().alsoTo(logger).log("perfetto stopped gracefully");
    } else {
      // File I/O should be quick enough in this period to finish file dump.
      try {
        perfettoProcess.await(DEFAULT_PERFETTO_SHUTDOWN_DURATION);
      } catch (TimeoutException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Timeout waiting for Perfetto script to shutdown for the second time");
      }
      if (!perfettoProcess.isAlive()) {
        testInfo.log().atInfo().alsoTo(logger).log("perfetto stopped gracefully after second try");
      } else {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("perfetto did NOT stop gracefully, killing...");
        perfettoProcess.kill();
      }
    }
  }
}
