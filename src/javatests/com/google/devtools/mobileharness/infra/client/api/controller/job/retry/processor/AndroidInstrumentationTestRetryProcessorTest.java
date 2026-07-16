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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.TestSuiteResultLoader;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link AndroidInstrumentationTestRetryProcessor}. */
@RunWith(JUnit4.class)
public final class AndroidInstrumentationTestRetryProcessorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private TestSuiteResultLoader testResultLoader;
  private AndroidInstrumentationTestRetryProcessor testRetryProcessor;

  @Before
  public void setUp() {
    testRetryProcessor = new AndroidInstrumentationTestRetryProcessor(testResultLoader);
  }

  @Test
  public void generateRetryTestTargetsProperty_testNotFail_withRetryProperty_inheritsProperty()
      throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");
    test.resultWithCause().setPass();
    test.properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_RETRY_TEST_TARGETS,
            "com.google.example.MyClass#testOne");

    assertThat(testRetryProcessor.generateRetryTestTargetsProperty(test))
        .containsExactly(
            "android_instrumentation_retry_test_targets", "com.google.example.MyClass#testOne");
  }

  @Test
  public void generateRetryTestTargetsProperty_testNotFail_withoutRetryProperty_returnsEmptyMap()
      throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");
    test.resultWithCause().setPass();

    assertThat(testRetryProcessor.generateRetryTestTargetsProperty(test)).isEmpty();
  }

  @Test
  public void generateRetryTestTargetsProperty_noTestResultLoaded_returnsEmptyMap()
      throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");
    test.resultWithCause()
        .setNonPassing(
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "failed"));

    when(testResultLoader.loadTestResult(test)).thenReturn(Optional.empty());

    assertThat(testRetryProcessor.generateRetryTestTargetsProperty(test)).isEmpty();
  }

  @Test
  public void generateRetryTestTargetsProperty_hasFailedTests_returnsRetryTargetsProperty()
      throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");
    test.resultWithCause()
        .setNonPassing(
            com.google.devtools.mobileharness.api.model.proto.Test.TestResult.FAIL,
            new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "failed"));

    TestSuiteResult suiteResult =
        TestSuiteResult.newBuilder()
            .addTestResult(
                TestResult.newBuilder()
                    .setTestStatus(TestStatus.FAILED)
                    .setTestCase(
                        TestCase.newBuilder()
                            .setTestPackage("com.google.example")
                            .setTestClass("MyClass")
                            .setTestMethod("testOne")
                            .build())
                    .build())
            .addTestResult(
                TestResult.newBuilder()
                    .setTestStatus(TestStatus.PASSED)
                    .setTestCase(
                        TestCase.newBuilder()
                            .setTestPackage("com.google.example")
                            .setTestClass("MyClass")
                            .setTestMethod("testTwo")
                            .build())
                    .build())
            .addTestResult(
                TestResult.newBuilder()
                    .setTestStatus(TestStatus.FAILED)
                    .setTestCase(
                        TestCase.newBuilder()
                            .setTestPackage("com.google.example")
                            .setTestClass("OtherClass")
                            .setTestMethod("testThree")
                            .build())
                    .build())
            .addTestResult(
                TestResult.newBuilder()
                    .setTestStatus(TestStatus.ERROR)
                    .setTestCase(
                        TestCase.newBuilder()
                            .setTestPackage("com.google.example")
                            .setTestClass("OtherClass")
                            .setTestMethod("testFour")
                            .build())
                    .build())
            .build();
    when(testResultLoader.loadTestResult(test)).thenReturn(Optional.of(suiteResult));

    assertThat(testRetryProcessor.generateRetryTestTargetsProperty(test))
        .containsExactly(
            "android_instrumentation_retry_test_targets",
            "com.google.example.MyClass#testOne,com.google.example.OtherClass#testThree,"
                + "com.google.example.OtherClass#testFour");
  }
}
