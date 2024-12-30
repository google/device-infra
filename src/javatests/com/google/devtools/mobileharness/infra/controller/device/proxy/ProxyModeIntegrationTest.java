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

package com.google.devtools.mobileharness.infra.controller.device.proxy;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import java.time.Duration;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProxyModeIntegrationTest {

  @Rule public final CaptureLogs captureLogs = new CaptureLogs("", /* printFailedLogs= */ true);
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Bind @GlobalInternalEventBus private final EventBus globalInternalEventBus = new EventBus();

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  @Inject private ClientApi clientApi;

  @Before
  public void setUp() {
    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "detect_adb_device",
            "false",
            "enable_proxy_mode",
            "true",
            "external_adb_initializer_template",
            "true");
    ImmutableList<String> flagList =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    Flags.parse(flagList.toArray(new String[0]));

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  @Test
  public void startJob() throws Exception {
    JobInfo jobInfo = createJobInfo();

    clientApi.startJob(jobInfo, new LocalMode(), ImmutableList.of());
    clientApi.waitForJob(jobInfo.locator().getId());

    TestInfo testInfo = jobInfo.tests().getOnly();
    assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.log().get(0)).contains("Sleep for 5 seconds");

    assertThat(testInfo.properties().getAll())
        .containsEntry("dimension_control_id", "fake-device-id");

    assertWithMessage(
            "Log of a passed MH job should not contain exception stack traces, which will"
                + " confuse users when they debug a failed one")
        .that(captureLogs.getLogs())
        .doesNotContain("\tat ");
  }

  private static JobInfo createJobInfo() {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_name"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .setTimeout(
                        Timeout.newBuilder()
                            .setStartTimeoutMs(Duration.ofSeconds(10L).toMillis())
                            .build())
                    .build())
            .build();
    jobInfo.subDeviceSpecs().getSubDevice(0).dimensions().add(Name.ID, "fake-device-id");
    jobInfo.params().add("sleep_time_sec", Integer.toString(5));
    return jobInfo;
  }
}
