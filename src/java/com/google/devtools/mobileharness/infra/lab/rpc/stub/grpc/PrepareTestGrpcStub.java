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

package com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc;

import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceGrpc;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceGrpc.PrepareTestServiceBlockingStub;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineResponse;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import io.grpc.Channel;

/** gRPC stub of {@code PrepareTestService}. */
public class PrepareTestGrpcStub implements PrepareTestStub {

  private final PrepareTestServiceBlockingStub stub;

  public PrepareTestGrpcStub(Channel channel) {
    this.stub = PrepareTestServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public CreateTestResponse createTest(CreateTestRequest request) throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::createTest,
        request,
        InfraErrorId.LAB_RPC_PREPARE_TEST_CREATE_TEST_GRPC_ERROR,
        "Failed to create test, test_id=" + request.getTest().getTestId());
  }

  @Override
  public GetTestEngineStatusResponse getTestEngineStatus(GetTestEngineStatusRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getTestEngineStatus,
        request,
        InfraErrorId.LAB_RPC_PREPARE_TEST_GET_TEST_ENGINE_STATUS_GRPC_ERROR,
        "Failed to get test engine status, test_id=" + request.getTestId());
  }

  @Override
  public StartTestEngineResponse startTestEngine(StartTestEngineRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::startTestEngine,
        request,
        InfraErrorId.LAB_RPC_PREPARE_TEST_START_TEST_ENGINE_GRPC_ERROR,
        "Failed to start test engine, test_id=" + request.getTestId());
  }

  @Override
  public CloseTestResponse closeTest(CloseTestRequest request) throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::closeTest,
        request,
        InfraErrorId.LAB_RPC_PREPARE_TEST_CLOST_TEST_GRPC_ERROR,
        "Failed to close test, test_id=" + request.getTestId());
  }

  @Override
  public void close() {
    // This stub is not responsible for managing lifecycle of the channel.
  }
}
