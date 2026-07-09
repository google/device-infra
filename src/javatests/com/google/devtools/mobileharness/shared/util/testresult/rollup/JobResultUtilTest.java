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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteMetaData;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class JobResultUtilTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void computeJobLevelTestResult_success() throws Exception {
    // Shard 0 - Run 1 (Fail)
    TestInfo runShard0Test1 =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testFoo", TestStatus.FAILED, Duration.ofSeconds(5))));

    // Shard 0 - Run 2 (Pass)
    TestInfo runShard0Test2 =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testFoo", TestStatus.PASSED, Duration.ofSeconds(6))));

    // Shard 1 - Run 1 (Pass)
    TestInfo runShard1Test1 =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testBar", TestStatus.PASSED, Duration.ofSeconds(2))));

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values())
        .thenReturn(ImmutableList.of(runShard0Test1, runShard0Test2, runShard1Test1));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    // Outcome should be FLAKY because Shard 0 was rerun (1st failed, 2nd passed)
    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.FLAKY);

    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest",
                "testFoo",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FLAKY,
                Duration.ofSeconds(6)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testBar",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 102L));

    // Suite overview
    assertThat(result.testSuiteOverviews()).hasSize(1);
    assertThat(result.testSuiteOverviews().get(0).name()).isEqualTo("com.example.Suite");
    assertThat(result.testSuiteOverviews().get(0).totalCount()).isEqualTo(2);
    assertThat(result.testSuiteOverviews().get(0).flakyCount()).isEqualTo(1);
  }

  @Test
  public void computeJobRunResult_twoShardsAllPass() throws Exception {
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest1", "testCase1", TestStatus.PASSED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest1", "testCase2", TestStatus.PASSED, Duration.ofSeconds(6))));

    TestInfo testB =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest2", "testCase3", TestStatus.PASSED, Duration.ofSeconds(7)),
                buildTestCaseInfo(
                    "MyTest2", "testCase4", TestStatus.PASSED, Duration.ofSeconds(8))));

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.of(testA, testB));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.SUCCESS);
    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest1",
                "testCase1",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 105L),
            buildExpectedTestCase(
                "com.example.MyTest1",
                "testCase2",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 115L,
                /* endTimeSeconds= */ 121L),
            buildExpectedTestCase(
                "com.example.MyTest2",
                "testCase3",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 107L),
            buildExpectedTestCase(
                "com.example.MyTest2",
                "testCase4",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 117L,
                /* endTimeSeconds= */ 125L));
  }

  @Test
  public void computeJobRunResult_flakyAndPassedShards() throws Exception {
    // A Job has 4 tests:
    // - testA and testB for test_shard_0:
    //   - testA has failed test result (flakyAttemptIndex 0, errorAttemptIndex 0). Has
    // testCase1(fail), testCase2(pass).
    //   - testB has pass test result (flakyAttemptIndex 1, errorAttemptIndex 0). Has
    // testCase1(pass), testCase2(pass).
    // - testX and testY for test_shard_1:
    //   - testX has error test result (flakyAttemptIndex 0, errorAttemptIndex 0). No test cases
    // ran.
    //   - testY has pass test result (flakyAttemptIndex 0, errorAttemptIndex 1). Has
    // testCase3(pass), testCase4(pass).
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase1", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.PASSED, Duration.ofSeconds(6))));

    TestInfo testB =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase1", TestStatus.PASSED, Duration.ofSeconds(4)),
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.PASSED, Duration.ofSeconds(5))));

    TestInfo testX =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.ERROR,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            /* testSuiteName= */ null,
            TestStatus.ERROR,
            ImmutableList.of());

    TestInfo testY =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 1,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase3", TestStatus.PASSED, Duration.ofSeconds(10)),
                buildTestCaseInfo(
                    "MyTest", "testCase4", TestStatus.PASSED, Duration.ofSeconds(12))));

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values())
        .thenReturn(ImmutableList.of(testA, testB, testX, testY));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.FLAKY);
    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase1",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FLAKY,
                Duration.ofSeconds(5)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase2",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                Duration.ofSeconds(6)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase3",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 110L),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase4",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 120L,
                /* endTimeSeconds= */ 132L));
  }

  @Test
  public void computeJobRunResult_flakyAndFailedShards() throws Exception {
    // Shard 1 attempts
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase1", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.FAILED, Duration.ofSeconds(6))));

    TestInfo testB =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase1", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.FAILED, Duration.ofSeconds(6))));

    TestInfo testC =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 2,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase1", TestStatus.PASSED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.PASSED, Duration.ofSeconds(6))));

    // Shard 2 attempts
    TestInfo testX =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase3", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase4", TestStatus.FAILED, Duration.ofSeconds(6))));

    TestInfo testY =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase3", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase4", TestStatus.FAILED, Duration.ofSeconds(6))));

    TestInfo testZ =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 2,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo("MyTest", "testCase3", TestStatus.FAILED, Duration.ofSeconds(5)),
                buildTestCaseInfo(
                    "MyTest", "testCase4", TestStatus.FAILED, Duration.ofSeconds(6))));

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values())
        .thenReturn(ImmutableList.of(testA, testB, testC, testX, testY, testZ));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.FAILURE);
    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase1",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FLAKY,
                Duration.ofSeconds(5)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase2",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FLAKY,
                Duration.ofSeconds(6)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase3",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FAILED,
                Duration.ofSeconds(5)),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase4",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FAILED,
                Duration.ofSeconds(6)));
  }

  @Test
  public void computeJobRunResult_twoShardsBothFail() throws Exception {
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo(
                    "MyTest", "testCase1", TestStatus.FAILED, Duration.ofSeconds(5))));

    TestInfo testX =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.FAILED,
            ImmutableList.of(
                buildTestCaseInfo(
                    "MyTest", "testCase2", TestStatus.FAILED, Duration.ofSeconds(5))));

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.of(testA, testX));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.FAILURE);
    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase1",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FAILED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 105L),
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase2",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .FAILED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 105L));
  }

  @Test
  public void computeJobRunResult_noValidTestRuns() throws Exception {
    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.of());

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
    assertThat(result.testCases()).isEmpty();
    assertThat(result.testSuiteOverviews()).isEmpty();
  }

  @Test
  public void computeJobRunResult_shardWithOnlyErrorResultIsNotSkipped() throws Exception {
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            "com.example.Suite",
            TestStatus.PASSED,
            ImmutableList.of(
                buildTestCaseInfo(
                    "MyTest", "testCase1", TestStatus.PASSED, Duration.ofSeconds(5))));

    TestInfo testX =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.ERROR,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            /* testSuiteName= */ null,
            TestStatus.ERROR,
            ImmutableList.of());

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.of(testA, testX));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    // The shard with only error result should be kept as the last run of that shard, rolling up to
    // INCONCLUSIVE.
    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
    assertThat(result.testCases())
        .containsExactly(
            buildExpectedTestCase(
                "com.example.MyTest",
                "testCase1",
                "com.example.Suite",
                com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
                    .PASSED,
                /* startTimeSeconds= */ 100L,
                /* endTimeSeconds= */ 105L));
  }

  @Test
  public void computeJobRunResult_fallbackToSimpleTestResult() throws Exception {
    // If no pb file is found, it will fall back to buildFallbackTestResult.
    // Let's create two shards, one passes, one fails, both without pb files.
    TestInfo testA =
        mockTestRun(
            "test_shard_0",
            "0",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.PASS,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            /* testSuiteName= */ null,
            TestStatus.PASSED,
            ImmutableList.of());

    TestInfo testB =
        mockTestRun(
            "test_shard_1",
            "1",
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            /* testSuiteName= */ null,
            TestStatus.FAILED,
            ImmutableList.of());

    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.of(testA, testB));

    com.google.devtools.mobileharness.shared.util.testresult.rollup.TestResult result =
        JobResultUtil.computeJobRunResult(jobInfo);

    // Shard 1 outcome: SUCCESS (derived from PASS fallback)
    // Shard 2 outcome: FAILURE (derived from FAIL fallback)
    // Combined job outcome: FAILURE
    assertThat(result.outcome().summary()).isEqualTo(OutcomeSummary.FAILURE);
    assertThat(result.testCases()).isEmpty();
  }

  private static final class TestCaseInfo {
    final String testClass;
    final String testMethod;
    final TestStatus status;
    final Duration duration;

    TestCaseInfo(String testClass, String testMethod, TestStatus status, Duration duration) {
      this.testClass = testClass;
      this.testMethod = testMethod;
      this.status = status;
      this.duration = duration;
    }
  }

  private TestInfo mockTestRun(
      String shardName,
      String shardIndex,
      com.google.devtools.mobileharness.api.model.proto.Test.TestResult mockResult,
      int flakyAttemptIndex,
      int errorAttemptIndex,
      String testSuiteName,
      TestStatus suiteStatus,
      ImmutableList<TestCaseInfo> cases)
      throws Exception {
    TestInfo testInfo = mock(TestInfo.class, Mockito.RETURNS_DEEP_STUBS);
    File genDir = tempFolder.newFolder();
    when(testInfo.getGenFileDir()).thenReturn(genDir.getAbsolutePath());

    Properties properties = new Properties(new Timing());
    properties.add("flaky_attempt_index", String.valueOf(flakyAttemptIndex));
    properties.add("error_attempt_index", String.valueOf(errorAttemptIndex));
    if (shardIndex != null) {
      properties.add(
          com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.SHARD_INDEX,
          shardIndex);
    }
    when(testInfo.properties()).thenReturn(properties);

    when(testInfo.locator().getName()).thenReturn(shardName);
    when(testInfo.resultWithCause().get().type()).thenReturn(mockResult);

    if (testSuiteName != null) {
      TestSuiteResult testSuiteResult = buildTestSuiteResult(testSuiteName, suiteStatus, cases);
      writeProto(genDir, "instrument_test_result.pb", testSuiteResult);
    }

    return testInfo;
  }

  private static TestCaseInfo buildTestCaseInfo(
      String testClass, String testMethod, TestStatus status, Duration duration) {
    return new TestCaseInfo(testClass, testMethod, status, duration);
  }

  private TestSuiteResult buildTestSuiteResult(
      String suiteName, TestStatus suiteStatus, ImmutableList<TestCaseInfo> cases) {
    TestSuiteResult.Builder builder =
        TestSuiteResult.newBuilder()
            .setTestSuiteMetaData(TestSuiteMetaData.newBuilder().setTestSuiteName(suiteName))
            .setTestStatus(suiteStatus);
    long timeOffset = 100L;
    for (TestCaseInfo testCaseInfo : cases) {
      builder.addTestResult(
          TestResult.newBuilder()
              .setTestCase(
                  TestCase.newBuilder()
                      .setTestClass(testCaseInfo.testClass)
                      .setTestPackage("com.example")
                      .setTestMethod(testCaseInfo.testMethod)
                      .setStartTime(Timestamps.fromSeconds(timeOffset))
                      .setEndTime(
                          Timestamps.fromSeconds(timeOffset + testCaseInfo.duration.toSeconds())))
              .setTestStatus(testCaseInfo.status));
      timeOffset += testCaseInfo.duration.plusSeconds(10).toSeconds();
    }
    return builder.build();
  }

  private void writeProto(File directory, String filename, TestSuiteResult proto) throws Exception {
    File file = new File(directory, filename);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      proto.writeTo(fos);
    }
  }

  private static com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase
      buildExpectedTestCase(
          String className,
          String methodName,
          String suiteName,
          com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
              status,
          Duration elapsedTime) {
    return com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.builder()
        .setTestCaseReference(
            TestCaseReference.builder()
                .setClassName(className)
                .setName(methodName)
                .setTestSuiteName(suiteName)
                .build())
        .setStatus(status)
        .setElapsedTime(elapsedTime)
        .build();
  }

  private static com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase
      buildExpectedTestCase(
          String className,
          String methodName,
          String suiteName,
          com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus
              status,
          long startTimeSeconds,
          long endTimeSeconds) {
    return com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.builder()
        .setTestCaseReference(
            TestCaseReference.builder()
                .setClassName(className)
                .setName(methodName)
                .setTestSuiteName(suiteName)
                .build())
        .setStatus(status)
        .setStartTime(Instant.ofEpochSecond(startTimeSeconds))
        .setEndTime(Instant.ofEpochSecond(endTimeSeconds))
        .setElapsedTime(Duration.ofSeconds(endTimeSeconds - startTimeSeconds))
        .build();
  }
}
