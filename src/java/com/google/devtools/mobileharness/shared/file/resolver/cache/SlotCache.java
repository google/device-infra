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

package com.google.devtools.mobileharness.shared.file.resolver.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolvedFile;
import com.google.devtools.mobileharness.shared.file.resolver.cache.ResolvedFileCache.CacheLoader;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessRunnable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * A cache of resolved files that keeps the files of each entry in a directory of its own, a slot,
 * under a root only this cache writes to.
 *
 * <p>{@link LocalCache} leaves the files where the first caller asked for them, and that caller
 * removes them whenever it is done. Here every caller, the first one included, links or copies the
 * files out of the slot while it {@linkplain Entry#acquire holds} the entry, so no caller's
 * directory backs what another caller reads. Only this cache removes a slot, once its entry is
 * retired and nothing loads into it or holds it.
 *
 * <p>An entry is retired when it was not asked for within the idle TTL, when it is older than the
 * maximum age, or when its load failed longer ago than the failure TTL. A result with no file in
 * its slot, such as a path the lab resolves itself, goes to the callers waiting for it and is not
 * kept.
 */
@ThreadSafe
public final class SlotCache {

  /**
   * Where this cache keeps its slots, and for how long.
   *
   * @param rootDir the directory holding the slots, in which this cache removes whatever it does
   *     not know
   * @param idleTtl how long an entry is kept after it was last asked for
   */
  @Immutable
  public record Options(String rootDir, Duration idleTtl) {
    public Options {
      checkArgument(!rootDir.isBlank(), "rootDir must not be blank");
      // deviceinfra OSS targets Java 17, which lacks Duration.isPositive().
      checkArgument(idleTtl.compareTo(Duration.ZERO) > 0, "idleTtl must be positive: %s", idleTtl);
      Path normalizedRoot = Path.of(rootDir).toAbsolutePath().normalize();
      checkArgument(
          normalizedRoot.getParent() != null, "rootDir must not be a filesystem root: %s", rootDir);
      rootDir = normalizedRoot.toString();
    }
  }

  /**
   * What {@link #get} found.
   *
   * @param entry the entry of the resolve source
   * @param isNew whether this call started loading the entry, which other callers wait for
   */
  public record Lookup(Entry entry, boolean isNew) {}

  @Immutable
  private record Key(String path, ImmutableMap<String, String> parameters) {}

  private enum LoadState {
    PENDING,
    RUNNING,
    DONE,
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The delay between the end of one sweep and the start of the next. */
  private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

  private final Options options;
  private final Duration maxAge;
  private final Duration failureTtl;
  private final Executor executor;
  private final InstantSource instantSource;

  private final LocalFileUtil localFileUtil;

  private final CacheLoader cacheLoader;

  private final ConcurrentMap<Key, Entry> entries = new ConcurrentHashMap<>();

  /**
   * The directory names of the slots that are in use or being removed. Any other directory under
   * the root is left over.
   */
  private final ConcurrentMap<String, Boolean> slotNames = new ConcurrentHashMap<>();

  private final AtomicBoolean sweeping = new AtomicBoolean();

  /**
   * @param maxAge how long a loaded entry is kept at most, however often it is asked for
   * @param failureTtl how long a failed load is kept, so that callers get its failure instead of
   *     loading again
   * @param executor runs the loads and removes the slots
   * @param sweepScheduler runs the startup and periodic sweeps of expired entries and leftovers
   */
  public SlotCache(
      Options options,
      Duration maxAge,
      Duration failureTtl,
      Executor executor,
      ScheduledExecutorService sweepScheduler,
      InstantSource instantSource,
      LocalFileUtil localFileUtil,
      CacheLoader cacheLoader) {
    this(
        options,
        maxAge,
        failureTtl,
        executor,
        checkNotNull(sweepScheduler),
        SWEEP_INTERVAL,
        instantSource,
        localFileUtil,
        cacheLoader);
  }

  @VisibleForTesting
  SlotCache(
      Options options,
      Duration maxAge,
      Duration failureTtl,
      Executor executor,
      @Nullable ScheduledExecutorService sweepScheduler,
      Duration sweepInterval,
      InstantSource instantSource,
      LocalFileUtil localFileUtil,
      CacheLoader cacheLoader) {
    checkArgument(!maxAge.isNegative(), "maxAge must not be negative: %s", maxAge);
    checkArgument(!failureTtl.isNegative(), "failureTtl must not be negative: %s", failureTtl);
    // deviceinfra OSS targets Java 17, which lacks Duration.isPositive().
    checkArgument(
        sweepInterval.compareTo(Duration.ZERO) > 0,
        "sweepInterval must be positive: %s",
        sweepInterval);
    this.options = options;
    this.maxAge = maxAge;
    this.failureTtl = failureTtl;
    this.executor = executor;
    this.instantSource = instantSource;
    this.localFileUtil = localFileUtil;
    this.cacheLoader = cacheLoader;
    if (sweepScheduler != null) {
      startSweepScheduler(sweepScheduler, sweepInterval);
    }
  }

  /**
   * Returns the entry of {@code resolveSource}, which this call starts loading into a new slot if
   * there is none or the one there is has expired.
   */
  public Lookup get(ResolveSource resolveSource) {
    Key key = new Key(resolveSource.path(), resolveSource.parameters());
    while (true) {
      Instant now = instantSource.instant();
      Entry existing = entries.get(key);
      if (existing != null && !isExpired(existing, now)) {
        existing.touch(now);
        return new Lookup(existing, /* isNew= */ false);
      }
      Entry created = new Entry(key, now);
      // Register the slot name before publishing the entry so a concurrent retire() or sweep()
      // never observes the entry before its slot is tracked.
      slotNames.put(created.slotName, Boolean.TRUE);
      boolean added =
          existing == null
              ? entries.putIfAbsent(key, created) == null
              : entries.replace(key, existing, created);
      if (!added) {
        slotNames.remove(created.slotName);
        continue;
      }
      try {
        created.load(resolveSource);
      } finally {
        if (existing != null) {
          existing.retire();
        }
      }
      return new Lookup(created, /* isNew= */ true);
    }
  }

  /**
   * Drops {@code entry} from this cache, unless a newer entry took its place already, and removes
   * its slot once nothing holds it.
   */
  public void retire(Entry entry) {
    entries.remove(entry.key, entry);
    entry.retire();
  }

  private boolean isExpired(Entry entry, Instant now) {
    if (!entry.future.isDone()) {
      return false;
    }
    if (entry.future.isCancelled()) {
      return true;
    }
    Instant finished = entry.finishedAt.get();
    if (finished == null) {
      return true;
    }
    try {
      Futures.getDone(entry.future);
    } catch (ExecutionException e) {
      return now.isAfter(finished.plus(failureTtl));
    }
    // A load that took longer than the idle TTL would otherwise expire before its caller got the
    // files out.
    Instant lastUsed = entry.lastUsed.get();
    Instant activeAt = lastUsed.isAfter(finished) ? lastUsed : finished;
    return now.isAfter(finished.plus(maxAge)) || now.isAfter(activeAt.plus(options.idleTtl()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void startSweepScheduler(
      ScheduledExecutorService sweepScheduler, Duration sweepInterval) {
    try {
      sweepScheduler.scheduleWithFixedDelay(
          this::runScheduledSweep, /* initialDelay= */ 0L, sweepInterval.toNanos(), NANOSECONDS);
    } catch (RejectedExecutionException e) {
      logger.atWarning().withCause(e).log("Failed to schedule cache sweep.");
    }
  }

  private void runScheduledSweep() {
    try {
      sweep();
    } catch (Throwable t) {
      logger.atWarning().withCause(t).log("Failed to sweep cache.");
    }
  }

  /**
   * Retires the entries that expired without being asked for again, and removes the directories
   * under the root that belong to no slot: those an earlier process left, and those a cancelled
   * load wrote to after its slot was removed.
   */
  @VisibleForTesting
  void sweep() {
    if (!sweeping.compareAndSet(false, true)) {
      return;
    }
    try {
      Instant now = instantSource.instant();
      for (Entry entry : entries.values()) {
        if (isExpired(entry, now)) {
          retire(entry);
        }
      }
      if (!localFileUtil.isDirExist(options.rootDir())) {
        return;
      }
      File[] leftovers;
      try {
        leftovers =
            localFileUtil.listFilesOrDirs(
                options.rootDir(), fileOrDir -> !slotNames.containsKey(fileOrDir.getName()));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to list %s.", options.rootDir());
        return;
      }
      for (File leftover : leftovers) {
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        remove(leftover.getName());
      }
    } finally {
      sweeping.set(false);
    }
  }

  /** Removes the directory {@code name} under the root, which the next sweep retries on failure. */
  private void remove(String name) {
    String dir = PathUtil.join(options.rootDir(), name);
    try {
      // The thread of a cancelled load is interrupted, and a removal fails at once on such a
      // thread.
      Callables.runAll((MobileHarnessRunnable) () -> removeDir(dir));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove %s.", dir);
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted removing %s.", dir);
      Thread.currentThread().interrupt();
    } finally {
      slotNames.remove(name);
    }
  }

  private void removeDir(String dir) throws MobileHarnessException, InterruptedException {
    if (!existsOnDisk(dir)) {
      return;
    }
    try {
      localFileUtil.removeFileOrDir(dir);
    } catch (MobileHarnessException e) {
      // A resolver can leave read-only directories that block unlinking; add owner rwx to
      // directories only so shared inodes hard-linked into active jobs are untouched.
      makeDirectoriesOwnerWritable(dir);
      localFileUtil.removeFileOrDir(dir);
    }
  }

  private static void makeDirectoriesOwnerWritable(String dir) {
    Path root;
    try {
      root = Path.of(dir);
    } catch (InvalidPathException e) {
      logger.atWarning().withCause(e).log(
          "Failed to make directories under %s owner-writable.", dir);
      return;
    }
    Deque<Path> queue = new ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Path current = queue.poll();
      if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      // Restore owner rwx before opening the directory so unreadable/untraversable subdirectories
      // can still be listed and walked.
      addOwnerRwx(current);
      try (DirectoryStream<Path> children = Files.newDirectoryStream(current)) {
        for (Path child : children) {
          if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            queue.add(child);
          }
        }
      } catch (IOException | DirectoryIteratorException e) {
        logger.atWarning().withCause(e).log("Failed to list directory %s.", current);
      }
    }
  }

  private static void addOwnerRwx(Path dir) {
    try {
      if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
      perms.addAll(Files.getPosixFilePermissions(dir, LinkOption.NOFOLLOW_LINKS));
      boolean changed = perms.add(PosixFilePermission.OWNER_READ);
      changed |= perms.add(PosixFilePermission.OWNER_WRITE);
      changed |= perms.add(PosixFilePermission.OWNER_EXECUTE);
      if (changed) {
        Files.setPosixFilePermissions(dir, perms);
      }
    } catch (IOException | UnsupportedOperationException e) {
      logger.atWarning().withCause(e).log("Failed to update permissions of %s.", dir);
    }
  }

  private static boolean existsOnDisk(String path) {
    try {
      return Files.exists(Path.of(path), LinkOption.NOFOLLOW_LINKS);
    } catch (InvalidPathException e) {
      return true;
    }
  }

  /** A resolved source and the slot holding its files. */
  @ThreadSafe
  public final class Entry {
    private final Key key;
    private final String slotName = "e_" + UUID.randomUUID();
    private final SettableFuture<Optional<ResolveResult>> future = SettableFuture.create();

    private final AtomicReference<Instant> lastUsed;
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();

    @GuardedBy("this")
    private LoadState loadState = LoadState.PENDING;

    /** Whether the slot is retired and waiting only for loading and active holders to finish. */
    @GuardedBy("this")
    private boolean retired;

    @GuardedBy("this")
    private int holders;

    @GuardedBy("this")
    private boolean removing;

    private Entry(Key key, Instant now) {
      this.key = key;
      this.lastUsed = new AtomicReference<>(now);
    }

    /**
     * Returns the result of resolving into the slot. Its files are in the slot, unless the resolver
     * fetched nothing and names them where they are.
     */
    public ListenableFuture<Optional<ResolveResult>> future() {
      return future;
    }

    /**
     * Returns where the file a resolver put at {@code slotPath} belongs for {@code resolveSource},
     * or empty if the path is not strictly inside the slot's run or tmp directory.
     */
    public Optional<String> pathFor(ResolveSource resolveSource, String slotPath) {
      return relocate(slotPath, runDir(), resolveSource.targetDir())
          .or(() -> relocate(slotPath, tmpDir(), resolveSource.tmpDir()));
    }

    /**
     * Keeps the slot from being removed until {@link #release}.
     *
     * @return false if the entry is retired, in which case its files may be gone
     */
    public synchronized boolean acquire() {
      if (retired) {
        return false;
      }
      holders++;
      return true;
    }

    /** Ends one {@link #acquire}. */
    public void release() {
      synchronized (this) {
        checkState(holders > 0, "release() called without a matching acquire()");
        holders--;
      }
      removeSlotIfUnused();
    }

    private void touch(Instant now) {
      lastUsed.accumulateAndGet(now, (prev, cur) -> cur.isAfter(prev) ? cur : prev);
    }

    private String slotDir() {
      return PathUtil.join(options.rootDir(), slotName);
    }

    private String runDir() {
      return PathUtil.join(slotDir(), "run");
    }

    private String tmpDir() {
      return PathUtil.join(slotDir(), "tmp");
    }

    private void load(ResolveSource resolveSource) {
      boolean skipLoad;
      synchronized (this) {
        if (retired || future.isCancelled()) {
          loadState = LoadState.DONE;
          retired = true;
          skipLoad = true;
        } else {
          skipLoad = false;
        }
      }
      if (skipLoad) {
        entries.remove(key, this);
        future.cancel(/* mayInterruptIfRunning= */ false);
        removeSlotIfUnused();
        return;
      }
      logger.atInfo().log("%s has not been resolved before. Resolving it into %s.", key, slotDir());
      ResolveSource slotSource =
          ResolveSource.create(
              resolveSource.path(),
              resolveSource.tag(),
              resolveSource.parameters(),
              runDir(),
              tmpDir());
      ListenableFuture<Optional<ResolveResult>> task;
      try {
        task = Futures.submit(() -> loadIntoSlot(slotSource), executor);
      } catch (Throwable t) {
        entries.remove(key, this);
        future.setException(t);
        synchronized (this) {
          loadState = LoadState.DONE;
          retired = true;
          removing = true;
        }
        slotNames.remove(slotName);
        throw t;
      }
      future.setFuture(task);
      task.addListener(this::onLoadTaskDone, directExecutor());
    }

    private void onLoadTaskDone() {
      boolean cancelledBeforeStart = false;
      synchronized (this) {
        if (loadState == LoadState.PENDING) {
          // Task was cancelled while queued before loadIntoSlot began running.
          loadState = LoadState.DONE;
          retired = true;
          cancelledBeforeStart = true;
        }
      }
      if (cancelledBeforeStart) {
        entries.remove(key, this);
        removeSlotIfUnused();
      }
    }

    private Optional<ResolveResult> loadIntoSlot(ResolveSource slotSource)
        throws MobileHarnessException, InterruptedException {
      boolean startLoad;
      synchronized (this) {
        if (retired || future.isCancelled()) {
          loadState = LoadState.DONE;
          retired = true;
          startLoad = false;
        } else {
          loadState = LoadState.RUNNING;
          startLoad = true;
        }
      }
      if (!startLoad) {
        entries.remove(key, this);
        future.cancel(/* mayInterruptIfRunning= */ false);
        removeSlotIfUnused();
        return Optional.empty();
      }
      boolean kept = false;
      try {
        Optional<ResolveResult> result = cacheLoader.load(slotSource);
        if (result.isPresent()) {
          validateNoUnrelocatableSlotPaths(result.get());
        }
        if (result.isEmpty() || !isResultInSlot(result.get())) {
          entries.remove(key, this);
          return result;
        }
        if (!hasFileUnder(result.get(), tmpDir())) {
          removeTmpDir();
        }
        synchronized (this) {
          if (!retired && !future.isCancelled()) {
            kept = true;
          }
        }
        if (!kept) {
          entries.remove(key, this);
        }
        return result;
      } finally {
        finishedAt.set(instantSource.instant());
        synchronized (this) {
          loadState = LoadState.DONE;
          // A failed load stays in the cache as a failure, without its slot.
          retired |= !kept;
        }
        removeSlotIfUnused();
      }
    }

    private void validateNoUnrelocatableSlotPaths(ResolveResult result)
        throws MobileHarnessException {
      String normalizedSlot = normalizePath(slotDir());
      String normalizedRun = normalizePath(runDir());
      String normalizedTmp = normalizePath(tmpDir());
      for (ResolvedFile file : result.resolvedFiles()) {
        String normalized = normalizePath(file.path());
        if (isEqualOrUnder(normalized, normalizedSlot)
            && !isStrictlyUnder(normalized, normalizedRun)
            && !isStrictlyUnder(normalized, normalizedTmp)) {
          entries.remove(key, this);
          throw new MobileHarnessException(
              BasicErrorId.RESOLVE_FILE_GENERIC_ERROR,
              String.format(
                  "Resolver returned path %s under cache slot %s outside run/tmp subdirectories.",
                  file.path(), slotDir()));
        }
      }
    }

    private boolean isResultInSlot(ResolveResult result) {
      return hasFileUnder(result, runDir()) || hasFileUnder(result, tmpDir());
    }

    /** Removes what the resolver needed only while it ran. */
    private void removeTmpDir() throws InterruptedException {
      try {
        removeDir(tmpDir());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to remove %s.", tmpDir());
      }
    }

    private void retire() {
      synchronized (this) {
        retired = true;
      }
      removeSlotIfUnused();
    }

    private void removeSlotIfUnused() {
      synchronized (this) {
        // A load that is cancelled returns to its caller before its thread stops writing.
        if (!retired || loadState != LoadState.DONE || holders > 0 || removing) {
          return;
        }
        removing = true;
      }
      try {
        executor.execute(() -> remove(slotName));
      } catch (RejectedExecutionException e) {
        synchronized (this) {
          removing = false;
        }
        slotNames.remove(slotName);
        logger.atWarning().withCause(e).log("Failed to schedule removal of %s.", slotDir());
      } catch (Throwable t) {
        synchronized (this) {
          removing = false;
        }
        slotNames.remove(slotName);
        throw t;
      }
    }
  }

  private static boolean hasFileUnder(ResolveResult result, String dir) {
    String normalizedDir = normalizePath(dir);
    return result.resolvedFiles().stream()
        .anyMatch(file -> isStrictlyUnder(normalizePath(file.path()), normalizedDir));
  }

  private static Optional<String> relocate(String path, String fromDir, String toDir) {
    String normalizedPath = normalizePath(path);
    String normalizedFrom = normalizePath(fromDir);
    if (!isStrictlyUnder(normalizedPath, normalizedFrom)) {
      return Optional.empty();
    }
    String relative =
        normalizedFrom.equals("/")
            ? normalizedPath.substring(1)
            : normalizedPath.substring(normalizedFrom.length() + 1);
    return Optional.of(PathUtil.join(normalizePath(toDir), relative));
  }

  private static boolean isEqualOrUnder(String normalizedPath, String normalizedDir) {
    return normalizedPath.equals(normalizedDir) || isStrictlyUnder(normalizedPath, normalizedDir);
  }

  private static boolean isStrictlyUnder(String normalizedPath, String normalizedDir) {
    if (normalizedDir.equals("/")) {
      return normalizedPath.startsWith("/") && normalizedPath.length() > 1;
    }
    return normalizedPath.startsWith(normalizedDir + "/");
  }

  private static String normalizePath(String path) {
    try {
      return Path.of(path).normalize().toString();
    } catch (InvalidPathException e) {
      return PathUtil.removeExtraneousSlashes(path);
    }
  }
}
