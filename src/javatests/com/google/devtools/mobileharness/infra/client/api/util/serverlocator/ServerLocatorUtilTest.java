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

package com.google.devtools.mobileharness.infra.client.api.util.serverlocator;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil.getMasterServerLocator;
import static com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil.parseGrpcServerLocator;
import static com.google.devtools.mobileharness.infra.client.api.util.serverlocator.ServerLocatorUtil.parseServerLocator;
import static com.google.wireless.qa.mobileharness.shared.model.job.JobInfoMocker.mockJobInfo;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.GrpcServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ServerLocatorUtilTest {

  @Test
  public void getMasterServerLocator_empty() {
    JobInfo jobInfo = mockJobInfo();
    assertThat(getMasterServerLocator(jobInfo)).isEmpty();
  }

  @Test
  public void getMasterServerLocator_getLocalEndpoint() {
    JobInfo jobInfo = mockJobInfo();
    jobInfo.params().add(JobInfo.PARAM_MASTER_LOCAL_GRPC_TARGET, "localhost:9876");
    assertThat(getMasterServerLocator(jobInfo))
        .hasValue(
            ServerLocator.newBuilder()
                .setGrpcServerLocator(
                    GrpcServerLocator.newBuilder().setHostname("localhost").setPort(9876))
                .build());
  }

  @Test
  public void parseGrpcServerLocator_success() {
    assertThat(parseGrpcServerLocator("localhost:9876"))
        .isEqualTo(GrpcServerLocator.newBuilder().setHostname("localhost").setPort(9876).build());
    assertThat(parseGrpcServerLocator("127.0.0.1:9877"))
        .isEqualTo(GrpcServerLocator.newBuilder().setIp("127.0.0.1").setPort(9877).build());
    assertThat(parseGrpcServerLocator("[2001:db8::1]:80"))
        .isEqualTo(GrpcServerLocator.newBuilder().setIp("2001:db8::1").setPort(80).build());
  }

  @Test
  public void parseGrpcServerLocator_throwsException() {
    assertThrows(IllegalStateException.class, () -> parseGrpcServerLocator("localhost"));
  }

  @Test
  public void parseServerLocator_success() {
    assertThat(parseServerLocator("grpc:127.0.0.1:9876"))
        .isEqualTo(
            ServerLocator.newBuilder()
                .setGrpcServerLocator(
                    GrpcServerLocator.newBuilder().setIp("127.0.0.1").setPort(9876))
                .build());
  }
}
