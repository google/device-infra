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

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceGrpc.ControlServiceBlockingStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import io.grpc.Channel;

/** Stub of {@link ControlServiceGrpc}. */
public class ControlStub {

  private final ControlServiceBlockingStub controlServiceStub;

  public ControlStub(Channel channel) {
    this.controlServiceStub = ControlServiceGrpc.newBlockingStub(channel);
  }

  public KillServerResponse killServer() throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        controlServiceStub::killServer,
        KillServerRequest.getDefaultInstance(),
        InfraErrorId.OLCS_STUB_KILL_SERVER_ERROR,
        "Failed to kill server");
  }
}
