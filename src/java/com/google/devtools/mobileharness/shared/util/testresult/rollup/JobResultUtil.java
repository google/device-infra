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

package com.google.devtools.mobileharness.shared.util.testresult.rollup;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.FlakyTestRetryConstants;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.testresult.Outcome;
import com.google.devtools.mobileharness.shared.util.testresult.OutcomeSummary;
import com.google.devtools.mobileharness.shared.util.testresult.State;
import com.google.devtools.mobileharness.shared.util.testresult.TestCase;
import com.google.devtools.mobileharness.shared.util.testresult.TestCaseReference;
import com.google.devtools.mobileharness.shared.util.testresult.TestSuiteOverview;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Utility for computing job-level rolled up test results. */
public final class JobResultUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private JobResultUtil() {}

  /**
   * Computes the job-level test result by grouping test runs by shard, rolling them up vertically
   * to get a rollup {@link TestResult} for each shard, and then rolling up shards horizontally.
   */
  public static JobRunResult computeJobRunResult(JobInfo jobInfo) {
    ImmutableListMultimap<String, TestInfo> groupedTestRunsByShard =
        groupTestRunsByShard(ImmutableList.copyOf(jobInfo.tests().getAll().values()));
    if (groupedTestRunsByShard.isEmpty()) {
      logger.atInfo().log("No valid test runs found for job %s.", jobInfo.locator().getId());
      return JobRunResult.create(
          TestResult.create(
              /* testCases= */ ImmutableList.of(),
              /* testSuiteOverviews= */ ImmutableList.of(),
              Outcome.create(OutcomeSummary.INCONCLUSIVE),
              State.COMPLETE));
    }

    ImmutableList.Builder<TestResult> shardResultsOfJobRunBuilder = ImmutableList.builder();
    for (String name : groupedTestRunsByShard.keySet()) {
      ImmutableList<TestInfo> attemptsForOneShard = groupedTestRunsByShard.get(name);
      List<TestResult> rerunShardTestResults = new ArrayList<>();
      for (TestInfo attempt : attemptsForOneShard) {
        rerunShardTestResults.add(loadTestResult(attempt));
      }
      TestResult rollupShardResult = rollupToShardTestResult(rerunShardTestResults);
      shardResultsOfJobRunBuilder.add(rollupShardResult);
    }

    ImmutableList<TestResult> shardResults = shardResultsOfJobRunBuilder.build();
    TestResult jobTestResult = rollupShardResultsToJobResult(shardResults);

    return JobRunResult.create(jobTestResult);
  }

  /**
   * Groups test runs by shard.
   *
   * <p>Each entry in the returned map represents all test runs(including original runs and reruns,
   * only consider pass or fail runs) for a particular shard.
   */
  private static ImmutableListMultimap<String, TestInfo> groupTestRunsByShard(
      List<TestInfo> testInfoList) {
    // Use test name as key to group tests by shard, i.e., tests with same name are considered for
    // the same shard. We may need shard index in the future to identify tests for the same shard,
    // and update TestRetryHandler accordingly.
    ImmutableListMultimap<String, TestInfo> testNameToTestInfoList =
        Multimaps.index(testInfoList, t -> t.locator().getName());

    ImmutableListMultimap.Builder<String, TestInfo> testRunsByShard =
        ImmutableListMultimap.builder();

    for (String testName : testNameToTestInfoList.keySet()) {
      Map<Integer, ImmutableList<TestInfo>> flakyAttemptIndexToTestInfoList =
          testNameToTestInfoList.get(testName).stream()
              .collect(
                  Collectors.groupingBy(
                      t ->
                          getAttemptIndexProperty(
                              t, FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX),
                      TreeMap::new,
                      toImmutableList()));

      for (ImmutableList<TestInfo> testsFromSameFlakyAttempt :
          flakyAttemptIndexToTestInfoList.values()) {
        testsFromSameFlakyAttempt.stream()
            .filter(JobResultUtil::isPassOrFail)
            .max(
                Comparator.comparingInt(
                    t ->
                        getAttemptIndexProperty(
                            t, FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX)))
            .ifPresent(passOrFailTest -> testRunsByShard.put(testName, passOrFailTest));
      }
    }

    return testRunsByShard.build();
  }

  private static boolean isPassOrFail(TestInfo testInfo) {
    if (testInfo.resultWithCause() == null || testInfo.resultWithCause().get() == null) {
      return false;
    }
    com.google.devtools.mobileharness.api.model.proto.Test.TestResult type =
        testInfo.resultWithCause().get().type();
    return type == com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS
        || type == com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL;
  }

  private static int getAttemptIndexProperty(TestInfo testInfo, String propertyName) {
    String value = testInfo.properties().get(propertyName);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static TestResult loadTestResult(TestInfo testInfo) {
    LocalFileUtil localFileUtil = new LocalFileUtil();
    try {
      String genFileDir = testInfo.getGenFileDir();
      String pbPath = PathUtil.join(genFileDir, "instrument_test_result.pb");
      if (localFileUtil.isFileExist(pbPath)) {
        byte[] bytes = localFileUtil.readBinaryFile(pbPath);
        TestSuiteResult testSuiteResult =
            TestSuiteResult.parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry());
        return AndroidInstrumentationTestSuiteResultConverter.toTestResult(testSuiteResult);
      } else {
        logger.atInfo().log(
            "No instrument_test_result.pb found for test %s.", testInfo.locator().getId());
      }
    } catch (InvalidProtocolBufferException | MobileHarnessException e) {
      // Fall back to simple TestResult
      logger.atWarning().withCause(e).log(
          "Failed to load test result for test %s, fallback to simple TestResult.",
          testInfo.locator().getId());
    }
    return buildFallbackTestResult(testInfo);
  }

  private static TestResult buildFallbackTestResult(TestInfo testInfo) {
    ResultTypeWithCause resultWithCause = testInfo.resultWithCause().get();
    com.google.devtools.mobileharness.api.model.proto.Test.TestResult testResultType =
        resultWithCause.type();

    OutcomeSummary outcomeSummary =
        switch (testResultType) {
          case PASS -> OutcomeSummary.SUCCESS;
          case FAIL -> OutcomeSummary.FAILURE;
          case SKIP -> OutcomeSummary.SKIPPED;
          case ERROR, TIMEOUT, ABORT, UNKNOWN, UNRECOGNIZED -> OutcomeSummary.INCONCLUSIVE;
        };

    Outcome outcome = Outcome.create(outcomeSummary);
    State state = State.COMPLETE;
    return TestResult.create(
        /* testCases= */ ImmutableList.of(),
        /* testSuiteOverviews= */ ImmutableList.of(),
        outcome,
        state);
  }

  /** Rolls up test results from different runs of the same shard. */
  public static TestResult rollupToShardTestResult(List<TestResult> testResults) {
    if (testResults.isEmpty()) {
      return TestResult.create(
          /* testCases= */ ImmutableList.of(),
          /* testSuiteOverviews= */ ImmutableList.of(),
          Outcome.create(OutcomeSummary.INCONCLUSIVE),
          State.COMPLETE);
    }
    if (testResults.size() == 1) {
      return testResults.get(0);
    }

    ImmutableList<TestCase> rerunTestCases = rollupTestCases(testResults);

    ImmutableList<TestSuiteOverview> rolledUpTestSuiteOverviews =
        TestSuiteOverviewRollupHelper.rollupTestSuiteOverviewsElapsedTime(testResults);

    State shardState =
        StateRollupHelper.rollUpShardStatesFromDifferentRuns(
            testResults.stream().map(TestResult::state).collect(toImmutableList()));

    Outcome testCaseResultsToOutcome =
        TestCaseRollupHelper.convertTestCaseResultsToOutcome(rerunTestCases);
    // If the shard had reruns, that means the first step had failed, so the best possible outcome
    // should be flaky. So roll up shard outcome with flaky outcome (outcome computer uses worst
    // outcome first ordering) so that the best possible outcome is flaky.
    Outcome shardOutcome =
        testResults.size() > 1
            ? OutcomeRollupHelper.rollUpOutcomes(
                ImmutableList.of(testCaseResultsToOutcome, Outcome.create(OutcomeSummary.FLAKY)))
            : testCaseResultsToOutcome;

    return TestResult.create(rerunTestCases, rolledUpTestSuiteOverviews, shardOutcome, shardState);
  }

  /** Groups test cases by the {@link TestCaseReference}, and rolls up each group. */
  private static ImmutableList<TestCase> rollupTestCases(List<TestResult> testResults) {
    ImmutableList<TestCase> allTestCases =
        testResults.stream()
            .flatMap(testResult -> testResult.testCases().stream())
            .collect(toImmutableList());

    ImmutableListMultimap<TestCaseReference, TestCase> groupedCases =
        Multimaps.index(allTestCases, TestCase::testCaseReference);

    ImmutableList.Builder<TestCase> rolledUpCasesBuilder = ImmutableList.builder();
    for (TestCaseReference key : groupedCases.keySet()) {
      TestCaseRollupHelper.rollupTestCasesFromDifferentRuns(groupedCases.get(key))
          .ifPresent(rolledUpCasesBuilder::add);
    }
    return rolledUpCasesBuilder.build();
  }

  public static TestResult rollupShardResultsToJobResult(
      ImmutableList<TestResult> shardTestResults) {
    if (shardTestResults.isEmpty()) {
      return TestResult.create(
          ImmutableList.of(),
          ImmutableList.of(),
          Outcome.create(OutcomeSummary.INCONCLUSIVE),
          State.COMPLETE);
    }

    // Shard level test cases should not have any duplicates since test cases are not shared among
    // shards. So these test cases shouldn't be rolled up but rather be kept as individual test
    // cases. Rerun test cases are rolled up already.
    ImmutableList<TestCase> jobRunTestCases =
        shardTestResults.stream()
            .flatMap(shardTestResult -> shardTestResult.testCases().stream())
            .collect(toImmutableList());

    // Current design is only computing the max of all shards
    ImmutableList<TestSuiteOverview> jobRunTestSuiteOverviews =
        TestSuiteOverviewRollupHelper.rollupTestSuiteOverviewsElapsedTime(shardTestResults);

    Outcome jobRunOutcome =
        OutcomeRollupHelper.rollUpOutcomes(
            shardTestResults.stream().map(TestResult::outcome).collect(toImmutableList()));

    State jobRunState =
        StateRollupHelper.rollUpShardStatesFromDifferentRuns(
            shardTestResults.stream().map(TestResult::state).collect(toImmutableList()));

    return TestResult.create(jobRunTestCases, jobRunTestSuiteOverviews, jobRunOutcome, jobRunState);
  }
}
