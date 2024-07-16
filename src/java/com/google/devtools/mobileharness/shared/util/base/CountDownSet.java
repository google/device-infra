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

package com.google.devtools.mobileharness.shared.util.base;

import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.annotation.concurrent.GuardedBy;

/**
 * Similar to {@link java.util.concurrent.CountDownLatch}, but use {@link #remove(Object)} of a set
 * to replace {@linkplain CountDownLatch#countDown() countDown()}.
 */
public class CountDownSet<E> {

  @GuardedBy("itself")
  private final Set<E> set;

  public CountDownSet(Collection<? extends E> initialValues) {
    this.set = new HashSet<>(initialValues);
  }

  /**
   * Removes an element from the set. If the set becomes empty, {@link #await()} and {@link
   * #await(Duration)} will return.
   */
  public void remove(E value) {
    synchronized (set) {
      set.remove(value);
      if (set.isEmpty()) {
        set.notifyAll();
      }
    }
  }

  /** Waits until the set becomes empty. */
  public void await() throws InterruptedException {
    synchronized (set) {
      while (!set.isEmpty()) {
        set.wait();
      }
    }
  }

  /**
   * Waits until the set becomes empty, or until the given {@code timeout} time has elapsed.
   *
   * @return a snapshot of the set when it became empty or when the wait time elapsed
   */
  public ImmutableSet<E> await(Duration timeout) throws InterruptedException {
    Instant timeoutInstant = Instant.now().plus(timeout);
    synchronized (set) {
      while (true) {
        if (set.isEmpty()) {
          return ImmutableSet.of();
        }
        Duration waitTime = Duration.between(Instant.now(), timeoutInstant);
        if (waitTime.isNegative()) {
          return ImmutableSet.copyOf(set);
        }
        set.wait(waitTime.toMillis());
      }
    }
  }
}
