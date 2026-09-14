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
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getGrpcTargetByHostName;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getGrpcTargetByIp;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getLabServerGrpcTarget;
import static com.google.devtools.mobileharness.infra.client.api.util.stub.StubUtils.getTestEngineGrpcTarget;

import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator.GrpcLocator;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.TestEngineLocator;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StubUtilsTest {

  @Rule public final SetFlags flags = new SetFlags();

  @Test
  public void getGrpcTargetByIp_ipv4_returnsUnbracketedTarget() {
    assertThat(getGrpcTargetByIp("127.0.0.1", 9994)).isEqualTo("127.0.0.1:9994");
    assertThat(getGrpcTargetByIp("192.168.1.100", 8080)).isEqualTo("192.168.1.100:8080");
  }

  @Test
  public void getGrpcTargetByIp_ipv6Uncompressed_returnsBracketedTarget() {
    assertThat(getGrpcTargetByIp("0:0:0:0:0:0:0:1", 9994)).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
    assertThat(getGrpcTargetByIp("2001:db8:0:0:0:0:0:1", 8080))
        .isEqualTo("[2001:db8:0:0:0:0:0:1]:8080");
  }

  @Test
  public void getGrpcTargetByIp_ipv6Compressed_returnsBracketedTarget() {
    assertThat(getGrpcTargetByIp("::1", 9994)).isEqualTo("[::1]:9994");
    assertThat(getGrpcTargetByIp("2001:db8::1", 8080)).isEqualTo("[2001:db8::1]:8080");
  }

  @Test
  public void getGrpcTargetByIp_ipv6AlreadyBracketed_returnsBracketedTarget() {
    assertThat(getGrpcTargetByIp("[::1]", 9994)).isEqualTo("[::1]:9994");
  }

  @Test
  public void getGrpcTargetByIp_reverseTunnelingLabServer_returnsLocalhost() {
    flags.set("reverse_tunneling_lab_server", "true");

    assertThat(getGrpcTargetByIp("0:0:0:0:0:0:0:1", 9994)).isEqualTo("localhost:9994");
  }

  @Test
  public void getGrpcTargetByHostName_standard_returnsDnsTarget() {
    assertThat(getGrpcTargetByHostName("lab-host", 9994)).isEqualTo("dns:///lab-host:9994");
  }

  @Test
  public void getGrpcTargetByHostName_reverseTunnelingLabServer_returnsDnsLocalhost() {
    flags.set("reverse_tunneling_lab_server", "true");

    assertThat(getGrpcTargetByHostName("lab-host", 9994)).isEqualTo("dns:///localhost:9994");
  }

  @Test
  public void getLabServerGrpcTarget_byHostnameByDefault() {
    LabLocator labLocator =
        LabLocator.newBuilder()
            .setIp("127.0.0.1")
            .setHostName("lab-host")
            .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    assertThat(getLabServerGrpcTarget(labLocator)).isEqualTo("dns:///lab-host:9994");
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingIp_ipv4() {
    flags.set("connect_to_lab_server_using_ip", "true");

    LabLocator labLocator =
        LabLocator.newBuilder()
            .setIp("127.0.0.1")
            .setHostName("lab-host")
            .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    assertThat(getLabServerGrpcTarget(labLocator)).isEqualTo("127.0.0.1:9994");
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingIp_ipv6() {
    flags.set("connect_to_lab_server_using_ip", "true");

    LabLocator labLocator =
        LabLocator.newBuilder()
            .setIp("0:0:0:0:0:0:0:1")
            .setHostName("lab-host")
            .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    assertThat(getLabServerGrpcTarget(labLocator)).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
  }

  @Test
  public void getLabServerGrpcTarget_connectUsingMasterDetectedIp_ipv6() {
    flags.set("connect_to_lab_server_using_ip", "true");
    flags.set("connect_to_lab_server_using_master_detected_ip", "true");

    LabLocator labLocator =
        LabLocator.newBuilder()
            .setIp("127.0.0.1")
            .setHostName("lab-host")
            .setMasterDetectedIp("0:0:0:0:0:0:0:1")
            .addPort(LabPort.newBuilder().setType(PortType.LAB_SERVER_GRPC).setNum(9994))
            .build();

    assertThat(getLabServerGrpcTarget(labLocator)).isEqualTo("[0:0:0:0:0:0:0:1]:9994");
  }

  @Test
  public void getGrpcTargetByIp_ipv6WithScopeId_returnsBracketedTarget() {
    assertThat(getGrpcTargetByIp("fe80::1%eth0", 9994)).isEqualTo("[fe80::1%eth0]:9994");
  }

  @Test
  public void getTestEngineGrpcTarget_connectUsingMasterDetectedIp_ipv6() {
    flags.set("connect_to_lab_server_using_ip", "true");
    flags.set("connect_to_lab_server_using_master_detected_ip", "true");

    TestEngineLocator testEngineLocator =
        TestEngineLocator.newBuilder()
            .setMasterDetectedIp("::1")
            .setTestEngineLocator(
                TestEngine.TestEngineLocator.newBuilder()
                    .setGrpcLocator(GrpcLocator.newBuilder().setGrpcPort(8888)))
            .build();

    assertThat(getTestEngineGrpcTarget(testEngineLocator)).isEqualTo("[::1]:8888");
  }

  @Test
  public void getTestEngineGrpcTarget_connectUsingHostIp_ipv6() {
    flags.set("connect_to_lab_server_using_ip", "true");

    TestEngineLocator testEngineLocator =
        TestEngineLocator.newBuilder()
            .setTestEngineLocator(
                TestEngine.TestEngineLocator.newBuilder()
                    .setGrpcLocator(
                        GrpcLocator.newBuilder().setHostIp("0:0:0:0:0:0:0:1").setGrpcPort(8888)))
            .build();

    assertThat(getTestEngineGrpcTarget(testEngineLocator)).isEqualTo("[0:0:0:0:0:0:0:1]:8888");
  }
}
