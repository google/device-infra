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

package com.google.devtools.mobileharness.shared.util.cache.persistent;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.ChecksumAlgorithm;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.Metadata;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.StorageApi;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ImmutableTypeParameter;
import com.google.errorprone.annotations.ThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache files in a persistent file system.
 *
 * <p>The persistent file system might be distributed to multiple machines and processes.
 * Synchronization across machines and processes is achieved via file locks, ensuring that the
 * expensive {@code load} operation is only performed once across all machines and processes for a
 * given key. For each cache entry, a corresponding write lock is used to avoid the
 * OverlappingFileLockException across threads on the same JVM.
 *
 * <p>This cache is ideal for large, expensive-to-load files that are needed over a long period
 * (e.g., weeks). Key characteristics include:
 *
 * <ul>
 *   <li>Heavy file lock operations for cross-machine/process synchronization.
 *   <li>Data retention is managed by the persistent file system's TTL policy.
 *   <li>Returned paths are symbolic links, providing read-only access to the cached data.
 * </ul>
 */
@ThreadSafe
public class PersistentCache<@ImmutableTypeParameter K> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final ConcurrentMap<Path, ReadWriteLock> lockMap = new ConcurrentHashMap<>();

  private final Path rootPersistentDir;

  private final LocalFileUtil localFileUtil;

  private final InstantSource instantSource;

  private final CacheLoader<K> cacheLoader;

  PersistentCache(
      Path rootPersistentDir,
      LocalFileUtil localFileUtil,
      InstantSource instantSource,
      CacheLoader<K> cacheLoader) {
    this.rootPersistentDir = rootPersistentDir;
    this.localFileUtil = localFileUtil;
    this.instantSource = instantSource;
    this.cacheLoader = cacheLoader;
  }

  static enum CacheState {
    NOT_PRESENT,
    VALID,
    INVALID
  }

  @AutoValue
  abstract static class CacheStatus {
    abstract CacheState cacheState();

    abstract Optional<Metadata> metadata();

    static CacheStatus create(CacheState cacheState, Optional<Metadata> metadata) {
      return new AutoValue_PersistentCache_CacheStatus(cacheState, metadata);
    }
  }

  @VisibleForTesting
  static class CachePaths {

    private final Path cacheDirPath;

    private CachePaths(Path cacheDirPath) {
      this.cacheDirPath = cacheDirPath;
    }

    Path cacheDirPath() {
      return cacheDirPath;
    }

    Path dataPath() {
      return cacheDirPath().resolve(".data");
    }

    Path lockPath() {
      return cacheDirPath().resolve(".lock");
    }

    Path metadataPath() {
      return cacheDirPath().resolve(".metadata");
    }
  }

  /**
   * Gets the cache for the given {@code cacheKey}.
   *
   * <p>The return value is a soft link to the actual cache data. Users can read the cache data via
   * the soft link but can't update the actual cache data.
   *
   * @param cacheKey the cache key
   * @param targetPath the target symlink path
   * @param isTargetDir whether the target path is a directory. If true, the target path is a
   *     directory and the symlink will be created under the directory with the same file name as
   *     the first symlink in the metadata file.
   * @return the path to the soft link to the cache data, or empty if the cache is not present
   * @throws MobileHarnessException if the load operation fails or IO operations fail
   * @throws InterruptedException if the thread is interrupted
   */
  public Optional<Path> get(CacheKey<K> cacheKey, Path targetPath, boolean isTargetDir)
      throws MobileHarnessException, InterruptedException {
    CachePaths cachePaths = createCachePaths(cacheKey);

    try (FileChannel channel =
        FileChannel.open(
            cachePaths.lockPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE)) {
      ReadWriteLock rwLock =
          lockMap.computeIfAbsent(cachePaths.cacheDirPath(), path -> new ReentrantReadWriteLock());
      // Use a write lock to avoid the OverlappingFileLockException across threads on the same JVM.
      Lock exclusiveThreadLock = rwLock.writeLock();
      if (exclusiveThreadLock.tryLock()) {
        try (FileLock sharedLock = channel.tryLock(0L, Long.MAX_VALUE, /* shared= */ true)) {
          if (sharedLock != null) {
            logger.atInfo().log("Acquired shared lock.");
            CacheStatus cacheStatus = getCacheStatus(cachePaths);
            if (cacheStatus.cacheState().equals(CacheState.VALID)) {
              logger.atInfo().log("Cache %s is valid. Fetching cache.", cacheKey.getRelativePath());
              Metadata metadata = cacheStatus.metadata().get();
              Path symlink = createSymlink(cachePaths, metadata, targetPath, isTargetDir);
              if (!metadata.getSymlinksList().contains(symlink.toAbsolutePath().toString())) {
                addSymlink(cachePaths.metadataPath(), symlink);
              }
              return Optional.of(symlink);
            }
          }
        } finally {
          exclusiveThreadLock.unlock();
        }
      }

      // Use a write lock to avoid the OverlappingFileLockException across threads on the same JVM.
      exclusiveThreadLock.lock();
      // Use an exclusive lock to protect the cache state change.
      try (FileLock exclusiveLock = channel.lock()) {
        logger.atInfo().log("Acquired exclusive lock.");
        CacheStatus cacheStatus = getCacheStatus(cachePaths);
        Optional<Metadata> metadata = Optional.empty();
        boolean createMetadata = false;
        switch (cacheStatus.cacheState()) {
          case VALID:
            metadata = cacheStatus.metadata();
            break;
          case INVALID:
            logger.atInfo().log("Cache %s is invalid. Clearing cache.", cacheKey.getRelativePath());
            clearCache(cachePaths);
          // fall through to load cache
          case NOT_PRESENT:
            logger.atInfo().log(
                "Cache %s is not present. Loading cache.", cacheKey.getRelativePath());
            metadata = loadCache(cacheKey, cachePaths);
            createMetadata = true;
            break;
        }
        if (metadata.isPresent()) {
          Path newSymlink = createSymlink(cachePaths, metadata.get(), targetPath, isTargetDir);
          if (createMetadata) {
            serializeMetadata(
                cachePaths.metadataPath(), updateMetadata(metadata.get(), newSymlink));
          } else if (!metadata
              .get()
              .getSymlinksList()
              .contains(newSymlink.toAbsolutePath().toString())) {
            addSymlink(cachePaths.metadataPath(), newSymlink);
          }
          return Optional.of(newSymlink);
        }
        return Optional.empty();
      } finally {
        exclusiveThreadLock.unlock();
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.PERSISTENT_CACHE_LOCK_FILE_ERROR, "Failed to lock file.", e);
    }
  }

  private CachePaths createCachePaths(CacheKey<K> cacheKey) throws MobileHarnessException {
    Path cacheDirPath = rootPersistentDir.resolve(cacheKey.getRelativePath());
    localFileUtil.prepareDir(cacheDirPath);
    return new CachePaths(cacheDirPath);
  }

  private CacheStatus getCacheStatus(CachePaths cachePaths) {
    if (!isCachePresent(cachePaths)) {
      return CacheStatus.create(CacheState.NOT_PRESENT, Optional.empty());
    }
    Optional<Metadata> metadata = deserializeMetadata(cachePaths.metadataPath());
    if (metadata.isEmpty()) {
      return CacheStatus.create(CacheState.INVALID, Optional.empty());
    }
    if (isCacheValid(cachePaths.dataPath(), metadata.get())) {
      return CacheStatus.create(CacheState.VALID, metadata);
    }
    return CacheStatus.create(CacheState.INVALID, metadata);
  }

  private boolean isCachePresent(CachePaths cachePaths) {
    return localFileUtil.isFileExist(cachePaths.metadataPath());
  }

  private boolean isCacheValid(Path dataPath, Metadata metadata) {
    return isCacheValid(
        dataPath,
        metadata.getStorageApi(),
        metadata.getChecksumAlgorithm(),
        metadata.getChecksum());
  }

  private boolean isCacheValid(
      Path dataPath, StorageApi storageApi, ChecksumAlgorithm checksumAlgorithm, String checksum) {
    return ChecksumHelper.isChecksumValid(dataPath, storageApi, checksumAlgorithm, checksum);
  }

  private Path createSymlink(
      CachePaths cachePaths, Metadata metadata, Path targetPath, boolean isTargetDir)
      throws MobileHarnessException, InterruptedException {
    Path targetFilePath = getTargetFilePath(metadata, targetPath, isTargetDir);
    // It will force to create a new symlink regardless of the previous symlink.
    localFileUtil.linkFileOrDir(cachePaths.dataPath().toString(), targetFilePath.toString());
    return targetFilePath;
  }

  @VisibleForTesting
  static Optional<Metadata> deserializeMetadata(Path metadataPath) {
    try (FileChannel channel = FileChannel.open(metadataPath, StandardOpenOption.READ);
        FileLock metadataLock = channel.lock(0L, Long.MAX_VALUE, /* shared= */ true)) {
      byte[] metadataBytes = new byte[(int) channel.size()];
      channel.position(0);
      channel.read(ByteBuffer.wrap(metadataBytes));
      return Optional.of(
          Metadata.parseFrom(metadataBytes, ProtoExtensionRegistry.getGeneratedRegistry()));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to read cache metadata %s.", metadataPath);
      return Optional.empty();
    }
  }

  @VisibleForTesting
  static void serializeMetadata(Path metadataPath, Metadata metadata)
      throws MobileHarnessException {
    try (FileChannel channel =
        FileChannel.open(
            metadataPath,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ,
            StandardOpenOption.CREATE)) {
      ByteBuffer buffer = ByteBuffer.wrap(metadata.toByteArray());
      channel.write(buffer);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.PERSISTENT_CACHE_CREATE_METADATA_ERROR,
          "Failed to create metadata file.",
          e);
    }
  }

  @VisibleForTesting
  static void addSymlink(Path metadataPath, Path newSymlink) throws MobileHarnessException {
    try (FileChannel channel =
            FileChannel.open(metadataPath, StandardOpenOption.WRITE, StandardOpenOption.READ);
        FileLock metadataLock = channel.lock()) {
      logger.atInfo().log("Adding symlink %s to metadata %s.", newSymlink, metadataPath);
      int size = (int) channel.size();
      checkState(size > 0, "Metadata file is empty.");
      byte[] existedBytes = new byte[size];
      Metadata.Builder builder = Metadata.newBuilder();
      channel.position(0);
      channel.read(ByteBuffer.wrap(existedBytes));
      builder.mergeFrom(existedBytes, ProtoExtensionRegistry.getGeneratedRegistry());

      // Check if the symlink already exists.
      String newSymlinkStr = newSymlink.toAbsolutePath().toString();
      if (builder.getSymlinksList().contains(newSymlinkStr)) {
        return;
      }
      // Incrementally update the metadata file by appending the new symlink to the end of the
      // list.
      Metadata partialUpdate = Metadata.newBuilder().addSymlinks(newSymlinkStr).build();
      channel.position(size);
      channel.write(ByteBuffer.wrap(partialUpdate.toByteArray()));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.PERSISTENT_CACHE_APPEND_METADATA_ERROR,
          "Failed to append symlink to metadata.",
          e);
    }
  }

  private Metadata updateMetadata(Metadata metadata, Path targetFilePath) {
    String newSymlink = targetFilePath.toAbsolutePath().toString();
    return metadata.getSymlinksList().contains(newSymlink)
        ? metadata
        : metadata.toBuilder().addSymlinks(newSymlink).build();
  }

  private Path getTargetFilePath(Metadata metadata, Path targetPath, boolean isTargetDir) {
    checkArgument(
        metadata.getSymlinksCount() > 0,
        "Metadata must have at least one symlink when first loaded.");
    if (isTargetDir) {
      for (String symlink : metadata.getSymlinksList()) {
        if (Path.of(symlink).getParent().startsWith(targetPath)) {
          return Path.of(symlink);
        }
      }
      Path fileName = Path.of(metadata.getSymlinks(0)).getFileName();
      return targetPath.resolve(fileName);
    }
    return targetPath;
  }

  private void clearCache(CachePaths cachePaths)
      throws MobileHarnessException, InterruptedException {
    // Don't remove the lock file.
    localFileUtil.removeFileOrDir(cachePaths.metadataPath());
    localFileUtil.removeFileOrDir(cachePaths.dataPath());
  }

  private Optional<Metadata> loadCache(CacheKey<K> key, CachePaths cachePaths)
      throws MobileHarnessException, InterruptedException {
    Optional<Path> dataOp = cacheLoader.load(key.originalKey());
    if (dataOp.isPresent()
        && isCacheValid(dataOp.get(), key.storageApi(), key.checksumAlgorithm(), key.checksum())) {
      Path linkPath = createSymLinkToData(cachePaths, dataOp.get());
      return Optional.of(createMetadata(linkPath, key));
    }
    return Optional.empty();
  }

  @CanIgnoreReturnValue
  private Path createSymLinkToData(CachePaths cachePaths, Path srcFilePath)
      throws MobileHarnessException, InterruptedException {
    Path targetFilePath = cachePaths.dataPath();
    localFileUtil.moveFileOrDir(srcFilePath, targetFilePath);
    localFileUtil.linkFileOrDir(
        targetFilePath.toAbsolutePath().toString(), srcFilePath.toAbsolutePath().toString());
    return srcFilePath;
  }

  private Metadata createMetadata(Path linkPath, CacheKey<K> cacheKey) {
    return Metadata.newBuilder()
        .setStorageApi(cacheKey.storageApi())
        .setChecksumAlgorithm(cacheKey.checksumAlgorithm())
        .setChecksum(cacheKey.checksum())
        .setCreationTime(toProtoTimestamp(instantSource.instant()))
        .addSymlinks(linkPath.toAbsolutePath().toString())
        .build();
  }
}
