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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.grpc;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyLabConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyLabConfigsResponse;
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
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import com.google.inject.Inject;

/** Blocking GRPC stub class for talking to device config service. */
final class DeviceConfigGrpcStub implements DeviceConfigStub {

  private final DeviceConfigServiceGrpc.DeviceConfigServiceBlockingStub deviceConfigStub;
  private final DeviceConfigServiceGrpc.DeviceConfigServiceFutureStub deviceConfigServiceFutureStub;

  @Inject
  DeviceConfigGrpcStub(
      DeviceConfigServiceGrpc.DeviceConfigServiceBlockingStub deviceConfigStub,
      DeviceConfigServiceGrpc.DeviceConfigServiceFutureStub deviceConfigServiceFutureStub) {
    this.deviceConfigStub = deviceConfigStub;
    this.deviceConfigServiceFutureStub = deviceConfigServiceFutureStub;
  }

  @Override
  public GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::getDeviceConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_GET_DEVICE_CONFIGS_ERROR,
        String.format(
            "Failed to get device config for device %s",
            request.getDeviceLocatorList().stream()
                .map(DeviceLocator::getDeviceUuid)
                .collect(toImmutableList())));
  }

  @Override
  public ListenableFuture<GetDeviceConfigsResponse> getDeviceConfigsAsync(
      GetDeviceConfigsRequest request, boolean useClientRpcAuthority) {
    if (useClientRpcAuthority) {
      throw new UnsupportedOperationException(
          "useClientRpcAuthority is not supported in gRPC stub");
    }
    return GrpcStubUtil.invokeAsync(
        deviceConfigServiceFutureStub::getDeviceConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_GET_DEVICE_CONFIGS_ERROR,
        String.format(
            "Failed to get device config for device %s",
            request.getDeviceLocatorList().stream()
                .map(DeviceLocator::getDeviceUuid)
                .collect(toImmutableList())));
  }

  @Override
  public GetLabConfigResponse getLabConfig(GetLabConfigRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::getLabConfig,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_GET_LAB_CONFIG_ERROR,
        String.format("Failed to get config for lab %s", request.getLabHost()));
  }

  @Override
  public ListenableFuture<GetLabConfigResponse> getLabConfigAsync(
      GetLabConfigRequest request, boolean useClientRpcAuthority) {
    if (useClientRpcAuthority) {
      throw new UnsupportedOperationException(
          "useClientRpcAuthority is not supported in gRPC stub");
    }
    return GrpcStubUtil.invokeAsync(
        deviceConfigServiceFutureStub::getLabConfig,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_GET_LAB_CONFIG_ERROR,
        String.format("Failed to get config for lab %s", request.getLabHost()));
  }

  @Override
  public DeleteDeviceConfigsResponse deleteDeviceConfigs(DeleteDeviceConfigsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::deleteDeviceConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_DELETE_DEVICE_CONFIGS_ERROR,
        String.format("Failed to device config for device %s ", request.getDeviceUuidList()));
  }

  @Override
  public DeleteLabConfigResponse deleteLabConfig(DeleteLabConfigRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::deleteLabConfig,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_DELETE_LAB_CONFIG_ERROR,
        String.format("Failed to delete config for lab %s", request.getLabHost()));
  }

  @Override
  public UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::updateDeviceConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_UPDATE_DEVICE_CONFIGS_ERROR,
        String.format(
            "Failed to update config for device %s",
            request.getDeviceLocatorConfigList().stream()
                .map(DeviceLocatorConfigPair::getDeviceLocator)
                .map(DeviceLocator::getDeviceUuid)
                .collect(toImmutableList())));
  }

  @Override
  public UpdateLabConfigResponse updateLabConfig(UpdateLabConfigRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::updateLabConfig,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_UPDATE_LAB_CONFIG_ERROR,
        String.format("Failed to update config for lab %s", request.getLabConfig().getHostName()));
  }

  @Override
  public CopyDeviceConfigsResponse copyDeviceConfigs(CopyDeviceConfigsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::copyDeviceConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_COPY_DEVICE_CONFIGS_ERROR,
        String.format(
            "Failed to copy configs from devices %s to devices %s",
            request.getSourceUuid(), request.getDestUuidList()));
  }

  @Override
  public CopyLabConfigsResponse copyLabConfigs(CopyLabConfigsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        deviceConfigStub::copyLabConfigs,
        request,
        InfraErrorId.DEVICE_CONFIG_RPC_STUB_COPY_LAB_CONFIG__ERROR,
        String.format(
            "Failed to copy config from lab %s to lab %s",
            request.getSourceHost(), request.getDestHostList()));
  }
}
