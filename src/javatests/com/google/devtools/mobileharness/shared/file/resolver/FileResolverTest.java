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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
  public void preBatchProcess()
      throws MobileHarnessException, InterruptedException, ExecutionException {
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

  private class FileResolverUnderTest extends AbstractFileResolver {

    private Function<ResolveSource, Boolean> shouldResolve;

    private FileResolverUnderTest(ListeningExecutorService executorService) {
      this(executorService, source -> true);
    }

    private FileResolverUnderTest(
        ListeningExecutorService executorService, Function<ResolveSource, Boolean> shouldResolve) {
      super(executorService);
      this.shouldResolve = shouldResolve;
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
