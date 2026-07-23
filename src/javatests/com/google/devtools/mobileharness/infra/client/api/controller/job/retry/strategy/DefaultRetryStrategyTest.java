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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionSummary;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.RetryStrategy.RetryInfo;
import com.google.devtools.mobileharness.infra.container.proto.ModeSettingProto.ContainerModePreference;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import java.util.List;
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

/** Unit test for {@link DefaultRetryStrategy}. */
@RunWith(JUnit4.class)
public class DefaultRetryStrategyTest {
  private static final JobLocator JOB_LOCATOR = new JobLocator("job_id", "job_name");
  private static final String TEST_NAME = "test_name";
  private static final String ANDROID_INSTRUMENTATION = "AndroidInstrumentation";
  private static final Retry DEFAULT_RETRY =
      Retry.newBuilder().setRetryLevel(Retry.Level.ERROR).setTestAttempts(2).build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private JobInfo jobInfo;
  @Mock private CountDownTimer jobTimer;
  @Mock private TestInfos testInfos;
  @Mock private Result testResult;
  private Properties jobProperties;
  private Params jobParams;

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private ResultTypeWithCause testResultWithCause;

  private DefaultRetryStrategy retryStrategy;

  @Before
  public void setUp() {
    when(jobInfo.locator()).thenReturn(JOB_LOCATOR);
    when(jobInfo.log()).thenReturn(new Log(new Timing()));
    when(jobInfo.tests()).thenReturn(testInfos);
    when(jobInfo.timer()).thenReturn(jobTimer);
    when(jobInfo.type())
        .thenReturn(JobType.newBuilder().setDriver(ANDROID_INSTRUMENTATION).build());
    when(testResult.get()).thenReturn(testResultWithCause);
    when(testResultWithCause.causeProtoNonEmpty()).thenReturn(ExceptionDetail.getDefaultInstance());

    jobParams = new Params(null);
    when(jobInfo.params()).thenReturn(jobParams);

    jobProperties = new Properties(new Timing());
    jobProperties.add(PropertyName.Job.ACTUAL_USER, "mh");
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(jobInfo.jobUser()).thenReturn(JobUser.newBuilder().setRunAs("mh").build());

    retryStrategy = new DefaultRetryStrategy();
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestError()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());

    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));
    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);

    assertThat(retryInfos).hasSize(1);
    assertThat(retryInfos.get(0).retryReason()).isEqualTo("TEST_ERROR");
    assertThat(retryInfos.get(0).newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestFail()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestPass()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.PASS);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestSkip()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.SKIP);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestTimeout_retryOnTimeoutTrue()
      throws MobileHarnessException, InterruptedException {
    jobParams.add("retry_on_timeout", "true");
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.TIMEOUT);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("TEST_TIMEOUT");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withTestTimeout_retryOnTimeoutFalse()
      throws MobileHarnessException, InterruptedException {
    jobParams.add("retry_on_timeout", "false");
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.TIMEOUT);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withCustomerTimeoutException_retryOnTimeoutTrue()
      throws MobileHarnessException, InterruptedException {
    jobParams.add("retry_on_timeout", "true");
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt =
        mockTestInfo(
            "init_test_id",
            TestResult.ERROR,
            BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("TEST_ERROR");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_defaultLevel_withCustomerTimeoutException_retryOnTimeoutFalse()
      throws MobileHarnessException, InterruptedException {
    jobParams.add("retry_on_timeout", "false");
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting()).thenReturn(JobSetting.newBuilder().setRetry(DEFAULT_RETRY).build());
    TestInfo initAttempt =
        mockTestInfo(
            "init_test_id",
            TestResult.ERROR,
            BasicErrorId.CUSTOMER_TEST_EXECUTION_TIMEOUT_EXCEPTION_WRAPPER);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_failLevel_withTestFail()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(DEFAULT_RETRY.toBuilder().setRetryLevel(Retry.Level.FAIL).build())
                .build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("TEST_FAIL");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_failedWithNoValidUidAssigned()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(DEFAULT_RETRY.toBuilder().setRetryLevel(Retry.Level.FAIL).build())
                .build());
    TestInfo initAttempt =
        mockTestInfo(
            "init_test_id",
            TestResult.FAIL,
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("TEST_FAIL");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.RETRY_AFTER_NO_VALID_UID_ASSIGNED.name()),
            "true",
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_containerTest_asNonContainer()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(DEFAULT_RETRY.toBuilder().setRetryLevel(Retry.Level.FAIL).build())
                .build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.ERROR);
    initAttempt.properties().add(PropertyName.Test.CONTAINER_MODE, "true");
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("POTENTIAL_CONTAINER_ISSUE");

    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.RETRY_AFTER_CONTAINER_FAILS.name()), "true");
  }

  @Test
  public void decideRetryOnTestEnd_mandatoryContainer_shouldNotRetry()
      throws MobileHarnessException, InterruptedException {
    jobParams.add(
        JobInfo.PARAM_CONTAINER_MODE_PREFERENCE,
        ContainerModePreference.MANDATORY_CONTAINER.name());
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(1).build())
                .build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.ERROR);
    initAttempt.properties().add(PropertyName.Test.CONTAINER_MODE, "true");
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_extraAttemptForMHInfraError()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type())
        .thenReturn(JobType.newBuilder().setDriver("AndroidInstrumentation").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofHours(1));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    TestInfo lastAttempt =
        mockTestInfo(
            "last_test_id", TestResult.ERROR, BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(lastAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("EXTRA_RETRY_FOR_INFRA_ISSUE_AS_CRITICAL_ERROR");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "2");
  }

  @Test
  public void decideRetryOnTestEnd_extraAttemptForMHInfraError_shortJobRemainingTime()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type())
        .thenReturn(JobType.newBuilder().setDriver("AndroidInstrumentation").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofMinutes(2));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);

    TestInfo lastAttempt =
        mockTestInfo(
            "last_test_id", TestResult.ERROR, BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_noExtraAttemptForOtherInfraError()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type()).thenReturn(JobType.newBuilder().setDriver("VegaTest").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofHours(1));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    ErrorId nonMhInfraErrorId =
        new ErrorId() {
          @Override
          public Namespace namespace() {
            return Namespace.DA;
          }

          @Override
          public int code() {
            return 1;
          }

          @Override
          public String name() {
            return "VEGA_TEST_ERROR";
          }

          @Override
          public ErrorType type() {
            return ErrorType.INFRA_ISSUE;
          }
        };

    TestInfo lastAttempt = mockTestInfo("last_test_id", TestResult.ERROR, nonMhInfraErrorId);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));
    assertThat(retryStrategy.decideRetryOnTestEnd(lastAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_noExtraAttemptForManekiTest()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type()).thenReturn(JobType.newBuilder().setDriver("ManekiTest").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofHours(1));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    TestInfo lastAttempt =
        mockTestInfo(
            "last_test_id", TestResult.ERROR, BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(lastAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_noExtraAttemptForYtsTest()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type()).thenReturn(JobType.newBuilder().setDriver("YtsTest").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofHours(1));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    TestInfo lastAttempt =
        mockTestInfo(
            "last_test_id", TestResult.FAIL, BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(lastAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_noExtraAttemptForBlockedUser()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type())
        .thenReturn(JobType.newBuilder().setDriver("AndroidInstrumentation").build());
    when(jobInfo.jobUser()).thenReturn(JobUser.newBuilder().setRunAs("ytlr-lab-users").build());
    when(jobTimer.remainingTimeJava()).thenReturn(Duration.ofHours(1));
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    TestInfo lastAttempt =
        mockTestInfo(
            "last_test_id", TestResult.FAIL, BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(lastAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_testAttemptsExceed_shouldNotRetry()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    when(jobInfo.type())
        .thenReturn(JobType.newBuilder().setDriver("AndroidInstrumentation").build());
    TestInfo initAttempt = mockTestInfo("init_test_id", TestResult.FAIL);
    TestInfo lastAttempt = mockTestInfo("last_test_id", TestResult.FAIL);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt, lastAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(lastAttempt)).isEmpty();
  }

  @Test
  public void decideRetryOnTestEnd_testAttemptsExceed_additionalRetryForContainerIsInvalidAttempt()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(2).build())
                .build());
    TestInfo attempt1 = mockTestInfo("test_id_1", TestResult.ERROR);
    attempt1.properties().add(PropertyName.Test.CONTAINER_MODE, "true");
    TestInfo lastAttempt = mockTestInfo("last_test_id", TestResult.FAIL);
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(attempt1, lastAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(lastAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("TEST_FAIL");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test.RETRY_INDEX.name()),
            "1");
  }

  @Test
  public void decideRetryOnTestEnd_additionalRetryForDrainTimeoutError()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(DEFAULT_RETRY.toBuilder().setRetryLevel(Retry.Level.FAIL).build())
                .build());
    TestInfo initAttempt =
        mockTestInfo(
            "init_test_id",
            TestResult.ERROR,
            InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP);
    initAttempt.properties().add(PropertyName.Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS, "2");
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    List<RetryInfo> retryInfos = retryStrategy.decideRetryOnTestEnd(initAttempt);
    assertThat(retryInfos).hasSize(1);
    RetryInfo retryInfo = retryInfos.get(0);

    assertThat(retryInfo.retryReason()).isEqualTo("DRAIN_TIMEOUT_ERROR");
    assertThat(retryInfo.newTestProperties())
        .containsExactly(
            Ascii.toLowerCase(PropertyName.Test.CONTAINER_MODE.name()),
            "false",
            Ascii.toLowerCase(PropertyName.Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS.name()),
            "3");
  }

  @Test
  public void decideRetryOnTestEnd_drainTimeout_noRetryTimesLeft()
      throws MobileHarnessException, InterruptedException {
    when(jobTimer.isExpired()).thenReturn(false);
    when(jobInfo.setting())
        .thenReturn(
            JobSetting.newBuilder()
                .setRetry(
                    Retry.newBuilder().setRetryLevel(Retry.Level.FAIL).setTestAttempts(1).build())
                .build());
    TestInfo initAttempt =
        mockTestInfo(
            "init_test_id",
            TestResult.ERROR,
            InfraErrorId.TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP);
    initAttempt.properties().add(PropertyName.Test.CONTAINER_MODE, "false");
    initAttempt.properties().add(PropertyName.Test._DRAIN_TIMEOUT_RETRY_ATTEMPTS, "5");
    when(testInfos.getByName(TEST_NAME)).thenReturn(ImmutableList.of(initAttempt));

    assertThat(retryStrategy.decideRetryOnTestEnd(initAttempt)).isEmpty();
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
