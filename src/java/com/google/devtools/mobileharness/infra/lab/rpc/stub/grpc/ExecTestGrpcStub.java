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
import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.ForwardTestMessageResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestDetailResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestGenDataResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.GetTestStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestRequest;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServ.KickOffTestResponse;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServiceGrpc;
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServiceGrpc.ExecTestServiceBlockingStub;
import io.grpc.Channel;

/** gRPC stub of {@code ExecTestService}. */
public class ExecTestGrpcStub implements ExecTestStub {

  private final ExecTestServiceBlockingStub stub;

  public ExecTestGrpcStub(Channel channel) {
    this.stub = ExecTestServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public KickOffTestResponse kickOffTest(KickOffTestRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::kickOffTest,
        request,
        InfraErrorId.LAB_RPC_EXEC_TEST_KICK_OFF_TEST_GRPC_ERROR,
        "Failed to kill off test, test_id=" + request.getTest().getTestId());
  }

  @Override
  public GetTestStatusResponse getTestStatus(GetTestStatusRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getTestStatus,
        request,
        InfraErrorId.LAB_RPC_EXEC_TEST_GET_TEST_STATUS_GRPC_ERROR,
        "Failed to get test status, test_id=" + request.getTestId());
  }

  @Override
  public GetTestDetailResponse getTestDetail(GetTestDetailRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getTestDetail,
        request,
        InfraErrorId.LAB_RPC_EXEC_TEST_GET_TEST_DETAIL_GRPC_ERROR,
        "Failed to get test detail, test_id=" + request.getTestId());
  }

  @Override
  public ForwardTestMessageResponse forwardTestMessage(ForwardTestMessageRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::forwardTestMessage,
        request,
        InfraErrorId.LAB_RPC_EXEC_TEST_FORWARD_TEST_MESSAGE_GRPC_ERROR,
        "Failed to forward test message, test_id=" + request.getTestId());
  }

  @Override
  public GetTestGenDataResponse getTestGenData(GetTestGenDataRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getTestGenData,
        request,
        InfraErrorId.LAB_RPC_EXEC_TEST_GET_TEST_GEN_DATA_GRPC_ERROR,
        "Failed to get test gen data, test_id=" + request.getTestId());
  }

  @Override
  public void close() {
    // This stub is not responsible for managing lifecycle of the channel.
  }
}
