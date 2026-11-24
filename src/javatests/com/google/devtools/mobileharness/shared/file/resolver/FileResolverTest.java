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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ForwardingListeningExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolvedFile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class FileResolverTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  private FileResolverUnderTest resolver;

  @Test
  public void resolve() throws MobileHarnessException, InterruptedException {
    resolver = new FileResolverUnderTest(null);
    Optional<ResolveResult> result =
        resolver.resolve(ResolveSource.create("/a/b/c", ImmutableMap.of(), "/", "/"));

    assertThat(result.get().paths()).containsExactly("/a/b/c");
  }

  @Test
  public void resolve_checkResolvedFiles() throws MobileHarnessException, InterruptedException {
    resolver = new FileResolverUnderTest(null);
    Optional<ResolveResult> result =
        resolver.resolve(ResolveSource.create("/a/b/c", ImmutableMap.of(), "/", "/"));

    assertThat(result.get().resolvedFiles())
        .containsExactly(ResolvedFile.create("/a/b/c", null, "/a/b/c"));
  }

  @Test
  public void batchResolve_inSerial() throws MobileHarnessException, InterruptedException {
    resolver = new FileResolverUnderTest(null);
    List<Optional<ResolveResult>> resolvedResults =
        resolver.resolve(
            ImmutableList.of(
                ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"),
                ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/")));
    assertThat(resolvedResults)
        .containsExactly(
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/b", null, "/a/b")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"))),
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/c", null, "/a/c")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/"))));
  }

  @Test
  public void batchResolve_parallelWithDirectExecutorService()
      throws MobileHarnessException, InterruptedException {
    resolver = new FileResolverUnderTest(MoreExecutors.newDirectExecutorService());
    List<Optional<ResolveResult>> resolvedResults =
        resolver.resolve(
            ImmutableList.of(
                ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"),
                ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/")));
    assertThat(resolvedResults)
        .containsExactly(
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/b", null, "/a/b")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"))),
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/c", null, "/a/c")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/"))));
  }

  @Test
  public void batchResolve_parallelWithRealThreads()
      throws MobileHarnessException, InterruptedException {
    resolver =
        new FileResolverUnderTest(
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool()));
    List<Optional<ResolveResult>> resolvedResults =
        resolver.resolve(
            ImmutableList.of(
                ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"),
                ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/")));
    assertThat(resolvedResults)
        .containsExactly(
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/b", null, "/a/b")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"))),
            Optional.of(
                ResolveResult.of(
                    ImmutableList.of(ResolvedFile.create("/a/c", null, "/a/c")),
                    ImmutableMap.of(),
                    ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/"))));
  }

  @Test
  public void batchResolve_oneFileFails_interruptsFilesStillBeingResolved() throws Exception {
    CountDownLatch otherFileResolving = new CountDownLatch(1);
    CountDownLatch otherFileInterrupted = new CountDownLatch(1);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    ListeningExecutorService threadPool =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    AbstractFileResolver failingResolver =
        new AbstractFileResolver(threadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws MobileHarnessException, InterruptedException {
            if (resolveSource.path().equals("/a/failing")) {
              otherFileResolving.await();
              throw new MobileHarnessException(
                  BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve file");
            }
            otherFileResolving.countDown();
            try {
              neverCountedDown.await();
            } catch (InterruptedException e) {
              otherFileInterrupted.countDown();
              throw e;
            }
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      ListenableFuture<List<Optional<ResolveResult>>> unused =
          failingResolver.resolveAsync(
              ImmutableList.of(
                  ResolveSource.create("/a/failing", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/other", ImmutableMap.of(), "/", "/")));

      assertThat(otherFileInterrupted.await(10, SECONDS)).isTrue();
    } finally {
      threadPool.shutdownNow();
    }
  }

  @Test
  public void batchResolve_oneFileFails_throwsFailingFileException() {
    MobileHarnessException failure =
        new MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve /a/failing");
    CountDownLatch otherFileResolving = new CountDownLatch(1);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    ListeningExecutorService threadPool =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    AbstractFileResolver failingResolver =
        new AbstractFileResolver(threadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws MobileHarnessException, InterruptedException {
            if (resolveSource.path().equals("/a/failing")) {
              otherFileResolving.await();
              throw failure;
            }
            otherFileResolving.countDown();
            neverCountedDown.await();
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      MobileHarnessException thrown =
          assertThrows(
              MobileHarnessException.class,
              () ->
                  failingResolver.resolve(
                      ImmutableList.of(
                          ResolveSource.create("/a/failing", ImmutableMap.of(), "/", "/"),
                          ResolveSource.create("/a/other", ImmutableMap.of(), "/", "/"))));

      assertThat(thrown).isSameInstanceAs(failure);
    } finally {
      threadPool.shutdownNow();
    }
  }

  @Test
  public void batchResolve_submitFailsMidBatch_interruptsAlreadySubmittedFiles() throws Exception {
    CountDownLatch firstFileResolving = new CountDownLatch(1);
    CountDownLatch firstFileInterrupted = new CountDownLatch(1);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    AtomicInteger submitCalls = new AtomicInteger();
    ListeningExecutorService rawThreadPool =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    ListeningExecutorService rejectingThreadPool =
        new ForwardingListeningExecutorService() {
          @Override
          protected ListeningExecutorService delegate() {
            return rawThreadPool;
          }

          @Override
          public <T> ListenableFuture<T> submit(Callable<T> task) {
            // Submission 1 is the first file, 2 is the second file.
            if (submitCalls.incrementAndGet() == 2) {
              try {
                if (!firstFileResolving.await(10, SECONDS)) {
                  throw new AssertionError("First file did not start resolving");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
              throw new RejectedExecutionException("Executor full");
            }
            return super.submit(task);
          }
        };
    AbstractFileResolver partialSubmitResolver =
        new AbstractFileResolver(rejectingThreadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws InterruptedException {
            firstFileResolving.countDown();
            try {
              neverCountedDown.await();
            } catch (InterruptedException e) {
              firstFileInterrupted.countDown();
              throw e;
            }
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      ListenableFuture<List<Optional<ResolveResult>>> unused =
          partialSubmitResolver.resolveAsync(
              ImmutableList.of(
                  ResolveSource.create("/a/first", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/second", ImmutableMap.of(), "/", "/")));

      assertThat(firstFileInterrupted.await(10, SECONDS)).isTrue();
    } finally {
      rawThreadPool.shutdownNow();
    }
  }

  @Test
  public void batchResolve_directExecutor_oneFileFails_skipsRemainingFiles() {
    List<String> resolvedPaths = new ArrayList<>();
    AbstractFileResolver failingResolver =
        new AbstractFileResolver(MoreExecutors.newDirectExecutorService()) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws MobileHarnessException {
            resolvedPaths.add(resolveSource.path());
            if (resolveSource.path().equals("/a/failing")) {
              throw new MobileHarnessException(
                  BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve file");
            }
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    ListenableFuture<List<Optional<ResolveResult>>> unused =
        failingResolver.resolveAsync(
            ImmutableList.of(
                ResolveSource.create("/a/failing", ImmutableMap.of(), "/", "/"),
                ResolveSource.create("/a/other", ImmutableMap.of(), "/", "/")));

    assertThat(resolvedPaths).containsExactly("/a/failing");
  }

  @Test
  public void batchResolve_directExecutor_callerInterrupted_throwsInterruptedException() {
    List<String> resolvedPaths = new ArrayList<>();
    AbstractFileResolver interruptingResolver =
        new AbstractFileResolver(MoreExecutors.newDirectExecutorService()) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource) {
            resolvedPaths.add(resolveSource.path());
            // The file ends normally in spite of the interrupt.
            Thread.currentThread().interrupt();
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      assertThrows(
          InterruptedException.class,
          () ->
              interruptingResolver.resolve(
                  ImmutableList.of(
                      ResolveSource.create("/a/first", ImmutableMap.of(), "/", "/"),
                      ResolveSource.create("/a/second", ImmutableMap.of(), "/", "/"))));

      assertThat(resolvedPaths).containsExactly("/a/first");
    } finally {
      // The interrupt must not reach the tests that run after this one.
      boolean unused = Thread.interrupted();
    }
  }

  @Test
  public void batchResolve_earlierFileFailsMidSubmission_skipsLaterFiles() throws Exception {
    CountDownLatch allowFirstToFail = new CountDownLatch(1);
    CountDownLatch firstFailureHandled = new CountDownLatch(1);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    AtomicInteger submitCalls = new AtomicInteger();
    AtomicReference<ListenableFuture<?>> firstFileFuture = new AtomicReference<>();
    ListeningExecutorService rawThreadPool =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    ListeningExecutorService coordinatedThreadPool =
        new ForwardingListeningExecutorService() {
          @Override
          protected ListeningExecutorService delegate() {
            return rawThreadPool;
          }

          @Override
          public <T> ListenableFuture<T> submit(Callable<T> task) {
            int call = submitCalls.incrementAndGet();
            if (call == 1) {
              ListenableFuture<T> future = super.submit(task);
              firstFileFuture.set(future);
              return future;
            }
            if (call == 2) {
              // Registered after AbstractFileResolver's listener so FIFO completion guarantees the
              // batch-failure flag is set before the third file is checked.
              firstFileFuture.get().addListener(firstFailureHandled::countDown, directExecutor());
              allowFirstToFail.countDown();
              try {
                if (!firstFailureHandled.await(10, SECONDS)) {
                  throw new AssertionError("First file failure listener did not run");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
            }
            return super.submit(task);
          }
        };
    AbstractFileResolver failingResolver =
        new AbstractFileResolver(coordinatedThreadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws MobileHarnessException, InterruptedException {
            if (resolveSource.path().equals("/a/failing")) {
              allowFirstToFail.await();
              throw new MobileHarnessException(
                  BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Failed to resolve file");
            }
            neverCountedDown.await();
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      CountDownLatch batchDone = new CountDownLatch(1);
      ListenableFuture<List<Optional<ResolveResult>>> batchFuture =
          failingResolver.resolveAsync(
              ImmutableList.of(
                  ResolveSource.create("/a/failing", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/second", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/third", ImmutableMap.of(), "/", "/")));
      batchFuture.addListener(batchDone::countDown, directExecutor());

      assertThat(batchDone.await(10, SECONDS)).isTrue();
      assertThat(submitCalls.get()).isEqualTo(2);
    } finally {
      rawThreadPool.shutdownNow();
    }
  }

  @Test
  public void batchResolve_cancelledWhileFileRunsOnSubmittingThread_interruptsAllFiles()
      throws Exception {
    CountDownLatch bothFilesResolving = new CountDownLatch(2);
    CountDownLatch bothFilesInterrupted = new CountDownLatch(2);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    AtomicInteger resolveCalls = new AtomicInteger();
    // One thread runs the batch and the other the first file, so the second file runs on the
    // thread of the batch, which is submitting it.
    ListeningExecutorService callerRunsThreadPool = newCallerRunsThreadPool(/* threads= */ 2);
    AbstractFileResolver blockingResolver =
        new AbstractFileResolver(callerRunsThreadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws InterruptedException {
            resolveCalls.incrementAndGet();
            bothFilesResolving.countDown();
            try {
              neverCountedDown.await();
            } catch (InterruptedException e) {
              bothFilesInterrupted.countDown();
              throw e;
            }
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      ListenableFuture<List<Optional<ResolveResult>>> batchFuture =
          blockingResolver.resolveAsync(
              ImmutableList.of(
                  ResolveSource.create("/a/first", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/second", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/third", ImmutableMap.of(), "/", "/")));
      assertThat(bothFilesResolving.await(10, SECONDS)).isTrue();

      assertThat(batchFuture.cancel(/* mayInterruptIfRunning= */ true)).isTrue();

      assertThat(bothFilesInterrupted.await(10, SECONDS)).isTrue();
      // The batch submits nothing more once its thread is done.
      callerRunsThreadPool.shutdown();
      assertThat(callerRunsThreadPool.awaitTermination(10, SECONDS)).isTrue();
      assertThat(resolveCalls.get()).isEqualTo(2);
    } finally {
      callerRunsThreadPool.shutdownNow();
    }
  }

  @Test
  public void batchResolve_cancelledFileOnSubmittingThreadEndsNormally_submitsNoMoreFiles()
      throws Exception {
    CountDownLatch bothFilesResolving = new CountDownLatch(2);
    CountDownLatch firstFileInterrupted = new CountDownLatch(1);
    CountDownLatch neverCountedDown = new CountDownLatch(1);
    AtomicInteger resolveCalls = new AtomicInteger();
    // One thread runs the batch and the other the first file, so the second file runs on the
    // thread of the batch, which is submitting it.
    ListeningExecutorService callerRunsThreadPool = newCallerRunsThreadPool(/* threads= */ 2);
    AbstractFileResolver interruptKeepingResolver =
        new AbstractFileResolver(callerRunsThreadPool) {
          @Override
          protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
            return true;
          }

          @Override
          protected ResolveResult actuallyResolve(ResolveSource resolveSource)
              throws InterruptedException {
            resolveCalls.incrementAndGet();
            bothFilesResolving.countDown();
            try {
              neverCountedDown.await();
            } catch (InterruptedException e) {
              if (resolveSource.path().equals("/a/first")) {
                firstFileInterrupted.countDown();
                throw e;
              }
              // The file on the submitting thread keeps the interrupt and ends normally.
              Thread.currentThread().interrupt();
            }
            return ResolveResult.of(ImmutableList.of(), ImmutableMap.of(), resolveSource);
          }
        };

    try {
      ListenableFuture<List<Optional<ResolveResult>>> batchFuture =
          interruptKeepingResolver.resolveAsync(
              ImmutableList.of(
                  ResolveSource.create("/a/first", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/second", ImmutableMap.of(), "/", "/"),
                  ResolveSource.create("/a/third", ImmutableMap.of(), "/", "/")));
      assertThat(bothFilesResolving.await(10, SECONDS)).isTrue();

      assertThat(batchFuture.cancel(/* mayInterruptIfRunning= */ true)).isTrue();

      assertThat(firstFileInterrupted.await(10, SECONDS)).isTrue();
      // The batch submits nothing more once its thread is done.
      callerRunsThreadPool.shutdown();
      assertThat(callerRunsThreadPool.awaitTermination(10, SECONDS)).isTrue();
      assertThat(resolveCalls.get()).isEqualTo(2);
    } finally {
      callerRunsThreadPool.shutdownNow();
    }
  }

  @Test
  public void preBatchProcess() throws MobileHarnessException, InterruptedException {
    resolver =
        new FileResolverUnderTest(
            MoreExecutors.newDirectExecutorService(), source -> source.path().equals("/a/b"));
    Set<ResolveResult> resolvedResults =
        resolver.preBatchProcess(
            ImmutableList.of(
                ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/"),
                ResolveSource.create("/a/c", ImmutableMap.of(), "/", "/")));
    assertThat(resolvedResults)
        .containsExactly(
            ResolveResult.of(
                ImmutableList.of(ResolvedFile.create("/a/b", null, "/a/b")),
                ImmutableMap.of(),
                ResolveSource.create("/a/b", ImmutableMap.of(), "/", "/")));
  }

  @Test
  public void getResolveTimeout_noSuccessor_returnsDefaultSelfTimeout() {
    resolver = new FileResolverUnderTest(null);

    assertThat(resolver.getResolveTimeout())
        .isEqualTo(Duration.ofHours(AbstractFileResolver.RESOLVE_TIMEOUT_IN_HOUR));
  }

  @Test
  public void getResolveTimeout_noSuccessor_returnsOverriddenSelfTimeout() {
    resolver = new FileResolverUnderTest(null, Duration.ofHours(7));

    assertThat(resolver.getResolveTimeout()).isEqualTo(Duration.ofHours(7));
  }

  @Test
  public void getResolveTimeout_chain_returnsMaxTimeoutOfSuccessors() {
    resolver = new FileResolverUnderTest(null, Duration.ofHours(1));
    FileResolverUnderTest middleResolver = new FileResolverUnderTest(null, Duration.ofHours(7));
    FileResolverUnderTest lastResolver = new FileResolverUnderTest(null, Duration.ofHours(3));
    resolver.setSuccessor(middleResolver);
    middleResolver.setSuccessor(lastResolver);

    assertThat(resolver.getResolveTimeout()).isEqualTo(Duration.ofHours(7));
    assertThat(middleResolver.getResolveTimeout()).isEqualTo(Duration.ofHours(7));
    assertThat(lastResolver.getResolveTimeout()).isEqualTo(Duration.ofHours(3));
  }

  @Test
  public void getResolveTimeout_chain_returnsSelfTimeoutIfItIsTheMax() {
    resolver = new FileResolverUnderTest(null, Duration.ofHours(9));
    resolver.setSuccessor(new FileResolverUnderTest(null, Duration.ofHours(2)));

    assertThat(resolver.getResolveTimeout()).isEqualTo(Duration.ofHours(9));
  }

  /**
   * Returns a pool of {@code threads} threads that queues nothing: with all of them busy, a task
   * runs on the thread that submits it.
   */
  private static ListeningExecutorService newCallerRunsThreadPool(int threads) {
    return MoreExecutors.listeningDecorator(
        new ThreadPoolExecutor(
            threads,
            threads,
            /* keepAliveTime= */ 0L,
            SECONDS,
            new SynchronousQueue<>(),
            // Unlike ThreadPoolExecutor.CallerRunsPolicy, this also runs a task submitted after
            // shutdown, so a test can wait for the pool to end and still see a late submission.
            (task, executor) -> task.run()));
  }

  private class FileResolverUnderTest extends AbstractFileResolver {

    private Function<ResolveSource, Boolean> shouldResolve;
    private final Optional<Duration> selfResolveTimeout;

    private FileResolverUnderTest(ListeningExecutorService executorService) {
      this(executorService, source -> true);
    }

    private FileResolverUnderTest(
        ListeningExecutorService executorService, Duration selfResolveTimeout) {
      this(executorService, source -> true, Optional.of(selfResolveTimeout));
    }

    private FileResolverUnderTest(
        ListeningExecutorService executorService, Function<ResolveSource, Boolean> shouldResolve) {
      this(executorService, shouldResolve, /* selfResolveTimeout= */ Optional.empty());
    }

    private FileResolverUnderTest(
        ListeningExecutorService executorService,
        Function<ResolveSource, Boolean> shouldResolve,
        Optional<Duration> selfResolveTimeout) {
      super(executorService);
      this.shouldResolve = shouldResolve;
      this.selfResolveTimeout = selfResolveTimeout;
    }

    @Override
    protected Duration getSelfResolveTimeout() {
      return selfResolveTimeout.orElseGet(super::getSelfResolveTimeout);
    }

    @Override
    protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
      return shouldResolve.apply(resolveSource);
    }

    @Override
    protected ResolveResult actuallyResolve(ResolveSource resolveSource) {
      return ResolveResult.of(
          ImmutableList.of(ResolvedFile.create(resolveSource.path(), null, resolveSource.path())),
          ImmutableMap.of(),
          resolveSource);
    }

    @Override
    protected Set<ResolveResult> actuallyPreBatchProcess(List<ResolveSource> resolveSources)
        throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
            InterruptedException {
      return resolveSources.stream().map(this::actuallyResolve).collect(toImmutableSet());
    }
  }
}
