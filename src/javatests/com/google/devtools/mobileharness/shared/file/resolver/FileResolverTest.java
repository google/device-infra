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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolvedFile;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
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
