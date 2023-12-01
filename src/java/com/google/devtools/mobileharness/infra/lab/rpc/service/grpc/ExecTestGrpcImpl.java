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

package com.google.devtools.mobileharness.infra.lab.rpc.service.grpc;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
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
import com.google.wireless.qa.mobileharness.lab.proto.ExecTestServiceGrpc.ExecTestServiceImplBase;
import io.grpc.stub.StreamObserver;

/** gRPC implementation of Lab Server ExecTestService for RPC call via Bridge Server + CloudRPC. */
public class ExecTestGrpcImpl extends ExecTestServiceImplBase {

  private final ExecTestServiceImpl base;

  public ExecTestGrpcImpl(ExecTestServiceImpl service) {
    base = service;
  }

  @Override
  public void kickOffTest(
      KickOffTestRequest req, StreamObserver<KickOffTestResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::kickOffTest,
        ExecTestServiceGrpc.getServiceDescriptor(),
        ExecTestServiceGrpc.getKickOffTestMethod());
  }

  @Override
  public void getTestStatus(
      GetTestStatusRequest req, StreamObserver<GetTestStatusResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::getTestStatus,
        ExecTestServiceGrpc.getServiceDescriptor(),
        ExecTestServiceGrpc.getGetTestStatusMethod());
  }

  @Override
  public void getTestDetail(
      GetTestDetailRequest req, StreamObserver<GetTestDetailResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::getTestDetail,
        ExecTestServiceGrpc.getServiceDescriptor(),
        ExecTestServiceGrpc.getGetTestDetailMethod());
  }

  @Override
  public void getTestGenData(
      GetTestGenDataRequest req, StreamObserver<GetTestGenDataResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        request -> base.getTestGenData(request, /* encodeFilePath= */ false),
        ExecTestServiceGrpc.getServiceDescriptor(),
        ExecTestServiceGrpc.getGetTestGenDataMethod());
  }

  @Override
  public void forwardTestMessage(
      ForwardTestMessageRequest req, StreamObserver<ForwardTestMessageResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::forwardTestMessage,
        ExecTestServiceGrpc.getServiceDescriptor(),
        ExecTestServiceGrpc.getForwardTestMessageMethod());
  }
}
