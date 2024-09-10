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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartDownloadingGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartUploadingFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileRequest;
import java.time.Duration;

/** Utils to share the common logic for all stubs. */
public final class CloudFileTransferStubUtils {

  public static StartUploadingFileRequest createStartUploadingFileRequest(
      UploadFileRequest request, Duration initialTimeout) {
    return StartUploadingFileRequest.newBuilder()
        .setRequest(request)
        .setInitialTimeoutSec((int) initialTimeout.getSeconds())
        .build();
  }

  public static StartDownloadingGcsFileRequest createStartDownloadingGcsFileRequest(
      DownloadGcsFileRequest request, Duration initialTimeout) {
    return StartDownloadingGcsFileRequest.newBuilder()
        .setRequest(request)
        .setInitialTimeoutSec((int) initialTimeout.getSeconds())
        .build();
  }

  public static GetProcessStatusRequest createGetProcessStatusRequest(String processId) {
    return GetProcessStatusRequest.newBuilder().setProcessId(processId).build();
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(DownloadGcsFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_DOWNLOAD_GCS_FILE_ERROR,
        String.format("Failed to download GCS file %s via RPC", request.getGcsFile()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(UploadFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_UPLOAD_GCS_FILE_ERROR,
        String.format("Failed to upload GCS file %s via RPC", request.getPath()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(ListFilesRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_LIST_FILE_ERROR,
        String.format("Failed to list files of %s via RPC", request.getDirPath()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(StartDownloadingGcsFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_START_DOWNLOAD_GCS_FILE_ERROR,
        String.format(
            "Failed to notify the server to start downloading GCS file %s",
            request.getRequest().getGcsFile()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(StartUploadingFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_START_UPLOADING_GCS_FILE_ERROR,
        String.format(
            "Failed to notify the server to start uploading GCS file %s",
            request.getRequest().getPath()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(GetProcessStatusRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_GET_PROCESS_STATUS_ERROR,
        String.format("Failed to get the status of the process %s", request.getProcessId()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(SaveFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_SAVE_FILE_ERROR,
        String.format(
            "Failed to notify the server to save the file %s", request.getOriginalPath()));
  }

  public static RpcExceptionWrapper getRpcExceptionWrapper(GetFileRequest request) {
    return RpcExceptionWrapper.create(
        InfraErrorId.FT_RPC_STUB_GET_FILE_ERROR,
        String.format("Failed to get file %s", request.getPath()));
  }

  private CloudFileTransferStubUtils() {}
}
