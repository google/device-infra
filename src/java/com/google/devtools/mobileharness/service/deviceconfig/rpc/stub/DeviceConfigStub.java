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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.stub;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;

/** RPC stub interface for talking to Mobile Harness device config service. */
// TODO: Add more async methods to this interface.
public interface DeviceConfigStub {

  /** Gets a list of configurations of devices. */
  GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request)
      throws RpcExceptionWithErrorId;

  /** Gets a list of configurations of devices. */
  default GetDeviceConfigsResponse getDeviceConfigs(
      GetDeviceConfigsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getDeviceConfigs(request);
  }

  /** Gets a list of configurations of devices asynchronously. */
  ListenableFuture<GetDeviceConfigsResponse> getDeviceConfigsAsync(
      GetDeviceConfigsRequest request, boolean useClientRpcAuthority);

  /** Gets the configuration of a lab. */
  GetLabConfigResponse getLabConfig(GetLabConfigRequest request) throws RpcExceptionWithErrorId;

  /** Gets the configuration of a lab asynchronously. */
  ListenableFuture<GetLabConfigResponse> getLabConfigAsync(
      GetLabConfigRequest request, boolean useClientRpcAuthority);

  /** Deletes a list of configurations of devices. */
  DeleteDeviceConfigsResponse deleteDeviceConfigs(DeleteDeviceConfigsRequest request)
      throws RpcExceptionWithErrorId;

  /** Deletes the configuration of a lab. */
  DeleteLabConfigResponse deleteLabConfig(DeleteLabConfigRequest request)
      throws RpcExceptionWithErrorId;

  /** Updates a list of configurations of devices. */
  @CanIgnoreReturnValue
  UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request)
      throws RpcExceptionWithErrorId;

  /** Updates a list of configurations of devices. */
  default UpdateDeviceConfigsResponse updateDeviceConfigs(
      UpdateDeviceConfigsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return updateDeviceConfigs(request);
  }

  /** Updates the configuration of a lab. */
  @CanIgnoreReturnValue
  UpdateLabConfigResponse updateLabConfig(UpdateLabConfigRequest request)
      throws RpcExceptionWithErrorId;

  /** Copies the configuration of a device to multiple ones. */
  CopyDeviceConfigsResponse copyDeviceConfigs(CopyDeviceConfigsRequest request)
      throws RpcExceptionWithErrorId;

  /** Copies the configuration of a lab to multiple ones. */
  CopyLabConfigsResponse copyLabConfigs(CopyLabConfigsRequest request)
      throws RpcExceptionWithErrorId;
}
