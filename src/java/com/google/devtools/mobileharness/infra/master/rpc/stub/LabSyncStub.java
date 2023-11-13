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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** RPC stub for talking to Master LabSyncService. */
public interface LabSyncStub extends AutoCloseable {
  /** Sends full information of the lab and its devices to master. */
  @CanIgnoreReturnValue
  SignUpLabResponse signUpLab(SignUpLabRequest request) throws RpcExceptionWithErrorId;

  /** Signals that the lab and devices are alive. */
  @CanIgnoreReturnValue
  HeartbeatLabResponse heartbeatLab(HeartbeatLabRequest request) throws RpcExceptionWithErrorId;

  /** Signs out device. No effect if the device does not exist. */
  ListenableFuture<SignOutDeviceResponse> signOutDevice(SignOutDeviceRequest request);
}
