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
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDevicesRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDevicesResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.stub.GrpcDirectTargetConfigures;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Channel;
import javax.inject.Inject;

/** GRPC stub for talking to Master LabSyncService via OnePlatform API. */
public class LabSyncGrpcStub implements LabSyncStub {

  private final NonThrowingAutoCloseable closeable;
  private final BlockingInterface stub;
  private final FutureInterface asyncStub;

  @Inject
  public LabSyncGrpcStub(MasterGrpcStubHelper helper) {
    this(
        helper,
        newBlockingInterface(helper.getChannelWithInterceptors()),
        newFutureInterface(helper.getChannelWithInterceptors()));
  }

  public LabSyncGrpcStub(
      NonThrowingAutoCloseable closeable, BlockingInterface stub, FutureInterface asyncStub) {
    this.closeable = closeable;
    this.stub = stub;
    this.asyncStub = asyncStub;
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
    closeable.close();
  }

  /**
   * Blocking interface for LabSyncService.
   *
   * <p>It has the same methods as LabSyncServiceGrpc.LabSyncServiceBlockingStub.
   */
  public static interface BlockingInterface {

    SignUpLabResponse signUpLab(SignUpLabRequest request);

    HeartbeatLabResponse heartbeatLab(HeartbeatLabRequest request);

    SignOutDeviceResponse signOutDevice(SignOutDeviceRequest request);

    RemoveMissingDeviceResponse removeMissingDevice(RemoveMissingDeviceRequest request);

    RemoveMissingDevicesResponse removeMissingDevices(RemoveMissingDevicesRequest request);

    RemoveMissingHostResponse removeMissingHost(RemoveMissingHostRequest request);

    RemoveMissingHostsResponse removeMissingHosts(RemoveMissingHostsRequest request);
  }

  public static BlockingInterface newBlockingInterface(
      LabSyncServiceGrpc.LabSyncServiceBlockingStub blockingStub) {
    return GrpcDirectTargetConfigures.newBlockingInterface(blockingStub, BlockingInterface.class);
  }

  public static BlockingInterface newBlockingInterface(Channel channel) {
    return newBlockingInterface(LabSyncServiceGrpc.newBlockingStub(channel));
  }

  /**
   * Future interface for LabSyncService.
   *
   * <p>It has the same methods as LabSyncServiceGrpc.LabSyncServiceFutureStub.
   */
  public static interface FutureInterface {

    ListenableFuture<SignUpLabResponse> signUpLab(SignUpLabRequest request);

    ListenableFuture<HeartbeatLabResponse> heartbeatLab(HeartbeatLabRequest request);

    ListenableFuture<SignOutDeviceResponse> signOutDevice(SignOutDeviceRequest request);

    ListenableFuture<RemoveMissingDeviceResponse> removeMissingDevice(
        RemoveMissingDeviceRequest request);

    ListenableFuture<RemoveMissingDevicesResponse> removeMissingDevices(
        RemoveMissingDevicesRequest request);

    ListenableFuture<RemoveMissingHostResponse> removeMissingHost(RemoveMissingHostRequest request);

    ListenableFuture<RemoveMissingHostsResponse> removeMissingHosts(
        RemoveMissingHostsRequest request);
  }

  public static FutureInterface newFutureInterface(
      LabSyncServiceGrpc.LabSyncServiceFutureStub futureStub) {
    return GrpcDirectTargetConfigures.newBlockingInterface(futureStub, FutureInterface.class);
  }

  public static FutureInterface newFutureInterface(Channel channel) {
    return newFutureInterface(LabSyncServiceGrpc.newFutureStub(channel));
  }
}
