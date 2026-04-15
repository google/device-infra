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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest.Device;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.VersionCheckRequest;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RemoteDeviceManagerIntegrationTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final String DEVICE_UUID = "fake_uuid";
  private static final String LAB_HOST_NAME = "fake_lab_host_name";
  private static final String DIMENSION_NAME = "fake_dimension_name";
  private static final String DIMENSION_VALUE = "fake_dimension_value";
  private static final String TEMP_DIMENSION_NAME = "temp_dimension_name";
  private static final String TEMP_DIMENSION_VALUE = "temp_dimension_value";

  private static final SignUpLabRequest SIGN_UP_LAB_REQUEST =
      SignUpLabRequest.newBuilder()
          .setVersionCheckRequest(
              VersionCheckRequest.newBuilder()
                  .setStubVersion(Version.LAB_VERSION.toString())
                  .setMinServiceVersion(Version.MIN_MASTER_V5_VERSION.toString()))
          .setLabIp("fake_lab_ip")
          .setLabHostName(LAB_HOST_NAME)
          .setLabServerSetting(
              LabServerSetting.newBuilder()
                  .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234)))
          .addDevice(
              Device.newBuilder()
                  .setControlId(DEVICE_UUID)
                  .setUuid(DEVICE_UUID)
                  .setStatus(DeviceStatus.IDLE)
                  .setFeature(
                      DeviceFeature.newBuilder()
                          .addType("NoOpDevice")
                          .addDriver("NoOpDriver")
                          .setCompositeDimension(
                              DeviceCompositeDimension.newBuilder()
                                  .addSupportedDimension(
                                      DeviceDimension.newBuilder()
                                          .setName(DIMENSION_NAME)
                                          .setValue(DIMENSION_VALUE)))))
          .build();

  @Inject private ListeningExecutorService threadPool;
  @Inject private ListeningScheduledExecutorService scheduledThreadPool;

  @Bind @Mock private LabRecordManager labRecordManager;
  @Bind @Mock private NetUtil netUtil;

  @Inject private RemoteDeviceManager remoteDeviceManager;
  @Inject @AtsModeAbstractScheduler private AbstractScheduler scheduler;

  private LabSyncGrpcStub labSyncGrpcStub;
  private Server grpcServer;

  @Before
  public void setUp() throws Exception {
    int grpcPort = PortProber.pickUnusedPort();
    MasterGrpcStubHelper masterGrpcStubHelper =
        new MasterGrpcStubHelper(ChannelFactory.createLocalChannel(grpcPort, directExecutor()));
    labSyncGrpcStub = new LabSyncGrpcStub(masterGrpcStubHelper);

    when(netUtil.getLocalHostName()).thenReturn("fake_olc_host_name");

    Guice.createInjector(
            BoundFieldModule.of(this),
            new CommonModule(ImmutableList.of(), ImmutableMap.of(), ImmutableMap.of()),
            new AtsModeModule())
        .injectMembers(this);

    grpcServer =
        NettyServerBuilder.forPort(grpcPort)
            .addService(remoteDeviceManager.getLabSyncService())
            .build()
            .start();

    remoteDeviceManager.start();
    threadPool.execute((Runnable) scheduler);
  }

  @After
  public void tearDown() throws Exception {
    if (grpcServer != null) {
      grpcServer.shutdownNow();
    }
    threadPool.shutdownNow();
    scheduledThreadPool.shutdownNow();
  }

  @Test
  public void tempRequiredDimensions_blockingAndThenAllocating() throws Exception {
    // 1. Add device via LabSyncService.
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    // 2. Add a job with proper dimensions, verify allocation.
    JobScheduleUnit job1 = createJobScheduleUnit("job1", DIMENSION_NAME, DIMENSION_VALUE);
    TestScheduleUnit test1 = createTestScheduleUnit(job1, "test1");

    AllocationEventListener allocationListener = new AllocationEventListener();
    scheduler.registerEventHandler(allocationListener);

    scheduler.addJob(job1);
    scheduler.addTest(test1);

    // Wait for allocation.
    assertThat(allocationListener.awaitAllocation(Duration.ofSeconds(10L))).isTrue();

    // Unallocate device.
    scheduler.unallocate(test1.locator(), /* removeDevices= */ false, /* closeTest= */ true);

    // 3. Add temp required dimensions (duration 10 seconds).
    remoteDeviceManager.upsertDeviceTempRequiredDimensions(
        UpsertDeviceTempRequiredDimensionsRequest.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(DEVICE_UUID)
                    .setLabLocator(
                        LabLocator.newBuilder().setHostName(LAB_HOST_NAME).setIp("fake_lab_ip")))
            .addTempRequiredDimension(
                DeviceDimension.newBuilder()
                    .setName(TEMP_DIMENSION_NAME)
                    .setValue(TEMP_DIMENSION_VALUE))
            .setDurationMs(10000L)
            .build());

    // Waits for the listener to be notified and the scheduler to be updated.
    Thread.sleep(500L);

    // 4. Submit next job WITHOUT the temp dimension.
    JobScheduleUnit job2 = createJobScheduleUnit("job2", DIMENSION_NAME, DIMENSION_VALUE);
    TestScheduleUnit test2 = createTestScheduleUnit(job2, "test2");

    AllocationEventListener allocationListener2 = new AllocationEventListener();
    scheduler.registerEventHandler(allocationListener2);

    scheduler.addJob(job2);
    scheduler.addTest(test2);

    // Verify it does NOT get allocated within 5 seconds (should be blocked for 10s).
    assertThat(allocationListener2.awaitAllocation(Duration.ofSeconds(5L))).isFalse();

    // Verify it GETS allocated after 10 seconds (dimensions expire are checked periodically).
    // Wait another 15 seconds to be sure.
    assertThat(allocationListener2.awaitAllocation(Duration.ofSeconds(15L))).isTrue();
  }

  @Test
  public void tempRequiredDimensions_matchingAllocatingImmediately() throws Exception {
    // 1. Add device via LabSyncService.
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    // 2. Add temp required dimensions (duration 10 seconds).
    remoteDeviceManager.upsertDeviceTempRequiredDimensions(
        UpsertDeviceTempRequiredDimensionsRequest.newBuilder()
            .setDeviceLocator(
                DeviceLocator.newBuilder()
                    .setId(DEVICE_UUID)
                    .setLabLocator(
                        LabLocator.newBuilder().setHostName(LAB_HOST_NAME).setIp("fake_lab_ip")))
            .addTempRequiredDimension(
                DeviceDimension.newBuilder()
                    .setName(TEMP_DIMENSION_NAME)
                    .setValue(TEMP_DIMENSION_VALUE))
            .setDurationMs(10000L)
            .build());

    // 3. Submit job WITH the temp dimension.
    JobScheduleUnit job = createJobScheduleUnit("job3", DIMENSION_NAME, DIMENSION_VALUE);
    job.dimensions().add(TEMP_DIMENSION_NAME, TEMP_DIMENSION_VALUE);
    TestScheduleUnit test = createTestScheduleUnit(job, "test3");

    AllocationEventListener allocationListener = new AllocationEventListener();
    scheduler.registerEventHandler(allocationListener);

    scheduler.addJob(job);
    scheduler.addTest(test);

    // Verify it gets allocated within 10 seconds.
    assertThat(allocationListener.awaitAllocation(Duration.ofSeconds(10L))).isTrue();
  }

  private JobScheduleUnit createJobScheduleUnit(
      String id, String dimensionName, String dimensionValue) {
    JobScheduleUnit job =
        new JobScheduleUnit(
            new JobLocator(id, id),
            JobUser.newBuilder().setRunAs("fake_user").setActualUser("fake_user").build(),
            JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build(),
            JobSetting.newBuilder().setPriority(Priority.HIGH).build(),
            new Timing(Clock.systemUTC()));
    job.dimensions().add(dimensionName, dimensionValue);
    return job;
  }

  private TestScheduleUnit createTestScheduleUnit(JobScheduleUnit job, String testId) {
    return new TestScheduleUnit(
        new TestLocator(testId, testId, job.locator()), new Timing(Clock.systemUTC()));
  }

  private static class AllocationEventListener {

    private final SettableFuture<Boolean> allocationFuture = SettableFuture.create();

    @Subscribe
    public void onAllocation(AllocationEvent event) {
      allocationFuture.set(true);
    }

    private boolean awaitAllocation(Duration timeout) throws InterruptedException {
      try {
        return allocationFuture.get(timeout.toMillis(), MILLISECONDS);
      } catch (ExecutionException | TimeoutException | RuntimeException e) {
        return false;
      }
    }
  }
}
