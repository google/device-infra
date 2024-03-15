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
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.lab.rpc.service.DeviceStatusServiceImpl;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServ.GetDeviceStatusRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServ.GetDeviceStatusResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServiceGrpc;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceStatusServiceGrpc.DeviceStatusServiceImplBase;
import io.grpc.stub.StreamObserver;

/** gRPC service class for DeviceStatusService. */
public class DeviceStatusGrpcImpl extends DeviceStatusServiceImplBase {

  private final DeviceStatusServiceImpl base;

  public DeviceStatusGrpcImpl(LocalDeviceManager deviceManager) {
    base = new DeviceStatusServiceImpl(deviceManager);
  }

  @Override
  public void getDeviceStatus(
      GetDeviceStatusRequest req, StreamObserver<GetDeviceStatusResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        req,
        responseObserver,
        base::getDeviceStatus,
        DeviceStatusServiceGrpc.getServiceDescriptor(),
        DeviceStatusServiceGrpc.getGetDeviceStatusMethod());
  }
}
