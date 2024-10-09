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

import static com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubUtils.createGetProcessStatusRequest;
import static com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubUtils.createStartDownloadingGcsFileRequest;
import static com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubUtils.createStartUploadingFileRequest;
import static com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubUtils.getRpcExceptionWrapper;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartDownloadingGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartDownloadingGcsFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartUploadingFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartUploadingFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransferServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.stub.GrpcDirectTargetConfigures;
import io.grpc.Channel;
import java.time.Duration;

/** gRPC stub of {@code mobileharness.shared.CloudFileTransferService}. */
public class CloudFileTransferGrpcStub implements CloudFileTransferStubInterface {

  private final BlockingInterface stub;

  public CloudFileTransferGrpcStub(Channel channel) {
    this(newBlockingInterface(channel));
  }

  public CloudFileTransferGrpcStub(BlockingInterface stub) {
    this.stub = stub;
  }

  @Override
  public void downloadGcsFile(DownloadGcsFileRequest request) throws MobileHarnessException {
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      DownloadGcsFileResponse unused =
          GrpcStubUtil.invoke(stub::downloadGcsFile, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public UploadFileResponse uploadGcsFile(UploadFileRequest request) throws MobileHarnessException {
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      return GrpcStubUtil.invoke(stub::uploadFile, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public ListFilesResponse listFiles(ListFilesRequest request) throws MobileHarnessException {
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      return GrpcStubUtil.invoke(stub::listFiles, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public StartUploadingFileResponse startUploadingFile(
      UploadFileRequest request, Duration initialTimeout) throws MobileHarnessException {
    StartUploadingFileRequest startUploadingFileRequest =
        createStartUploadingFileRequest(request, initialTimeout);
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(startUploadingFileRequest);
    try {
      return GrpcStubUtil.invoke(
          stub::startUploadingFile,
          startUploadingFileRequest,
          wrapper.errorId(),
          wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public StartDownloadingGcsFileResponse startDownloadingGcsFile(
      DownloadGcsFileRequest request, Duration initialTimeout) throws MobileHarnessException {
    StartDownloadingGcsFileRequest startDownloadingGcsFileRequest =
        createStartDownloadingGcsFileRequest(request, initialTimeout);
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(startDownloadingGcsFileRequest);
    try {
      return GrpcStubUtil.invoke(
          stub::startDownloadingGcsFile,
          startDownloadingGcsFileRequest,
          wrapper.errorId(),
          wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public GetProcessStatusResponse getProcessStatus(String processId) throws MobileHarnessException {
    GetProcessStatusRequest request = createGetProcessStatusRequest(processId);
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      return GrpcStubUtil.invoke(
          stub::getProcessStatus, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public SaveFileResponse saveFile(SaveFileRequest request) throws MobileHarnessException {
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      return GrpcStubUtil.invoke(stub::saveFile, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public GetFileResponse getFile(GetFileRequest request) throws MobileHarnessException {
    RpcExceptionWrapper wrapper = getRpcExceptionWrapper(request);
    try {
      return GrpcStubUtil.invoke(stub::getFile, request, wrapper.errorId(), wrapper.message());
    } catch (GrpcExceptionWithErrorId e) {
      throw wrapper.mobileHarnessException(e);
    }
  }

  @Override
  public void shutdown() {}

  /** Interface for {@link CloudFileTransferServiceBlockingStub}. */
  public static interface BlockingInterface {
    DownloadGcsFileResponse downloadGcsFile(DownloadGcsFileRequest request);

    UploadFileResponse uploadFile(UploadFileRequest request);

    ListFilesResponse listFiles(ListFilesRequest request);

    StartUploadingFileResponse startUploadingFile(StartUploadingFileRequest request);

    StartDownloadingGcsFileResponse startDownloadingGcsFile(StartDownloadingGcsFileRequest request);

    GetProcessStatusResponse getProcessStatus(GetProcessStatusRequest request);

    SaveFileResponse saveFile(SaveFileRequest request);

    GetFileResponse getFile(GetFileRequest request);
  }

  /** Creates a {@link BlockingInterface} from a {@link Channel}. */
  public static BlockingInterface newBlockingInterface(Channel channel) {
    return GrpcDirectTargetConfigures.newBlockingInterface(
        CloudFileTransferServiceGrpc.newBlockingStub(channel), BlockingInterface.class);
  }
}
