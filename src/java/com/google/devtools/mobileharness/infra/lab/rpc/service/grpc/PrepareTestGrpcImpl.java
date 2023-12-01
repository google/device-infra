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
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceGrpc;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.CreateTestResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.GetTestEngineStatusResponse;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.StartTestEngineResponse;
import com.google.devtools.mobileharness.infra.lab.rpc.service.PrepareTestServiceImpl;
import io.grpc.stub.StreamObserver;

/** gRPC implementation for {@code PrepareTestService}. */
public class PrepareTestGrpcImpl extends PrepareTestServiceGrpc.PrepareTestServiceImplBase {

  private final PrepareTestServiceImpl impl;

  public PrepareTestGrpcImpl(PrepareTestServiceImpl impl) {
    this.impl = impl;
  }

  @Override
  public void createTest(
      CreateTestRequest req, StreamObserver<CreateTestResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        impl::createTest,
        PrepareTestServiceGrpc.getServiceDescriptor(),
        PrepareTestServiceGrpc.getCreateTestMethod());
  }

  @Override
  public void getTestEngineStatus(
      GetTestEngineStatusRequest req,
      StreamObserver<GetTestEngineStatusResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        impl::getTestEngineStatus,
        PrepareTestServiceGrpc.getServiceDescriptor(),
        PrepareTestServiceGrpc.getGetTestEngineStatusMethod());
  }

  @Override
  public void startTestEngine(
      StartTestEngineRequest req, StreamObserver<StartTestEngineResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        impl::startTestEngine,
        PrepareTestServiceGrpc.getServiceDescriptor(),
        PrepareTestServiceGrpc.getStartTestEngineMethod());
  }

  @Override
  public void closeTest(CloseTestRequest req, StreamObserver<CloseTestResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        impl::closeTest,
        PrepareTestServiceGrpc.getServiceDescriptor(),
        PrepareTestServiceGrpc.getCloseTestMethod());
  }
}
