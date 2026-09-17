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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.testresult.loader.JunitXmlTestCase;
import com.google.devtools.mobileharness.shared.util.testresult.loader.JunitXmlTestSuite;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.Outcome.OutcomeSummary;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link JunitTestSuiteResultConverter}. */
@RunWith(JUnit4.class)
public final class JunitTestSuiteResultConverterTest {

  @Test
  public void toTestResult_groupsTestSuiteOverviewsByIosTarget() {
    JunitXmlTestSuite testSuite =
        JunitXmlTestSuite.create(
            "",
            ImmutableList.of(
                passedTestCase("TargetA.FooTest", "testOne", Duration.ofSeconds(1)),
                passedTestCase("TargetA.BarTest", "testTwo", Duration.ofSeconds(2)),
                passedTestCase("TargetB.BazTest", "testThree", Duration.ofSeconds(4))));

    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of(testSuite));

    assertThat(testResult.testCases()).hasSize(3);
    assertThat(testResult.testSuiteOverviews()).hasSize(2);
    assertThat(
            testResult.testSuiteOverviews().stream()
                .filter(overview -> overview.name().equals("TargetA"))
                .findFirst()
                .orElseThrow()
                .elapsedTime())
        .isEqualTo(Duration.ofSeconds(3));
    assertThat(
            testResult.testSuiteOverviews().stream()
                .filter(overview -> overview.name().equals("TargetB"))
                .findFirst()
                .orElseThrow()
                .elapsedTime())
        .isEqualTo(Duration.ofSeconds(4));
    assertThat(testResult.outcome().summary()).isEqualTo(OutcomeSummary.SUCCESS);
    assertThat(testResult.state()).isEqualTo(State.COMPLETE);
  }

  @Test
  public void toTestResult_classNameWithoutPackage_fallsBackToEnclosingTestSuiteName() {
    JunitXmlTestSuite testSuite =
        JunitXmlTestSuite.create(
            "MyTestSuite",
            ImmutableList.of(passedTestCase("FooTest", "testOne", Duration.ofSeconds(1))));

    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of(testSuite));

    assertThat(testResult.testCases().get(0).testCaseReference().testSuiteName())
        .isEqualTo("MyTestSuite");
    assertThat(testResult.testSuiteOverviews().get(0).name()).isEqualTo("MyTestSuite");
  }

  @Test
  public void toTestResult_failedTestCase_reportsFailureStatusAndStackTrace() {
    JunitXmlTestSuite testSuite =
        JunitXmlTestSuite.create(
            "",
            ImmutableList.of(
                JunitXmlTestCase.builder()
                    .setClassName("TargetA.FooTest")
                    .setName("testOne")
                    .setStatus(JunitXmlTestCase.Status.FAILED)
                    .setStackTrace("expected true\nat FooTest.testOne")
                    .build()));

    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of(testSuite));

    TestCase testCase = testResult.testCases().get(0);
    assertThat(testCase.status()).isEqualTo(TestCase.TestStatus.FAILED);
    assertThat(testCase.stackTraces()).hasSize(1);
    assertThat(testCase.stackTraces().get(0).exception())
        .isEqualTo("expected true\nat FooTest.testOne");
    assertThat(testResult.outcome().summary()).isEqualTo(OutcomeSummary.FAILURE);
  }

  @Test
  public void toTestResult_erroredTestCase_reportsErrorStatusAndStackTrace() {
    JunitXmlTestSuite testSuite =
        JunitXmlTestSuite.create(
            "",
            ImmutableList.of(
                JunitXmlTestCase.builder()
                    .setClassName("TargetA.FooTest")
                    .setName("testOne")
                    .setStatus(JunitXmlTestCase.Status.ERROR)
                    .setStackTrace("app crashed\nat FooTest.testOne")
                    .build()));

    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of(testSuite));

    TestCase testCase = testResult.testCases().get(0);
    assertThat(testCase.status()).isEqualTo(TestCase.TestStatus.ERROR);
    assertThat(testCase.stackTraces().get(0).exception())
        .isEqualTo("app crashed\nat FooTest.testOne");
    assertThat(testResult.outcome().summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  @Test
  public void toTestResult_skippedTestCase_reportsSkippedStatus() {
    JunitXmlTestSuite testSuite =
        JunitXmlTestSuite.create(
            "",
            ImmutableList.of(
                JunitXmlTestCase.builder()
                    .setClassName("TargetA.FooTest")
                    .setName("testOne")
                    .setStatus(JunitXmlTestCase.Status.SKIPPED)
                    .build()));

    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of(testSuite));

    assertThat(testResult.testCases().get(0).status()).isEqualTo(TestCase.TestStatus.SKIPPED);
    assertThat(testResult.testCases().get(0).stackTraces()).isEmpty();
  }

  @Test
  public void toTestResult_noTestSuites_returnsInconclusiveResult() {
    TestResult testResult = JunitTestSuiteResultConverter.toTestResult(ImmutableList.of());

    assertThat(testResult.testCases()).isEmpty();
    assertThat(testResult.testSuiteOverviews()).isEmpty();
    assertThat(testResult.outcome().summary()).isEqualTo(OutcomeSummary.INCONCLUSIVE);
  }

  private static JunitXmlTestCase passedTestCase(
      String className, String name, Duration elapsedTime) {
    return JunitXmlTestCase.builder()
        .setClassName(className)
        .setName(name)
        .setElapsedTime(elapsedTime)
        .setStatus(JunitXmlTestCase.Status.PASSED)
        .build();
  }
}
