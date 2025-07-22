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

import static java.util.Comparator.comparing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.common.FileTransferConstant;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.CompressOptions;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.DownloadGcsFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetFileResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusRequest;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.proto.CloudFileTransfer.GetProcessStatusResponse.ProcessStatus;
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
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.FileHandlers;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.FileHandlers.Handler;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.common.proto.FileInfoProto.FileInfo;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsFileManager;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** Implements of {@code mobileharness.shared.CloudFileTransferService}. */
public class CloudFileTransferServiceImpl {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss z").withZone(googleTimeZone());

  /** Random generator for creating tmp files. */
  private static final Random random = new Random();

  /** TTL of process result cache. It is far more enough to cache a process result for 12 hours. */
  private static final Duration DEFAULT_PROCESS_STATUS_CACHE_TTL = Duration.ofHours(12);

  /** For generating process Id. */
  private static final AtomicLong nextProcessId = new AtomicLong(1000L);

  /** Thread pool to run all processes. */
  private static final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("cloud-file-transfer");

  private final FileHandlers handlers = new FileHandlers();

  private final LocalFileUtil localFileUtil;

  /** Cache of process result. */
  private final Cache<String, ProcessResponseOrException> processStatusCache;

  private final Path publicDir;

  /** Directory for temp file. */
  private final Path tmpDir;

  private final Path gcsHomeDir;

  private final ConcurrentHashMap<String, GcsFileManager> gcsFileManagers =
      new ConcurrentHashMap<>();

  /**
   * Creates a file transfer service based on Google Cloud storage. Caches in {@code homeDir} is
   * managed and cleaned automatically, DO NOT delete any of them while services is running.
   *
   * @param homeDir directory for local cached files
   * @param publicDir directory that is accessible by client
   */
  public CloudFileTransferServiceImpl(Path homeDir, Path publicDir)
      throws MobileHarnessException, InterruptedException {
    this(
        publicDir,
        homeDir.resolve("gcs"),
        new LocalFileUtil(),
        DEFAULT_PROCESS_STATUS_CACHE_TTL,
        homeDir.resolve("tmp"));
  }

  @VisibleForTesting
  CloudFileTransferServiceImpl(
      Path publicDir,
      Path gcsHomeDir,
      LocalFileUtil localFileUtil,
      Duration processStatusCacheTtl,
      Path tmpDir) {
    this.gcsHomeDir = gcsHomeDir;
    this.localFileUtil = localFileUtil;
    this.publicDir = publicDir;
    this.processStatusCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(processStatusCacheTtl)
            .removalListener(
                (RemovalListener<String, ProcessResponseOrException>)
                    notification ->
                        logger.atInfo().log(
                            "Remove cached process status: [Cause: %s] %s",
                            notification.getCause(), notification.getValue()))
            .build();
    this.tmpDir = tmpDir;
  }

  /** Downloads gcs file specified in {@code request}. */
  @CanIgnoreReturnValue
  public DownloadGcsFileResponse downloadGcsFile(DownloadGcsFileRequest request)
      throws MobileHarnessException {
    return downloadGcsFile(request, "");
  }

  /** Downloads gcs file specified in {@code request}. */
  private DownloadGcsFileResponse downloadGcsFile(DownloadGcsFileRequest request, String logPrefix)
      throws MobileHarnessException {
    logger.atInfo().log(
        "%sDownloading file %s from GCS file %s. request: %s",
        logPrefix,
        request.getOriginalPath(),
        request.getGcsFile(),
        TextFormat.printer().printToString(request));
    try {
      GcsFileManager gcsFileManager = getGcsFileManager(request.getBucket());
      Path localCache =
          gcsFileManager.getGcsFileCache(
              GcsUtil.GcsApiObject.create(Path.of(request.getGcsFile())));
      // Update modify time of |localCache| to flatter FileCleaner, which deletes files based on
      // their last modified time. b/111713552.
      localFileUtil.touchFileOrDir(localCache, false);
      handleReceivedFile(
          localCache,
          request.getIsCompressed(),
          logPrefix,
          request.getMetadata(),
          request.getOriginalPath(),
          request.hasCompressOptions() ? request.getCompressOptions() : null);
      logger.atInfo().log(
          "%sDownloaded file %s from GCS file %s",
          logPrefix, request.getOriginalPath(), request.getGcsFile());
      return DownloadGcsFileResponse.getDefaultInstance();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.FT_RPC_DOWNLOAD_GCS_FILE_INTERRUPTED, "Interrupted", e);
    }
  }

  private GcsFileManager getGcsFileManager(String bucket)
      throws MobileHarnessException, InterruptedException {
    GcsFileManager gcsFileManager =
        gcsFileManagers.computeIfAbsent(
            bucket,
            bucketName ->
                createGcsFileManager(
                    gcsHomeDir.resolve(bucketName),
                    bucketName,
                    Optional.of(FileTransferConstant.getCloudCacheTtl()),
                    FileTransferConstant.getLocalCacheTtl(),
                    Optional.of(FileTransferConstant.uploadShardSize()),
                    Optional.of(FileTransferConstant.downloadShardSize())));
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Failed to create GcsFileManager for bucket: " + bucket);
    }
    if (gcsFileManager == null) {
      throw new MobileHarnessException(
          InfraErrorId.FT_GCS_FILE_MANAGER_CREATION_ERROR,
          "Failed to create GcsFileManager for bucket: " + bucket);
    }

    return gcsFileManager;
  }

  @VisibleForTesting
  @Nullable
  GcsFileManager createGcsFileManager(
      Path homeDir,
      String bucketName,
      Optional<Duration> cloudCacheTtl,
      Duration localCacheTtl,
      Optional<Long> uploadShardSize,
      Optional<Long> downloadShardSize) {
    try {
      logger.atInfo().log(
          "Creating GcsFileManager for bucket: %s, cloudCacheTtl: %s, localCacheTtl: %s,"
              + " uploadShardSize: %s, downloadShardSize: %s",
          bucketName, cloudCacheTtl, localCacheTtl, uploadShardSize, downloadShardSize);
      return new GcsFileManager(
          homeDir,
          bucketName,
          cloudCacheTtl,
          localCacheTtl,
          uploadShardSize,
          downloadShardSize,
          GcsUtil.CredentialType.ofCredentialFile(GcsFileManager.getCredentialFile()));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to create GcsFileManager for bucket: %s", bucketName);
      return null;
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log(
          "Failed to create GcsFileManager for bucket: %s", bucketName);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private void handleReceivedFile(
      Path receivedFile,
      boolean isCompressed,
      String logPrefix,
      Any metadata,
      String originalPath,
      @Nullable CompressOptions compressOptions)
      throws MobileHarnessException, InterruptedException {
    @Nullable Path unzippedCache = null;
    try {
      Path finalizedReceivedFileOrDir;
      if (isCompressed) {
        // Decompress the file if it is compressed
        unzippedCache =
            localFileUtil.createTempFile(
                receivedFile.getParent(), receivedFile.getFileName().toString(), "_UNZIP");
        logger.atInfo().log("%sUnzip %s to %s", logPrefix, receivedFile, unzippedCache);
        decompressDirectory(receivedFile, unzippedCache, compressOptions);
        finalizedReceivedFileOrDir = unzippedCache;
      } else {
        finalizedReceivedFileOrDir = receivedFile;
      }
      handlers.notify(metadata, finalizedReceivedFileOrDir, Path.of(originalPath));
    } finally {
      // Cleanup the unzipped caches as soon as possible as zipped file for directory could be large
      if (unzippedCache != null && localFileUtil.isFileOrDirExist(unzippedCache)) {
        // Make all subfiles writable before delete. Otherwise, file transfer may fail on deleting
        // the non-writable files. b/72416819.
        localFileUtil.grantFileOrDirFullAccessRecursively(unzippedCache);
        localFileUtil.removeFileOrDir(unzippedCache);
        logger.atInfo().log("%sRemove temporary unzipped directory: %s", logPrefix, unzippedCache);
      }
    }
  }

  /** Decompresses directory {@code zipPath}, and saves output in {@code outputDir}. */
  private void decompressDirectory(
      Path zipPath, Path outputDir, @Nullable CompressOptions compressOptions)
      throws MobileHarnessException, InterruptedException {
    // Make all subfiles writable before delete. Otherwise, file transfer may fail on deleting the
    // non-writable files. b/72416819.
    if (localFileUtil.isDirExist(outputDir)) {
      localFileUtil.grantFileOrDirFullAccessRecursively(outputDir);
    }
    localFileUtil.removeFileOrDir(outputDir);
    localFileUtil.prepareParentDir(outputDir);

    Duration timeout =
        compressOptions != null ? Duration.ofMillis(compressOptions.getTimeoutMs()) : null;
    String info =
        localFileUtil.unzipFile(
            zipPath.toAbsolutePath().toString(), outputDir.toAbsolutePath().toString(), timeout);
    logger.atInfo().log("Unzip %s to %s:\n%s; timeout: %s", zipPath, outputDir, info, timeout);
  }

  /** Uploads a local file specified in {@code request} to GCS. */
  public UploadFileResponse uploadFile(UploadFileRequest request) throws MobileHarnessException {
    return uploadFile(request, "");
  }

  /** Uploads a local file specified in {@code request} to GCS. */
  public UploadFileResponse uploadFile(UploadFileRequest request, String logPrefix)
      throws MobileHarnessException {
    logger.atInfo().log(
        "%sUploading file %s to GCS. request: %s", logPrefix, request.getPath(), request);
    Path src = Path.of(request.getPath());
    if (!localFileUtil.isFileOrDirExist(src)) {
      throw new MobileHarnessException(InfraErrorId.FT_FILE_NOT_EXIST, "File doesn't exist:" + src);
    }

    String checksum;

    try {
      GcsFileManager gcsFileManager = getGcsFileManager(request.getBucket());
      if (request.hasCompressOptions()) {
        CompressOptions compressOptions = request.getCompressOptions();
        checksum =
            gcsFileManager
                .upload(
                    src,
                    compressOptions.getStoreOnly(),
                    Optional.of(Duration.ofMillis(compressOptions.getTimeoutMs())),
                    Optional.empty())
                .checksum();
      } else {
        checksum = gcsFileManager.upload(src).checksum();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(
          InfraErrorId.FT_RPC_UPLOAD_FILE_INTERRUPTED, "Interrupted", e);
    }

    logger.atInfo().log("%sUploaded file %s to GCS file %s", logPrefix, src, checksum);
    return UploadFileResponse.newBuilder()
        .setGcsFile(checksum)
        .setIsCompressed(localFileUtil.isDirExist(src))
        .setChecksum(checksum)
        .build();
  }

  /** Lists sub files/directories under directory specified in {@code request}. */
  public ListFilesResponse listFiles(ListFilesRequest request) throws MobileHarnessException {
    String dir = request.getDirPath();
    logger.atInfo().log("List files in: %s", request.getDirPath());

    validatePath(Path.of(dir));
    List<File> files = new ArrayList<>(localFileUtil.listFiles(dir, false));

    // Sort the files according to their last modified date.
    files.sort(
        comparing(
            File::lastModified,
            (Long leftProperty, Long rightProperty) -> leftProperty > rightProperty ? -1 : 1));
    ListFilesResponse.Builder listFilesResponseBuilder = ListFilesResponse.newBuilder();
    for (File file : files) {
      listFilesResponseBuilder.addFile(
          FileInfo.newBuilder()
              .setFilepath(file.getAbsolutePath())
              .setModifiedtime(
                  DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(file.lastModified())))
              .setSize(StrUtil.getHumanReadableSize(file.length())));
    }
    return listFilesResponseBuilder.build();
  }

  public StartUploadingFileResponse startUploadingFile(StartUploadingFileRequest request)
      throws MobileHarnessException {
    String processId = nextProcessId();
    String actionInfo = String.format("upload file %s to GCS", request.getRequest().getPath());
    logger.atInfo().log("[%s] Starting %s. request: %s", processId, actionInfo, request);
    processStatusCache.put(
        processId,
        ProcessResponseOrException.of(
            GetProcessStatusResponse.newBuilder().setStatus(ProcessStatus.RUNNING).build()));
    ListenableFuture<ProcessResponseOrException> future =
        threadPool.submit(new CloudProcess<>(processId, this::uploadFile, request.getRequest()));
    future.addListener(() -> logger.atInfo().log("[%s] finished", processId), threadPool);
    logger.atInfo().log("[%s] Started %s", processId, actionInfo);

    int initialTimeout = request.getInitialTimeoutSec();
    if (initialTimeout > 0) {
      logger.atInfo().log("[%s] Start initial waiting for %s", processId, actionInfo);
      try {
        StartUploadingFileResponse response =
            StartUploadingFileResponse.newBuilder()
                .setProcessId(processId)
                .setResponse(
                    future
                        .get(initialTimeout, TimeUnit.SECONDS)
                        .getWrappedResponse(UploadFileResponse.class))
                .build();
        logger.atInfo().log(
            "[%s] %s is finished in initial timeout %s seconds",
            processId, actionInfo, initialTimeout);
        return response;
      } catch (ExecutionException e) {
        // It mostly shouldn't happen, because |Process.run| catches all errors.
        throw new MobileHarnessException(
            InfraErrorId.FT_FILE_UPLOAD_ERROR, "Failed to " + actionInfo, e);
      } catch (TimeoutException e) {
        logger.atInfo().log(
            "[%s] %s is still running after initial timeout %s seconds",
            processId, actionInfo, initialTimeout);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MobileHarnessException(
            InfraErrorId.FT_RPC_START_UPLOADING_FILE_INTERRUPTED, "Interrupted", e);
      }
    }

    return StartUploadingFileResponse.newBuilder().setProcessId(processId).build();
  }

  public StartDownloadingGcsFileResponse startDownloadingGcsFile(
      StartDownloadingGcsFileRequest request) throws MobileHarnessException {
    String processId = nextProcessId();
    String actionInfo =
        String.format(
            "download file %s from GCS file %s",
            request.getRequest().getOriginalPath(), request.getRequest().getGcsFile());

    logger.atInfo().log(
        "[%s] Starting %s; request: %s",
        processId, actionInfo, TextFormat.printer().printToString(request));
    processStatusCache.put(
        processId,
        ProcessResponseOrException.of(
            GetProcessStatusResponse.newBuilder().setStatus(ProcessStatus.RUNNING).build()));

    ListenableFuture<ProcessResponseOrException> future =
        threadPool.submit(
            new CloudProcess<>(processId, this::downloadGcsFile, request.getRequest()));
    future.addListener(() -> logger.atInfo().log("[%s] finished", processId), threadPool);
    logger.atInfo().log("[%s] Started %s", processId, actionInfo);

    int initialTimeout = request.getInitialTimeoutSec();
    if (initialTimeout > 0) {
      logger.atInfo().log("[%s] Start initial waiting for %s", processId, actionInfo);
      try {
        StartDownloadingGcsFileResponse response =
            StartDownloadingGcsFileResponse.newBuilder()
                .setProcessId(processId)
                .setResponse(
                    future
                        .get(request.getInitialTimeoutSec(), TimeUnit.SECONDS)
                        .getWrappedResponse(DownloadGcsFileResponse.class))
                .build();
        logger.atInfo().log(
            "[%s] %s is finished in initial timeout %s seconds",
            processId, actionInfo, initialTimeout);
        return response;
      } catch (ExecutionException e) {
        // It mostly shouldn't happen, because |Process.run| catches all errors.
        throw new MobileHarnessException(
            InfraErrorId.FT_FILE_DOWNLOAD_ERROR, "Failed to " + actionInfo, e);
      } catch (TimeoutException e) {
        logger.atInfo().log(
            "[%s] %s is still running after initial timeout %s seconds",
            processId, actionInfo, request.getInitialTimeoutSec());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MobileHarnessException(
            InfraErrorId.FT_RPC_START_DOWNLOADING_GCS_FILE_INTERRUPTED, "Interrupted", e);
      }
    }
    return StartDownloadingGcsFileResponse.newBuilder().setProcessId(processId).build();
  }

  public GetProcessStatusResponse getProcessStatus(GetProcessStatusRequest request)
      throws MobileHarnessException {
    @Nullable
    ProcessResponseOrException response = processStatusCache.getIfPresent(request.getProcessId());
    if (response == null) {
      logger.atWarning().log(
          "Try to get status of non existing process: %s", request.getProcessId());
      return GetProcessStatusResponse.newBuilder().setStatus(ProcessStatus.UNKNOWN).build();
    }
    return response.get();
  }

  /** Saves content specified in {@code request} to a local file directly. */
  @CanIgnoreReturnValue
  public SaveFileResponse saveFile(SaveFileRequest request) throws MobileHarnessException {
    logger.atFine().log(
        "Saving file %s directly. File metadata: %s",
        request.getOriginalPath(), request.getMetadata());
    try {
      // It's safe to use the default bucket name because GcsFileManager.addToCache doesn't use the
      // bucket name.
      GcsFileManager gcsFileManager = getGcsFileManager(FileTransferConstant.getBucket());
      // Save the file to the cache of gcsFileManager, so that gcsFileManager can takes care of its
      // life cycle.
      handleReceivedFile(
          gcsFileManager.addToCache(request.getContent().toByteArray()),
          request.getIsCompressed(),
          "",
          request.getMetadata(),
          request.getOriginalPath(),
          request.hasCompressOptions() ? request.getCompressOptions() : null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MobileHarnessException(InfraErrorId.FT_RPC_SAVE_FILE_INTERRUPTED, "Interrupted", e);
    }
    logger.atFine().log("Saved file %s directly.", request.getOriginalPath());
    return SaveFileResponse.getDefaultInstance();
  }

  /** Gets content of the file specified {@code request} directly. */
  public GetFileResponse getFile(GetFileRequest request) throws MobileHarnessException {
    logger.atInfo().log("Getting file %s directly. request: %s", request.getPath(), request);

    Path src = Path.of(request.getPath());
    if (!localFileUtil.isFileOrDirExist(src)) {
      throw new MobileHarnessException(
          InfraErrorId.FT_FILE_NOT_EXIST, "File doesn't exist: " + src);
    }

    GetFileResponse.Builder response = GetFileResponse.newBuilder();
    if (localFileUtil.isDirExist(src)) {
      long totalFileSize = 0;
      for (Path file : localFileUtil.listFilePaths(src, true)) {
        totalFileSize += localFileUtil.getFileSize(file);
        if (totalFileSize > request.getFileSizeByteLimit()) {
          logger.atInfo().log(
              "Failed to get directory %s directly, because total size exceed limitation %s",
              request.getPath(), request.getFileSizeByteLimit());
          return response.setSizeExceed(true).build();
        }
      }

      Path tmpZipFile =
          tmpDir.resolve(String.format("%s.zip", Long.toUnsignedString(random.nextLong())));
      try {
        localFileUtil.prepareParentDir(tmpZipFile);
        try {
          if (request.hasCompressOptions()) {
            CompressOptions compressOptions = request.getCompressOptions();
            localFileUtil.zipDir(
                src.toString(),
                tmpZipFile.toString(),
                /* sortFile= */ false,
                compressOptions.getStoreOnly(),
                /* compressionLevel= */ 1,
                Timeout.fixed(Duration.ofMillis(compressOptions.getTimeoutMs())));
          } else {
            localFileUtil.zipDir(src.toString(), tmpZipFile.toString());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new MobileHarnessException(
              InfraErrorId.FT_RPC_GET_FILE_INTERRUPTED, "Interrupted", e);
        }
        response
            .setIsCompressed(true)
            .setContent(ByteString.copyFrom(localFileUtil.readBinaryFile(tmpZipFile.toString())));
      } finally {
        if (localFileUtil.isFileOrDirExist(tmpZipFile)) {
          try {
            localFileUtil.removeFileOrDir(tmpZipFile);
          } catch (Exception e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            logger.atInfo().withCause(e).log("Failed to remove the file or dir: %s", tmpZipFile);
          }
        }
      }
    } else {
      if (localFileUtil.getFileSize(src) > request.getFileSizeByteLimit()) {
        logger.atInfo().log(
            "Failed to get file %s directly, because total size exceed limitation %s",
            request.getPath(), request.getFileSizeByteLimit());
        return response.setSizeExceed(true).build();
      }
      response.setContent(ByteString.copyFrom(localFileUtil.readBinaryFile(src.toString())));
    }
    logger.atInfo().log(
        "Getting file %s directly, size : %s", request.getPath(), response.getContent().size());
    return response.build();
  }

  /** Validates directory that client requires. */
  @CanIgnoreReturnValue
  private Path validatePath(Path path) throws MobileHarnessException {
    if (!path.isAbsolute()) {
      throw new MobileHarnessException(
          InfraErrorId.FT_FILE_PATH_ERROR, "Path is not absolute: " + path);
    }
    if (!path.startsWith(publicDir.toString())) {
      throw new MobileHarnessException(
          InfraErrorId.FT_FILE_PATH_ERROR, "Permission denied: can not access directory " + path);
    }
    if (!localFileUtil.isDirExist(path)) {
      throw new MobileHarnessException(
          InfraErrorId.FT_FILE_NOT_EXIST, "Required path doesn't exist: " + path);
    }
    return path;
  }

  private static ZoneId googleTimeZone() {
    return ZoneId.of("America/Los_Angeles");
  }

  /**
   * Adds handler for request with metadata in the type of {@code metadataClass}. There is only one
   * handler allowed for each metadata class, because the handler may move the receive file away.
   */
  @CanIgnoreReturnValue
  public <T extends Message> CloudFileTransferServiceImpl addHandler(
      Class<T> metadataClass, Handler<T> handler) throws MobileHarnessException {
    handlers.addHandler(metadataClass, handler);
    return this;
  }

  private String nextProcessId() {
    return Long.toUnsignedString(nextProcessId.getAndIncrement());
  }

  /** Process to upload or download a file. */
  private class CloudProcess<RequestT extends Message, ResponseT extends Message>
      implements Callable<ProcessResponseOrException> {

    private final String processId;
    private final RequestHandler<RequestT, ResponseT> func;
    private final RequestT request;

    private CloudProcess(
        String processId, RequestHandler<RequestT, ResponseT> func, RequestT request) {
      this.processId = processId;
      this.func = func;
      this.request = request;
    }

    @Override
    public ProcessResponseOrException call() {
      ProcessResponseOrException processResponse;
      try {
        ResponseT response = func.apply(request, String.format("[%s] ", processId));
        processResponse =
            ProcessResponseOrException.of(
                GetProcessStatusResponse.newBuilder()
                    .setStatus(ProcessStatus.FINISHED)
                    .setResponse(Any.pack(response))
                    .build());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        processResponse =
            ProcessResponseOrException.of(
                new MobileHarnessException(
                    InfraErrorId.FT_CLOUD_PROCESS_INTERRUPTED,
                    String.format("Process %s is interrupted", processId)));
      } catch (MobileHarnessException e) {
        processResponse = ProcessResponseOrException.of(e);
      }
      processStatusCache.put(processId, processResponse);
      return processResponse;
    }
  }

  /** Handler of a request. */
  private interface RequestHandler<RequestT extends Message, ResponseT extends Message> {
    ResponseT apply(RequestT request, String logPrefix)
        throws MobileHarnessException, InterruptedException;
  }

  /** Holder of a response or an exception. */
  private static class ProcessResponseOrException {

    private static ProcessResponseOrException of(GetProcessStatusResponse response) {
      return new ProcessResponseOrException(Optional.of(response), Optional.empty());
    }

    private static ProcessResponseOrException of(MobileHarnessException exception) {
      return new ProcessResponseOrException(Optional.empty(), Optional.of(exception));
    }

    private final Optional<GetProcessStatusResponse> response;
    private final Optional<MobileHarnessException> exception;

    private ProcessResponseOrException(
        Optional<GetProcessStatusResponse> response, Optional<MobileHarnessException> exception) {
      this.response = response;
      this.exception = exception;
    }

    private GetProcessStatusResponse get() throws MobileHarnessException {
      if (response.isPresent()) {
        return response.get();
      }
      throw exception.get();
    }

    /** Gets responses wrapped in a {@link GetProcessStatusResponse}. */
    private <T extends Message> T getWrappedResponse(Class<T> clazz) throws MobileHarnessException {
      Any raw = get().getResponse();
      try {
        return raw.unpack(clazz);
      } catch (InvalidProtocolBufferException e) {
        throw new MobileHarnessException(
            InfraErrorId.FT_INVALID_PROTOCOL,
            String.format("Failed to convert Any %s to object of class %s", raw, clazz),
            e);
      }
    }

    @Override
    public String toString() {
      return response
          .map(response -> "Response: " + response)
          .orElseGet(() -> "Exception: " + exception.get());
    }
  }
}
