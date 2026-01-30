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

package com.google.devtools.mobileharness.shared.util.file.remote;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsApiObject;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams.Scope;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manager of Downloaded GcsFile. It caches files for multiple use. */
public class GcsFileManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Random for creating temporary files for compressing directory. */
  private static final Random random = new Random();

  /** Default TTL for local cached file. */
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

  /** Locks for uploading/downloading threads. The key is file path. */
  private static final ConcurrentHashMap<Path, CountDownLatch> transferringLocks =
      new ConcurrentHashMap<>();

  /**
   * A short time global cache for local file to gcs file and doesn't based on the file
   * checksum.(b/128417457).
   */
  private static final Cache<Path, ExecutionInfo> fileUploadCache =
      CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(3)).build();

  /** Home directory of output file. */
  private final Path homeDir;

  private final GcsUtil gcsUtil;

  private final LocalFileUtil localFileUtil;

  /** TTL of File Transfer caches in Google Cloud Storage. Default is 1 day. */
  private final Optional<Duration> cloudCacheTtl;

  /** Size of shard for uploading GCS file in parellel. */
  private final Optional<Long> uploadShardSize;

  /** Size of shard for downloading GCS file in parellel. */
  private final Optional<Long> downloadShardSize;

  /** Caches of local file. */
  @VisibleForTesting final Cache<String, Path> localCache;

  /**
   * Creates a GCS file manager of {@code bucket}. The local cached in {@code homeDir} is cleaned
   * automatically by GcsFileManager, DO NOT remove them during GcsFileManager is running.
   *
   * @param homeDir directory of local cache
   * @param bucket name of bucket to manage
   */
  public GcsFileManager(Path homeDir, String bucket)
      throws MobileHarnessException, InterruptedException {
    this(
        homeDir,
        bucket,
        Optional.empty(),
        DEFAULT_CACHE_TTL,
        Optional.empty(),
        Optional.empty(),
        GcsUtil.CredentialType.ofCredentialFile(getCredentialFile()));
  }

  @SuppressWarnings("GoodTime") // TODO: fix GoodTime violation
  public GcsFileManager(
      Path homeDir,
      String bucket,
      Optional<Duration> cloudCacheTtl,
      Duration localCacheTtl,
      Optional<Long> uploadShardSize,
      Optional<Long> downloadShardSize,
      GcsUtil.CredentialType credentialType)
      throws MobileHarnessException, InterruptedException {
    this(
        homeDir,
        new GcsUtil(new GcsParams(bucket, Scope.READ_WRITE, credentialType)),
        new LocalFileUtil(),
        cloudCacheTtl,
        localCacheTtl,
        uploadShardSize,
        downloadShardSize);
  }

  @SuppressWarnings("GoodTime") // TODO: fix GoodTime violation
  public GcsFileManager(
      Path homeDir,
      GcsUtil gcsUtil,
      LocalFileUtil localFileUtil,
      Optional<Duration> cloudCacheTtl,
      Duration localCacheTtl,
      Optional<Long> uploadShardSize,
      Optional<Long> downloadShardSize)
      throws MobileHarnessException, InterruptedException {
    this.homeDir = homeDir;
    this.gcsUtil = gcsUtil;
    this.localFileUtil = localFileUtil;
    this.cloudCacheTtl = cloudCacheTtl;
    this.uploadShardSize = uploadShardSize;
    this.downloadShardSize = downloadShardSize;

    removeFileOrDir(homeDir);
    localFileUtil.prepareDir(homeDir);
    this.localCache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(localCacheTtl)
            .removalListener(this::onLocalCacheRemoval)
            .build();
  }

  /**
   * Gets the credential file path.
   *
   * <p>It will first try to get the credential file from the {@code --file_transfer_cred_file}
   * flag, then from the {@code --internal_service_credential_file} flag.
   */
  public static String getCredentialFile() throws MobileHarnessException {
    LocalFileUtil localFileUtil = new LocalFileUtil();
    String fileTransferCredFile = Flags.instance().fileTransferCredFile.get();
    if (fileTransferCredFile != null && localFileUtil.isFileExist(fileTransferCredFile)) {
      return fileTransferCredFile;
    }

    String internalServiceCredFile = Flags.instance().internalServiceCredentialFile.get();
    if (internalServiceCredFile != null && localFileUtil.isFileExist(internalServiceCredFile)) {
      return internalServiceCredFile;
    }
    throw new MobileHarnessException(
        BasicErrorId.GCS_CREDENTIAL_FILE_NOT_FOUND,
        "Credential file not found. Please provide a valid credential file in flags"
            + " --file_transfer_cred_file or --internal_service_credential_file.");
  }

  /** Information of an execution (uploading/downloading). */
  @AutoValue
  public abstract static class ExecutionInfo {
    /** Size of the file in uploading/downloading. */
    public abstract long fileSize();

    /** Whether file is cached in peer side (so the execution is skipped. */
    public abstract boolean isCached();

    /** Checksum of the file in uploading/downloading. */
    public abstract String checksum();

    @VisibleForTesting
    public static ExecutionInfo create(long fileSize, boolean isCached, String checksum) {
      return new AutoValue_GcsFileManager_ExecutionInfo(fileSize, isCached, checksum);
    }
  }

  @AutoValue
  abstract static class ZipInfo {

    abstract String decodedChecksum();

    abstract Path zipFilePath();

    static ZipInfo create(String decodedChecksum, Path zipFilePath) {
      return new AutoValue_GcsFileManager_ZipInfo(decodedChecksum, zipFilePath);
    }
  }

  /**
   * Mounts {@code gcsFile} to {@code localFile}. {@code localFile} must not exist. The mounted file
   * is readable/executable, but not writable.
   *
   * @return {@code localFile}
   */
  @CanIgnoreReturnValue
  public Path mountGcsFile(GcsApiObject gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException {
    Path localCache = downloadGcsFileIfNonExisting(gcsFile).localCachePath();
    localFileUtil.prepareParentDir(localFile);
    localFileUtil.linkFileOrDir(localCache.toString(), localFile.toString());
    return localFile;
  }

  @CanIgnoreReturnValue
  public Path getGcsFileCache(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return downloadGcsFileIfNonExisting(gcsFile).localCachePath();
  }

  public CacheInfo getCacheInfo(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return downloadGcsFileIfNonExisting(gcsFile);
  }

  /** Gets name of GCS bucket which is connected to. */
  public String getBucketName() {
    return gcsUtil.getBucketName();
  }

  @CanIgnoreReturnValue
  public Path copyGcsFile(GcsApiObject gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException {
    Path localCache = downloadGcsFileIfNonExisting(gcsFile).localCachePath();
    localFileUtil.prepareParentDir(localFile);
    localFileUtil.copyFileOrDir(localCache.toString(), localFile.toString());
    localFileUtil.grantFileOrDirFullAccess(localFile);
    return localFile;
  }

  /**
   * Adds {@code bytes} to cache as a single file.
   *
   * @return cached file path
   */
  public Path addToCache(byte[] bytes) throws MobileHarnessException {
    String decodedChecksum = gcsUtil.decodeCrc32c(gcsUtil.calculateCrc32cOfBytes(bytes));
    CacheLoader loader =
        cache -> {
          localFileUtil.prepareParentDir(cache);
          localFileUtil.writeToFile(cache.toString(), bytes);
        };
    try {
      return loadCacheIfNonExisting(decodedChecksum, loader).localCachePath();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_SAVE_CACHE_ERROR,
          "Failed to add bytes to cache, size %d" + bytes.length,
          e);
    }
  }

  /**
   * @return {@code pair<cached_file, has_already_cached>}
   */
  private CacheInfo downloadGcsFileIfNonExisting(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    // Acquire the lock to download |gcsFile|. There is only one downloading thread allowed for
    // each gcsFile.
    CountDownLatch latch = new CountDownLatch(1);
    while (true) {
      CountDownLatch running = transferringLocks.putIfAbsent(gcsFile.path(), latch);
      if (running == null) {
        // Acquired.
        break;
      }
      running.await();
    }

    try {
      Optional<String> checksum = gcsUtil.getCrc32c(gcsFile);
      MobileHarnessExceptions.check(
          checksum.isPresent(),
          BasicErrorId.GCS_DOWNLOAD_FILE_ERROR,
          () -> String.format("GCS file %s doesn't exist", gcsFile));

      String decodedChecksum = gcsUtil.decodeCrc32c(checksum.get());
      logger.atInfo().log(
          "CRC32C of GCS file %s: [%s]; decoded crc32c: [%s]",
          gcsFile, checksum.get(), decodedChecksum);
      return loadCacheIfNonExisting(
          decodedChecksum, localCache -> downloadGcsFile(gcsFile, localCache));
    } finally {
      // Remove the item first before counting down.
      transferringLocks.remove(gcsFile.path());
      latch.countDown();
    }
  }

  /** Loader of gcs file. */
  private interface CacheLoader {

    /** loads gcs file to {@code cache}. */
    void load(Path cache) throws MobileHarnessException, InterruptedException;
  }

  /**
   * @return {@code pair<cached_file, has_already_cached>}
   */
  private CacheInfo loadCacheIfNonExisting(String key, CacheLoader loader)
      throws MobileHarnessException {
    try {
      AtomicBoolean isCached = new AtomicBoolean(true);
      Callable<Path> finalizedLoader =
          () -> {
            isCached.set(false);
            Path localCachedFile = homeDir.resolve(key);
            loader.load(localCachedFile);
            return localCachedFile;
          };
      Path localCachedFile = this.localCache.get(key, finalizedLoader);
      if (!localFileUtil.isFileOrDirExist(localCachedFile)) {
        // The cached file is deleted from outside. Delete the cache and retry.
        logger.atWarning().log(
            "Cached file %s was deleted outside. Try to recover it.", localCachedFile);
        this.localCache.invalidate(key);
        this.localCache.get(key, finalizedLoader);
      }
      MobileHarnessExceptions.check(
          localFileUtil.isFileOrDirExist(localCachedFile),
          BasicErrorId.GCS_LOAD_CACHE_ERROR,
          () ->
              String.format("Failed to Updated cache with key %s, path %s", key, localCachedFile));
      return CacheInfo.create(localCachedFile, isCached.get());
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new MobileHarnessException(
            BasicErrorId.GCS_LOAD_CACHE_ERROR,
            String.format("Failed to update cache with key %s", key),
            e);
      }
    }
  }

  /** Downloads GCS file {@code gcsFile} to {@code local}. */
  @CanIgnoreReturnValue
  private Path downloadGcsFile(GcsApiObject gcsFile, Path localCache)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.prepareParentDir(localCache);
    if (downloadShardSize.isPresent()) {
      gcsUtil.copyFileToLocalInParallel(gcsFile, localCache, downloadShardSize.get());
    } else {
      gcsUtil.copyFileToLocal(gcsFile, localCache);
    }
    localFileUtil.setFilePermission(localCache, "r-xr-xr-x");
    return localCache;
  }

  /**
   * Uploads local {@code fileOrDir} to GCS, with the checksum as the name. Return the gcs file
   * path.
   *
   * @return information of the uploading
   */
  public ExecutionInfo upload(Path fileOrDir) throws MobileHarnessException, InterruptedException {
    return upload(
        fileOrDir, /* zipStoreOnly= */ false, /* zipTimeout= */ Optional.empty(), Optional.empty());
  }

  /**
   * Uploads local {@code fileOrDir} to GCS, with the checksum as the name. Return the gcs file
   * path.
   *
   * @param fileOrDir file or directory to upload
   * @param zipStoreOnly whether pack all file together without any compression
   * @param zipTimeout the timeout of the zip operation in milliseconds; empty means default timeout
   * @return information of the uploading
   */
  public ExecutionInfo upload(
      Path fileOrDir,
      boolean zipStoreOnly,
      Optional<Duration> zipTimeout,
      Optional<String> checksum)
      throws MobileHarnessException, InterruptedException {
    AtomicBoolean isCached = new AtomicBoolean(true);
    Callable<ExecutionInfo> finalizedLoader =
        () -> {
          isCached.set(false);
          if (localFileUtil.isDirExist(fileOrDir)) {
            ZipInfo zipInfo = compressDirectory(fileOrDir, zipStoreOnly, zipTimeout, checksum);
            long fileSize = localFileUtil.getFileSize(zipInfo.zipFilePath());
            String decodedChecksum = zipInfo.decodedChecksum();
            ExecutionInfo executionInfo =
                ExecutionInfo.create(
                    fileSize,
                    !uploadFile(zipInfo.zipFilePath(), Path.of(decodedChecksum), "application/zip"),
                    decodedChecksum);
            // Remove the zip file after uploading.
            localFileUtil.removeFileOrDir(zipInfo.zipFilePath());
            return executionInfo;
          } else if (fileExists(fileOrDir)) {
            String decodedChecksum = checksum.orElse(gcsUtil.calculateChecksum(fileOrDir));
            long fileSize = gcsUtil.getFileSize(fileOrDir);
            return ExecutionInfo.create(
                fileSize,
                !uploadFile(fileOrDir, Path.of(decodedChecksum), "text/plain"),
                decodedChecksum);
          }
          throw new MobileHarnessException(
              BasicErrorId.GCS_UPLOAD_FILE_ERROR,
              String.format("File Or directory %s doesn't exist.", fileOrDir));
        };
    try {
      ExecutionInfo executionInfo = fileUploadCache.get(fileOrDir, finalizedLoader);
      return ExecutionInfo.create(
          executionInfo.fileSize(), isCached.get(), executionInfo.checksum());
    } catch (ExecutionException e) {
      return MobileHarnessExceptions.rethrow(e.getCause(), BasicErrorId.GCS_UPLOAD_FILE_ERROR);
    }
  }

  private boolean fileExists(Path fileOrDir) {
    return localFileUtil.isFileOrDirExist(fileOrDir);
  }

  /**
   * Uploads local {@code fileOrDir} as "text/plain" type to GCS, with the checksum as the name.
   *
   * @return true if file is upload; false if it is cached
   */
  @CanIgnoreReturnValue
  public boolean uploadFile(Path localFile, Path gcsFilePath)
      throws MobileHarnessException, InterruptedException {
    return uploadFile(localFile, gcsFilePath, "text/plain");
  }

  /**
   * Uploads local {@code fileOrDir} to GCS, with the checksum as the name.
   *
   * @return true if file is upload; false if it is cached
   */
  @CanIgnoreReturnValue
  public boolean uploadFile(Path localFile, Path gcsFilePath, String contentType)
      throws MobileHarnessException, InterruptedException {
    // Acquire the lock to upload |gcsFile|. There is only one uploading thread allowed for
    // each gcsFile.
    CountDownLatch latch = new CountDownLatch(1);
    while (true) {
      CountDownLatch running = transferringLocks.putIfAbsent(gcsFilePath, latch);
      if (running == null) {
        // Acquired.
        break;
      }
      running.await();
    }
    try {
      if (cloudCacheTtl.isPresent()) {
        if (uploadShardSize.isPresent()) {
          return gcsUtil.copyFileToCloudInParallelIfNonExistingOrDead(
              localFile, gcsFilePath, cloudCacheTtl.get(), uploadShardSize.get(), contentType);
        } else {
          return gcsUtil.copyFileToCloudIfNonExistingOrDead(
              localFile, gcsFilePath, cloudCacheTtl.get(), contentType);
        }
      } else {
        if (uploadShardSize.isPresent()) {
          gcsUtil.copyFileToCloudInParallel(
              localFile, gcsFilePath, uploadShardSize.get(), contentType);
        } else {
          gcsUtil.copyFileToCloud(localFile, gcsFilePath, contentType);
        }
        return true;
      }
    } finally {
      // Remove the item first before counting down.
      transferringLocks.remove(gcsFilePath);
      latch.countDown();
    }
  }

  /**
   * Compresses {@code dir} into a file named {@code <zipped_file_md5>.zip}
   *
   * @param dir directory to zip
   * @param zipStoreOnly whether pack all file together without any compression
   * @param zipTimeout the timeout of the zip operation in milliseconds; empty means default timeout
   * @param checksum the checksum of the directory; empty means it need to calculate the checksum
   * @return {@code pair<decoded_checksum, zip_file_path>}
   */
  private ZipInfo compressDirectory(
      Path dir, boolean zipStoreOnly, Optional<Duration> zipTimeout, Optional<String> checksum)
      throws MobileHarnessException, InterruptedException {
    if (!localFileUtil.isDirExist(dir)) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_COMPRESS_DIRECTORY, String.format("Directory %s doesn't exist.", dir));
    }

    Path tmpZipFile =
        homeDir.resolve(String.format("TMP_%s.zip", Long.toUnsignedString(random.nextLong())));
    try {
      localFileUtil.prepareParentDir(tmpZipFile);
      logger.atInfo().log("Start compressing: directory %s to tmp file %s.", dir, tmpZipFile);
      localFileUtil.zipDir(
          dir.toString(),
          tmpZipFile.toString(),
          /* sortFile= */ true,
          zipStoreOnly,
          /* compressionLevel= */ 1,
          zipTimeout.map(Timeout::fixed).orElse(null),
          /* keepLocalSourceRootBaseName= */ false,
          /* keepFileMetadata= */ false);
      String decodedChecksum = checksum.orElse(gcsUtil.calculateChecksum(tmpZipFile));
      String crc32cChecksum = gcsUtil.decodeCrc32c(gcsUtil.calculateCrc32c(tmpZipFile));
      logger.atInfo().log(
          "Compressed: directory %s to tmp file %s. checksum: %s. crc32c checksum: %s;"
              + "store_only: %s; timeout: %s",
          dir, tmpZipFile, decodedChecksum, crc32cChecksum, zipStoreOnly, zipTimeout);

      Path zipFile = homeDir.resolve(decodedChecksum + ".zip");
      return ZipInfo.create(
          decodedChecksum,
          localCache.get(
              crc32cChecksum,
              () -> {
                localFileUtil.moveFileOrDir(tmpZipFile, zipFile);
                localFileUtil.setFilePermission(zipFile, "r-xr-xr-x");
                logger.atInfo().log("Update local cache: %s: %s", crc32cChecksum, zipFile);
                return zipFile;
              }));
    } catch (ExecutionException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_COMPRESS_DIRECTORY, "Failed to cache compressed file " + tmpZipFile, e);
    } finally {
      if (localFileUtil.isFileExist(tmpZipFile)) {
        localFileUtil.removeFileOrDir(tmpZipFile);
      }
    }
  }

  /** Handles local cache removal notifications from {@link #localCache}. */
  private void onLocalCacheRemoval(RemovalNotification<String, Path> notification) {
    Path fileOrDir = notification.getValue();
    String fileInfo = String.format("[Cause: %s] %s", notification.getCause(), fileOrDir);
    if (!localFileUtil.isFileOrDirExist(fileOrDir)) {
      logger.atInfo().log("Ignore non existing cached file: %s", fileInfo);
    }
    try {
      removeFileOrDir(fileOrDir);
      logger.atInfo().log("Removed cached file: %s", fileInfo);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove cached file :%s", fileInfo);
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted while removing cached file: %s", fileInfo);
    }
  }

  private void removeFileOrDir(Path fileOrDir) throws MobileHarnessException, InterruptedException {
    if (localFileUtil.isDirExist(fileOrDir)) {
      localFileUtil.grantFileOrDirFullAccessRecursively(fileOrDir);
    } else if (localFileUtil.isFileExist(fileOrDir)) {
      localFileUtil.grantFileOrDirFullAccess(fileOrDir);
    }
    localFileUtil.removeFileOrDir(fileOrDir);
    logger.atInfo().log("Removed file: %s", fileOrDir);
  }

  /*
   * Lists all the files in the directory.
   */
  public List<String> listFiles(String gcsFileDir) throws MobileHarnessException {
    return gcsUtil.listFiles(gcsFileDir);
  }

  /** Returns whether the path {@code gcsFile} exists. */
  public boolean fileExist(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return gcsUtil.fileExist(gcsFile);
  }

  /** Returns whether the path {@code gcsFile} exists and also fresh. */
  public boolean fileExistAndFresh(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    if (cloudCacheTtl.isPresent()) {
      Optional<Duration> age = gcsUtil.getAge(gcsFile);
      return age.isPresent() && age.get().compareTo(cloudCacheTtl.get()) < 0;
    } else {
      return gcsUtil.fileExist(gcsFile);
    }
  }

  /** Returns the file size of the path {@code gcsFile}. */
  public long getGcsFileSize(Path gcsFile) throws MobileHarnessException, InterruptedException {
    return gcsUtil.getGcsFileSize(gcsFile);
  }

  /** Returns whether the path {@code gcsFile} is compressed. */
  public boolean isCompressed(Path gcsFile) throws MobileHarnessException, InterruptedException {
    return gcsUtil.isCompressed(gcsFile);
  }
}
