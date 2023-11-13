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

package com.google.devtools.mobileharness.infra.master.rpc.stub.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceGrpc;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.ClientInterceptors;
import javax.inject.Inject;

/** GRPC stub for talking to Master LabSyncService via OnePlatform API. */
public class LabSyncGrpcStub implements LabSyncStub {

  private final MasterGrpcStubHelper helper;
  private final LabSyncServiceGrpc.LabSyncServiceBlockingStub stub;
  private final LabSyncServiceGrpc.LabSyncServiceFutureStub asyncStub;

  @Inject
  public LabSyncGrpcStub(MasterGrpcStubHelper helper) {
    this.helper = helper;
    this.stub =
        LabSyncServiceGrpc.newBlockingStub(
            ClientInterceptors.intercept(helper.getChannel(), helper.getInterceptors()));
    this.asyncStub =
        LabSyncServiceGrpc.newFutureStub(
            ClientInterceptors.intercept(helper.getChannel(), helper.getInterceptors()));
  }

  @CanIgnoreReturnValue
  @Override
  public SignUpLabResponse signUpLab(SignUpLabRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::signUpLab,
        request,
        InfraErrorId.MASTER_RPC_STUB_LAB_SYNC_SIGN_UP_LAB_ERROR,
        String.format("Failed to sign up lab %s", request.getLabHostName()));
  }

  @CanIgnoreReturnValue
  @Override
  public HeartbeatLabResponse heartbeatLab(HeartbeatLabRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::heartbeatLab,
        request,
        InfraErrorId.MASTER_RPC_STUB_LAB_SYNC_HEARTBEAT_LAB_ERROR,
        String.format("Failed to heartbeat lab %s", request.getLabHostName()));
  }

  @Override
  public ListenableFuture<SignOutDeviceResponse> signOutDevice(SignOutDeviceRequest request) {
    return asyncStub.signOutDevice(request);
  }

  @Override
  public void close() {
    helper.closeChannel();
  }
}
