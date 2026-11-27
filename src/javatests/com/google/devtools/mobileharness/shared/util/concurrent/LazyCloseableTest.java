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

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LazyCloseable}. */
@RunWith(JUnit4.class)
public final class LazyCloseableTest {

  /** Generous, since a test waits this long only when it is about to fail. */
  private static final long TIMEOUT_SECONDS = 30;

  /** The states of a thread that is waiting for a lock or for a latch. */
  private static final ImmutableSet<Thread.State> PARKED =
      Sets.immutableEnumSet(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING);

  @Test
  public void close_withoutGet_isNoOpAndPreventsSubsequentGet() {
    AtomicInteger creations = new AtomicInteger();
    AtomicInteger closes = new AtomicInteger();
    LazyCloseable<String> lazy =
        LazyCloseable.of(
            () -> {
              creations.incrementAndGet();
              return "resource";
            },
            ignored -> closes.incrementAndGet());

    lazy.close();
    lazy.close();

    assertThat(creations.get()).isEqualTo(0);
    assertThat(closes.get()).isEqualTo(0);
    assertThrows(IllegalStateException.class, lazy::get);
    assertThat(creations.get()).isEqualTo(0);
  }

  @Test
  public void get_createsOnceAndReusesResource() {
    AtomicInteger creations = new AtomicInteger();
    List<String> closedResources = new ArrayList<>();
    try (LazyCloseable<String> lazy =
        LazyCloseable.of(() -> "resource-" + creations.incrementAndGet(), closedResources::add)) {
      assertThat(creations.get()).isEqualTo(0);
      String first = lazy.get();
      String second = lazy.get();
      assertThat(first).isEqualTo("resource-1");
      assertThat(second).isSameInstanceAs(first);
      assertThat(creations.get()).isEqualTo(1);
      lazy.close();
    }

    assertThat(closedResources).containsExactly("resource-1");
  }

  @Test
  public void close_whenCloserThrows_remainsClosedAndDoesNotRetryCloser() {
    AtomicInteger closes = new AtomicInteger();
    RuntimeException failure = new RuntimeException("close failed");
    LazyCloseable<String> lazy =
        LazyCloseable.of(
            () -> "resource",
            ignored -> {
              closes.incrementAndGet();
              throw failure;
            });
    assertThat(lazy.get()).isEqualTo("resource");

    RuntimeException thrown = assertThrows(RuntimeException.class, lazy::close);
    assertThat(thrown).isSameInstanceAs(failure);

    lazy.close();
    assertThat(closes.get()).isEqualTo(1);
    assertThrows(IllegalStateException.class, lazy::get);
  }

  @Test
  public void get_whenSupplierThrows_doesNotCacheFailure() {
    AtomicInteger attempts = new AtomicInteger();
    LazyCloseable<String> lazy =
        LazyCloseable.of(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalArgumentException("transient");
              }
              return "recovered";
            },
            ignored -> {});

    assertThrows(IllegalArgumentException.class, lazy::get);
    assertThat(lazy.get()).isEqualTo("recovered");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  public void get_whenSupplierReturnsNull_throwsNullPointerExceptionWithoutCaching() {
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger closes = new AtomicInteger();
    try (LazyCloseable<String> lazy =
        LazyCloseable.of(
            () -> attempts.incrementAndGet() == 1 ? null : "non-null",
            ignored -> closes.incrementAndGet())) {
      assertThrows(NullPointerException.class, lazy::get);
      assertThat(lazy.get()).isEqualTo("non-null");
    }

    assertThat(closes.get()).isEqualTo(1);
  }

  @Test
  public void close_runsCloserOutsideLock() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CountDownLatch enteredCloser = new CountDownLatch(1);
      CountDownLatch allowCloserToFinish = new CountDownLatch(1);
      LazyCloseable<String> lazy =
          LazyCloseable.of(
              () -> "resource",
              ignored -> {
                enteredCloser.countDown();
                try {
                  assertThat(allowCloserToFinish.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
              });
      assertThat(lazy.get()).isEqualTo("resource");

      Future<?> closeFuture = executor.submit(lazy::close);
      assertThat(enteredCloser.await(TIMEOUT_SECONDS, SECONDS)).isTrue();

      // get() acquires the internal lock and must fail fast while closer is still running.
      assertThrows(IllegalStateException.class, lazy::get);

      allowCloserToFinish.countDown();
      closeFuture.get(TIMEOUT_SECONDS, SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void concurrentGet_createsResourceOnce() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      AtomicInteger creations = new AtomicInteger();
      CountDownLatch enteredSupplier = new CountDownLatch(1);
      CountDownLatch allowSupplierToReturn = new CountDownLatch(1);
      CountDownLatch secondStarted = new CountDownLatch(1);
      AtomicReference<Thread> secondThread = new AtomicReference<>();
      LazyCloseable<Object> lazy =
          LazyCloseable.of(
              () -> {
                creations.incrementAndGet();
                enteredSupplier.countDown();
                try {
                  assertThat(allowSupplierToReturn.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
                return new Object();
              },
              ignored -> {});

      Future<Object> first = executor.submit(lazy::get);
      assertThat(enteredSupplier.await(TIMEOUT_SECONDS, SECONDS)).isTrue();

      Future<Object> second =
          executor.submit(() -> {
            secondThread.set(Thread.currentThread());
            secondStarted.countDown();
            return lazy.get();
          });
      assertThat(secondStarted.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
      awaitParked(secondThread.get());
      assertThat(second.isDone()).isFalse();

      allowSupplierToReturn.countDown();

      assertThat(first.get(TIMEOUT_SECONDS, SECONDS))
          .isSameInstanceAs(second.get(TIMEOUT_SECONDS, SECONDS));
      assertThat(creations.get()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void close_whileGetInFlight_waitsAndClosesCreatedResource() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<String> closedResources = new ArrayList<>();
      CountDownLatch enteredSupplier = new CountDownLatch(1);
      CountDownLatch allowSupplierToReturn = new CountDownLatch(1);
      CountDownLatch closeStarted = new CountDownLatch(1);
      AtomicReference<Thread> closeThread = new AtomicReference<>();
      LazyCloseable<String> lazy =
          LazyCloseable.of(
              () -> {
                enteredSupplier.countDown();
                try {
                  assertThat(allowSupplierToReturn.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
                return "in-flight-resource";
              },
              closedResources::add);

      Future<String> getFuture = executor.submit(lazy::get);
      assertThat(enteredSupplier.await(TIMEOUT_SECONDS, SECONDS)).isTrue();

      Future<?> closeFuture =
          executor.submit(() -> {
            closeThread.set(Thread.currentThread());
            closeStarted.countDown();
            lazy.close();
          });
      assertThat(closeStarted.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
      awaitParked(closeThread.get());
      assertThat(closeFuture.isDone()).isFalse();

      allowSupplierToReturn.countDown();

      assertThat(getFuture.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("in-flight-resource");
      closeFuture.get(TIMEOUT_SECONDS, SECONDS);
      assertThat(closedResources).containsExactly("in-flight-resource");
      assertThrows(IllegalStateException.class, lazy::get);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Waits until {@code thread} can go no further without another thread. Its state is polled, since
   * nothing announces that a thread started waiting for a lock.
   */
  private static void awaitParked(Thread thread) {
    long deadlineNanos = System.nanoTime() + Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();
    while (!PARKED.contains(thread.getState()) && System.nanoTime() < deadlineNanos) {
      Thread.onSpinWait();
    }
    assertThat(thread.getState()).isIn(PARKED);
  }
}
