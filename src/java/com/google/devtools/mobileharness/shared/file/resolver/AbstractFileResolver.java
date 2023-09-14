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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
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

  /** Resolve multiple files syncly. */
  @Override
  public final List<Optional<ResolveResult>> resolve(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException {
    try {
      return resolveAsync(resolveSources).get(RESOLVE_TIMEOUT_IN_HOUR, HOURS);
    } catch (TimeoutException e) {
      throw new MobileHarnessException(
          BasicErrorId.RESOLVE_FILE_TIMEOUT, "Timeout while resolving files", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      } else {
        throw new MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve file", e);
      }
    }
  }

  /**
   * Resolves one file syncly.
   *
   * @param resolveSource the resolve source, including file and parameters.
   * @return the resolved files and properties
   */
  @Override
  public Optional<ResolveResult> resolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException {
    if (shouldActuallyResolve(resolveSource)) {
      ResolveResult result = actuallyResolve(resolveSource);
      return Optional.of(
          ResolveResult.create(
              ImmutableList.sortedCopyOf(String::compareToIgnoreCase, result.paths()),
              result.properties(),
              resolveSource));
    }
    if (successor != null) {
      return successor.resolve(resolveSource);
    }
    return Optional.empty();
  }

  /** Resolves one file asyncly. */
  @Override
  public final ListenableFuture<Optional<ResolveResult>> resolveAsync(ResolveSource resolveSource) {
    return executorService.submit(() -> resolve(resolveSource));
  }

  /** Resolves multiple files asyncly. */
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

          for (ResolveSource source : resolveSources) {
            fileResolveFutures.add(resolveAsync(source));
          }
          return Futures.allAsList(fileResolveFutures);
        },
        executorService);
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
          .collect(Collectors.toSet());
    } else {
      return resolveResults;
    }
  }

  /**
   * Sets the next resolver. The method is package visible so only the builder can set the successor
   * when building the resolver chain. Users can not change the successor when using the chain
   *
   * @param successor the next resolver
   */
  @CanIgnoreReturnValue
  final AbstractFileResolver setSuccessor(AbstractFileResolver successor) {
    this.successor = successor;
    return this;
  }
}
