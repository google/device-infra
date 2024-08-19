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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.service;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
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
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransferServiceGrpc.CloudFileTransferServiceImplBase;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.FileHandlers.Handler;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import io.grpc.stub.StreamObserver;
import java.nio.file.Path;

/** Implements of {@code mobileharness.shared.CloudFileTransferService}. */
public class CloudFileTransferServiceGrpcImpl extends CloudFileTransferServiceImplBase {

  private final CloudFileTransferServiceImpl impl;

  /**
   * Creates a file transfer service based on Google Cloud storage. Caches in {@code homeDir} is
   * managed and cleaned automatically, DO NOT delete any of them while services is running.
   *
   * @param homeDir directory for local cached files
   * @param publicDir directory that is accessible by client
   */
  public CloudFileTransferServiceGrpcImpl(Path homeDir, Path publicDir)
      throws MobileHarnessException, InterruptedException {
    this(new CloudFileTransferServiceImpl(homeDir, publicDir));
  }

  /**
   * Creates a file transfer service based on Google Cloud storage.
   *
   * @param impl implementation of all RPC interfaces
   */
  public CloudFileTransferServiceGrpcImpl(CloudFileTransferServiceImpl impl) {
    this.impl = impl;
  }

  @Override
  public void downloadGcsFile(
      DownloadGcsFileRequest request, StreamObserver<DownloadGcsFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::downloadGcsFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getDownloadGcsFileMethod());
  }

  @Override
  public void uploadFile(
      UploadFileRequest request, StreamObserver<UploadFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::uploadFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getUploadFileMethod());
  }

  @Override
  public void listFiles(
      ListFilesRequest request, StreamObserver<ListFilesResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::listFiles,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getListFilesMethod());
  }

  @Override
  public void startUploadingFile(
      StartUploadingFileRequest request,
      StreamObserver<StartUploadingFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::startUploadingFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getStartUploadingFileMethod());
  }

  @Override
  public void startDownloadingGcsFile(
      StartDownloadingGcsFileRequest request,
      StreamObserver<StartDownloadingGcsFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::startDownloadingGcsFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getStartDownloadingGcsFileMethod());
  }

  @Override
  public void getProcessStatus(
      GetProcessStatusRequest request, StreamObserver<GetProcessStatusResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::getProcessStatus,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getGetProcessStatusMethod());
  }

  @Override
  public void saveFile(SaveFileRequest request, StreamObserver<SaveFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::saveFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getSaveFileMethod());
  }

  @Override
  public void getFile(GetFileRequest request, StreamObserver<GetFileResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        impl::getFile,
        CloudFileTransferServiceGrpc.getServiceDescriptor(),
        CloudFileTransferServiceGrpc.getGetFileMethod());
  }

  /**
   * Adds handler for request with metadata in the type of {@code metadataClass}. There is only one
   * handler allowed for each metadata class, because the handler may move the receive file away.
   */
  @CanIgnoreReturnValue
  public <T extends Message> CloudFileTransferServiceGrpcImpl addHandler(
      Class<T> metadataClass, Handler<T> handler) throws MobileHarnessException {
    impl.addHandler(metadataClass, handler);
    return this;
  }
}
