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

import static com.google.common.collect.Comparators.max;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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

  private static final int CLEANUP_THREAD_POOL_MAX_SIZE = 4;

  /**
   * Content type of the object of a file. {@link #isCompressed} reads "application/zip" as a zipped
   * directory.
   */
  private static final String FILE_CONTENT_TYPE = "text/plain";

  /** What {@link #copyFromGcs} did. */
  public enum ServerCopyOutcome {
    /** GCS copied the source to the object. */
    COPIED,
    /** The object was already fresh once other transfers of it finished, so nothing was copied. */
    REUSED,
    /** GCS did not copy the source, so the caller is to upload the file instead. */
    NOT_COPIED,
  }

  /**
   * Whether a transfer queries GCS for a fresh destination object when it acquires the transfer
   * lock without waiting for another thread.
   */
  public enum FreshnessCheck {
    /** Checks the in-process freshness cache, then queries GCS on a miss. */
    ALWAYS,
    /**
     * Checks the in-process freshness cache, and skips the remote GCS query when freshness caching
     * is enabled and no other thread held the transfer lock. Callers should pass this only right
     * after {@link #fileExistAndFresh} returned false for the same object.
     */
    RECHECK_IF_CONTENDED,
  }

  /**
   * Thread pool for removing the home directories that new instances replace. Shared and bounded,
   * since callers create an instance per file transfer client and nobody waits on the removal.
   */
  private static final ExecutorService cleanupThreadPool =
      ThreadPools.createStandardThreadPoolWithMaxSize(
          "gcs-file-manager-cleanup", CLEANUP_THREAD_POOL_MAX_SIZE);

  /** Home directory of output file. */
  private final Path homeDir;

  private final GcsUtil gcsUtil;

  private final LocalFileUtil localFileUtil;

  /** TTL of File Transfer caches in Google Cloud Storage. Default is 1 day. */
  private final Optional<Duration> cloudCacheTtl;

  /** Size of shard for uploading GCS file in parallel. */
  private final Optional<Long> uploadShardSize;

  /** Size of shard for downloading GCS file in parellel. */
  private final Optional<Long> downloadShardSize;

  private final CloudFreshnessCache freshnessCache;

  /** Caches of local file. */
  @VisibleForTesting final Cache<String, Path> localCache;

  /**
   * Creates an instance to manage a specified bucket with the specified credential type.
   *
   * @param homeDir directory of local cache
   * @param bucket name of bucket to manage
   * @param credentialType credential type for GCS
   */
  public GcsFileManager(Path homeDir, String bucket, GcsUtil.CredentialType credentialType)
      throws MobileHarnessException, InterruptedException {
    this(
        homeDir,
        bucket,
        Optional.empty(),
        DEFAULT_CACHE_TTL,
        Optional.empty(),
        Optional.empty(),
        credentialType);
  }

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
        GcsUtilFactory.create(new GcsParams(bucket, Scope.READ_WRITE, credentialType)),
        new LocalFileUtil(),
        cloudCacheTtl,
        localCacheTtl,
        uploadShardSize,
        downloadShardSize,
        Ticker.systemTicker());
  }

  @VisibleForTesting
  GcsFileManager(
      Path homeDir,
      GcsUtil gcsUtil,
      LocalFileUtil localFileUtil,
      Optional<Duration> cloudCacheTtl,
      Duration localCacheTtl,
      Optional<Long> uploadShardSize,
      Optional<Long> downloadShardSize,
      Ticker ticker)
      throws MobileHarnessException, InterruptedException {
    this(
        homeDir,
        gcsUtil,
        localFileUtil,
        cloudCacheTtl,
        localCacheTtl,
        uploadShardSize,
        downloadShardSize,
        ticker,
        cleanupThreadPool);
  }

  @VisibleForTesting
  GcsFileManager(
      Path homeDir,
      GcsUtil gcsUtil,
      LocalFileUtil localFileUtil,
      Optional<Duration> cloudCacheTtl,
      Duration localCacheTtl,
      Optional<Long> uploadShardSize,
      Optional<Long> downloadShardSize,
      Ticker ticker,
      Executor cleanupExecutor)
      throws MobileHarnessException, InterruptedException {
    this.homeDir = homeDir;
    this.gcsUtil = gcsUtil;
    this.localFileUtil = localFileUtil;
    this.cloudCacheTtl = cloudCacheTtl;
    this.uploadShardSize = uploadShardSize;
    this.downloadShardSize = downloadShardSize;
    this.freshnessCache =
        CloudFreshnessCache.create(
            cloudCacheTtl, Flags.fileTransferCloudFreshCacheTtl.getNonNull(), ticker);

    if (localFileUtil.isDirExist(homeDir)) {
      Path tmpDirToDelete =
          homeDir.resolveSibling(
              homeDir.getFileName().toString()
                  + "_delete_"
                  + System.currentTimeMillis()
                  + "_"
                  + random.nextLong());
      localFileUtil.moveFileOrDir(homeDir, tmpDirToDelete);
      cleanupExecutor.execute(() -> {
        try {
          removeFileOrDir(localFileUtil, tmpDirToDelete);
        } catch (InterruptedException e) {
          logger.atWarning().withCause(e).log(
              "Interrupted while removing old cache directory: %s", tmpDirToDelete);
          Thread.currentThread().interrupt();
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to remove old cache directory: %s", tmpDirToDelete);
        }
      });
    }
    localFileUtil.prepareDir(homeDir);

    this.localCache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(localCacheTtl)
            .ticker(ticker)
            .removalListener(this::onLocalCacheRemoval)
            .build();
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
    // Only one downloading thread is allowed for each gcsFile.
    TransferLock lock = acquireTransferLock(gcsFile.path());
    try {
      CloudFreshnessCache.ReadStamp readStamp = freshnessCache.beginRead();
      Optional<String> checksum = gcsUtil.getCrc32c(gcsFile);
      if (checksum.isEmpty() && gcsFile.generationNumber().isEmpty()) {
        freshnessCache.invalidate(gcsFile.path(), readStamp);
      }
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
      releaseTransferLock(gcsFile.path(), lock);
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

  /** Downloads GCS file {@code gcsFile} to {@code localCache}. */
  @CanIgnoreReturnValue
  private Path downloadGcsFile(GcsApiObject gcsFile, Path localCache)
      throws MobileHarnessException, InterruptedException {
    localFileUtil.prepareParentDir(localCache);
    // Stage to a sibling temp file and rename so hard links to an evicted entry are never
    // overwritten in place.
    Path tmpFile =
        localCache.resolveSibling(
            String.format(
                "TMP_%s_%s", localCache.getFileName(), Long.toUnsignedString(random.nextLong())));
    try {
      if (downloadShardSize.isPresent()) {
        gcsUtil.copyFileToLocalInParallel(gcsFile, tmpFile, downloadShardSize.get());
      } else {
        gcsUtil.copyFileToLocal(gcsFile, tmpFile);
      }
      localFileUtil.setFilePermission(tmpFile, "r-xr-xr-x");
      localFileUtil.moveFileOrDir(tmpFile, localCache);
      return localCache;
    } finally {
      removeTmpFileBestEffort(tmpFile);
    }
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
   * @param checksum the checksum of the directory; empty means it need to calculate the checksum
   * @return information of the uploading
   */
  public ExecutionInfo upload(
      Path fileOrDir,
      boolean zipStoreOnly,
      Optional<Duration> zipTimeout,
      Optional<String> checksum)
      throws MobileHarnessException, InterruptedException {
    return upload(fileOrDir, zipStoreOnly, zipTimeout, checksum, FreshnessCheck.ALWAYS);
  }

  /**
   * Same as {@link #upload(Path, boolean, Optional, Optional)}, controlling whether GCS is queried
   * again for a provided {@code checksum} when no other thread held the transfer lock.
   *
   * <p>When {@code checksum} is empty (the object name is computed during upload) or {@code
   * fileOrDir} is a directory (compressed before upload), GCS is always checked.
   */
  public ExecutionInfo upload(
      Path fileOrDir,
      boolean zipStoreOnly,
      Optional<Duration> zipTimeout,
      Optional<String> checksum,
      FreshnessCheck freshnessCheck)
      throws MobileHarnessException, InterruptedException {
    FreshnessCheck effectiveCheck = checksum.isPresent() ? freshnessCheck : FreshnessCheck.ALWAYS;
    AtomicBoolean isCached = new AtomicBoolean(true);
    Callable<ExecutionInfo> finalizedLoader =
        () -> {
          isCached.set(false);
          if (localFileUtil.isDirExist(fileOrDir)) {
            ZipInfo zipInfo = compressDirectory(fileOrDir, zipStoreOnly, zipTimeout, checksum);
            try {
              long fileSize = localFileUtil.getFileSize(zipInfo.zipFilePath());
              String decodedChecksum = zipInfo.decodedChecksum();
              return ExecutionInfo.create(
                  fileSize,
                  !uploadFile(
                      zipInfo.zipFilePath(),
                      Path.of(decodedChecksum),
                      "application/zip",
                      FreshnessCheck.ALWAYS),
                  decodedChecksum);
            } finally {
              // Each upload owns its temporary zip so cleanup cannot race with concurrent uploads.
              removeTmpFileBestEffort(zipInfo.zipFilePath());
            }
          } else if (fileExists(fileOrDir)) {
            String decodedChecksum =
                checksum.isPresent() ? checksum.get() : gcsUtil.calculateChecksum(fileOrDir);
            long fileSize = gcsUtil.getFileSize(fileOrDir);
            return ExecutionInfo.create(
                fileSize,
                !uploadFile(fileOrDir, Path.of(decodedChecksum), FILE_CONTENT_TYPE, effectiveCheck),
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

  /** Returns whether {@link #upload} can read what is at {@code fileOrDir}. */
  public boolean canUpload(Path fileOrDir) {
    return localFileUtil.isDirExist(fileOrDir) || fileExists(fileOrDir);
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
    return uploadFile(localFile, gcsFilePath, FILE_CONTENT_TYPE, FreshnessCheck.ALWAYS);
  }

  /**
   * Uploads local {@code fileOrDir} to GCS, with the checksum as the name.
   *
   * @return true if file is upload; false if it is cached
   */
  @CanIgnoreReturnValue
  public boolean uploadFile(Path localFile, Path gcsFilePath, String contentType)
      throws MobileHarnessException, InterruptedException {
    return uploadFile(localFile, gcsFilePath, contentType, FreshnessCheck.ALWAYS);
  }

  /**
   * Same as {@link #uploadFile(Path, Path, String)}, controlling whether GCS is queried again for
   * {@code gcsFilePath} when no other thread held the transfer lock.
   *
   * @return true if file is uploaded; false if it is already fresh in GCS
   */
  @CanIgnoreReturnValue
  @VisibleForTesting
  boolean uploadFile(
      Path localFile, Path gcsFilePath, String contentType, FreshnessCheck freshnessCheck)
      throws MobileHarnessException, InterruptedException {
    // Only one uploading thread is allowed for each gcsFile.
    TransferLock lock = acquireTransferLock(gcsFilePath);
    try {
      if (cloudCacheTtl.isPresent()
          && isFreshUnderTransferLock(gcsFilePath, lock, freshnessCheck)) {
        logger.atInfo().log(
            "Skip copying file %s to Google Cloud Storage %s, because the gcs file is fresh"
                + " within ttl [%s]",
            localFile, gcsFilePath, cloudCacheTtl.get());
        return false;
      }
      if (uploadShardSize.isPresent()) {
        gcsUtil.copyFileToCloudInParallel(
            localFile, gcsFilePath, uploadShardSize.get(), contentType);
      } else {
        gcsUtil.copyFileToCloud(localFile, gcsFilePath, contentType);
      }
      freshnessCache.recordFresh(gcsFilePath);
      return true;
    } finally {
      releaseTransferLock(gcsFilePath, lock);
    }
  }

  /**
   * Has GCS copy {@code source} to {@code gcsFile} as the kind of object {@link #upload} makes of a
   * file, so the bytes do not pass through this process. Skips the copy if {@code gcsFile} is fresh
   * once the transfers of it by other threads are over, using {@code freshnessCheck} to control
   * whether GCS is queried again when no other thread held the transfer lock.
   *
   * @param localCopy a local file whose bytes the object is to hold, in which case the source is
   *     copied only if its size and CRC32C match
   */
  public ServerCopyOutcome copyFromGcs(
      GcsUri source, Path gcsFile, Optional<Path> localCopy, FreshnessCheck freshnessCheck)
      throws MobileHarnessException, InterruptedException {
    // The lock uploadFile takes, so an upload and a copy of one object do not run at once.
    TransferLock lock = acquireTransferLock(gcsFile);
    try {
      if (isFreshUnderTransferLock(gcsFile, lock, freshnessCheck)) {
        return ServerCopyOutcome.REUSED;
      }
      GcsUtil.ServerCopyResult result =
          gcsUtil.copyFromGcs(source, gcsFile, FILE_CONTENT_TYPE, localCopy);
      if (result == GcsUtil.ServerCopyResult.COPIED) {
        freshnessCache.recordFresh(gcsFile);
        return ServerCopyOutcome.COPIED;
      }
      logger.atInfo().log("Did not copy %s to %s on the server: %s", source, gcsFile, result);
      return ServerCopyOutcome.NOT_COPIED;
    } finally {
      releaseTransferLock(gcsFile, lock);
    }
  }

  private record TransferLock(CountDownLatch latch, boolean waited) {}

  private static TransferLock acquireTransferLock(Path gcsFilePath) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    boolean waited = false;
    while (true) {
      CountDownLatch running = transferringLocks.putIfAbsent(gcsFilePath, latch);
      if (running == null) {
        return new TransferLock(latch, waited);
      }
      waited = true;
      running.await();
    }
  }

  private static void releaseTransferLock(Path gcsFilePath, TransferLock lock) {
    // Remove the entry before counting down so waiters acquire cleanly.
    transferringLocks.remove(gcsFilePath);
    lock.latch().countDown();
  }

  /**
   * Compresses {@code dir} into a temporary zip file owned by the caller.
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
    boolean succeeded = false;
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
      String decodedChecksum =
          checksum.isPresent() ? checksum.get() : gcsUtil.calculateChecksum(tmpZipFile);
      logger.atInfo().log(
          "Compressed: directory %s to tmp file %s. checksum: %s; store_only: %s; timeout: %s",
          dir, tmpZipFile, decodedChecksum, zipStoreOnly, zipTimeout);
      succeeded = true;
      return ZipInfo.create(decodedChecksum, tmpZipFile);
    } finally {
      if (!succeeded) {
        removeTmpFileBestEffort(tmpZipFile);
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
      removeFileOrDir(localFileUtil, fileOrDir);
      logger.atInfo().log("Removed cached file: %s", fileInfo);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove cached file :%s", fileInfo);
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted while removing cached file: %s", fileInfo);
    }
  }

  private void removeTmpFileBestEffort(Path tmpFile) {
    boolean interrupted = Thread.interrupted();
    try {
      if (localFileUtil.isFileExist(tmpFile)) {
        localFileUtil.removeFileOrDir(tmpFile);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove temporary file: %s", tmpFile);
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted when removing temporary file: %s", tmpFile);
      interrupted = true;
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void removeFileOrDir(LocalFileUtil localFileUtil, Path fileOrDir)
      throws MobileHarnessException, InterruptedException {
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
    CloudFreshnessCache.ReadStamp readStamp = freshnessCache.beginRead();
    boolean exists = gcsUtil.fileExist(gcsFile);
    if (!exists && gcsFile.generationNumber().isEmpty()) {
      freshnessCache.invalidate(gcsFile.path(), readStamp);
    }
    return exists;
  }

  /** Returns whether the path {@code gcsFile} exists and also fresh. */
  public boolean fileExistAndFresh(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    if (freshnessCache.isFresh(gcsFile)) {
      return true;
    }
    return checkFileExistAndFreshInGcs(gcsFile);
  }

  /** Returns the file size of the path {@code gcsFile}. */
  public long getGcsFileSize(Path gcsFile) throws MobileHarnessException, InterruptedException {
    CloudFreshnessCache.ReadStamp readStamp = freshnessCache.beginRead();
    try {
      return gcsUtil.getGcsFileSize(gcsFile);
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == BasicErrorId.GCS_NO_METADATA) {
        freshnessCache.invalidate(gcsFile, readStamp);
      }
      throw e;
    }
  }

  /** Returns whether the path {@code gcsFile} is compressed. */
  public boolean isCompressed(Path gcsFile) throws MobileHarnessException, InterruptedException {
    return gcsUtil.isCompressed(gcsFile);
  }

  /** See {@link GcsUtil#renewIfOlderThan}. */
  public GcsUtil.RenewResult renewIfOlderThan(Path gcsFile, Duration threshold)
      throws MobileHarnessException, InterruptedException {
    CloudFreshnessCache.ReadStamp readStamp = freshnessCache.beginRead();
    GcsUtil.RenewResult result = gcsUtil.renewIfOlderThan(gcsFile, threshold);
    switch (result) {
      case RENEWED -> freshnessCache.recordFresh(gcsFile);
      // A non-zero generation precondition also fails if the object was deleted meanwhile, so
      // LOST_RACE cannot assume the object is fresh.
      case LOST_RACE, MISSING -> freshnessCache.invalidate(gcsFile, readStamp);
      case FRESH -> {}
    }
    return result;
  }

  private boolean isFreshUnderTransferLock(
      Path gcsFile, TransferLock lock, FreshnessCheck freshnessCheck)
      throws MobileHarnessException, InterruptedException {
    if (freshnessCache.isFresh(gcsFile)) {
      return true;
    }
    if (freshnessCheck == FreshnessCheck.RECHECK_IF_CONTENDED
        && freshnessCache.isEnabled()
        && !lock.waited()) {
      return false;
    }
    return checkFileExistAndFreshInGcs(gcsFile);
  }

  private boolean checkFileExistAndFreshInGcs(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    if (cloudCacheTtl.isEmpty()) {
      return gcsUtil.fileExist(gcsFile);
    }
    CloudFreshnessCache.ReadStamp readStamp = freshnessCache.beginRead();
    Optional<Duration> age = gcsUtil.getAge(gcsFile);
    if (age.isPresent() && age.get().compareTo(cloudCacheTtl.get()) < 0) {
      Duration effectiveAge = max(age.get(), Duration.ZERO);
      freshnessCache.recordFresh(gcsFile, cloudCacheTtl.get().minus(effectiveAge), readStamp);
      return true;
    }
    freshnessCache.invalidate(gcsFile, readStamp);
    return false;
  }
}
