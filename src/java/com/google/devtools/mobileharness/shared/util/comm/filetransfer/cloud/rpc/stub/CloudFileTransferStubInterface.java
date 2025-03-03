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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartDownloadingGcsFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartUploadingFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import javax.annotation.Nullable;

/** Stub of {@code mobileharness.shared.CloudFileTransferService}. */
public interface CloudFileTransferStubInterface {

  /** Downloads gcs file specified in {@code request} to peer side. */
  void downloadGcsFile(DownloadGcsFileRequest request) throws MobileHarnessException;

  /** Downloads gcs file specified in {@code request} to peer side with impersonation user. */
  default void downloadGcsFile(DownloadGcsFileRequest request, @Nullable String impersonationUser)
      throws MobileHarnessException {
    downloadGcsFile(request);
  }

  /** Uploads file specified in {@code request} from peer side. */
  UploadFileResponse uploadGcsFile(UploadFileRequest request) throws MobileHarnessException;

  /** Uploads file specified in {@code request} from peer side with impersonation user. */
  default UploadFileResponse uploadGcsFile(
      UploadFileRequest request, @Nullable String impersonationUser) throws MobileHarnessException {
    return uploadGcsFile(request);
  }

  /** Lists files in directory specified in {@code request} from peer side. */
  ListFilesResponse listFiles(ListFilesRequest request) throws MobileHarnessException;

  /**
   * Lists files in directory specified in {@code request} from peer side with impersonation user.
   */
  default ListFilesResponse listFiles(ListFilesRequest request, @Nullable String impersonationUser)
      throws MobileHarnessException {
    return listFiles(request);
  }

  /**
   * Starts downloading gcs file specified in {@code request} to peer side, and returns the process
   * id.
   */
  StartDownloadingGcsFileResponse startDownloadingGcsFile(
      DownloadGcsFileRequest request, Duration initialTimeout) throws MobileHarnessException;

  /**
   * Starts downloading gcs file specified in {@code request} to peer side with impersonation user,
   * and returns the process id.
   */
  default StartDownloadingGcsFileResponse startDownloadingGcsFile(
      DownloadGcsFileRequest request, Duration initialTimeout, @Nullable String impersonationUser)
      throws MobileHarnessException {
    return startDownloadingGcsFile(request, initialTimeout);
  }

  /**
   * Starts uploading file specified in {@code request} from peer side , and returns the process id.
   */
  StartUploadingFileResponse startUploadingFile(UploadFileRequest request, Duration initialTimeout)
      throws MobileHarnessException;

  /**
   * Starts uploading file specified in {@code request} from peer side with impersonation user, and
   * returns the process id.
   */
  default StartUploadingFileResponse startUploadingFile(
      UploadFileRequest request, Duration initialTimeout, @Nullable String impersonationUser)
      throws MobileHarnessException {
    return startUploadingFile(request, initialTimeout);
  }

  /** Gets status of process {@code processId} from peer side. */
  @CanIgnoreReturnValue
  GetProcessStatusResponse getProcessStatus(String processId) throws MobileHarnessException;

  /** Gets status of process {@code processId} from peer side with impersonation user. */
  @CanIgnoreReturnValue
  default GetProcessStatusResponse getProcessStatus(
      String processId, @Nullable String impersonationUser) throws MobileHarnessException {
    return getProcessStatus(processId);
  }

  /** Saves file content specified in {@code request} to server directly. */
  @CanIgnoreReturnValue
  SaveFileResponse saveFile(SaveFileRequest request) throws MobileHarnessException;

  /** Saves file content specified in {@code request} to server directly with impersonation user. */
  @CanIgnoreReturnValue
  default SaveFileResponse saveFile(SaveFileRequest request, @Nullable String impersonationUser)
      throws MobileHarnessException {
    return saveFile(request);
  }

  /** Gets content of file specified in {@code request} from server directly. */
  GetFileResponse getFile(GetFileRequest request) throws MobileHarnessException;

  /**
   * Gets content of file specified in {@code request} from server directly with impersonation user.
   */
  default GetFileResponse getFile(GetFileRequest request, @Nullable String impersonationUser)
      throws MobileHarnessException {
    return getFile(request);
  }

  void shutdown();
}
