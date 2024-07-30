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

package com.google.devtools.mobileharness.shared.context;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationInfo.sameDisplayId;
import static com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType.OMNILAB_TEST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.shared.context.InvocationContext.ContextScope;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationInfo;
import com.google.devtools.mobileharness.shared.context.InvocationContext.InvocationType;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InvocationContextExecutorsTest {

  private final ListeningExecutorService threadPool =
      ThreadPools.createStandardThreadPool("testing-thread-pool");
  private final ListeningScheduledExecutorService scheduledThreadPool =
      ThreadPools.createStandardScheduledThreadPool("testing-scheduled-thread-pool", 1);

  private volatile SettableFuture<ImmutableMap<InvocationType, InvocationInfo>> contextFuture;

  private final Callable<ImmutableMap<InvocationType, InvocationInfo>> callable =
      InvocationContext::getCurrentContextImmutable;

  @SuppressWarnings({"UnusedAssignment", "unused"})
  @Test
  public void propagatingContext() throws Exception {
    InvocationContext.propagateContext(() -> {}).run();

    try (var ignored =
        new ContextScope(ImmutableMap.of(OMNILAB_TEST, sameDisplayId("fake_test_id")))) {
      assertThat(InvocationContext.getCurrentContext())
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      InvocationContext.propagateContext(() -> {}).run();

      assertThat(InvocationContext.getCurrentContext())
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      ListenableFuture<?> future;

      contextFuture = SettableFuture.create();
      threadPool.execute(this::readContext);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      future = threadPool.submit(this::readContext);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      future = threadPool.submit(this::readContext, "whatever");
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      assertThat(threadPool.submit(callable).get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      List<Future<ImmutableMap<InvocationType, InvocationInfo>>> futures =
          threadPool.invokeAll(ImmutableList.of(callable, callable));
      for (Future<ImmutableMap<InvocationType, InvocationInfo>> oneFuture : futures) {
        assertThat(oneFuture.get(3L, SECONDS))
            .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
      }

      assertThat(threadPool.invokeAny(ImmutableList.of(callable, callable)))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      future = scheduledThreadPool.schedule(this::readContext, 1L, MILLISECONDS);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      future = scheduledThreadPool.schedule(this::readContext, Duration.ofMillis(1L));
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      assertThat(scheduledThreadPool.schedule(callable, 1L, MILLISECONDS).get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      assertThat(scheduledThreadPool.schedule(callable, Duration.ofMillis(1L)).get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      future = scheduledThreadPool.scheduleAtFixedRate(this::readContext, 1L, 2_000L, MILLISECONDS);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
      future.cancel(false);

      contextFuture = SettableFuture.create();
      future =
          scheduledThreadPool.scheduleAtFixedRate(
              this::readContext, Duration.ofMillis(1L), Duration.ofSeconds(2L));
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
      future.cancel(false);

      contextFuture = SettableFuture.create();
      future =
          scheduledThreadPool.scheduleWithFixedDelay(this::readContext, 1L, 2_000L, MILLISECONDS);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
      future.cancel(false);

      contextFuture = SettableFuture.create();
      future =
          scheduledThreadPool.scheduleWithFixedDelay(
              this::readContext, Duration.ofMillis(1L), Duration.ofSeconds(2L));
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
      future.cancel(false);

      contextFuture = SettableFuture.create();
      Futures.addCallback(
          threadPool.submit(this::sleep), new ContextFutureCallback(), directExecutor());
      assertThat(contextFuture.get(4L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));

      contextFuture = SettableFuture.create();
      Futures.addCallback(threadPool.submit(() -> {}), new ContextFutureCallback(), threadPool);
      assertThat(contextFuture.get(3L, SECONDS))
          .containsExactly(OMNILAB_TEST, sameDisplayId("fake_test_id"));
    }

    assertThat(scheduledThreadPool.schedule(callable, Duration.ofMillis(1L)).get(3L, SECONDS))
        .isEmpty();

    ListenableFuture<?> future =
        threadPool.submit(
            (Callable<Void>)
                () -> {
                  throw new InterruptedException();
                });
    assertThat(assertThrows(ExecutionException.class, () -> future.get(3L, SECONDS)))
        .hasCauseThat()
        .isInstanceOf(InterruptedException.class);
  }

  private void readContext() {
    contextFuture.set(InvocationContext.getCurrentContextImmutable());
  }

  private Void sleep() throws InterruptedException {
    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));
    return null;
  }

  private class ContextFutureCallback implements FutureCallback<Object> {

    @Override
    public void onSuccess(Object result) {
      contextFuture.set(InvocationContext.getCurrentContextImmutable());
    }

    @Override
    public void onFailure(Throwable t) {
      // Does nothing.
    }
  }
}
