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

package com.google.devtools.mobileharness.infra.client.api.util.stub;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.GrpcLocator;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StubUtilsTest {

  @Rule public final SetFlags setFlags = new SetFlags();

  @Test
  public void getGrpcTargetByIp_ipv4() {
    String target = StubUtils.getGrpcTargetByIp("127.0.0.1", 9994);
    assertThat(target).isEqualTo("127.0.0.1:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getGrpcTargetByIp_ipv6Uncompressed() {
    String target = StubUtils.getGrpcTargetByIp("0:0:0:0:0:0:0:1", 9994);
    assertThat(target).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getGrpcTargetByIp_ipv6Compressed() {
    String target = StubUtils.getGrpcTargetByIp("::1", 9994);
    assertThat(target).isEqualTo("[::1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();

    String target2 = StubUtils.getGrpcTargetByIp("2001:db8::1", 80);
    assertThat(target2).isEqualTo("[2001:db8::1]:80");
    assertThat(NettyChannelBuilder.forTarget(target2)).isNotNull();
  }

  @Test
  public void getGrpcTargetByIp_ipv6AlreadyBracketed() {
    String target = StubUtils.getGrpcTargetByIp("[0:0:0:0:0:0:0:1]", 9994);
    assertThat(target).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();

    String target2 = StubUtils.getGrpcTargetByIp("[::1]", 9994);
    assertThat(target2).isEqualTo("[::1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target2)).isNotNull();
  }

  @Test
  public void getGrpcTargetByIp_reverseTunneling() {
    setFlags.set("reverse_tunneling_lab_server", "true");
    String target = StubUtils.getGrpcTargetByIp("0:0:0:0:0:0:0:1", 9994);
    assertThat(target).isEqualTo("localhost:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getGrpcTargetByHostName_standard() {
    String target = StubUtils.getGrpcTargetByHostName("lab-host", 9994);
    assertThat(target).isEqualTo("dns:///lab-host:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getGrpcTargetByHostName_ipv6() {
    String target = StubUtils.getGrpcTargetByHostName("0:0:0:0:0:0:0:1", 9994);
    assertThat(target).isEqualTo("dns:///[0:0:0:0:0:0:0:1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();

    String target2 = StubUtils.getGrpcTargetByHostName("[::1]", 9994);
    assertThat(target2).isEqualTo("dns:///[::1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target2)).isNotNull();
  }

  @Test
  public void getGrpcTargetByHostName_reverseTunneling() {
    setFlags.set("reverse_tunneling_lab_server", "true");
    String target = StubUtils.getGrpcTargetByHostName("lab-host", 9994);
    assertThat(target).isEqualTo("dns:///localhost:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingHostName() {
    setFlags.set("connect_to_lab_server_using_ip", "false");

    Lab.LabLocator labLocatorProto =
        Lab.LabLocator.newBuilder()
            .setIp("192.168.1.100")
            .setHostName("lab-host")
            .addPort(Lab.LabPort.newBuilder().setType(Lab.PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    String target = StubUtils.getLabServerGrpcTarget(labLocatorProto);
    assertThat(target).isEqualTo("dns:///lab-host:9994");
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingIp_ipv4() {
    setFlags.set("connect_to_lab_server_using_ip", "true");
    setFlags.set("connect_to_lab_server_using_master_detected_ip", "false");

    Lab.LabLocator labLocatorProto =
        Lab.LabLocator.newBuilder()
            .setIp("192.168.1.100")
            .setHostName("lab-host")
            .addPort(Lab.LabPort.newBuilder().setType(Lab.PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    String target = StubUtils.getLabServerGrpcTarget(labLocatorProto);
    assertThat(target).isEqualTo("192.168.1.100:9994");
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingMasterDetectedIp_ipv6() {
    setFlags.set("connect_to_lab_server_using_ip", "true");
    setFlags.set("connect_to_lab_server_using_master_detected_ip", "true");

    Lab.LabLocator labLocatorProto =
        Lab.LabLocator.newBuilder()
            .setIp("192.168.1.100")
            .setMasterDetectedIp("0:0:0:0:0:0:0:1")
            .setHostName("lab-host")
            .addPort(Lab.LabPort.newBuilder().setType(Lab.PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    String target = StubUtils.getLabServerGrpcTarget(labLocatorProto);
    assertThat(target).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getTestEngineGrpcTarget_connectUsingMasterDetectedIp_ipv6() {
    setFlags.set("connect_to_lab_server_using_ip", "true");
    setFlags.set("connect_to_lab_server_using_master_detected_ip", "true");

    Lab.LabLocator labLocatorProto =
        Lab.LabLocator.newBuilder()
            .setIp("192.168.1.100")
            .setMasterDetectedIp("0:0:0:0:0:0:0:1")
            .setHostName("lab-host")
            .addPort(Lab.LabPort.newBuilder().setType(Lab.PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();
    LabServerLocator labServerLocator =
        LabServerLocator.longRunningLabServer(LabLocator.of(labLocatorProto));

    TestEngine.TestEngineLocator testEngineLocator =
        TestEngine.TestEngineLocator.newBuilder()
            .setGrpcLocator(
                GrpcLocator.newBuilder()
                    .setHostIp("192.168.1.100")
                    .setHostName("lab-host")
                    .setGrpcPort(9995))
            .build();

    String target = StubUtils.getTestEngineGrpcTarget(labServerLocator, testEngineLocator);
    assertThat(target).isEqualTo("[0:0:0:0:0:0:0:1]:9995");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }

  @Test
  public void getTestEngineGrpcTarget_relayProto_masterDetectedIp_ipv6() {
    setFlags.set("connect_to_lab_server_using_ip", "true");
    setFlags.set("connect_to_lab_server_using_master_detected_ip", "true");

    DestinationProto.TestEngineLocator relayLocator =
        DestinationProto.TestEngineLocator.newBuilder()
            .setMasterDetectedIp("0:0:0:0:0:0:0:1")
            .setTestEngineLocator(
                TestEngine.TestEngineLocator.newBuilder()
                    .setGrpcLocator(
                        GrpcLocator.newBuilder()
                            .setHostIp("192.168.1.100")
                            .setHostName("lab-host")
                            .setGrpcPort(9995)))
            .build();

    String target = StubUtils.getTestEngineGrpcTarget(relayLocator);
    assertThat(target).isEqualTo("[0:0:0:0:0:0:0:1]:9995");
    assertThat(NettyChannelBuilder.forTarget(target)).isNotNull();
  }
}
