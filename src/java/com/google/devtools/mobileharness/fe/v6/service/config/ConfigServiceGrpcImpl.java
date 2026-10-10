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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.grpc.FeGrpcInvoker;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
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
    FeGrpcInvoker.invokeAsync(
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
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req ->
            immediateFuture(
                CheckDeviceWritePermissionResponse.newBuilder().setHasPermission(true).build()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckDeviceWritePermissionMethod());
  }

  @Override
  public void checkDeviceConfigPermission(
      CheckDeviceConfigPermissionRequest request,
      StreamObserver<CheckDeviceConfigPermissionResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req ->
            immediateFuture(
                CheckDeviceConfigPermissionResponse.newBuilder().setHasPermission(true).build()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckDeviceConfigPermissionMethod());
  }

  @Override
  public void updateDeviceConfig(
      UpdateDeviceConfigRequest request,
      StreamObserver<UpdateDeviceConfigResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req -> logic.updateDeviceConfig(req, Optional.empty()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getUpdateDeviceConfigMethod());
  }

  @Override
  public void getRecommendedWifi(
      GetRecommendedWifiRequest request,
      StreamObserver<GetRecommendedWifiResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
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
    FeGrpcInvoker.invokeAsync(
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
    FeGrpcInvoker.invokeAsync(
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
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req ->
            immediateFuture(
                CheckHostWritePermissionResponse.newBuilder().setHasPermission(true).build()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckHostWritePermissionMethod());
  }

  @Override
  public void checkHostConfigPermission(
      CheckHostConfigPermissionRequest request,
      StreamObserver<CheckHostConfigPermissionResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req ->
            immediateFuture(
                CheckHostConfigPermissionResponse.newBuilder().setHasPermission(true).build()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getCheckHostConfigPermissionMethod());
  }

  @Override
  public void updateHostConfig(
      UpdateHostConfigRequest request, StreamObserver<UpdateHostConfigResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req -> logic.updateHostConfig(req, Optional.empty()),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getUpdateHostConfigMethod());
  }

  @Override
  public void unlockHostProperties(
      UnlockHostPropertiesRequest request,
      StreamObserver<UnlockHostPropertiesResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        req -> logic.unlockHostProperties(req),
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getUnlockHostPropertiesMethod());
  }

  @Override
  public void getConfigurableDimensions(
      GetConfigurableDimensionsRequest request,
      StreamObserver<GetConfigurableDimensionsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getConfigurableDimensions,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetConfigurableDimensionsMethod());
  }

  @Override
  public void batchGetConfigurableDimensionKeys(
      BatchGetConfigurableDimensionKeysRequest request,
      StreamObserver<BatchGetConfigurableDimensionKeysResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::batchGetConfigurableDimensionKeys,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getBatchGetConfigurableDimensionKeysMethod());
  }

  @Override
  public void getDimensionValueSuggestions(
      GetDimensionValueSuggestionsRequest request,
      StreamObserver<GetDimensionValueSuggestionsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getDimensionValueSuggestions,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetDimensionValueSuggestionsMethod());
  }

  @Override
  public void batchGetDeviceDimensionConfigs(
      BatchGetDeviceDimensionConfigsRequest request,
      StreamObserver<BatchGetDeviceDimensionConfigsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::batchGetDeviceDimensionConfigs,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getBatchGetDeviceDimensionConfigsMethod());
  }

  @Override
  public void batchUpdateDeviceDimensionConfigs(
      BatchUpdateDeviceDimensionConfigsRequest request,
      StreamObserver<BatchUpdateDeviceDimensionConfigsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::batchUpdateDeviceDimensionConfigs,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getBatchUpdateDeviceDimensionConfigsMethod());
  }

  @Override
  public void getWifiSuggestions(
      GetWifiSuggestionsRequest request,
      StreamObserver<GetWifiSuggestionsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::getWifiSuggestions,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getGetWifiSuggestionsMethod());
  }

  @Override
  public void batchGetDeviceWifiConfigs(
      BatchGetDeviceWifiConfigsRequest request,
      StreamObserver<BatchGetDeviceWifiConfigsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::batchGetDeviceWifiConfigs,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getBatchGetDeviceWifiConfigsMethod());
  }

  @Override
  public void batchUpdateDeviceWifiConfigs(
      BatchUpdateDeviceWifiConfigsRequest request,
      StreamObserver<BatchUpdateDeviceWifiConfigsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::batchUpdateDeviceWifiConfigs,
        executor,
        ConfigServiceGrpc.getServiceDescriptor(),
        ConfigServiceGrpc.getBatchUpdateDeviceWifiConfigsMethod());
  }
}
