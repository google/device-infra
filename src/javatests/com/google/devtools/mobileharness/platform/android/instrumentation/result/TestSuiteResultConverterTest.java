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

package com.google.devtools.mobileharness.platform.android.instrumentation.result;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model.TestResult;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestSuiteResultConverterTest {
  private static final String TEST_RESULTS_DIR =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/android/instrumentation/result/testdata/testsuiteresults");

  private static final Timestamp START_TIME = Timestamps.fromSeconds(1000);

  private static final LocalDateTime EXPECTED_TIMESTAMP =
      Instant.ofEpochSecond(1000).atZone(TestSuiteResultConverter.ZONE_ID).toLocalDateTime();

  private static TestSuiteResult parseTestSuiteResult(Path testSuiteResultFile) throws Exception {
    TestSuiteResult.Builder result = TestSuiteResult.newBuilder();
    TextFormat.getParser().merge(Files.newBufferedReader(testSuiteResultFile, UTF_8), result);
    return result.build();
  }

  @Test
  public void toJunitTestResult_convertTestcases() throws Exception {
    TestSuiteResult testSuiteResult =
        parseTestSuiteResult(Path.of(TEST_RESULTS_DIR).resolve("basic_functionality_test.txt"));
    TestResult testSuites = TestSuiteResultConverter.toJunitTestResult(testSuiteResult, START_TIME);

    assertThat(testSuites.getName()).isEqualTo("All targets");

    TestResult testSuite = testSuites.getChildResults().get(0);
    assertThat(testSuite.getName()).isEqualTo("a-test-suite");
    assertThat(testSuite.getNumTests()).isEqualTo(2);
    assertThat(testSuite.getNumFailures()).isEqualTo(0);
    assertThat(testSuite.getNumSkips()).isEqualTo(0);
    assertThat(testSuite.getTimestamp().get()).isEqualTo(EXPECTED_TIMESTAMP);

    TestResult testcase = testSuite.getChildResults().get(0);
    assertThat(testcase.getClassName())
        .isEqualTo("com.google.android.apps.mtaas.testing.mtaastesting.EnvironmentVariableTest");
    assertThat(testcase.getName()).isEqualTo("testEnvironmentVariablesSet");
    assertThat(testcase.getNumFailures()).isEqualTo(0);
    assertThat(testcase.getNumSkips()).isEqualTo(0);
    assertThat(testcase.getRunTime()).isEqualTo(437);

    TestResult testcase2 = testSuite.getChildResults().get(1);
    assertThat(testcase2.getClassName())
        .isEqualTo("com.google.android.apps.mtaas.testing.mtaastesting.ObbFileTest");
    assertThat(testcase2.getName()).isEqualTo("testObbFilesHaveBeenPushed");
    assertThat(testcase2.getNumFailures()).isEqualTo(0);
    assertThat(testcase2.getNumSkips()).isEqualTo(0);
    assertThat(testcase2.getRunTime()).isEqualTo(212);
  }

  @Test
  public void toJunitTestResult_convertTestcase_withError() throws Exception {
    TestSuiteResult testSuiteResult =
        parseTestSuiteResult(Path.of(TEST_RESULTS_DIR).resolve("appcrash_test.txt"));
    TestResult testSuites = TestSuiteResultConverter.toJunitTestResult(testSuiteResult, START_TIME);

    assertThat(testSuites.getName()).isEqualTo("All targets");

    TestResult testSuite = testSuites.getChildResults().get(0);
    assertThat(testSuite.getName()).isEmpty();
    assertThat(testSuite.getNumTests()).isEqualTo(1);
    assertThat(testSuite.getNumFailures()).isEqualTo(1);
    assertThat(testSuite.getNumSkips()).isEqualTo(0);
    assertThat(testSuite.getTimestamp().get()).isEqualTo(EXPECTED_TIMESTAMP);

    TestResult testcase = testSuite.getChildResults().get(0);
    assertThat(testcase.getClassName())
        .isEqualTo("com.google.android.apps.mtaas.testing.appcrash.CrashActivityTest");
    assertThat(testcase.getName()).isEqualTo("crashesOnClick");
    assertThat(testcase.getNumFailures()).isEqualTo(1);
    assertThat(testcase.getNumSkips()).isEqualTo(0);
    assertThat(testcase.getRunTime()).isEqualTo(538);
    assertThat(testcase.getAllFailureMessages())
        .containsExactly("androidx.test.espresso.PerformException: ...");
  }

  @Test
  public void toJunitTestResult_convertTestcases_withIgnored() throws Exception {
    TestSuiteResult testSuiteResult =
        parseTestSuiteResult(Path.of(TEST_RESULTS_DIR).resolve("testcase_status_test.txt"));
    TestResult testSuites = TestSuiteResultConverter.toJunitTestResult(testSuiteResult, START_TIME);

    assertThat(testSuites.getName()).isEqualTo("All targets");

    TestResult testSuite = testSuites.getChildResults().get(0);
    assertThat(testSuite.getNumTests()).isEqualTo(2);
    assertThat(testSuite.getNumFailures()).isEqualTo(0);
    assertThat(testSuite.getNumSkips()).isEqualTo(2);
    assertThat(testSuite.getTimestamp().get()).isEqualTo(EXPECTED_TIMESTAMP);

    TestResult testcase1 = testSuite.getChildResults().get(0);
    assertThat(testcase1.getClassName())
        .isEqualTo("com.google.android.apps.mtaas.testing.mtaastesting.TestcaseStatusTest");
    assertThat(testcase1.getName()).isEqualTo("testAssumptionFailed");
    assertThat(testcase1.getNumFailures()).isEqualTo(0);
    assertThat(testcase1.getNumSkips()).isEqualTo(1);
    assertThat(testcase1.getRunTime()).isEqualTo(16);

    TestResult testcase2 = testSuite.getChildResults().get(1);
    assertThat(testcase2.getClassName())
        .isEqualTo("com.google.android.apps.mtaas.testing.mtaastesting.TestcaseStatusTest");
    assertThat(testcase2.getName()).isEqualTo("testIgnored");
    assertThat(testcase2.getNumFailures()).isEqualTo(0);
    assertThat(testcase2.getNumSkips()).isEqualTo(1);
    assertThat(testcase2.getRunTime()).isEqualTo(3);
  }

  @Test
  public void toJunitTestResult_convertTestcases_missingEndTime() throws Exception {
    TestSuiteResult testSuiteResult =
        parseTestSuiteResult(Path.of(TEST_RESULTS_DIR).resolve("missing_end_time_test.txt"));
    TestResult testSuites = TestSuiteResultConverter.toJunitTestResult(testSuiteResult, START_TIME);

    TestResult testcase = testSuites.getChildResults().get(0).getChildResults().get(0);
    assertThat(testcase.getRunTime()).isEqualTo(0);
  }

  @Test
  public void toJunitTestResult_convertTestcases_noTestResults() throws Exception {
    TestSuiteResult testSuiteResult =
        parseTestSuiteResult(Path.of(TEST_RESULTS_DIR).resolve("empty_test_results_test.txt"));
    TestResult testSuites = TestSuiteResultConverter.toJunitTestResult(testSuiteResult, START_TIME);

    assertThat(testSuites.getName()).isEqualTo("All targets");
    assertThat(testSuites.getChildResults()).hasSize(1);

    TestResult testSuite = testSuites.getChildResults().get(0); // No children aka TestCases
    assertThat(testSuite.getNumTests()).isEqualTo(0);
    assertThat(testSuite.getNumFailures()).isEqualTo(0);
    assertThat(testSuite.getNumSkips()).isEqualTo(0);
    assertThat(testSuite.getTimestamp().get()).isEqualTo(EXPECTED_TIMESTAMP);
  }
}
