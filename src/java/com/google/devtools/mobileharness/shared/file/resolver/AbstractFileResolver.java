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

package com.google.devtools.mobileharness.shared.file.resolver;

import static com.google.common.collect.Comparators.max;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The base class for all file resolver classes. It implements a Chain of Responsibility pattern.
 * The class and all its sub class implementations should be thread safe.
 *
 * <p>All the subclasses should only be created by {@link FileResolverBuilder}.
 */
@ThreadSafe
public abstract class AbstractFileResolver implements FileResolver {
  protected static final long RESOLVE_TIMEOUT_IN_HOUR = 5;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ListeningExecutorService executorService;

  /** The next resolver. */
  private AbstractFileResolver successor;

  protected AbstractFileResolver(@Nullable ListeningExecutorService executorService) {
    this.executorService = executorService == null ? newDirectExecutorService() : executorService;
  }

  /** Whether the file can be resolved by this resolver. */
  protected abstract boolean shouldActuallyResolve(ResolveSource resolveSource);

  /** Do the real resolve work. */
  protected abstract ResolveResult actuallyResolve(ResolveSource resolveSource)
      throws InterruptedException, MobileHarnessException;

  /** Do the real pre process work. */
  protected Set<ResolveResult> actuallyPreBatchProcess(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    return ImmutableSet.of();
  }

  /**
   * Resolves multiple files synchronously.
   *
   * <p>If any file fails to resolve, unfinished resolutions in the same batch are cancelled with
   * interruption and any remaining unsubmitted files are skipped.
   */
  @Override
  @SuppressWarnings("Interruption")
  public final List<Optional<ResolveResult>> resolve(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    ListenableFuture<List<Optional<ResolveResult>>> future = resolveAsync(resolveSources);
    try {
      return future.get(getResolveTimeout().toMillis(), MILLISECONDS);
    } catch (TimeoutException e) {
      // Cancels the background tasks to prevent resource leakage.
      future.cancel(/* mayInterruptIfRunning= */ true);
      throw new MobileHarnessException(
          BasicErrorId.RESOLVE_FILE_TIMEOUT,
          String.format("Timeout while resolving file %s", resolveSources),
          e);
    } catch (InterruptedException e) {
      // Cancels the background tasks to prevent resource leakage.
      future.cancel(/* mayInterruptIfRunning= */ true);
      throw e;
    } catch (CancellationException e) {
      throw new MobileHarnessException(
          BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Resolution was cancelled", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException mhe) {
        throw mhe;
      } else {
        throw new MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve file", e);
      }
    }
  }

  /**
   * Resolves one file synchronously.
   *
   * @param resolveSource the resolve source, including file and parameters.
   * @return the resolved files and properties
   */
  @Override
  public Optional<ResolveResult> resolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (shouldActuallyResolve(resolveSource)) {
      logger.atInfo().log(
          "[%s] Resolving file: %s", getClass().getSimpleName(), resolveSource.path());
      Stopwatch stopwatch = Stopwatch.createStarted();
      ResolveResult result = actuallyResolve(resolveSource);
      logger.atInfo().log(
          "[%s] Successfully resolved file: %s in %s",
          getClass().getSimpleName(), resolveSource.path(), stopwatch.elapsed());
      return Optional.of(
          ResolveResult.of(
              ImmutableList.sortedCopyOf(
                  (resolvedFile1, resolvedFile2) ->
                      resolvedFile1.path().compareToIgnoreCase(resolvedFile2.path()),
                  result.resolvedFiles()),
              result.properties(),
              resolveSource));
    }
    if (successor != null) {
      return successor.resolve(resolveSource);
    }
    return Optional.empty();
  }

  /** Resolves one file asynchronously. */
  @Override
  public final ListenableFuture<Optional<ResolveResult>> resolveAsync(ResolveSource resolveSource) {
    return executorService.submit(() -> resolve(resolveSource));
  }

  /**
   * Resolves multiple files asynchronously.
   *
   * <p>If any file fails to resolve, unfinished resolutions in the same batch are cancelled with
   * interruption and any remaining unsubmitted files are skipped.
   */
  @Override
  public final ListenableFuture<List<Optional<ResolveResult>>> resolveAsync(
      List<ResolveSource> resolveSources) {
    ListenableFuture<Set<ResolveResult>> preBatchProcessFuture =
        executorService.submit(() -> preBatchProcess(resolveSources));

    return Futures.transformAsync(
        preBatchProcessFuture,
        preBachProcessResult -> {
          List<ListenableFuture<Optional<ResolveResult>>> fileResolveFutures = new ArrayList<>();

          logger.atInfo().log(
              "Start to resolve files: %s",
              resolveSources.stream().map(ResolveSource::path).collect(joining(", ")));

          AtomicBoolean batchFailed = new AtomicBoolean();
          try {
            for (ResolveSource source : resolveSources) {
              if (batchFailed.get()) {
                break;
              }
              ListenableFuture<Optional<ResolveResult>> future = resolveAsync(source);
              fileResolveFutures.add(future);
              future.addListener(
                  () -> {
                    if (isFailedOrCancelled(future)) {
                      batchFailed.set(true);
                    }
                  },
                  directExecutor());
            }
          } catch (RuntimeException | Error e) {
            cancelUnfinished(resolveSources, fileResolveFutures);
            throw e;
          }
          ListenableFuture<List<Optional<ResolveResult>>> allFilesFuture =
              Futures.allAsList(fileResolveFutures);
          // Futures.allAsList fails fast without cancelling the other input futures.
          allFilesFuture.addListener(
              () -> cancelUnfinished(resolveSources, fileResolveFutures), directExecutor());
          return allFilesFuture;
        },
        executorService);
  }

  private static boolean isFailedOrCancelled(Future<?> future) {
    if (!future.isDone()) {
      return false;
    }
    try {
      Futures.getDone(future);
      return false;
    } catch (ExecutionException | CancellationException e) {
      return true;
    }
  }

  @SuppressWarnings("Interruption") // A resolver stops resolving only when it is interrupted.
  private static void cancelUnfinished(
      List<ResolveSource> resolveSources, List<? extends Future<?>> futures) {
    List<String> cancelledPaths = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
      Future<?> future = futures.get(i);
      if (!future.isDone() && future.cancel(/* mayInterruptIfRunning= */ true)) {
        cancelledPaths.add(resolveSources.get(i).path());
      }
    }
    if (!cancelledPaths.isEmpty()) {
      logger.atInfo().log(
          "Cancelled %d unfinished file resolution(s) after batch resolution ended: %s",
          cancelledPaths.size(), String.join(", ", cancelledPaths));
    }
  }

  /**
   * Gets the checksum of the file to be resolved.
   *
   * @param resolveSource the resolve source, including file and parameters.
   * @return the checksum of the file to be resolved.
   */
  @Override
  public Optional<Checksum> getChecksum(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (shouldActuallyResolve(resolveSource)) {
      return computeChecksum(resolveSource);
    }
    if (successor != null) {
      return successor.getChecksum(resolveSource);
    }
    return Optional.empty();
  }

  protected Optional<Checksum> computeChecksum(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    return Optional.empty();
  }

  @Override
  public Optional<String> getVersion(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (shouldActuallyResolve(resolveSource)) {
      return computeVersion(resolveSource);
    }
    if (successor != null) {
      return successor.getVersion(resolveSource);
    }
    return Optional.empty();
  }

  /** Computes the version of the file. See {@link FileResolver#getVersion}. */
  protected Optional<String> computeVersion(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    return Optional.empty();
  }

  @Override
  public final Optional<MobileHarnessException> findUserError(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof MobileHarnessException error && isUserError(error)) {
        return Optional.of(error);
      }
    }
    if (successor != null) {
      return successor.findUserError(failure);
    }
    return Optional.empty();
  }

  /**
   * Returns whether {@code error}, thrown by this resolver, is the user's fault. See {@link
   * FileResolver#findUserError}.
   */
  protected boolean isUserError(MobileHarnessException error) {
    return false;
  }

  /**
   * Pre step for files resolve.
   *
   * @param resolveSources all the files that one mobile test needs
   */
  protected Set<ResolveResult> preBatchProcess(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    Set<ResolveResult> resolveResults =
        actuallyPreBatchProcess(
            resolveSources.stream().filter(this::shouldActuallyResolve).collect(toImmutableList()));
    if (successor != null) {
      return Stream.concat(
              resolveResults.stream(), successor.preBatchProcess(resolveSources).stream())
          .collect(toImmutableSet());
    } else {
      return resolveResults;
    }
  }

  /**
   * Gets the timeout for this specific resolver. Subclasses should override this to provide a
   * custom timeout.
   */
  protected Duration getSelfResolveTimeout() {
    return Duration.ofHours(RESOLVE_TIMEOUT_IN_HOUR);
  }

  /** Gets the timeout for the resolution. It takes the maximum timeout across the chain. */
  protected final Duration getResolveTimeout() {
    Duration selfTimeout = getSelfResolveTimeout();
    if (successor != null) {
      return max(selfTimeout, successor.getResolveTimeout());
    }
    return selfTimeout;
  }

  /**
   * Sets the next resolver. The method is package visible so only the builder can set the successor
   * when building the resolver chain. Users can not change the successor when using the chain
   *
   * @param successor the next resolver
   */
  @CanIgnoreReturnValue
  public final AbstractFileResolver setSuccessor(AbstractFileResolver successor) {
    this.successor = successor;
    return this;
  }
}
