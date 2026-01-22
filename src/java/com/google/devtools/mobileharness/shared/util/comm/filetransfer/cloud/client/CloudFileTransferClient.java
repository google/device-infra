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

package com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.client;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.common.FileTransferConstant;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.CompressOptions;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse.ProcessStatus;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.ListFilesRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.SaveFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartDownloadingGcsFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.StartUploadingFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.UploadFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubInterface;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.factory.FileTransferParameters;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.FileInfoProto.FileInfo;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.TaggedFileMetadataProto.TaggedFileMetadata;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.FileTransferEvent;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.FileTransferEvent.ExecutionType;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.watcher.WatchableFileTransferClient;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.CacheInfo;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsFileManager;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsFileManager.ExecutionInfo;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/** FileTransfer Client based on {@code mobileharness.shared.CloudFileTransferService}. */
public class CloudFileTransferClient extends WatchableFileTransferClient {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval to get a process status. */
  private static final Duration GET_PROCESS_STATUS_INTERVAL = Duration.ofSeconds(5);

  /** Non local file types that is writable. */
  private static final ImmutableSet<RemoteFileType> NON_LOCAL_WRITABLE_FILE_TYPE =
      ImmutableSet.of();

  /** Thread pool to sending/receiving file from peer side. */
  private final ExecutorService threadPool =
      ThreadPools.createStandardThreadPool("cloud-file-transfer-client");

  /** Random generator for creating tmp files. */
  private final Random random = new Random();

  private final LocalFileUtil localFileUtil;

  private final CloudFileTransferStubInterface stub;

  private final GcsFileManager gcsFileManager;

  /** Timeout of starting to send/receive a file. */
  private final Duration initialTimeout;

  private final Path tmpDir;

  private final FileTransferParameters params;

  /** The impersonation user for the file transfer stub. */
  @Nullable private final String impersonationUser;

  /**
   * Creates a file transfer client based on cloud file transfer stub {@code stub}.
   *
   * @param stub for talking to the server in peer side
   */
  public CloudFileTransferClient(CloudFileTransferStubInterface stub, FileTransferParameters params)
      throws MobileHarnessException, InterruptedException {
    this(stub, params, null);
  }

  /**
   * Creates a file transfer client based on cloud file transfer stub {@code stub}.
   *
   * @param stub for talking to the server in peer side
   * @param impersonationUser the impersonated user for the file transfer stub
   */
  public CloudFileTransferClient(
      CloudFileTransferStubInterface stub,
      FileTransferParameters params,
      @Nullable String impersonationUser)
      throws MobileHarnessException, InterruptedException {
    this(
        stub,
        new GcsFileManager(
            params.homeDir().resolve("gcs"),
            params.cloudFileTransferBucket(),
            Optional.of(FileTransferConstant.getCloudCacheTtl()),
            FileTransferConstant.getLocalCacheTtl(),
            Optional.of(params.uploadShardSize()),
            Optional.of(params.downloadShardSize()),
            GcsUtil.CredentialType.ofCredentialFile(GcsFileManager.getCredentialFile())),
        params.homeDir().resolve("tmp"),
        params,
        new LocalFileUtil(),
        FileTransferConstant.getInitialTimeout(),
        impersonationUser);
  }

  @VisibleForTesting
  CloudFileTransferClient(
      CloudFileTransferStubInterface stub,
      GcsFileManager gcsFileManager,
      Path tmpDir,
      FileTransferParameters params,
      LocalFileUtil localFileUtil,
      Duration initialTimeout,
      @Nullable String impersonationUser) {
    this.stub = stub;
    this.gcsFileManager = gcsFileManager;
    this.tmpDir = tmpDir;
    this.params = params;
    this.localFileUtil = localFileUtil;
    this.initialTimeout = initialTimeout;
    this.impersonationUser = impersonationUser;
  }

  /** Sends file {@code local} to remote side with {@code metadata}. */
  @VisibleForTesting
  void sendFile(Any metadata, String local, @Nullable String checksum, String originalPath)
      throws MobileHarnessException, InterruptedException {
    FileTransferEvent.Builder event =
        FileTransferEvent.builder().setStart(Instant.now()).setType(ExecutionType.SEND);
    logger.atInfo().log(
        "Sending file: %s, checksum: %s, original path: %s", local, checksum, originalPath);
    long fileSize;
    boolean isCached;
    if (isFileExists(Path.of(local)) || localFileUtil.isDirExist(local)) {
      FileOperationStatus result =
          sendDirectlyIfSmall(metadata, Path.of(local), checksum, originalPath);
      if (result.isFinished()) {
        fileSize = result.fileSize();
        isCached = false;
      } else {
        ExecutionInfo executionInfo =
            gcsFileManager.upload(
                Path.of(local),
                params.zipStoreOnly(),
                Optional.of(params.zipTimeout()),
                Optional.ofNullable(checksum));
        fileSize = executionInfo.fileSize();
        isCached = executionInfo.isCached();
        downloadGcsFileToServer(
            metadata, executionInfo.checksum(), originalPath, /* isCompressed= */ dirExists(local));
      }
    } else if (checksum != null && gcsFileManager.fileExistAndFresh(Path.of(checksum))) {
      logger.atInfo().log(
          "Remote file %s is already cached as %s in GCS. Let lab server download it directly.",
          local, checksum);
      fileSize = gcsFileManager.getGcsFileSize(Path.of(checksum));
      isCached = true;
      downloadGcsFileToServer(
          metadata,
          checksum,
          originalPath,
          /* isCompressed= */ gcsFileManager.isCompressed(Path.of(checksum)));
    } else {
      throw new MobileHarnessException(
          InfraErrorId.FT_FILE_NOT_EXIST,
          "File Or directory doesn't exist and not cached: " + local);
    }
    publishEvent(event.setEnd(Instant.now()).setFileSize(fileSize).setIsCached(isCached).build());
    logger.atInfo().log("Sent file: %s with size %s", local, prettySize(fileSize));
  }

  /** {@inheritDoc} */
  @Override
  public void sendFile(
      String fileId, String tag, String srcPath, @Nullable String checksum, String originalPath)
      throws MobileHarnessException, InterruptedException {
    TaggedFileMetadata metadata =
        TaggedFileMetadata.newBuilder()
            .setFileId(fileId)
            .setTag(tag)
            .setOriginalPath(originalPath)
            .build();
    sendFile(Any.pack(metadata), srcPath, checksum, originalPath);
  }

  @Override
  public boolean isSendable(String path, @Nullable String checksum)
      throws MobileHarnessException, InterruptedException {
    return isFileExists(Path.of(path))
        || localFileUtil.isDirExist(path)
        || (checksum != null && gcsFileManager.fileExistAndFresh(Path.of(checksum)));
  }

  /**
   * Sends file {@code local} directly to server if its size is less than {@link
   * FileTransferParameters#smallFileSize} used when constructing this instance.
   *
   * @return {@link FileOperationStatus} of the send operation.
   */
  private FileOperationStatus sendDirectlyIfSmall(
      Any metadata, Path local, @Nullable String checksum, String originalPath)
      throws MobileHarnessException, InterruptedException {
    try {
      if (!fileOrDirExists(local.toString())) {
        throw new MobileHarnessException(
            InfraErrorId.FT_FILE_NOT_EXIST, "File Or directory doesn't exist: " + local);
      }

      if (params.smallFileSize() < 0) {
        return FileOperationStatus.create(false, -1L);
      }

      SaveFileRequest.Builder request =
          SaveFileRequest.newBuilder().setMetadata(metadata).setOriginalPath(originalPath);
      if (checksum != null) {
        request.setChecksum(checksum);
      }
      if (dirExists(local.toString())) {
        long totalSize = 0;
        for (Path file : listFilesForPath(local)) {
          totalSize += getFileSize(file);
          if (totalSize > params.smallFileSize()) {
            return FileOperationStatus.create(false, totalSize);
          }
        }

        Path tmpZipFile =
            tmpDir.resolve(String.format("%s.zip", Long.toUnsignedString(random.nextLong())));
        try {
          localFileUtil.prepareParentDir(tmpZipFile);
          localFileUtil.zipDir(
              local.toString(),
              tmpZipFile.toString(),
              /* sortFile= */ false,
              params.zipStoreOnly(),
              /* compressionLevel= */ 1,
              Timeout.fixed(params.zipTimeout()),
              /* keepLocalSourceRootBaseName= */ false,
              /* keepFileMetadata= */ false);
          request
              .setIsCompressed(true)
              .setCompressOptions(toCompressOptions(params))
              .setContent(ByteString.copyFrom(localFileUtil.readBinaryFile(tmpZipFile.toString())));
        } finally {
          if (localFileUtil.isFileOrDirExist(tmpZipFile)) {
            localFileUtil.removeFileOrDir(tmpZipFile);
          }
        }
      } else {
        long totalSize = getFileSize(local);
        if (totalSize > params.smallFileSize()) {
          return FileOperationStatus.create(false, totalSize);
        }
        request.setContent(ByteString.copyFrom(readBytes(local)));
      }
      logger.atInfo().log(
          "File %s size %s is less than %s, send directly to server",
          local, prettySize(request.getContent().size()), prettySize(params.smallFileSize()));
      stub.saveFile(request.build(), impersonationUser);
      return FileOperationStatus.create(true, (long) request.getContent().size());
    } catch (MobileHarnessException e) {
      if (isMethodNonFound(e)) {
        logger.atInfo().log(
            "Skip sending file %s directly, because lab server doesn't support it yet.", local);
        return FileOperationStatus.create(false, -1L);
      }
      throw e;
    }
  }

  /**
   * @return true if {@code e} is a method not found exception.
   */
  private boolean isMethodNonFound(MobileHarnessException e) {
    return e.getMessage().contains("Method not found");
  }

  private void downloadGcsFileToServer(
      Any metadata, String checksum, String originalPath, boolean isCompressed)
      throws MobileHarnessException, InterruptedException {
    withTimeout(
        () -> {
          Sleeper sleeper = Sleeper.defaultSleeper();
          StartDownloadingGcsFileResponse response =
              withRetry(
                  () ->
                      stub.startDownloadingGcsFile(
                          DownloadGcsFileRequest.newBuilder()
                              .setGcsFile(checksum)
                              .setChecksum(checksum)
                              .setBucket(gcsFileManager.getBucketName())
                              .setIsCompressed(isCompressed)
                              .setCompressOptions(toCompressOptions(params))
                              .setOriginalPath(originalPath)
                              .setMetadata(metadata)
                              .build(),
                          initialTimeout,
                          impersonationUser),
                  String.format(
                      "trigger server download gcs file %s; original path: %s",
                      checksum, originalPath));
          String processId = response.getProcessId();

          if (response.hasResponse()) {
            logger.atInfo().log(
                "[%s] Finish downloading gcs file %s without waiting.", processId, originalPath);
            return response.getResponse();
          }

          while (!Thread.interrupted()) {
            GetProcessStatusResponse statusResponse =
                withRetry(
                    () -> stub.getProcessStatus(processId, impersonationUser),
                    String.format(
                        "[%s] get status of downloading gcs file %s; original path: %s",
                        processId, checksum, originalPath));
            if (statusResponse.getStatus() == ProcessStatus.FINISHED) {
              return statusResponse.getResponse();
            }
            sleeper.sleep(GET_PROCESS_STATUS_INTERVAL);
          }
          throw new InterruptedException(
              String.format(
                  "[%s] Interrupted while waiting for the finish of sending file %s",
                  processId, originalPath));
        },
        String.format(
            "let server downloading gcs file %s; original path: %s", checksum, originalPath),
        /* isUploadFile= */ false);
  }

  /**
   * Gets remote file {@code remote} and saves to {@code local}.
   *
   * @return downloaded file size; note that it is just an estimate, it is only the size of
   *     (compressed) caches, use File APIs (such as {@link LocalFileUtil}) to get the accurate size
   *     if you really care about it
   */
  @CanIgnoreReturnValue
  public long getFile(Path remote, Path local) throws MobileHarnessException, InterruptedException {
    if (!isWritableFileType(local)) {
      throw new MobileHarnessException(
          InfraErrorId.FT_LOCAL_FILE_NOT_WRITABLE, "Doesn't support to write to file " + local);
    }
    FileTransferEvent.Builder event =
        FileTransferEvent.builder().setStart(Instant.now()).setType(ExecutionType.GET);
    logger.atInfo().log("Getting file: %s", local);
    // Only deleting existing dst single file but not directory
    if (isFileExists(local)) {
      removeLocalFileOrDir(local);
    }

    FileOperationStatus result = getFileDirectlyIfSmall(remote, local);
    if (result.isFinished()) {
      publishEvent(
          event.setEnd(Instant.now()).setIsCached(false).setFileSize(result.fileSize()).build());
      logger.atInfo().log("Got file: %s with size %s", local, prettySize(result.fileSize()));
      return result.fileSize();
    }

    UploadFileResponse response =
        uploadFileToGcsFromServer(
            UploadFileRequest.newBuilder()
                .setPath(remote.toString())
                .setBucket(gcsFileManager.getBucketName())
                .setCompressOptions(toCompressOptions(params))
                .build(),
            local);

    CacheInfo localCacheInfo =
        gcsFileManager.getCacheInfo(GcsUtil.GcsApiObject.create(Path.of(response.getGcsFile())));
    Path localCache = localCacheInfo.localCachePath();
    long receivedFileSize = getFileSize(localCache);
    saveReceivedFile(localCache, local, remote, response.getIsCompressed(), false);
    logger.atInfo().log("Got file: %s with size %s", local, prettySize(receivedFileSize));
    publishEvent(
        event
            .setEnd(Instant.now())
            .setIsCached(localCacheInfo.isCached())
            .setFileSize(receivedFileSize)
            .build());
    return receivedFileSize;
  }

  /** Saves {@code receivedFile} to {@code local}. */
  private void saveReceivedFile(
      Path receivedFile,
      Path local,
      Path remote,
      boolean isCompressed,
      boolean deleteReceivedFileFinally)
      throws MobileHarnessException, InterruptedException {
    Path finalizedReceivedFileOrDir;
    @Nullable Path tmpUnzippedFile = null;
    try {
      if (isCompressed) {
        // Decompress the file if it is compressed
        tmpUnzippedFile =
            localFileUtil.createTempFile(
                receivedFile.getParent(), receivedFile.getFileName().toString(), "_UNZIP");
        decompressDirectory(receivedFile, tmpUnzippedFile);
        finalizedReceivedFileOrDir = tmpUnzippedFile;
      } else {
        finalizedReceivedFileOrDir = receivedFile;
      }

      if (localFileUtil.isFileExist(finalizedReceivedFileOrDir)) {
        prepareParentDir(local);
        if (dirExists(local.toString())) {
          // If |local| has already existed and is a directory, copy received files to the
          // directory with the same relative path as |remote|.
          Path localFile = join(local, remote);
          prepareParentDir(localFile);
          replaceFile(finalizedReceivedFileOrDir, localFile, deleteReceivedFileFinally);
        } else {
          replaceFile(finalizedReceivedFileOrDir, local, deleteReceivedFileFinally);
        }
      } else if (localFileUtil.isDirExist(finalizedReceivedFileOrDir)) {
        mergeDir(finalizedReceivedFileOrDir, local);
      } else {
        // Should never reach here but just in case the received file is not found.
        throw new MobileHarnessException(
            InfraErrorId.FT_RECEIVED_FILE_NOT_FOUND, "Received file not found.");
      }
    } finally {
      // Cleanup the unzipped caches as soon as possible as zipped file for directory could be large
      if (tmpUnzippedFile != null && localFileUtil.isFileOrDirExist(tmpUnzippedFile)) {
        localFileUtil.grantFileOrDirFullAccess(tmpUnzippedFile);
        localFileUtil.removeFileOrDir(tmpUnzippedFile);
      }
      if (deleteReceivedFileFinally && localFileUtil.isFileOrDirExist(receivedFile)) {
        localFileUtil.removeFileOrDir(receivedFile);
      }
    }
  }

  /** Copies file {@code src} to {@code dest} in force. */
  private void replaceFile(Path src, Path dest, boolean deleteSrc)
      throws MobileHarnessException, InterruptedException {
    if (isLocalFileType(dest)) {
      if (deleteSrc) {
        // Move receivedFile to destination file directly.
        if (localFileUtil.isFileOrDirExist(dest)) {
          localFileUtil.removeFileOrDir(dest);
        }
        localFileUtil.moveFileOrDir(src, dest);
      } else {
        localFileUtil.copyFileOrDir(src, dest);
      }
      return;
    }
  }

  /** Merges {@code srcDir} into {@code destDir}. */
  private void mergeDir(Path srcDir, Path destDir)
      throws MobileHarnessException, InterruptedException {
    if (isLocalFileType(destDir)) {
      localFileUtil.mergeDir(srcDir, destDir);
      return;
    }
  }

  private static CompressOptions toCompressOptions(FileTransferParameters params) {
    return CompressOptions.newBuilder()
        .setStoreOnly(params.zipStoreOnly())
        .setTimeoutMs(params.zipTimeout().toMillis())
        .build();
  }

  /**
   * Gets file {@code remote} from server and saves to {@code local} if size of {@code remote} is
   * less than {@link FileTransferParameters#smallFileSize} used when constructing this instance.
   *
   * @return {@link FileOperationStatus} for get operation.
   */
  private FileOperationStatus getFileDirectlyIfSmall(Path remote, Path local)
      throws MobileHarnessException, InterruptedException {
    try {
      GetFileResponse response =
          stub.getFile(
              GetFileRequest.newBuilder()
                  .setFileSizeByteLimit(params.smallFileSize())
                  .setPath(remote.toString())
                  .setCompressOptions(toCompressOptions(params))
                  .build(),
              impersonationUser);
      if (response.getSizeExceed()) {
        return FileOperationStatus.create(false, 0L);
      }
      Path tmpReceivedFile = tmpDir.resolve(Long.toUnsignedString(random.nextLong()));
      localFileUtil.prepareParentDir(tmpReceivedFile);
      localFileUtil.writeToFile(tmpReceivedFile.toString(), response.getContent().toByteArray());

      saveReceivedFile(tmpReceivedFile, local, remote, response.getIsCompressed(), true);
      logger.atInfo().log(
          "File %s size %s is less than %s, got directly from server",
          remote, prettySize(response.getContent().size()), prettySize(params.smallFileSize()));
      return FileOperationStatus.create(true, (long) response.getContent().size());
    } catch (MobileHarnessException e) {
      if (isMethodNonFound(e)) {
        logger.atInfo().log(
            "Skip getting file %s directly, because lab server doesn't support it yet.", remote);
        return FileOperationStatus.create(false, -1L);
      }
      throw e;
    }
  }

  private String prettySize(long size) {
    return StrUtil.getHumanReadableSize(size);
  }

  private UploadFileResponse uploadFileToGcsFromServer(UploadFileRequest request, Path local)
      throws MobileHarnessException, InterruptedException {
    return withTimeout(
        () -> {
          StartUploadingFileResponse response =
              withRetry(
                  () -> stub.startUploadingFile(request, initialTimeout, impersonationUser),
                  "trigger server uploading file " + local);
          if (response.hasResponse()) {
            return response.getResponse();
          }

          String processId = response.getProcessId();
          Sleeper sleeper = Sleeper.defaultSleeper();
          while (!Thread.interrupted()) {
            GetProcessStatusResponse statusResponse =
                withRetry(
                    () -> stub.getProcessStatus(processId, impersonationUser),
                    String.format("get status of uploading file [%s] %s", processId, local));
            if (statusResponse.getStatus() == ProcessStatus.FINISHED) {
              return statusResponse.getResponse().unpack(UploadFileResponse.class);
            }
            sleeper.sleep(GET_PROCESS_STATUS_INTERVAL);
          }
          throw new InterruptedException(
              String.format(
                  "Interrupted while waiting for the finish of getting file [%s] %s",
                  processId, local));
        },
        "let server uploading file " + local,
        /* isUploadFile= */ true);
  }

  private Path join(Path dir, Path sub) {
    if (sub.isAbsolute()) {
      sub = sub.getRoot().relativize(sub);
    }
    return dir.resolve(sub);
  }

  /** Decompress directory {@code zipPath}, and saves output in {@code outputDir}. */
  private void decompressDirectory(Path zipPath, Path outputDir)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.removeFileOrDir(outputDir);
    localFileUtil.prepareParentDir(outputDir);
    logger.atInfo().log(
        "Start to unzip the file %s to %s; timeout: %s ms",
        zipPath, outputDir, params.zipTimeout().toMillis());
    localFileUtil.unzipFile(
        zipPath.toAbsolutePath().toString(),
        outputDir.toAbsolutePath().toString(),
        params.zipTimeout());
    logger.atInfo().log(
        "Unzipped file: %s to %s; timeout: %s ms",
        zipPath, outputDir, params.zipTimeout().toMillis());
  }

  /**
   * Executes {@code func} and attempts at most {@link FileTransferParameters#attempts} times if
   * failed.
   */
  private <ResponseT> ResponseT withRetry(Callable<ResponseT> func, String msg)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Start to %s", msg);
    ArrayList<String> exceptions = new ArrayList<>();
    for (int i = 0; i < params.attempts(); i++) {
      try {
        ResponseT response = func.call();
        if (i > 0) {
          logger.atInfo().log("Finish to %s in %d retries", msg, i);
        } else {
          logger.atInfo().log("Finish to %s", msg);
        }
        return response;
      } catch (InterruptedException e) {
        throw e;
      } catch (MobileHarnessException e) {
        Optional<String> deadlineExceededStackTrace = lookForDeadlineExceededStackTrace(e);
        if (deadlineExceededStackTrace.isPresent()) {
          exceptions.add(Instant.now() + ":" + deadlineExceededStackTrace.get());
          continue;
        }
        throw e;
      } catch (Throwable e) {
        throw new MobileHarnessException(InfraErrorId.FT_GENERAL_ERROR, "Failed to " + msg, e);
      }
    }
    throw new MobileHarnessException(
        InfraErrorId.FT_GENERAL_ERROR,
        String.format(
            "Failed to %s after %s attempts. Exceptions from all tries:\n%s",
            msg, params.attempts(), String.join(",", exceptions)));
  }

  private static Optional<String> lookForDeadlineExceededStackTrace(MobileHarnessException e) {
    Optional<Status> status =
        lookForRpcStatus(e.getCause()).filter(s -> Objects.equals(s.getCode(), DEADLINE_EXCEEDED));
    return status.isPresent() ? Optional.of(Throwables.getStackTraceAsString(e)) : Optional.empty();
  }

  private static Optional<Status> lookForRpcStatus(Throwable cause) {
    while (cause != null) {
      if (cause instanceof StatusException) {
        return Optional.of(((StatusException) cause).getStatus());
      } else if (cause instanceof RpcExceptionWithErrorId
          && ((RpcExceptionWithErrorId) cause).getUnderlyingRpcException()
              instanceof StatusRuntimeException) {
        return Optional.of(
            ((StatusRuntimeException) ((RpcExceptionWithErrorId) cause).getUnderlyingRpcException())
                .getStatus());
      } else {
        cause = cause.getCause();
      }
    }
    return Optional.empty();
  }

  /**
   * Executes {@code func} in with timeout {@link FileTransferParameters#timeout}.
   *
   * @throws MobileHarnessException if failed to execute {@code func} or timeout
   */
  @SuppressWarnings("Interruption")
  @CanIgnoreReturnValue
  private <ResponseT> ResponseT withTimeout(
      Callable<ResponseT> func, String msg, boolean isUploadFile)
      throws InterruptedException, MobileHarnessException {
    logger.atInfo().log("Start to %s", msg);
    Future<ResponseT> future = threadPool.submit(func);
    try {
      ResponseT response = future.get(params.timeout().toMillis(), MILLISECONDS);
      logger.atInfo().log("Finish to %s", msg);
      return response;
    } catch (ExecutionException e) {
      throw new MobileHarnessException(InfraErrorId.FT_GENERAL_ERROR, "Failed to " + msg, e);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new MobileHarnessException(
          isUploadFile
              ? InfraErrorId.LAB_UPLOAD_FILE_TIMEOUT
              : InfraErrorId.LAB_DOWNLOAD_FILE_TIMEOUT,
          String.format("Timeout to %s", msg),
          e);
    }
  }

  /** Returns true if {@code file} is a writable file type. */
  private boolean isWritableFileType(Path file) {
    boolean isNonLocal =
        stream(RemoteFileType.values()).anyMatch(t -> file.toString().startsWith(t.prefix()));
    if (isNonLocal) {
      return NON_LOCAL_WRITABLE_FILE_TYPE.stream()
          .anyMatch(t -> file.toString().startsWith(t.prefix()));
    }
    // Local file.
    return true;
  }

  /** Returns true if {@code file} is a writable file type. */
  private boolean isLocalFileType(Path file) {
    return stream(RemoteFileType.values()).noneMatch(t -> file.startsWith(t.prefix()));
  }

  /** {@inheritDoc} */
  @Override
  public long downloadFile(String remote, String local)
      throws MobileHarnessException, InterruptedException {
    return getFile(Path.of(remote), Path.of(local));
  }

  /** {@inheritDoc} */
  @Override
  public List<FileInfo> listFiles(String dir) throws MobileHarnessException {
    return stub.listFiles(ListFilesRequest.newBuilder().setDirPath(dir).build(), impersonationUser)
        .getFileList();
  }

  /** {@inheritDoc} */
  @Override
  public void shutdown() {
    stub.shutdown();
    threadPool.shutdownNow();
  }

  private boolean fileOrDirExists(String path) {
    return localFileUtil.isFileOrDirExist(path);
  }

  private boolean dirExists(String local) {
    return localFileUtil.isDirExist(local);
  }

  private long getFileSize(Path file)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return localFileUtil.getFileSize(file);
  }

  private List<Path> listFilesForPath(Path local)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return listFilesForLocalPath(local);
  }

  private ImmutableList<Path> listFilesForLocalPath(Path local)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return localFileUtil.listFiles(local.toString(), true).stream()
        .map(File::toPath)
        .collect(toImmutableList());
  }

  private byte[] readBytes(Path local)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return readLocalBytes(local);
  }

  private byte[] readLocalBytes(Path local)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return localFileUtil.readBinaryFile(local.toString());
  }

  private void removeLocalFileOrDir(Path local)
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    localFileUtil.removeFileOrDir(local);
  }

  private boolean isFileExists(Path local) {
    return localFileUtil.isFileExist(local);
  }

  private void prepareParentDir(Path local)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    localFileUtil.prepareParentDir(local);
  }
}
