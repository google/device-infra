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

package com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.GrpcDirectTargetConfigures;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceDebugInfoRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceDebugInfoResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceLogRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceLogResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.TakeScreenshotRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.TakeScreenshotResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServiceGrpc;
import io.grpc.Channel;

/** gRPC stub of {@code DeviceOpsService}. */
public class DeviceOpsGrpcStub implements DeviceOpsStub {

  private final BlockingInterface stub;
  private final FutureInterface futureStub;

  public DeviceOpsGrpcStub(Channel channel) {
    this(newBlockingInterface(channel), newFutureInterface(channel));
  }

  public DeviceOpsGrpcStub(BlockingInterface stub, FutureInterface futureStub) {
    this.stub = stub;
    this.futureStub = futureStub;
  }

  @Override
  public TakeScreenshotResponse takeScreenshot(TakeScreenshotRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::takeScreenshot,
        request,
        InfraErrorId.LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_GRPC_ERROR,
        "Failed to take screenshot, device_id=" + request.getDeviceId());
  }

  @Override
  public ListenableFuture<TakeScreenshotResponse> takeScreenshotAsync(
      TakeScreenshotRequest request) {
    return futureStub.takeScreenshot(request);
  }

  @Override
  public ListenableFuture<TakeScreenshotResponse> takeScreenshotAsync(
      TakeScreenshotRequest request, boolean useClientRpcAuthority) {
    if (useClientRpcAuthority) {
      throw new UnsupportedOperationException(
          "useClientRpcAuthority is not supported in gRPC stub");
    }
    return takeScreenshotAsync(request);
  }

  @Override
  public GetDeviceLogResponse getDeviceLog(GetDeviceLogRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getDeviceLog,
        request,
        InfraErrorId.LAB_RPC_DEVICE_OPS_GET_DEVICE_LOG_GRPC_ERROR,
        "Failed to get device log, device_id=" + request.getDeviceId());
  }

  @Override
  public ListenableFuture<GetDeviceLogResponse> getDeviceLogAsync(GetDeviceLogRequest request) {
    return futureStub.getDeviceLog(request);
  }

  @Override
  public ListenableFuture<GetDeviceLogResponse> getDeviceLogAsync(
      GetDeviceLogRequest request, boolean useClientRpcAuthority) {
    if (useClientRpcAuthority) {
      throw new UnsupportedOperationException(
          "useClientRpcAuthority is not supported in gRPC stub");
    }
    return getDeviceLogAsync(request);
  }

  @Override
  public GetDeviceDebugInfoResponse getDeviceDebugInfo(GetDeviceDebugInfoRequest request)
      throws RpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getDeviceDebugInfo,
        request,
        InfraErrorId.LAB_RPC_DEVICE_OPS_GET_DEVICE_DEBUG_INFO_GRPC_ERROR,
        "Failed to get device debug info");
  }

  @Override
  public ListenableFuture<GetDeviceDebugInfoResponse> getDeviceDebugInfoAsync(
      GetDeviceDebugInfoRequest request) {
    return futureStub.getDeviceDebugInfo(request);
  }

  @Override
  public void close() {
    // This stub is not responsible for managing lifecycle of the channel.
  }

  /** Interface for {@link DeviceOpsServiceBlockingStub} */
  public static interface BlockingInterface {
    TakeScreenshotResponse takeScreenshot(TakeScreenshotRequest request);

    GetDeviceLogResponse getDeviceLog(GetDeviceLogRequest request);

    GetDeviceDebugInfoResponse getDeviceDebugInfo(GetDeviceDebugInfoRequest request);
  }

  /** Interface for {@link DeviceOpsServiceFutureStub} */
  public static interface FutureInterface {
    ListenableFuture<TakeScreenshotResponse> takeScreenshot(TakeScreenshotRequest request);

    ListenableFuture<GetDeviceLogResponse> getDeviceLog(GetDeviceLogRequest request);

    ListenableFuture<GetDeviceDebugInfoResponse> getDeviceDebugInfo(
        GetDeviceDebugInfoRequest request);
  }

  public static BlockingInterface newBlockingInterface(Channel channel) {
    return GrpcDirectTargetConfigures.newBlockingInterface(
        DeviceOpsServiceGrpc.newBlockingStub(channel), BlockingInterface.class);
  }

  public static FutureInterface newFutureInterface(Channel channel) {
    return GrpcDirectTargetConfigures.newBlockingInterface(
        DeviceOpsServiceGrpc.newFutureStub(channel), FutureInterface.class);
  }
}
