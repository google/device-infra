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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import java.time.Duration;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalModeIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final SetFlagsOss flags = new SetFlagsOss();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs();
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Bind @GlobalInternalEventBus private final EventBus globalInternalEventBus = new EventBus();

  @Inject private ClientApi clientApi;

  private LocalMode localMode;
  private DeviceReserver deviceReserver;
  private DeviceQuerier deviceQuerier;

  @Before
  public void setUp() throws InterruptedException {
    flags.setAllFlags(
        ImmutableMap.of(
            "detect_adb_device",
            "false",
            "external_adb_initializer_template",
            "true",
            "no_op_device_num",
            "1"));

    localMode = new LocalMode();
    localMode.initialize(globalInternalEventBus);
    deviceReserver = localMode.createDeviceReserver();
    deviceQuerier = localMode.createDeviceQuerier();

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void testDeviceReserver() throws Exception {
    JobInfo jobInfo1 = createJobInfo("fake_job_name_1");

    String allocationKey = "allocation-key-" + UUID.randomUUID();
    TestPlugin testPlugin = new TestPlugin(deviceReserver, allocationKey);

    clientApi.startJob(jobInfo1, localMode, ImmutableList.of(testPlugin));
    clientApi.waitForJob(jobInfo1.locator().getId());

    assertThat(jobInfo1.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

    DeviceQuery.DeviceQueryResult result =
        deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    assertThat(result.getDeviceInfoList()).hasSize(1);
    DeviceQuery.DeviceInfo deviceInfo = result.getDeviceInfo(0);
    assertThat(deviceInfo.getDimensionList())
        .contains(
            DeviceQuery.Dimension.newBuilder()
                .setName("ALLOCATION_KEY")
                .setValue(allocationKey)
                .setRequired(true)
                .build());
    assertThat(deviceInfo.getStatus()).isEqualTo("idle");

    JobInfo jobInfo2 = createJobInfo("fake_job_name_2");

    clientApi.startJob(jobInfo2, localMode);
    clientApi.waitForJob(jobInfo2.locator().getId());

    assertThat(jobInfo2.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
  }

  private static JobInfo createJobInfo(String jobName) {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(jobName))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .setTimeout(
                        Timeout.newBuilder()
                            .setStartTimeoutMs(Duration.ofSeconds(3L).toMillis())
                            .build())
                    .build())
            .build();
    jobInfo.params().add("sleep_time_sec", "1");
    return jobInfo;
  }

  private record TestPlugin(DeviceReserver deviceReserver, String allocationKey) {

    @Subscribe
    public void onTestStarting(LocalTestStartingEvent event) throws SkipTestException {
      try {
        logger.atInfo().log("TestPlugin.onTestStarting");

        Device device = event.getLocalDevice();
        assertThat(device.getDeviceControlId()).isEqualTo("NoOpDevice-0");
        assertThat(device.getDeviceUuid()).endsWith(":NoOpDevice-0");

        DeviceLocator deviceLocator = event.getDeviceLocator();
        assertThat(deviceLocator.getSerial()).isEqualTo("NoOpDevice-0");

        deviceReserver.addTempAllocationKeyToDevice(deviceLocator, allocationKey);
      } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
        throw SkipTestException.create(
            "TestPlugin error", DesiredTestResult.FAIL, BasicErrorId.USER_PLUGIN_ERROR, e);
      }
    }
  }
}
