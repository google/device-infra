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

package com.google.devtools.mobileharness.infra.client.api.performance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClientApiLatencyTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final SetFlagsOss flags = new SetFlagsOss();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs();
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Bind @GlobalInternalEventBus private final EventBus globalEventBus = new EventBus();

  @Bind
  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");

  @Inject private ClientApi clientApi;

  @Before
  public void setUp() {
    flags.setAllFlags(
        ImmutableMap.of(
            "detect_adb_device",
            "false",
            "enable_device_config_manager",
            "false",
            "enable_emulator_detection",
            "false",
            "enable_fastboot_detector",
            "false",
            "enable_fastboot_in_android_real_device",
            "false",
            "no_op_device_num",
            "1"));

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void noOpTest_localMode() throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_name"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .build())
            .build();

    logger.atInfo().log("Starting job");
    Stopwatch stopwatch = Stopwatch.createStarted();
    clientApi.startJob(jobInfo, new LocalMode());
    clientApi.waitForJob(jobInfo.locator().getId());
    Duration executionTime = stopwatch.elapsed();
    logger.atInfo().log("Job ended, execution_time=%s", executionTime);

    assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertWithMessage("No-op job execution time")
        .that(executionTime)
        .isAtMost(Duration.ofSeconds(10L));
  }
}
