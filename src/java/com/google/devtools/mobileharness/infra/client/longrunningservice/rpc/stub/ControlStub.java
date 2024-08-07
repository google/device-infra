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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub;

import static com.google.devtools.mobileharness.shared.util.comm.stub.Stubs.withDeadline;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc.ControlServiceBlockingStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc.ControlServiceStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.GetLogResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.HeartbeatRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.time.Duration;

/** Stub of {@link ControlServiceGrpc}. */
public class ControlStub {

  private final ControlServiceBlockingStub controlServiceBlockingStub;
  private final ControlServiceStub controlServiceStub;

  public ControlStub(Channel channel) {
    this.controlServiceBlockingStub = ControlServiceGrpc.newBlockingStub(channel);
    this.controlServiceStub = ControlServiceGrpc.newStub(channel);
  }

  @CanIgnoreReturnValue
  public KillServerResponse killServer(KillServerRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(controlServiceBlockingStub, Duration.ofSeconds(20L))::killServer,
        request,
        InfraErrorId.OLCS_STUB_KILL_SERVER_ERROR,
        "Failed to kill server");
  }

  public StreamObserver<GetLogRequest> getLog(StreamObserver<GetLogResponse> responseObserver) {
    return controlServiceStub.getLog(responseObserver);
  }

  public void setLogLevel(SetLogLevelRequest request) throws GrpcExceptionWithErrorId {
    GrpcStubUtil.invoke(
        withDeadline(controlServiceBlockingStub, Duration.ofSeconds(20L))::setLogLevel,
        request,
        InfraErrorId.OLCS_STUB_SET_LOG_LEVEL_ERROR,
        "Failed to set log level");
  }

  public void heartbeat(HeartbeatRequest request) throws GrpcExceptionWithErrorId {
    GrpcStubUtil.invoke(
        withDeadline(controlServiceBlockingStub, Duration.ofSeconds(20L))::heartbeat,
        request,
        InfraErrorId.OLCS_STUB_HEARTBEAT_ERROR,
        "Failed to send heartbeat");
  }
}
