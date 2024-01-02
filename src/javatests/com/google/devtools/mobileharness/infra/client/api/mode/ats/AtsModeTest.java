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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckRequest;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import io.grpc.BindableService;
import io.grpc.netty.NettyServerBuilder;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtsModeTest {

  private static final SignUpLabRequest SIGN_UP_LAB_REQUEST =
      SignUpLabRequest.newBuilder()
          .setVersionCheckRequest(
              VersionCheckRequest.newBuilder()
                  .setStubVersion(Version.LAB_VERSION.toString())
                  .setMinServiceVersion(Version.MIN_MASTER_V5_VERSION.toString()))
          .setLabIp("fake_lab_ip")
          .setLabHostName("fake_lab_host_name")
          .setLabServerSetting(
              LabServerSetting.newBuilder()
                  .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_RPC).setNum(1234)))
          .addDevice(
              SignUpLabRequest.Device.newBuilder()
                  .setControlId("fake_control_id")
                  .setUuid("fake_uuid")
                  .setTimestampMs(1234L)
                  .setStatus(DeviceStatus.IDLE)
                  .setFeature(
                      DeviceFeature.newBuilder()
                          .addOwner("fake_owner")
                          .addType("NoOpDevice")
                          .addDriver("NoOpDriver")
                          .addDecorator("NoOpDecorator")
                          .setCompositeDimension(
                              DeviceCompositeDimension.newBuilder()
                                  .addSupportedDimension(
                                      DeviceDimension.newBuilder()
                                          .setName("fake_dimension_name")
                                          .setValue("fake_dimension_value")))))
          .build();

  private static final SignUpLabResponse SIGN_UP_LAB_RESPONSE =
      SignUpLabResponse.newBuilder()
          .setVersionCheckResponse(
              VersionCheckResponse.newBuilder()
                  .setServiceVersion(Version.MASTER_V5_VERSION.toString()))
          .build();

  @Bind private MasterGrpcStubHelper masterGrpcStubHelper;
  @Bind private Sleeper sleeper;
  @Bind private ListeningScheduledExecutorService listeningScheduledExecutorService;
  @Bind private ExecutorService executorService;
  @Bind private ListeningExecutorService listeningExecutorService;

  @Inject private AtsMode atsMode;
  @Inject private LabSyncGrpcStub labSyncGrpcStub;
  @Inject private LabInfoGrpcStub labInfoGrpcStub;

  @Before
  public void setUp() throws Exception {
    int serverPort = PortProber.pickUnusedPort();
    masterGrpcStubHelper =
        new MasterGrpcStubHelper(ChannelFactory.createLocalChannel(serverPort, directExecutor()));
    sleeper = Sleeper.defaultSleeper();
    listeningExecutorService = ThreadPools.createStandardThreadPool("ats-mode-thread-pool");
    executorService = listeningExecutorService;
    listeningScheduledExecutorService =
        ThreadPools.createStandardScheduledThreadPool(
            "ats-mode-scheduled-thread-pool", /* corePoolSize= */ 5);

    Guice.createInjector(BoundFieldModule.of(this), new AtsModeModule()).injectMembers(this);

    atsMode.initialize(null);
    ImmutableList<BindableService> bindableServices = atsMode.getExtraServices();
    NettyServerBuilder nettyServerBuilder =
        NettyServerBuilder.forPort(serverPort).executor(listeningExecutorService);
    bindableServices.forEach(nettyServerBuilder::addService);
    nettyServerBuilder.build().start();
  }

  @Test
  public void deviceAllocator() throws Exception {
    // Creates JobInfo.
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_id", "fake_job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("NoOpDevice")
                    .setDriver("NoOpDriver")
                    .addDecorator("NoOpDecorator")
                    .build())
            .setJobUser(JobUser.newBuilder().setRunAs("fake_owner").build())
            .build();
    jobInfo.tests().add("fake_test_id", "fake_test_name");

    // Creates and sets up allocator.
    DeviceAllocator deviceAllocator =
        atsMode.createDeviceAllocator(jobInfo, /* globalInternalBus= */ null);
    assertThat(deviceAllocator.setUp()).isEmpty();
    assertThat(deviceAllocator.pollAllocations()).isEmpty();

    // Signs up device.
    SignUpLabResponse signUpLabResponse = labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);
    assertThat(signUpLabResponse).isEqualTo(SIGN_UP_LAB_RESPONSE);

    // Gets allocation.
    List<AllocationWithStats> allocations = ImmutableList.of();
    for (int i = 0; i < 5; i++) {
      allocations = deviceAllocator.pollAllocations();
      if (!allocations.isEmpty()) {
        break;
      }
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));
    }

    // Checks allocation.
    assertThat(allocations).hasSize(1);
    Allocation allocation = allocations.get(0).allocation();
    assertThat(allocation.getTest())
        .isEqualTo(
            TestLocator.of(
                "fake_test_id",
                "fake_test_name",
                com.google.devtools.mobileharness.api.model.job.JobLocator.of(
                    "fake_job_id", "fake_job_name")));
    LabLocator labLocator = LabLocator.of("fake_lab_ip", "fake_lab_host_name");
    labLocator.ports().add(PortType.LAB_SERVER_RPC, 1234);
    assertThat(allocation.getDevice()).isEqualTo(DeviceLocator.of("fake_uuid", labLocator));
  }

  @Test
  public void deviceQuerier() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    DeviceQueryResult deviceQueryResult =
        atsMode
            .createDeviceQuerier()
            .queryDevice(DeviceQueryFilter.newBuilder().addTypeRegex("NoOpDevice").build());

    assertThat(deviceQueryResult)
        .isEqualTo(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("fake_uuid")
                        .setStatus("IDLE")
                        .addOwner("fake_owner")
                        .addType("NoOpDevice")
                        .addDriver("NoOpDriver")
                        .addDecorator("NoOpDecorator")
                        .addDimension(
                            Dimension.newBuilder()
                                .setName("fake_dimension_name")
                                .setValue("fake_dimension_value")))
                .build());

    deviceQueryResult =
        atsMode
            .createDeviceQuerier()
            .queryDevice(DeviceQueryFilter.newBuilder().addTypeRegex("AndroidRealDevice").build());

    assertThat(deviceQueryResult).isEqualToDefaultInstance();
  }

  @Test
  public void labInfoService() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    assertThat(labInfoGrpcStub.getLabInfo(GetLabInfoRequest.getDefaultInstance()))
        .comparingExpectedFieldsOnly()
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setLabView(
                            LabView.newBuilder()
                                .setLabTotalCount(1)
                                .addLabData(
                                    LabData.newBuilder()
                                        .setLabInfo(
                                            LabInfo.newBuilder()
                                                .setLabLocator(
                                                    Lab.LabLocator.newBuilder()
                                                        .setHostName("fake_lab_host_name")))
                                        .setDeviceList(
                                            DeviceList.newBuilder()
                                                .setDeviceTotalCount(1)
                                                .addDeviceInfo(
                                                    LabQueryProto.DeviceInfo.newBuilder()
                                                        .setDeviceLocator(
                                                            Device.DeviceLocator.newBuilder()
                                                                .setId("fake_uuid"))
                                                        .setDeviceUuid("fake_uuid"))))))
                .build());
  }
}
