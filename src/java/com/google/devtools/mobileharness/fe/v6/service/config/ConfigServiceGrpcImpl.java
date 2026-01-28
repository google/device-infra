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

package com.google.devtools.mobileharness.fe.v6.service.config;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the gRPC ConfigService. */
public final class ConfigServiceGrpcImpl extends ConfigServiceGrpc.ConfigServiceImplBase {

  private final ConfigServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  ConfigServiceGrpcImpl(ConfigServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getDeviceConfig(
      GetDeviceConfigRequest request, StreamObserver<GetDeviceConfigResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceConfig,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetDeviceConfigMethod());
  }

  @Override
  public void checkDeviceWritePermission(
      CheckDeviceWritePermissionRequest request,
      StreamObserver<CheckDeviceWritePermissionResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::checkDeviceWritePermission,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckDeviceWritePermissionMethod());
  }

  @Override
  public void updateDeviceConfig(
      UpdateDeviceConfigRequest request,
      StreamObserver<UpdateDeviceConfigResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::updateDeviceConfig,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getUpdateDeviceConfigMethod());
  }

  @Override
  public void getRecommendedWifi(
      GetRecommendedWifiRequest request,
      StreamObserver<GetRecommendedWifiResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getRecommendedWifi,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetRecommendedWifiMethod());
  }

  @Override
  public void getHostDefaultDeviceConfig(
      GetHostDefaultDeviceConfigRequest request,
      StreamObserver<GetHostDefaultDeviceConfigResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostDefaultDeviceConfig,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetHostDefaultDeviceConfigMethod());
  }

  @Override
  public void getHostConfig(
      GetHostConfigRequest request, StreamObserver<GetHostConfigResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostConfig,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetHostConfigMethod());
  }

  @Override
  public void checkHostWritePermission(
      CheckHostWritePermissionRequest request,
      StreamObserver<CheckHostWritePermissionResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::checkHostWritePermission,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckHostWritePermissionMethod());
  }

  @Override
  public void updateHostConfig(
      UpdateHostConfigRequest request, StreamObserver<UpdateHostConfigResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::updateHostConfig,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getUpdateHostConfigMethod());
  }
}
