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

package com.google.devtools.deviceinfra.infra.client.api;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Iterables;
import com.google.common.eventbus.EventBus;
import com.google.devtools.deviceinfra.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.deviceinfra.infra.client.api.mode.local.LocalMode;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ClientApiTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Handler logHandler;

  @Bind @GlobalInternalEventBus private EventBus globalInternalEventBus;

  @Captor private ArgumentCaptor<LogRecord> logRecordCaptor;

  @Inject private ClientApi clientApi;

  @Before
  public void setUp() {
    Flags.parse(new String[] {"--no_op_device_num=1"});

    Logger.getLogger("").addHandler(logHandler);

    globalInternalEventBus = new EventBus();

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();

    Logger.getLogger("").removeHandler(logHandler);
  }

  @Test
  public void startJob() throws Exception {
    JobInfo jobInfo = createJobInfo();

    try {
      clientApi.startJob(jobInfo, new LocalMode());
      clientApi.waitForJob(jobInfo.locator().getId());

      assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

      TestInfo testInfo = Iterables.getOnlyElement(jobInfo.tests().getAll().values());
      assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
      assertThat(testInfo.log().get(0)).contains("Sleep for 5 seconds");
    } catch (
        @SuppressWarnings("InterruptedExceptionSwallowed")
        Throwable e) {
      e.addSuppressed(
          new IllegalStateException(
              String.format(
                  "job_result=%s, job_log=[%s]",
                  jobInfo.resultWithCause().get().toStringWithDetail(), jobInfo.log().get(0))));
      throw e;
    }

    assertNoExceptionInLog();
  }

  private void assertNoExceptionInLog() {
    verify(logHandler, atLeast(0)).publish(logRecordCaptor.capture());
    assertWithMessage(
            "Log of a passed ATS job should not contain exception stack traces, which will confuse"
                + " users when they debug a failed one")
        .that(
            logRecordCaptor.getAllValues().stream()
                .filter(ClientApiTest::logRecordWithException)
                .collect(toImmutableList()))
        .isEmpty();
  }

  private static boolean logRecordWithException(LogRecord logRecord) {
    return logRecord.getThrown() != null || logRecord.getMessage().contains("\tat ");
  }

  private static JobInfo createJobInfo() {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_name"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .build())
            .build();
    jobInfo.params().add("sleep_time_sec", "5");
    return jobInfo;
  }
}
