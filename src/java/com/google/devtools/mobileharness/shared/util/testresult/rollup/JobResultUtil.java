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
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.RetryTestsGrouper;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.RetryTestsGrouper.GroupedTests;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.RetryTestsGrouper.ShardTestRuns;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;

/** Utility for computing job-level rolled up test results. */
public final class JobResultUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private JobResultUtil() {}

  /**
   * Computes the job-level test result by grouping test runs by shard, rolling them up vertically
   * to get a rollup {@link TestResult} for each shard, and then rolling up shards horizontally.
   */
  public static TestResult computeJobRunResult(JobInfo jobInfo) {
    GroupedTests groupedTests = RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(jobInfo);
    if (groupedTests.testsByShardIndex().isEmpty()) {
      logger.atInfo().log("No valid test runs found for job %s.", jobInfo.locator().getId());
      return TestResult.create(
          /* testCases= */ ImmutableList.of(),
          /* testSuiteOverviews= */ ImmutableList.of(),
          Outcome.create(OutcomeSummary.INCONCLUSIVE),
          State.COMPLETE);
    }

    ImmutableList.Builder<TestResult> shardResultsOfJobRunBuilder = ImmutableList.builder();
    for (ShardTestRuns attemptsForOneShard : groupedTests.testsByShardIndex().values()) {
      List<TestResult> rerunShardTestResults = new ArrayList<>();
      for (TestInfo attempt : attemptsForOneShard.testsByFlakyAttemptIndex().values()) {
        rerunShardTestResults.add(loadTestResult(attempt));
      }
      TestResult rollupShardResult = rollupToShardTestResult(rerunShardTestResults);
      shardResultsOfJobRunBuilder.add(rollupShardResult);
    }

    ImmutableList<TestResult> shardResults = shardResultsOfJobRunBuilder.build();
    return rollupShardResultsToJobResult(shardResults);
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

  private static TestResult loadTestResult(TestInfo testInfo) {
    if (!isPassOrFail(testInfo)) {
      return TestResult.create(
          /* testCases= */ ImmutableList.of(),
          /* testSuiteOverviews= */ ImmutableList.of(),
          Outcome.create(OutcomeSummary.INCONCLUSIVE),
          State.COMPLETE);
    }

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
  private static TestResult rollupToShardTestResult(List<TestResult> testResults) {
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
        TestSuiteOverview.rollUp(
            testResults.stream()
                .flatMap(r -> r.testSuiteOverviews().stream())
                .collect(toImmutableList()));

    State shardState =
        State.rollUp(testResults.stream().map(TestResult::state).collect(toImmutableList()));

    Outcome testCaseResultsToOutcome = TestCase.convertToOutcome(rerunTestCases);
    // If the shard had reruns, that means the first step had failed, so the best possible outcome
    // should be flaky. So roll up shard outcome with flaky outcome (outcome computer uses worst
    // outcome first ordering) so that the best possible outcome is flaky.
    Outcome shardOutcome =
        testResults.size() > 1
            ? Outcome.rollUp(
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
      TestCase.rollUp(groupedCases.get(key)).ifPresent(rolledUpCasesBuilder::add);
    }
    return rolledUpCasesBuilder.build();
  }

  private static TestResult rollupShardResultsToJobResult(
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
        TestSuiteOverview.rollUp(
            shardTestResults.stream()
                .flatMap(r -> r.testSuiteOverviews().stream())
                .collect(toImmutableList()));

    // TestResults at shard level don't have duplicate test cases (unlike reruns at test execution
    // level), so we can always do outcome based rollup here.
    Outcome jobRunOutcome =
        Outcome.rollUp(
            shardTestResults.stream().map(TestResult::outcome).collect(toImmutableList()));

    State jobRunState =
        State.rollUp(shardTestResults.stream().map(TestResult::state).collect(toImmutableList()));

    return TestResult.create(jobRunTestCases, jobRunTestSuiteOverviews, jobRunOutcome, jobRunState);
  }
}
