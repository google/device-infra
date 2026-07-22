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

package com.google.devtools.mobileharness.fe.v6.service.test;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.GetTestResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.test.TestServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** gRPC implementation of the TestService. */
public final class TestServiceGrpcImpl extends TestServiceGrpc.TestServiceImplBase {

  private final TestServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  TestServiceGrpcImpl(TestServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getTest(GetTestRequest request, StreamObserver<GetTestResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getTest,
        executor,
        TestServiceGrpc.getServiceDescriptor(),
        TestServiceGrpc.getGetTestMethod());
  }

  @Override
  public void getTestLog(
      GetTestLogRequest request, StreamObserver<GetTestLogResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getTestLog,
        executor,
        TestServiceGrpc.getServiceDescriptor(),
        TestServiceGrpc.getGetTestLogMethod());
  }
}
