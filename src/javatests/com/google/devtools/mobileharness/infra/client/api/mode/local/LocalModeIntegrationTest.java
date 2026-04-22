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
import static com.google.devtools.mobileharness.shared.util.truth.Correspondences.isInstanceOf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceGrpc;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceGrpc.LabInfoServiceBlockingStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
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
  @Inject private LocalMode localMode;

  private DeviceReserver deviceReserver;
  private DeviceQuerier deviceQuerier;
  private String allocationKey;

  @Before
  public void setUp() throws InterruptedException {
    flags.setAllFlags(
        ImmutableMap.of(
            "detect_adb_device",
            "false",
            "enable_emulator_detection",
            "false",
            "enable_fastboot_detector",
            "false",
            "external_adb_initializer_template",
            "true",
            "no_op_device_num",
            "1"));

    Guice.createInjector(new ClientApiModule(), BoundFieldModule.of(this)).injectMembers(this);

    deviceReserver = localMode.createDeviceReserver();
    deviceQuerier = localMode.createDeviceQuerier();

    localMode.initialize(globalInternalEventBus);
  }

  @After
  public void tearDown() throws Exception {
    if (allocationKey != null) {
      deviceReserver.addTempAllocationKeyToDevice(
          new DeviceLocator("NoOpDevice-0"), allocationKey, Duration.ZERO);
    }
  }

  @Test
  public void testDeviceReserver() throws Exception {
    JobInfo jobInfo1 = createJobInfo("fake_job_name_1");

    allocationKey = "allocation-key-" + UUID.randomUUID();
    TestPluginForDeviceReserver testPlugin =
        new TestPluginForDeviceReserver(deviceReserver, allocationKey);

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
    assertThat(deviceInfo.getId()).isEqualTo("NoOpDevice-0");

    JobInfo jobInfo2 = createJobInfo("fake_job_name_2");

    clientApi.startJob(jobInfo2, localMode);
    clientApi.waitForJob(jobInfo2.locator().getId());

    assertThat(jobInfo2.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
  }

  @Test
  public void testLabInfoService() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    ServerBuilder<?> serverBuilder = InProcessServerBuilder.forName(serverName);
    for (BindableService service : localMode.provideServicesForNonWorker()) {
      serverBuilder.addService(service);
    }
    Server server = serverBuilder.build().start();
    ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    LabInfoServiceBlockingStub stub = LabInfoServiceGrpc.newBlockingStub(channel);

    try {
      GetLabInfoResponse response = stub.getLabInfo(GetLabInfoRequest.getDefaultInstance());
      LabQueryResult result = response.getLabQueryResult();

      GetLabInfoResponse expectedResponse =
          GetLabInfoResponse.newBuilder()
              .setLabQueryResult(
                  LabQueryResult.newBuilder()
                      .setLabView(
                          LabView.newBuilder()
                              .setLabTotalCount(1)
                              .addLabData(
                                  LabData.newBuilder()
                                      .setDeviceList(
                                          DeviceList.newBuilder()
                                              .setDeviceTotalCount(1)
                                              .addDeviceInfo(
                                                  DeviceInfo.newBuilder()
                                                      .setDeviceStatus(DeviceStatus.IDLE))))))
              .build();
      assertThat(response).comparingExpectedFieldsOnly().isEqualTo(expectedResponse);

      String deviceUuid =
          result
              .getLabView()
              .getLabData(0)
              .getDeviceList()
              .getDeviceInfo(0)
              .getDeviceLocator()
              .getId();
      assertThat(deviceUuid).endsWith(":NoOpDevice-0");
    } finally {
      channel.shutdownNow();
      server.shutdownNow();
    }
  }

  @Test
  public void testTestEvents() throws Exception {
    JobInfo jobInfo = createJobInfo("fake_job_for_test_events");
    TestPluginForTestEvent plugin = new TestPluginForTestEvent();

    clientApi.startJob(jobInfo, localMode, ImmutableList.of(plugin));
    clientApi.waitForJob(jobInfo.locator().getId());

    assertThat(jobInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(plugin.receivedEvents)
        .comparingElementsUsing(isInstanceOf())
        .containsExactly(
            com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.TestStartedEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.TestEndedEvent.class)
        .inOrder();
    assertThat(plugin.receivedEvents)
        .comparingElementsUsing(isInstanceOf())
        .containsExactly(
            com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent.class,
            LocalTestStartingEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent.class,
            LocalTestStartedEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.LocalTestEndingEvent.class,
            com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndedEvent.class,
            com.google.devtools.mobileharness.api.testrunner.event.test.LocalTestEndedEvent.class)
        .inOrder();
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

  private record TestPluginForDeviceReserver(DeviceReserver deviceReserver, String allocationKey) {

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

  private static class TestPluginForTestEvent {

    private final List<Object> receivedEvents = new ArrayList<>();

    @Subscribe
    public void onTestStarting(
        com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestStartingOld(
        com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestStartedOld(
        com.google.wireless.qa.mobileharness.shared.controller.event.TestStartedEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestStarted(
        com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestEnding(
        com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestEndingOld(
        com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestEndedOld(
        com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent event) {
      receivedEvents.add(event);
    }

    @Subscribe
    public void onTestEnded(
        com.google.devtools.mobileharness.api.testrunner.event.test.TestEndedEvent event) {
      receivedEvents.add(event);
    }
  }
}
