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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.collect.Comparators.min;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_TEST_START_EPOCH_MS;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationParser;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationResultBuilder;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.TestTimeTrackerKt;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.TestSuiteResultConverter;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.AntXmlResultWriter;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.XmlWriter;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.shared.android.parser.AndroidInstrumentationParser;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationOptionsPreparer.PreparedInstrumentationOptions;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Executes prepared Android instrumentation runs (asynchronous or synchronous across sequential
 * option maps), writes instrumentation logs and structured result artifacts, and aggregates test
 * outcomes.
 */
final class AndroidInstrumentationRunner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MIN_ASYNC_SDK_VERSION = 21;

  private final Adb adb;
  private final SystemStateManager systemStateManager;
  private final LocalFileUtil fileUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final Clock clock;

  @Inject
  AndroidInstrumentationRunner(
      Adb adb,
      SystemStateManager systemStateManager,
      LocalFileUtil fileUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      Clock clock) {
    this.adb = adb;
    this.systemStateManager = systemStateManager;
    this.fileUtil = fileUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.clock = clock;
  }

  /** Runs instrumentation asynchronously or synchronously according to job parameters. */
  void run(ExecutionParams params) throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = params.testInfo();
    JobInfo job = testInfo.jobInfo();
    Duration instrumentTimeout = getInstrumentTimeout(job);

    if (job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ASYNC)) {
      Optional<ImmutableMap<String, String>> maybeOptionMap =
          Iterables.getOnlyElement(params.preparedOptions().optionMaps());
      if (maybeOptionMap.isEmpty()) {
        testInfo
            .properties()
            .add(
                AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_0",
                AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
        return;
      }
      runAsync(params, maybeOptionMap.get(), instrumentTimeout);
    } else {
      try {
        runSync(params, instrumentTimeout);
      } finally {
        androidInstrumentationUtil.pullInstrumentationFilesFromDevice(
            params.deviceId(), testInfo, params.externalStoragePath());
      }
    }
  }

  private void runAsync(
      ExecutionParams params, Map<String, String> optionMap, @Nullable Duration instrumentTimeout)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = params.testInfo();
    String testTarget = params.preparedOptions().testTarget();
    Duration testTimeout = computeTestTimeout(testInfo, instrumentTimeout);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "%nAsync run instrumentation: %n"
                + " - test target: %s%n - timeout: %d ms%n - options: %s",
            testTarget, testTimeout.toMillis(), StrUtil.DEFAULT_MAP_JOINER.join(optionMap));
    if (params.deviceSdkVersion() < MIN_ASYNC_SDK_VERSION) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_INCOMPATIBLE_DEVICE,
          String.format(
              "Device sdk version is %d (<%d) which cannot support async run.",
              params.deviceSdkVersion(), MIN_ASYNC_SDK_VERSION));
    }
    try {
      androidInstrumentationUtil.instrument(
          params.deviceId(),
          params.deviceSdkVersion(),
          buildInstrumentationSetting(params, optionMap, /* async= */ true),
          testTimeout);
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT) {
        testInfo.resultWithCause().setNonPassing(TestResult.TIMEOUT, e);
      }
      throw e;
    }
    testInfo.log().atInfo().alsoTo(logger).log("Instrumentation started\n");
    // Async run is usually used in battery tests; result determination is deferred to downstream
    // decorators and plugins.
  }

  private void runSync(ExecutionParams params, @Nullable Duration instrumentTimeout)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = params.testInfo();
    JobInfo job = testInfo.jobInfo();
    Properties properties = testInfo.properties();
    ImmutableList<Optional<ImmutableMap<String, String>>> optionMaps =
        params.preparedOptions().optionMaps();

    properties.add(PropertyName.Test.ANDROID_INST_TEST_METHOD_REPEAT_TIMES, String.valueOf(1));
    boolean hasSequentialOptionMaps =
        optionMaps.size() > 1
            || job.params().get(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0") != null;
    boolean iterClearBuild =
        optionMaps.size() > 1
            && !params.buildPackageNames().isEmpty()
            && job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_BUILD);
    boolean iterClearTest =
        optionMaps.size() > 1
            && job.params().isTrue(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_TEST);

    List<String> logFilenames = new ArrayList<>();
    AggregatedOutcome aggregatedOutcome = new AggregatedOutcome();

    for (int optionMapIdx = 0; optionMapIdx < optionMaps.size(); optionMapIdx++) {
      Optional<ImmutableMap<String, String>> maybeOptionMap = optionMaps.get(optionMapIdx);
      if (maybeOptionMap.isEmpty()) {
        properties.add(
            AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_" + optionMapIdx,
            AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
        continue;
      }

      if (hasSequentialOptionMaps) {
        runBeforeIterationShellCmd(testInfo, params.deviceId(), optionMapIdx);
      }

      IterationExecution execution =
          executeIteration(params, maybeOptionMap.get(), instrumentTimeout);
      writeInstrumentationLog(
          testInfo, execution.output(), hasSequentialOptionMaps, optionMapIdx, logFilenames);
      if (execution.testSuiteResult() != null) {
        writeStructuredResults(testInfo, execution.testSuiteResult(), execution.startTime());
      }

      IterationEvaluation evaluation =
          evaluateIterationResult(testInfo, params.deviceId(), execution);
      recordIterationOutcome(testInfo, params.deviceId(), evaluation, execution, aggregatedOutcome);

      if (hasSequentialOptionMaps) {
        runAfterIterationCleanup(
            params, optionMapIdx, evaluation.result(), iterClearBuild, iterClearTest);
      }
    }

    properties.add(
        PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_LOG_FILENAMES,
        String.join(";", logFilenames));
    aggregatedOutcome.applyTo(testInfo);
  }

  private void runBeforeIterationShellCmd(TestInfo testInfo, String deviceId, int optionMapIdx)
      throws MobileHarnessException, InterruptedException {
    String beforeIterShellCmd =
        testInfo
            .jobInfo()
            .params()
            .get(
                AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION
                    + "_"
                    + optionMapIdx);
    if (beforeIterShellCmd == null) {
      return;
    }
    try {
      String beforeTestOutput =
          adb.runShell(deviceId, beforeIterShellCmd, /* timeout= */ null, /* lineCallback= */ null);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Run [adb shell %s]:%n%s%n", beforeIterShellCmd, beforeTestOutput);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR,
          String.format("Failed to execute ADB command before test: %s", beforeIterShellCmd),
          e);
    }
  }

  private IterationExecution executeIteration(
      ExecutionParams params, Map<String, String> optionMap, @Nullable Duration instrumentTimeout)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = params.testInfo();
    String testTarget = params.preparedOptions().testTarget();
    RawResultCollector rawResultCollector =
        params.showRawResults() ? new RawResultCollector(clock) : null;

    Duration testTimeout = computeTestTimeout(testInfo, instrumentTimeout);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "%nStart instrumentation: %n" + " - test target: %s%n - timeout: %d ms%n - options: %s",
            testTarget, testTimeout.toMillis(), StrUtil.DEFAULT_MAP_JOINER.join(optionMap));

    Instant startTime = clock.instant();
    testInfo
        .properties()
        .add(ANDROID_INSTRUMENTATION_TEST_START_EPOCH_MS, Long.toString(startTime.toEpochMilli()));

    String output;
    MobileHarnessException mhException = null;
    try {
      output =
          androidInstrumentationUtil.instrument(
              params.deviceId(),
              params.deviceSdkVersion(),
              buildInstrumentationSetting(params, optionMap, /* async= */ false),
              testTimeout,
              rawResultCollector == null ? null : rawResultCollector::newLineCallback);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .withCause(e)
          .log("MobileHarnessException captured during instrumentation test");
      mhException = e;
      output = e.getMessage();
    }

    TestSuiteResult testSuiteResult =
        rawResultCollector == null ? null : rawResultCollector.finish();
    testInfo
        .properties()
        .add(
            ANDROID_INSTRUMENTATION_TEST_END_EPOCH_MS,
            Long.toString(clock.instant().toEpochMilli()));
    testInfo.log().atInfo().alsoTo(logger).log("Finish instrumentation: %s", output);
    return new IterationExecution(output, mhException, startTime, testSuiteResult);
  }

  private void writeInstrumentationLog(
      TestInfo testInfo,
      String output,
      boolean hasSequentialOptionMaps,
      int optionMapIdx,
      List<String> logFilenames) {
    String instrumentLogFileName =
        "instrument" + (hasSequentialOptionMaps ? "_" + optionMapIdx : "") + ".log";
    try {
      fileUtil.writeToFile(PathUtil.join(testInfo.getGenFileDir(), instrumentLogFileName), output);
      logFilenames.add(instrumentLogFileName);
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_FILE_ERROR, e.getMessage()),
              logger);
    }
  }

  private void writeStructuredResults(
      TestInfo testInfo, TestSuiteResult testSuiteResult, Instant startTime)
      throws MobileHarnessException {
    fileUtil.writeToFile(
        PathUtil.join(testInfo.getGenFileDir(), "instrument_test_result.textproto"),
        testSuiteResult.toString());
    fileUtil.writeToFile(
        PathUtil.join(testInfo.getGenFileDir(), "instrument_test_result.pb"),
        testSuiteResult.toByteArray());
    generateJunitXml(testSuiteResult, testInfo, startTime);
  }

  private static void generateJunitXml(
      TestSuiteResult testSuiteResult, TestInfo testInfo, Instant instrumentationStartTime)
      throws MobileHarnessException {
    String testResultFilePath = PathUtil.join(testInfo.getGenFileDir(), "junit.xml");
    var testResult =
        TestSuiteResultConverter.toJunitTestResult(
            testSuiteResult, TimeUtils.toProtoTimestamp(instrumentationStartTime));
    try (OutputStream outputStream = new FileOutputStream(testResultFilePath)) {
      XmlWriter writer = new XmlWriter(outputStream);
      AntXmlResultWriter resultWriter = new AntXmlResultWriter(/* optionalTestSuitesNode= */ false);
      resultWriter.writeTestSuites(writer, testResult);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_JUNIT_XML_ERROR,
          String.format(
              "Failed to write instrumentation test result to the XML file: %s",
              testResultFilePath),
          e);
    }
  }

  private IterationEvaluation evaluateIterationResult(
      TestInfo testInfo, String deviceId, IterationExecution execution)
      throws MobileHarnessException, InterruptedException {
    IterationEvaluation evaluation = parseIterationOutput(testInfo, deviceId, execution);
    TestSuiteResult suiteResult = execution.testSuiteResult();
    if (!testInfo
            .jobInfo()
            .params()
            .getBool(AndroidInstrumentationDriverSpec.PARAM_FAIL_ON_ALL_SKIPPED, false)
        || evaluation.result() != TestResult.PASS
        || suiteResult == null
        || !suiteResult.getTestResultList().stream()
            .allMatch(testResult -> testResult.getTestStatus() == TestStatus.IGNORED)) {
      return evaluation;
    }
    logger.atInfo().log("All tests were ignored or assumption failures, assigning FAIL result.");
    if (suiteResult.getTestResultList().isEmpty()) {
      // No test cases ran at all, e.g., an empty shard of uniform sharding. Retrying won't
      // change the result, so halt further retries. This is consistent with the legacy FTL
      // behavior of not rerunning steps that have zero test cases.
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No test cases ran. Halt retries of this test since retry won't help.");
      testInfo.properties().add(PropertyName.Test.HALT_RETRY, "true");
    }
    return new IterationEvaluation(
        TestResult.FAIL,
        evaluation.errorMessage() + "All tests were ignored or assumption failures.");
  }

  private IterationEvaluation parseIterationOutput(
      TestInfo testInfo, String deviceId, IterationExecution execution)
      throws MobileHarnessException, InterruptedException {
    JobInfo job = testInfo.jobInfo();
    if (job.params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_USE_ANDROIDINSTRUMENTATION_PARSER, true)) {
      StringBuilder errorMsg = new StringBuilder();
      TestResult result =
          new AndroidInstrumentationParser()
              .parseOutput(
                  execution.output(),
                  errorMsg,
                  execution.mhException(),
                  job.params()
                      .getBool(AndroidInstrumentationDriverSpec.PARAM_FAIL_IF_NO_TESTS_RAN, false));
      return new IterationEvaluation(result, errorMsg.toString());
    }
    String filePathOnDevice =
        job.params().get(AndroidInstrumentationDriverSpec.PARAM_GTEST_XML_FILE_ON_DEVICE);
    if (filePathOnDevice == null) {
      // TODO: Move this check to AndroidInstrumentationJobValidator class.
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_GTEST_XML_FILE_ON_DEVICE_NOT_SET,
          "Param gtest_xml_file_on_device is not set."
              + " This is required when using gtest XML parser.");
    }
    AndroidInstrumentationUtil.GtestResult gtestResult =
        androidInstrumentationUtil.processGtestResult(
            deviceId, testInfo, filePathOnDevice, execution.mhException());
    return new IterationEvaluation(gtestResult.testResult(), gtestResult.errorMessage().orElse(""));
  }

  private void recordIterationOutcome(
      TestInfo testInfo,
      String deviceId,
      IterationEvaluation evaluation,
      IterationExecution execution,
      AggregatedOutcome aggregatedOutcome)
      throws MobileHarnessException, InterruptedException {
    switch (evaluation.result()) {
      case PASS -> aggregatedOutcome.recordPass();
      case FAIL ->
          aggregatedOutcome.recordFail(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_FAILED,
                  "Instrumentation failures: "
                      + evaluation.errorMessage()
                      + "\nThis usually indicates validation errors of the test app, or "
                      + "bugs of the app under test. You should be able to reproduce it with "
                      + "\"adb shell am instrument ...\" with your local devices, without "
                      + "Mobile Harness.",
                  execution.mhException()));
      case ERROR -> {
        MobileHarnessException cause =
            systemStateManager.isOnline(deviceId)
                ? new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_ERROR,
                    "Instrumentation start/finish unexpectedly: " + evaluation.errorMessage(),
                    execution.mhException())
                : new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_DEVICE_OFFLINE,
                    "Device is offline, instrumentation start/finish unexpectedly: "
                        + evaluation.errorMessage(),
                    execution.mhException());
        aggregatedOutcome.recordError(cause);
      }
      case TIMEOUT -> {
        MobileHarnessException timeoutCause =
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_TIMEOUT,
                String.format(
                    "Instrumentation timeout [please try to increase test_timeout_sec"
                        + ", or instrument_timeout_sec if it has been set.]:%n%s",
                    evaluation.errorMessage()),
                execution.mhException());
        testInfo.resultWithCause().setNonPassing(TestResult.TIMEOUT, timeoutCause);
        throw timeoutCause;
      }
      default -> {
        MobileHarnessException errorCause =
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_RESULT_NOT_FOUND,
                "Unexpected Test Result Found",
                execution.mhException());
        testInfo.resultWithCause().setNonPassing(TestResult.ERROR, errorCause);
        throw errorCause;
      }
    }
  }

  private void runAfterIterationCleanup(
      ExecutionParams params,
      int optionMapIdx,
      TestResult result,
      boolean iterClearBuild,
      boolean iterClearTest)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = params.testInfo();
    String deviceId = params.deviceId();
    String afterTestShellCmd =
        testInfo
            .jobInfo()
            .params()
            .get(
                AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_AFTER_INSTRUMENTATION
                    + "_"
                    + optionMapIdx);
    if (afterTestShellCmd != null) {
      try {
        String afterTestOutput =
            adb.runShell(
                deviceId, afterTestShellCmd, /* timeout= */ null, /* lineCallback= */ null);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Run [adb shell %s]:%n%s%n", afterTestShellCmd, afterTestOutput);
      } catch (MobileHarnessException e) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR,
                    String.format(
                        "Failed to execute ADB command after test: %s", afterTestShellCmd),
                    e),
                logger);
      }
    }

    testInfo
        .properties()
        .add(AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_" + optionMapIdx, result.name());

    try {
      if (iterClearBuild) {
        for (String buildPackageName : params.buildPackageNames()) {
          androidPackageManagerUtil.clearPackage(deviceId, buildPackageName);
          testInfo.log().atInfo().alsoTo(logger).log("%nClear build package %s", buildPackageName);
        }
      }
      if (iterClearTest) {
        androidPackageManagerUtil.clearPackage(deviceId, params.testPackageName());
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("%nClear test package %s", params.testPackageName());
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_CLEAR_PACKAGE_ERROR,
          "Failed to clear package with exception raised",
          e);
    }
  }

  @Nullable
  private static Duration getInstrumentTimeout(JobInfo job) {
    String timeoutSec =
        job.params().get(AndroidInstrumentationDriverSpec.PARAM_INSTRUMENT_TIMEOUT_SEC);
    return timeoutSec == null ? null : Duration.ofSeconds(Integer.parseInt(timeoutSec));
  }

  private static Duration computeTestTimeout(
      TestInfo testInfo, @Nullable Duration instrumentTimeout) throws MobileHarnessException {
    Duration remainingTime = testInfo.timer().remainingTimeJava();
    return instrumentTimeout == null ? remainingTime : min(remainingTime, instrumentTimeout);
  }

  private static AndroidInstrumentationSetting buildInstrumentationSetting(
      ExecutionParams params, Map<String, String> optionMap, boolean async) {
    JobInfo job = params.testInfo().jobInfo();
    return AndroidInstrumentationSetting.create(
        params.testPackageName(),
        params.runnerName(),
        params.preparedOptions().testTarget(),
        optionMap,
        async,
        params.showRawResults(),
        job.params().getBool(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, false),
        job.params()
            .isTrue(
                AndroidInstrumentationDriverSpec
                    .PARAM_DISABLE_ISOLATED_STORAGE_FOR_INSTRUMENTATION),
        job.params().getBool(AndroidInstrumentationDriverSpec.PARAM_USE_TEST_STORAGE_SERVICE, true),
        job.params().getBool(AndroidInstrumentationDriverSpec.PARAM_ENABLE_COVERAGE, false),
        job.params().getBool(AndroidInstrumentationDriverSpec.PARAM_USE_ORCHESTRATOR, false));
  }

  /** Parameters for executing a prepared instrumentation test run on a device. */
  record ExecutionParams(
      TestInfo testInfo,
      String deviceId,
      int deviceSdkVersion,
      String testPackageName,
      String runnerName,
      ImmutableList<String> buildPackageNames,
      @Nullable String externalStoragePath,
      PreparedInstrumentationOptions preparedOptions,
      boolean showRawResults) {
    static Builder builder() {
      return new AutoBuilder_AndroidInstrumentationRunner_ExecutionParams_Builder();
    }

    @AutoBuilder
    abstract static class Builder {
      abstract Builder setTestInfo(TestInfo testInfo);

      abstract Builder setDeviceId(String deviceId);

      abstract Builder setDeviceSdkVersion(int deviceSdkVersion);

      abstract Builder setTestPackageName(String testPackageName);

      abstract Builder setRunnerName(String runnerName);

      abstract Builder setBuildPackageNames(List<String> buildPackageNames);

      abstract Builder setExternalStoragePath(@Nullable String externalStoragePath);

      abstract Builder setPreparedOptions(PreparedInstrumentationOptions preparedOptions);

      abstract Builder setShowRawResults(boolean showRawResults);

      abstract ExecutionParams build();
    }
  }

  private static final class RawResultCollector {
    private final TestSuiteResult.Builder builder = TestSuiteResult.newBuilder();
    private final AmInstrumentationParser parser;

    RawResultCollector(Clock clock) {
      this.parser =
          new AmInstrumentationParser(
              ImmutableSet.of(new AmInstrumentationResultBuilder(builder)),
              () -> TestTimeTrackerKt.TestTimeTracker(clock::instant));
    }

    LineCallback newLineCallback() {
      builder.clear();
      return line -> {
        parser.parse(line);
        return LineCallback.Response.empty();
      };
    }

    TestSuiteResult finish() {
      parser.done();
      return builder.build();
    }
  }

  private record IterationExecution(
      String output,
      @Nullable MobileHarnessException mhException,
      Instant startTime,
      @Nullable TestSuiteResult testSuiteResult) {}

  private record IterationEvaluation(TestResult result, String errorMessage) {}

  private static final class AggregatedOutcome {
    private boolean hasPass;
    @Nullable private MobileHarnessException errorCause;
    @Nullable private MobileHarnessException failCause;

    void recordPass() {
      hasPass = true;
    }

    void recordFail(MobileHarnessException cause) {
      failCause = cause;
    }

    void recordError(MobileHarnessException cause) {
      errorCause = cause;
    }

    void applyTo(TestInfo testInfo) {
      MobileHarnessException error = errorCause;
      MobileHarnessException fail = failCause;
      if (error != null) {
        testInfo.resultWithCause().setNonPassing(TestResult.ERROR, error);
      } else if (fail != null) {
        testInfo.resultWithCause().setNonPassing(TestResult.FAIL, fail);
      } else if (hasPass) {
        testInfo.resultWithCause().setPass();
      }
    }
  }
}
