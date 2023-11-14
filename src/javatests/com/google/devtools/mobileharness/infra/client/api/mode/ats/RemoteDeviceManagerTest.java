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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest.Device;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckRequest;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RemoteDeviceManagerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private AbstractScheduler scheduler;
  @Bind private ListeningScheduledExecutorService scheduledThreadPool;
  @Bind private MasterGrpcStubHelper masterGrpcStubHelper;

  @Inject private RemoteDeviceManager remoteDeviceManager;
  @Inject private LabSyncGrpcStub labSyncGrpcStub;

  @Before
  public void setUp() throws Exception {
    int grpcPort = PortProber.pickUnusedPort();
    scheduledThreadPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(5));
    masterGrpcStubHelper =
        new MasterGrpcStubHelper(ChannelFactory.createLocalChannel(grpcPort, directExecutor()));

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    NettyServerBuilder.forPort(grpcPort)
        .addService(remoteDeviceManager.getLabSyncService())
        .build()
        .start();
    remoteDeviceManager.start();
  }

  @Test
  public void getLabInfo() throws Exception {
    labSyncGrpcStub.signUpLab(
        SignUpLabRequest.newBuilder()
            .setVersionCheckRequest(
                VersionCheckRequest.newBuilder()
                    .setStubVersion(Version.LAB_VERSION.toString())
                    .setMinServiceVersion(Version.MIN_MASTER_V5_VERSION.toString()))
            .setLabIp("fake_lab_ip")
            .setLabHostName("fake_lab_host_name")
            .setLabServerSetting(
                LabServerSetting.newBuilder()
                    .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234)))
            .addDevice(
                Device.newBuilder()
                    .setControlId("fake_control_id")
                    .setUuid("fake_uuid")
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
            .build());

    LabView labInfo = remoteDeviceManager.getLabInfo(Filter.getDefaultInstance());

    assertThat(labInfo)
        .isEqualTo(
            LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(
                    LabData.newBuilder()
                        .setLabInfo(
                            LabInfo.newBuilder()
                                .setLabLocator(
                                    LabLocator.newBuilder()
                                        .setIp("fake_lab_ip")
                                        .setHostName("fake_lab_host_name")
                                        .addPort(
                                            LabPort.newBuilder()
                                                .setType(PortType.LAB_SERVER_HTTP)
                                                .setNum(1234)))
                                .setLabServerSetting(
                                    LabServerSetting.newBuilder()
                                        .addPort(
                                            LabPort.newBuilder()
                                                .setType(PortType.LAB_SERVER_HTTP)
                                                .setNum(1234)))
                                .setLabServerFeature(LabServerFeature.getDefaultInstance()))
                        .setDeviceList(
                            DeviceList.newBuilder()
                                .setDeviceTotalCount(1)
                                .addDeviceInfo(
                                    DeviceInfo.newBuilder()
                                        .setDeviceLocator(
                                            DeviceLocator.newBuilder()
                                                .setId("fake_control_id")
                                                .setLabLocator(
                                                    LabLocator.newBuilder()
                                                        .setIp("fake_lab_ip")
                                                        .setHostName("fake_lab_host_name")
                                                        .addPort(
                                                            LabPort.newBuilder()
                                                                .setType(PortType.LAB_SERVER_HTTP)
                                                                .setNum(1234))))
                                        .setDeviceUuid("fake_uuid")
                                        .setDeviceStatus(DeviceStatus.IDLE)
                                        .setDeviceFeature(
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
                                                                .setValue(
                                                                    "fake_dimension_value")))))))
                .build());
  }
}
