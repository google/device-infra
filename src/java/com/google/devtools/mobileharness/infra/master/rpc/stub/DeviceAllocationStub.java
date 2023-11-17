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

package com.google.devtools.mobileharness.infra.master.rpc.stub;

import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.infra.master.rpc.proto.DeviceAllocationServiceProto.AllocationRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.DeviceAllocationServiceProto.AllocationResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.DeviceAllocationServiceProto.DeallocationRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.DeviceAllocationServiceProto.DeallocationResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.DeviceAllocationServiceProto.GetAvailableDevicesResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** RPC stub for talking to Master DeviceAllocationService. */
public interface DeviceAllocationStub extends AutoCloseable {
  /** Allocates devices. */
  AllocationResponse allocate(AllocationRequest request) throws RpcExceptionWithErrorId;

  /** Deallocates devices. */
  @CanIgnoreReturnValue
  DeallocationResponse deallocate(DeallocationRequest request) throws RpcExceptionWithErrorId;

  /** Gets the available devices. */
  GetAvailableDevicesResponse getAvailableDevices() throws RpcExceptionWithErrorId;
}
