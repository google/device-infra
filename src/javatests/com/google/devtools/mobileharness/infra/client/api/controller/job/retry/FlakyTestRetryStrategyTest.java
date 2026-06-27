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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.RetryStrategy.RetryInfo;
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
  @Mock private Params jobParams;
  @Mock private TestInfos testInfos;
  @Mock private Result testResult;

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private ResultTypeWithCause testResultWithCause;

  private FlakyTestRetryStrategy retryStrategy;

  @Before
  public void setUp() {
    when(jobInfo.locator()).thenReturn(JOB_LOCATOR);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(jobInfo.params()).thenReturn(jobParams);
    when(testResult.get()).thenReturn(testResultWithCause);
    when(testResultWithCause.causeProtoNonEmpty()).thenReturn(ExceptionDetail.getDefaultInstance());

    retryStrategy = new FlakyTestRetryStrategy();
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_noFlakyTestAttemptsParam()
      throws MobileHarnessException, InterruptedException {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    when(jobParams.get("flaky_test_attempts")).thenReturn(null);

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo.shouldRetry()).isFalse();
    assertThat(retryInfo.retryReason().isPresent()).isFalse();
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_invalidFlakyTestAttemptsParam_skipsRetry()
      throws MobileHarnessException, InterruptedException {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    when(jobParams.get("flaky_test_attempts")).thenReturn("invalid");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo.shouldRetry()).isFalse();
  }

  @Test
  public void decideRetryOnTestEnd_noRetry_zeroFlakyTestAttemptsParam_skipsRetry()
      throws MobileHarnessException, InterruptedException {
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    when(jobParams.get("flaky_test_attempts")).thenReturn("0");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfo.shouldRetry()).isFalse();
  }

  @Test
  public void decideRetryOnTestEnd_flakyRetry_limitReached()
      throws MobileHarnessException, InterruptedException {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.FAIL);
    currentAttempt.properties().add("flaky_retry_index", "2");
    currentAttempt.properties().add("error_retry_index", "0");
    when(jobParams.get("flaky_test_attempts")).thenReturn("3");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo.shouldRetry()).isFalse();
  }

  @Test
  public void decideRetryOnTestEnd_flakyRetry_retriesCorrectly()
      throws MobileHarnessException, InterruptedException {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.FAIL);
    when(jobParams.get("flaky_test_attempts")).thenReturn("3");

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo.shouldRetry()).isTrue();
    assertThat(retryInfo.retryReason().orElse(null)).isEqualTo("TEST_FAIL");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            "error_retry_index", "0",
            "flaky_retry_index", "1");
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_retriesCorrectly()
      throws MobileHarnessException, InterruptedException {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_retry_index", "2");
    currentAttempt.properties().add("error_retry_index", "0");
    when(jobParams.get("flaky_test_attempts")).thenReturn("3");

    // Mock other attempts returned by jobInfo.tests().getByName(...)
    // To calculate errorAttempts inside getErrorAttemptsForCurrentFlakyTestIdx.
    // It filters by TestResult.ERROR and FlakyTestIndex = 0.
    // Only currentAttempt matches (since it is the only one in getByName), count = 1.
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo.shouldRetry()).isTrue();
    assertThat(retryInfo.retryReason().orElse(null)).isEqualTo("TEST_ERROR");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            "error_retry_index", "1",
            "flaky_retry_index", "2");
  }

  @Test
  public void decideRetryOnTestEnd_errorRetry_limitReached()
      throws MobileHarnessException, InterruptedException {
    TestInfo currentAttempt = mockTestInfo("attempt_id", TestResult.ERROR);
    currentAttempt.properties().add("flaky_retry_index", "0");
    when(jobParams.get("flaky_test_attempts")).thenReturn("3");

    TestInfo otherAttempt = mockTestInfo("other_attempt_id", TestResult.ERROR);
    otherAttempt.properties().add("flaky_retry_index", "0");

    // Mock two error attempts, count = 2. Since errorAttempts < 2 condition checks, it should fail
    // to retry.
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(currentAttempt, otherAttempt));

    RetryInfo retryInfo = retryStrategy.decideRetryOnTestEnd(currentAttempt);

    assertThat(retryInfo.shouldRetry()).isFalse();
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
