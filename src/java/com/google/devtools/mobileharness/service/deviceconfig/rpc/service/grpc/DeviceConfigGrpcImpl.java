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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.service.grpc;

import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc.DeviceConfigServiceImplBase;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.service.DeviceConfigServiceImpl;
import io.grpc.stub.StreamObserver;

/** Implementation of the DeviceConfigService gRPC service. */
public final class DeviceConfigGrpcImpl extends DeviceConfigServiceImplBase {
  private final DeviceConfigServiceImpl deviceConfigServiceImpl;

  public DeviceConfigGrpcImpl(DeviceConfigServiceImpl deviceConfigServiceImpl) {
    this.deviceConfigServiceImpl = deviceConfigServiceImpl;
  }

  @Override
  public void getDeviceConfigs(
      GetDeviceConfigsRequest request, StreamObserver<GetDeviceConfigsResponse> responseObserver) {
    responseObserver.onNext(deviceConfigServiceImpl.getDeviceConfigs(request));
    responseObserver.onCompleted();
  }

  @Override
  public void updateDeviceConfigs(
      UpdateDeviceConfigsRequest request,
      StreamObserver<UpdateDeviceConfigsResponse> responseObserver) {
    responseObserver.onNext(deviceConfigServiceImpl.updateDeviceConfigs(request));
    responseObserver.onCompleted();
  }
}
