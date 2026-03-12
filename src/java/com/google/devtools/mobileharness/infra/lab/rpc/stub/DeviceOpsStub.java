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

package com.google.devtools.mobileharness.infra.lab.rpc.stub;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceDebugInfoRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceDebugInfoResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceLogRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.GetDeviceLogResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.TakeScreenshotRequest;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.TakeScreenshotResponse;
import javax.annotation.Nullable;

/** RPC stub interface for talking to Lab Server for DeviceOpsService. */
public interface DeviceOpsStub extends NonThrowingAutoCloseable {

  TakeScreenshotResponse takeScreenshot(TakeScreenshotRequest request)
      throws RpcExceptionWithErrorId;

  default TakeScreenshotResponse takeScreenshot(
      TakeScreenshotRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return takeScreenshot(request);
  }

  /** Takes a screenshot of a device asynchronously. */
  ListenableFuture<TakeScreenshotResponse> takeScreenshotAsync(TakeScreenshotRequest request);

  GetDeviceLogResponse getDeviceLog(GetDeviceLogRequest request) throws RpcExceptionWithErrorId;

  default GetDeviceLogResponse getDeviceLog(
      GetDeviceLogRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getDeviceLog(request);
  }

  /** Gets the device log asynchronously. */
  ListenableFuture<GetDeviceLogResponse> getDeviceLogAsync(GetDeviceLogRequest request);

  GetDeviceDebugInfoResponse getDeviceDebugInfo(GetDeviceDebugInfoRequest request)
      throws RpcExceptionWithErrorId;

  default GetDeviceDebugInfoResponse getDeviceDebugInfo(
      GetDeviceDebugInfoRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getDeviceDebugInfo(request);
  }

  /** Gets the device debug info asynchronously. */
  ListenableFuture<GetDeviceDebugInfoResponse> getDeviceDebugInfoAsync(
      GetDeviceDebugInfoRequest request);
}
