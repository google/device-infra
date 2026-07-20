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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.TestRetryProcessor;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryStrategy.RetryInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link FlakyTestRetryStrategy}. */
@RunWith(JUnit4.class)
public class FlakyTestRetryStrategyTest {
  private static final JobLocator JOB_LOCATOR = new JobLocator("job_id", "job_name");
  private static final String TEST_NAME = "test_name";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private JobInfo jobInfo;
  private Params jobParams;
  @Mock private TestInfos testInfos;
  @Mock private Result testResult;

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private ResultTypeWithCause testResultWithCause;

  @Mock private TestRetryProcessor testRetryProcessor;

  private FlakyTestRetryStrategy retryStrategy;

  @Before
  public void setUp() {
    jobParams = new Params(null);
    when(jobInfo.locator()).thenReturn(JOB_LOCATOR);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(jobInfo.params()).thenReturn(jobParams);
    when(testResult.get()).thenReturn(testResultWithCause);
    when(testResultWithCause.causeProtoNonEmpty()).thenReturn(ExceptionDetail.getDefaultInstance());
    when(testRetryProcessor.generateRetryTestTargetsProperty(any())).thenReturn(ImmutableMap.of());

    retryStrategy = new FlakyTestRetryStrategy(testRetryProcessor);
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_noFlakyTestAttemptsParam() throws Exception {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    // No flaky_test_attempts param set, empty by default.

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_invalidFlakyTestAttemptsParam_skipsRetry()
      throws Exception {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    jobParams.add("flaky_test_attempts", "invalid");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_zeroFlakyTestAttemptsParam_skipsRetry()
      throws Exception {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    jobParams.add("flaky_test_attempts", "0");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_flakyRetry_limitReached() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.FAIL);
    currentAttempt.properties().add("flaky_attempt_index", "2");
    currentAttempt.properties().add("error_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_flakyRetry_retriesCorrectly() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.FAIL);
    jobParams.add("flaky_test_attempts", "3");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo)
        .isEqualTo(
            new RetryInfo(
                Optional.of("TEST_FAIL"),
                ImmutableMap.of(
                    "error_attempt_index", "0",
                    "flaky_attempt_index", "1")));
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_retriesCorrectly() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_attempt_index", "2");
    currentAttempt.properties().add("error_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    // Mock other attempts returned by jobInfo.tests().getByName(...)
    // To calculate errorAttempts inside getErrorAttemptCountForCurrentFlakyAttemptIndex.
    // It filters by TEST_RESULTS_OF_ERROR_ATTEMPTS and flaky_attempt_index = 2.
    // Only currentAttempt matches (since it is the only one in getByName), count = 1.
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo)
        .isEqualTo(
            new RetryInfo(
                Optional.of("TEST_ERROR"),
                ImmutableMap.of(
                    "error_attempt_index", "1",
                    "flaky_attempt_index", "2")));
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_limitReached() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    TestInfo otherAttempt = mockTestInfo("other_attempt_id", TestResult.ERROR);
    otherAttempt.properties().add("flaky_attempt_index", "0");

    // Mock two error attempts, count = 2. Since errorAttempts < 2 condition checks, it should fail
    // to retry.
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt, otherAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_countsDifferentErrorResults() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    TestInfo timeoutAttempt = mockTestInfo("timeout_attempt_id", TestResult.TIMEOUT);
    timeoutAttempt.properties().add("flaky_attempt_index", "0");

    // Mock two error-type attempts: TIMEOUT and ERROR. Both are in TEST_RESULTS_OF_ERROR_ATTEMPTS,
    // so errorAttemptCount = 2, reaching MAX_ERROR_ATTEMPTS limit.
    when(testInfos.getByName(TEST_NAME))
        .thenReturn(ImmutableList.of(currentAttempt, timeoutAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo).isEqualTo(RetryStrategy.NO_RETRY);
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_ignoresAttemptsWithDifferentFlakyAttemptIndex()
      throws Exception {
    TestInfo errorAttempt = mockTestInfo("other_attempt_id", TestResult.ERROR);
    errorAttempt.properties().add("flaky_attempt_index", "0");

    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_attempt_index", "1");
    currentAttempt.properties().add("error_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    // Mock two ERROR attempts with different flaky_attempt_index (0 and 1).
    // getErrorAttemptCountForCurrentFlakyAttemptIndex should only count the attempt with
    // flaky_attempt_index = 1.
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt, errorAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo)
        .isEqualTo(
            new RetryInfo(
                Optional.of("TEST_ERROR"),
                ImmutableMap.of(
                    "error_attempt_index", "1",
                    "flaky_attempt_index", "1")));
  }

  @Test
  public void decideRetryOnTestEnd_flakyRetry_mergesProcessorProperties() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.FAIL);
    jobParams.add("flaky_test_attempts", "3");
    when(testRetryProcessor.generateRetryTestTargetsProperty(currentAttempt))
        .thenReturn(ImmutableMap.of("retry_prop_key", "retry_prop_value"));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo)
        .isEqualTo(
            new RetryInfo(
                Optional.of("TEST_FAIL"),
                ImmutableMap.of(
                    "error_attempt_index", "0",
                    "flaky_attempt_index", "1",
                    "retry_prop_key", "retry_prop_value")));
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_mergesProcessorProperties() throws Exception {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_attempt_index", "2");
    currentAttempt.properties().add("error_attempt_index", "0");
    jobParams.add("flaky_test_attempts", "3");

    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt));
    when(testRetryProcessor.generateRetryTestTargetsProperty(currentAttempt))
        .thenReturn(ImmutableMap.of("retry_prop_key", "retry_prop_value"));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo)
        .isEqualTo(
            new RetryInfo(
                Optional.of("TEST_ERROR"),
                ImmutableMap.of(
                    "error_attempt_index", "1",
                    "flaky_attempt_index", "2",
                    "retry_prop_key", "retry_prop_value")));
  }

  private TestInfo mockTestInfo(String testId, TestResult result) {
    return mockTestInfo(testId, result, null);
  }

  private TestInfo mockTestInfo(String testId, TestResult result, @Nullable ErrorId errorId) {
    TestInfo testInfo = mock(TestInfo.class);

    Timing testTiming = new Timing();
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.locator()).thenReturn(new TestLocator(testId, TEST_NAME, JOB_LOCATOR));
    when(testInfo.properties()).thenReturn(new Properties(testTiming));
    when(testInfo.resultWithCause()).thenReturn(this.testResult);
    when(testInfo.timing()).thenReturn(testTiming);
    when(testInfo.log()).thenReturn(new Log(testTiming));

    Result testResult = mock(Result.class);
    when(testInfo.resultWithCause()).thenReturn(testResult);
    ResultTypeWithCause resultWithCause = mock(ResultTypeWithCause.class);
    when(testResult.get()).thenReturn(resultWithCause);
    when(resultWithCause.type()).thenReturn(result);
    when(resultWithCause.causeProtoNonEmpty()).thenReturn(ExceptionDetail.getDefaultInstance());

    if (errorId != null) {
      when(resultWithCause.causeProto())
          .thenReturn(
              Optional.of(
                  ExceptionDetail.newBuilder()
                      .setSummary(
                          ExceptionSummary.newBuilder()
                              .setErrorId(ErrorModelConverter.toErrorIdProto(errorId))
                              .build())
                      .build()));
    }
    return testInfo;
  }
}
