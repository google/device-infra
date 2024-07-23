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

package com.google.devtools.mobileharness.infra.client.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.eventbus.EventBus;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Correspondence.BinaryPredicate;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.shared.context.InvocationContext.ContextScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClientApiTest {

  private static final Logger rootLogger = Logger.getLogger("");

  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  private Handler logHandler;

  @Bind @GlobalInternalEventBus private EventBus globalInternalEventBus;

  @Inject private ClientApi clientApi;

  @Before
  public void setUp() {
    Flags.parse(new String[] {"--no_op_device_num=1", "--detect_adb_device=false"});

    logHandler = new StreamHandler(outputStream, MobileHarnessLogFormatter.getDefaultFormatter());
    rootLogger.addHandler(logHandler);

    globalInternalEventBus = new EventBus();

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();

    rootLogger.removeHandler(logHandler);
  }

  @Test
  public void startJob() throws Exception {
    JobInfo jobInfo = createJobInfo();

    try (var ignored =
        new ContextScope(ImmutableMap.of(InvocationType.OLC_CLIENT, "fake_client_id"))) {
      clientApi.startJob(jobInfo, new LocalMode());

      assertThat(jobInfo.properties().get(Job.EXEC_MODE)).isNotNull();
      assertThat(jobInfo.properties().get(Job.CLIENT_HOSTNAME)).isNotNull();
      assertThat(jobInfo.properties().get(Job.CLIENT_VERSION)).isNotNull();

      clientApi.waitForJob(jobInfo.locator().getId());

      assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

      TestInfo testInfo = Iterables.getOnlyElement(jobInfo.tests().getAll().values());
      assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
      assertThat(testInfo.log().get(0)).contains("Sleep for 5 seconds");

      String logs = outputStream.toString(UTF_8);

      assertThat(Splitter.on('\n').splitToList(logs))
          .comparingElementsUsing(
              Correspondence.from(
                  (BinaryPredicate<String, List<String>>)
                      (actual, expected) -> expected.stream().allMatch(actual::contains),
                  "has all substrings in"))
          .contains(ImmutableList.of("Sleep for 5 seconds", "{olc_client_id=fake_client_id}"));

      assertWithMessage(
              "Log of a passed MH job should not contain exception stack traces, which will"
                  + " confuse users when they debug a failed one")
          .that(logs)
          .doesNotContain("\tat ");
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
