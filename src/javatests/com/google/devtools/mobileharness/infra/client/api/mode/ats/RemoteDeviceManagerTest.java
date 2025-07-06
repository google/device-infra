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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DecoratorMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DeviceUuidMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DimensionMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DriverMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.OwnerMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.StatusMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.TypeMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.IntegerMatch.Equal;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.LabFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.LabFilter.LabMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.LabFilter.LabMatchCondition.LabHostNameMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.LabFilter.LabMatchCondition.PropertyMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.AnyMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.NoneMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringListMatchCondition.SubsetMatch;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition.Include;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition.MatchesRegex;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMultimapMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.LabRecordManager.DeviceRecordData;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.LabRecordManager.LabRecordData;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest.Device;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.VersionCheckRequest;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RemoteDeviceManagerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

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
                  .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234)))
          .setLabServerFeature(
              LabServerFeature.newBuilder()
                  .setHostProperties(
                      HostProperties.newBuilder()
                          .addHostProperty(
                              HostProperty.newBuilder()
                                  .setKey("fake_property_key")
                                  .setValue("fake_property_value"))))
          .addDevice(
              Device.newBuilder()
                  .setControlId("fake_control_id")
                  .setUuid("fake_uuid")
                  .setStatus(DeviceStatus.IDLE)
                  .setFeature(
                      DeviceFeature.newBuilder()
                          .addOwner("fake_owner")
                          .addType("AndroidRealDevice")
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

  private static final HeartbeatLabRequest HEARTBEAT_LAB_REQUEST =
      HeartbeatLabRequest.newBuilder()
          .setLabIp("fake_lab_ip")
          .addDevice(
              HeartbeatLabRequest.Device.newBuilder()
                  .setId("fake_uuid")
                  .setStatus(DeviceStatus.IDLE)
                  .setTimestampMs(0L))
          .build();
  private static final LabInfo OLC_LAB_INFO =
      LabInfo.newBuilder()
          .setLabLocator(LabLocator.newBuilder().setHostName("fake_olc_lab_host_name"))
          .setLabStatus(LabStatus.LAB_RUNNING)
          .setLabServerSetting(LabServerSetting.getDefaultInstance())
          .setLabServerFeature(
              LabServerFeature.newBuilder().setHostProperties(HostProperties.getDefaultInstance()))
          .build();
  private static final LabInfo LAB_INFO =
      LabInfo.newBuilder()
          .setLabLocator(
              LabLocator.newBuilder()
                  .setIp("fake_lab_ip")
                  .setHostName("fake_lab_host_name")
                  .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234)))
          .setLabStatus(LabStatus.LAB_RUNNING)
          .setLabServerSetting(
              LabServerSetting.newBuilder()
                  .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234)))
          .setLabServerFeature(
              LabServerFeature.newBuilder()
                  .setHostProperties(
                      HostProperties.newBuilder()
                          .addHostProperty(
                              HostProperty.newBuilder()
                                  .setKey("fake_property_key")
                                  .setValue("fake_property_value"))))
          .build();

  private static final DeviceInfo DEVICE_INFO =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder()
                  .setId("fake_uuid")
                  .setLabLocator(
                      LabLocator.newBuilder()
                          .setIp("fake_lab_ip")
                          .setHostName("fake_lab_host_name")
                          .addPort(
                              LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234))))
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner("fake_owner")
                  .addType("AndroidRealDevice")
                  .addType("NoOpDevice")
                  .addDriver("NoOpDriver")
                  .addDecorator("NoOpDecorator")
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("fake_dimension_name")
                                  .setValue("fake_dimension_value"))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("host_ip")
                                  .setValue("fake_lab_ip"))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("host_name")
                                  .setValue("fake_lab_host_name"))))
          .build();
  private static final LabView LAB_VIEW =
      LabView.newBuilder()
          .setLabTotalCount(1)
          .addLabData(
              LabData.newBuilder()
                  .setLabInfo(LAB_INFO)
                  .setDeviceList(
                      DeviceList.newBuilder().setDeviceTotalCount(1).addDeviceInfo(DEVICE_INFO)))
          .build();
  private static final LabView LAB_VIEW_WITH_OLC =
      LabView.newBuilder()
          .setLabTotalCount(2)
          .addLabData(
              LabData.newBuilder()
                  .setLabInfo(OLC_LAB_INFO)
                  .setDeviceList(DeviceList.newBuilder().setDeviceTotalCount(0)))
          .addLabData(
              LabData.newBuilder()
                  .setLabInfo(LAB_INFO)
                  .setDeviceList(
                      DeviceList.newBuilder().setDeviceTotalCount(1).addDeviceInfo(DEVICE_INFO)))
          .build();

  @Bind @Mock @AtsModeAbstractScheduler private AbstractScheduler scheduler;
  @Bind @Mock private LabRecordManager labRecordManager;
  @Mock private AbstractScheduler scheduler2;
  @Mock private LabRecordManager labRecordManager2;
  @Bind @Mock private NetUtil netUtil;
  @Bind private ListeningScheduledExecutorService scheduledThreadPool;
  private ListeningScheduledExecutorService scheduledThreadPool2;

  @Bind private MasterGrpcStubHelper masterGrpcStubHelper;
  private MasterGrpcStubHelper masterGrpcStubHelper2;

  @Inject private RemoteDeviceManager remoteDeviceManager;
  private RemoteDeviceManager remoteDeviceManagerWithMockNetUtil;
  @Inject private LabSyncGrpcStub labSyncGrpcStub;
  private LabSyncGrpcStub labSyncGrpcStub2;

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

    int grpcPort2 = PortProber.pickUnusedPort();
    scheduledThreadPool2 = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(5));
    masterGrpcStubHelper2 =
        new MasterGrpcStubHelper(ChannelFactory.createLocalChannel(grpcPort2, directExecutor()));
    labSyncGrpcStub2 = new LabSyncGrpcStub(masterGrpcStubHelper2);
    when(netUtil.getLocalHostName()).thenReturn("fake_olc_lab_host_name");

    remoteDeviceManagerWithMockNetUtil =
        new RemoteDeviceManager(scheduler2, scheduledThreadPool2, labRecordManager2, netUtil);
    NettyServerBuilder.forPort(grpcPort2)
        .addService(remoteDeviceManagerWithMockNetUtil.getLabSyncService())
        .build()
        .start();
    remoteDeviceManagerWithMockNetUtil.start();
  }

  @Test
  public void getLabInfo() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    LabView labInfo = remoteDeviceManagerWithMockNetUtil.getLabInfos(Filter.getDefaultInstance());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withLabFilter_withLabHostNameMatchCondition() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    LabView labInfo =
        remoteDeviceManager.getLabInfos(
            Filter.newBuilder()
                .setLabFilter(
                    LabFilter.newBuilder()
                        .addLabMatchCondition(
                            LabMatchCondition.newBuilder()
                                .setLabHostNameMatchCondition(
                                    LabHostNameMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMatchCondition.newBuilder()
                                                .setInclude(
                                                    Include.newBuilder()
                                                        .addExpected("fake_lab_HOST_name")
                                                        .addExpected("whatever"))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW);

    labInfo =
        remoteDeviceManager.getLabInfos(
            Filter.newBuilder()
                .setLabFilter(
                    LabFilter.newBuilder()
                        .addLabMatchCondition(
                            LabMatchCondition.newBuilder()
                                .setLabHostNameMatchCondition(
                                    LabHostNameMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMatchCondition.newBuilder()
                                                .setInclude(
                                                    Include.newBuilder()
                                                        .addExpected("whatever"))))))
                .build());

    assertThat(labInfo)
        .isEqualTo(LAB_VIEW_WITH_OLC.toBuilder().clearLabData().clearLabTotalCount().build());
  }

  @Test
  public void getLabInfo_withLabFilter_withPropertyMatchCondition() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    LabView labInfo =
        remoteDeviceManager.getLabInfos(
            Filter.newBuilder()
                .setLabFilter(
                    LabFilter.newBuilder()
                        .addLabMatchCondition(
                            LabMatchCondition.newBuilder()
                                .setPropertyMatchCondition(
                                    PropertyMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMultimapMatchCondition.newBuilder()
                                                .setKey("fake_property_key")
                                                .setValueCondition(
                                                    StringListMatchCondition.newBuilder()
                                                        .setAnyMatch(
                                                            AnyMatch.newBuilder()
                                                                .setCondition(
                                                                    StringMatchCondition
                                                                        .newBuilder()
                                                                        .setInclude(
                                                                            Include.newBuilder()
                                                                                .addExpected(
                                                                                    "fake_property_value")))))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW);

    // Test StringMultimapMatchCondition with key only.
    labInfo =
        remoteDeviceManager.getLabInfos(
            Filter.newBuilder()
                .setLabFilter(
                    LabFilter.newBuilder()
                        .addLabMatchCondition(
                            LabMatchCondition.newBuilder()
                                .setPropertyMatchCondition(
                                    PropertyMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMultimapMatchCondition.newBuilder()
                                                .setKey("fake_property_key")))))
                .build());
    // The lab is not filtered out because the value condition is not set.
    assertThat(labInfo)
        .isEqualTo(LAB_VIEW_WITH_OLC.toBuilder().clearLabData().clearLabTotalCount().build());

    // Test StringMultimapMatchCondition with key and value condition with IntegerMatch.
    labInfo =
        remoteDeviceManager.getLabInfos(
            Filter.newBuilder()
                .setLabFilter(
                    LabFilter.newBuilder()
                        .addLabMatchCondition(
                            LabMatchCondition.newBuilder()
                                .setPropertyMatchCondition(
                                    PropertyMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMultimapMatchCondition.newBuilder()
                                                .setKey("fake_property_key")
                                                .setValueCondition(
                                                    StringListMatchCondition.newBuilder()
                                                        .setLengthMatch(
                                                            IntegerMatch.newBuilder()
                                                                .setEqual(
                                                                    Equal.newBuilder()
                                                                        .setValue(1))))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withDeviceUuidMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test StringMatchCondition with MatchesRegex.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDeviceUuidMatchCondition(
                                    DeviceUuidMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMatchCondition.newBuilder()
                                                .setMatchesRegex(
                                                    MatchesRegex.newBuilder()
                                                        .setRegex("fake_.*"))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);

    labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDeviceUuidMatchCondition(
                                    DeviceUuidMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMatchCondition.newBuilder()
                                                .setMatchesRegex(
                                                    MatchesRegex.newBuilder()
                                                        .setRegex("whatever"))))))
                .build());

    LabView.Builder labViewBuilder = LAB_VIEW_WITH_OLC.toBuilder();
    labViewBuilder
        .getLabDataBuilder(1)
        .getDeviceListBuilder()
        .clearDeviceInfo()
        .clearDeviceTotalCount();
    assertThat(labInfo).isEqualTo(labViewBuilder.build());
  }

  @Test
  public void getLabInfo_withDeviceFilter_withStatusMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test StringMatchCondition with Include.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setStatusMatchCondition(
                                    StatusMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMatchCondition.newBuilder()
                                                .setInclude(
                                                    Include.newBuilder().addExpected("idle"))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withTypeMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test AnyMatch with Include.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setTypeMatchCondition(
                                    TypeMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setAnyMatch(
                                                    AnyMatch.newBuilder()
                                                        .setCondition(
                                                            StringMatchCondition.newBuilder()
                                                                .setInclude(
                                                                    Include.newBuilder()
                                                                        .addExpected(
                                                                            "noopDevice"))))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);

    // Test SubsetMatch.
    labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setTypeMatchCondition(
                                    TypeMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setSubsetMatch(
                                                    SubsetMatch.newBuilder()
                                                        .addExpected("noopDevice")
                                                        .addExpected("AndroidRealDevice"))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);

    // Test SubsetMatch.
    labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setTypeMatchCondition(
                                    TypeMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setSubsetMatch(
                                                    SubsetMatch.newBuilder()
                                                        .addExpected("AndroidRealDevice"))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withOwnerMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test AnyMatch with MatchesRegex.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setOwnerMatchCondition(
                                    OwnerMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setAnyMatch(
                                                    AnyMatch.newBuilder()
                                                        .setCondition(
                                                            StringMatchCondition.newBuilder()
                                                                .setMatchesRegex(
                                                                    MatchesRegex.newBuilder()
                                                                        .setRegex("fake_.*"))))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withDriverMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test NoneMatch with Include.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDriverMatchCondition(
                                    DriverMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setNoneMatch(
                                                    NoneMatch.newBuilder()
                                                        .setCondition(
                                                            StringMatchCondition.newBuilder()
                                                                .setInclude(
                                                                    Include.newBuilder()
                                                                        .addExpected("none"))))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withDecoratorMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    // Test NoneMatch with MatchesRegex.
    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDecoratorMatchCondition(
                                    DecoratorMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setNoneMatch(
                                                    NoneMatch.newBuilder()
                                                        .setCondition(
                                                            StringMatchCondition.newBuilder()
                                                                .setMatchesRegex(
                                                                    MatchesRegex.newBuilder()
                                                                        .setRegex("fake_.*"))))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);

    // Test IntegerMatch with MatchesRegex.
    labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDecoratorMatchCondition(
                                    DecoratorMatchCondition.newBuilder()
                                        .setCondition(
                                            StringListMatchCondition.newBuilder()
                                                .setLengthMatch(
                                                    IntegerMatch.newBuilder()
                                                        .setEqual(
                                                            Equal.newBuilder().setValue(1)))))))
                .build());
    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void getLabInfo_withDeviceFilter_withDimensionMatchCondition() throws Exception {
    labSyncGrpcStub2.signUpLab(SIGN_UP_LAB_REQUEST);

    LabView labInfo =
        remoteDeviceManagerWithMockNetUtil.getLabInfos(
            Filter.newBuilder()
                .setDeviceFilter(
                    DeviceFilter.newBuilder()
                        .addDeviceMatchCondition(
                            DeviceMatchCondition.newBuilder()
                                .setDimensionMatchCondition(
                                    DimensionMatchCondition.newBuilder()
                                        .setCondition(
                                            StringMultimapMatchCondition.newBuilder()
                                                .setKey("FAKE_DIMENSION_NAME")
                                                .setValueCondition(
                                                    StringListMatchCondition.newBuilder()
                                                        .setAnyMatch(
                                                            AnyMatch.newBuilder()
                                                                .setCondition(
                                                                    StringMatchCondition
                                                                        .newBuilder()
                                                                        .setInclude(
                                                                            Include.newBuilder()
                                                                                .addExpected(
                                                                                    "FAKE_DIMENSION_VALUE")))))))))
                .build());

    assertThat(labInfo).isEqualTo(LAB_VIEW_WITH_OLC);
  }

  @Test
  public void signUpLab_heartbeatLab_addLabAndDeviceRecord() throws Exception {
    ArgumentCaptor<DeviceRecordData> deviceRecordData1 =
        ArgumentCaptor.forClass(DeviceRecordData.class);
    ArgumentCaptor<LabRecordData> labRecordData1 = ArgumentCaptor.forClass(LabRecordData.class);

    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);

    verify(labRecordManager).addLabRecordIfLabInfoChanged(labRecordData1.capture());
    assertThat(labRecordData1.getValue().toRecordProto().getLabInfo()).isEqualTo(LAB_INFO);

    verify(labRecordManager).addDeviceRecordIfDeviceInfoChanged(deviceRecordData1.capture());
    assertThat(deviceRecordData1.getValue().toRecordProto().getDeviceInfo()).isEqualTo(DEVICE_INFO);

    ArgumentCaptor<DeviceRecordData> deviceRecordData2 =
        ArgumentCaptor.forClass(DeviceRecordData.class);
    ArgumentCaptor<LabRecordData> labRecordData2 = ArgumentCaptor.forClass(LabRecordData.class);

    labSyncGrpcStub.heartbeatLab(HEARTBEAT_LAB_REQUEST);

    verify(labRecordManager).addLabRecordIfLabInfoChanged(labRecordData2.capture());
    assertThat(labRecordData2.getValue().toRecordProto().getLabInfo()).isEqualTo(LAB_INFO);

    verify(labRecordManager).addDeviceRecordIfDeviceInfoChanged(deviceRecordData2.capture());
    assertThat(deviceRecordData2.getValue().toRecordProto().getDeviceInfo()).isEqualTo(DEVICE_INFO);
  }

  @Test
  public void getDeviceInfos() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);
    ImmutableList<DeviceQuery.DeviceInfo> deviceInfos = remoteDeviceManager.getDeviceInfos();
    assertThat(deviceInfos).hasSize(1);
    DeviceQuery.DeviceInfo deviceInfo = deviceInfos.get(0);
    assertThat(deviceInfo.getDimensionList())
        .contains(
            Dimension.newBuilder()
                .setName("host_ip")
                .setValue(SIGN_UP_LAB_REQUEST.getLabIp())
                .build());
    assertThat(deviceInfo.getDimensionList())
        .contains(
            Dimension.newBuilder()
                .setName("host_name")
                .setValue(SIGN_UP_LAB_REQUEST.getLabHostName())
                .build());
  }

  @Test
  public void getDeviceInfos_updateLabAndDevice() throws Exception {
    labSyncGrpcStub.signUpLab(SIGN_UP_LAB_REQUEST);
    labSyncGrpcStub.signUpLab(
        SIGN_UP_LAB_REQUEST.toBuilder()
            .setDevice(0, SIGN_UP_LAB_REQUEST.getDevice(0).toBuilder().setTimestampMs(12345L))
            .build());
    ImmutableList<DeviceQuery.DeviceInfo> deviceInfos = remoteDeviceManager.getDeviceInfos();
    assertThat(deviceInfos).hasSize(1);
    DeviceQuery.DeviceInfo deviceInfo = deviceInfos.get(0);
    assertThat(deviceInfo.getDimensionList())
        .contains(
            Dimension.newBuilder()
                .setName("host_ip")
                .setValue(SIGN_UP_LAB_REQUEST.getLabIp())
                .build());
    assertThat(deviceInfo.getDimensionList())
        .contains(
            Dimension.newBuilder()
                .setName("host_name")
                .setValue(SIGN_UP_LAB_REQUEST.getLabHostName())
                .build());
  }
}
