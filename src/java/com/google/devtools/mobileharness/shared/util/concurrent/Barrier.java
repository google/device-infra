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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronization aid that allows a set of threads to all wait for each other to reach a common
 * barrier point. It's similar to {@link java.util.concurrent.CyclicBarrier}, while it supports
 * waking up waiting threads before all threads reach the barrier point and let later threads skip
 * the barrier.
 */
public class Barrier {

  /** The lock for guarding barrier entry */
  private final ReentrantLock lock = new ReentrantLock();

  /** Condition to wait on until tripped */
  private final Condition trip = lock.newCondition();

  /** The number of parties */
  private final int parties;

  /** Number of parties still waiting. Counts down from parties to 0. */
  @GuardedBy("lock")
  private int count;

  /** Whether to stop later awaits. */
  @GuardedBy("lock")
  private boolean awaitationStopped;

  /**
   * Creates a new {@code Barrier} that will trip when the given number of parties (threads) are
   * waiting upon it, or when {@link #stopAwaitations()} is called.
   *
   * @param parties the number of threads that must invoke {@link #await()} before the barrier is
   *     tripped
   * @throws IllegalArgumentException if {@code parties} is less than 1
   */
  public Barrier(int parties) {
    checkArgument(parties > 0);
    this.parties = parties;
    this.count = parties;
  }

  /**
   * Waits until all {@code parties} have invoked {@link #await()} on this barrier, or {@link
   * #stopAwaitations()} is called, or any waiting threads are interrupted which will stop other
   * waiting threads.
   *
   * <p>Later calls to {@link #await()} will return immediately if {@link #stopAwaitations()} was
   * called before, or any awaiting threads were interrupted.
   *
   * @return {@code true} if all parties have invoked {@link #await()} on this barrier, otherwise
   *     {@code false} if any parties have not invoked {@link #await()} on this barrier (e.g.,
   *     {@link #stopAwaitations()} is called, or any waiting threads are interrupted).
   */
  public boolean await() throws InterruptedException {
    lock.lock();
    try {
      if (count == 0) {
        return true;
      }
      if (awaitationStopped) {
        return count <= 0;
      }

      --count;
      // If the current thread is the last one to arrive
      if (count == 0) {
        trip.signalAll();
        return true;
      }

      // If the current thread is not the last one to arrive, current thread will wait.
      while (count > 0) {
        try {
          trip.await();
        } catch (InterruptedException ie) {
          stopAwaitations();
          throw ie;
        }
        if (awaitationStopped) {
          break;
        }
      }

      return count <= 0;
    } finally {
      lock.unlock();
    }
  }

  /** Notifies all waiting threads on this barrier and stops later awaits. */
  public void stopAwaitations() {
    lock.lock();
    try {
      this.awaitationStopped = true;
      trip.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the number of parties required to trip this barrier.
   *
   * @return the number of parties required to trip this barrier
   */
  public int getParties() {
    return parties;
  }
}
