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
import com.google.devtools.mobileharness.infra.lab.rpc.service.StatServiceImpl;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetDeviceStatRequest;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetDeviceStatResponse;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetLabStatRequest;
import com.google.wireless.qa.mobileharness.lab.proto.StatServ.GetLabStatResponse;
import com.google.wireless.qa.mobileharness.lab.proto.StatServiceGrpc;
import com.google.wireless.qa.mobileharness.lab.proto.StatServiceGrpc.StatServiceImplBase;
import io.grpc.stub.StreamObserver;

/** gRPC service class for StatService. */
public class StatGrpcImpl extends StatServiceImplBase {

  private final StatServiceImpl base;

  public StatGrpcImpl() {
    base = new StatServiceImpl();
  }

  @Override
  public void getLabStat(
      GetLabStatRequest req, StreamObserver<GetLabStatResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::getLabStat,
        StatServiceGrpc.getServiceDescriptor(),
        StatServiceGrpc.getGetLabStatMethod());
  }

  @Override
  public void getDeviceStat(
      GetDeviceStatRequest req, StreamObserver<GetDeviceStatResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::getDeviceStat,
        StatServiceGrpc.getServiceDescriptor(),
        StatServiceGrpc.getGetDeviceStatMethod());
  }
}
