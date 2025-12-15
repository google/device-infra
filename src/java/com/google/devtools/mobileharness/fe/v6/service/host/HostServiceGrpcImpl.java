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

package com.google.devtools.mobileharness.fe.v6.service.host;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the gRPC HostService. */
public final class HostServiceGrpcImpl extends HostServiceGrpc.HostServiceImplBase {

  private final HostServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  HostServiceGrpcImpl(HostServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getHostOverview(
      GetHostOverviewRequest request, StreamObserver<HostOverview> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostOverview,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());
  }

  @Override
  public void getHostDeviceSummaries(
      GetHostDeviceSummariesRequest request,
      StreamObserver<GetHostDeviceSummariesResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostDeviceSummaries,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostDeviceSummariesMethod());
  }

  @Override
  public void updatePassThroughFlags(
      UpdatePassThroughFlagsRequest request,
      StreamObserver<UpdatePassThroughFlagsResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::updatePassThroughFlags,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getUpdatePassThroughFlagsMethod());
  }
}
