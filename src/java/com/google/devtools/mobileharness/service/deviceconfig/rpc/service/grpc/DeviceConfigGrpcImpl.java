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

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc.DeviceConfigServiceImplBase;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigResponse;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.service.DeviceConfigServiceImpl;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the DeviceConfigService gRPC service. */
public final class DeviceConfigGrpcImpl extends DeviceConfigServiceImplBase {
  private final DeviceConfigServiceImpl deviceConfigServiceImpl;

  @Inject
  DeviceConfigGrpcImpl(DeviceConfigServiceImpl deviceConfigServiceImpl) {
    this.deviceConfigServiceImpl = deviceConfigServiceImpl;
  }

  @Override
  public void getDeviceConfigs(
      GetDeviceConfigsRequest request, StreamObserver<GetDeviceConfigsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::getDeviceConfigs,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getGetDeviceConfigsMethod());
  }

  @Override
  public void updateDeviceConfigs(
      UpdateDeviceConfigsRequest request,
      StreamObserver<UpdateDeviceConfigsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::updateDeviceConfigs,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getUpdateDeviceConfigsMethod());
  }

  @Override
  public void deleteDeviceConfigs(
      DeleteDeviceConfigsRequest request,
      StreamObserver<DeleteDeviceConfigsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::deleteDeviceConfigs,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getDeleteDeviceConfigsMethod());
  }

  @Override
  public void getLabConfig(
      GetLabConfigRequest request, StreamObserver<GetLabConfigResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::getLabConfig,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getGetLabConfigMethod());
  }

  @Override
  public void updateLabConfig(
      UpdateLabConfigRequest request, StreamObserver<UpdateLabConfigResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::updateLabConfig,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getUpdateLabConfigMethod());
  }

  @Override
  public void deleteLabConfig(
      DeleteLabConfigRequest request, StreamObserver<DeleteLabConfigResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        deviceConfigServiceImpl::deleteLabConfig,
        DeviceConfigServiceGrpc.getServiceDescriptor(),
        DeviceConfigServiceGrpc.getDeleteLabConfigMethod());
  }
}
