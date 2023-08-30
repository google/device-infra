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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.shared.constant.closeable.CountingCloseable;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.concurrent.GuardedBy;

/**
 * A holder of {@link ManagedChannel} which records whether there are stubs that point to the
 * channel and have not been {@linkplain AutoCloseable#close() closed}.
 */
class CountingManagedChannel {

  private final ManagedChannel channel;
  private final Duration expirationTime;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Set<CountingCloseable> openStubs = new HashSet<>();

  @GuardedBy("lock")
  private Instant latestStubCloseTime;

  CountingManagedChannel(ManagedChannel channel, Duration expirationTime) {
    this.channel = channel;
    this.expirationTime = expirationTime;
    this.latestStubCloseTime = Instant.now();
  }

  <T extends CountingCloseable> T createStub(Function<Channel, T> stubCreator) {
    T stub = stubCreator.apply(channel);
    synchronized (lock) {
      checkState(!channel.isShutdown(), "Channel has been shut down");
      openStubs.add(stub);
    }
    return stub;
  }

  /**
   * Shuts down the channel if no open stub for {@link #expirationTime} and returns {@code true}.
   * Does nothing and returns {@code false} otherwise.
   */
  boolean tryShutdown() {
    synchronized (lock) {
      boolean canShutdown = canShutdown();
      if (canShutdown) {
        channel.shutdown();
      }
      return canShutdown;
    }
  }

  /** Returns {@code true} if no open stub for {@link #expirationTime}. */
  @GuardedBy("lock")
  private boolean canShutdown() {
    Iterator<CountingCloseable> stubIterator = openStubs.iterator();
    while (stubIterator.hasNext()) {
      CountingCloseable stub = stubIterator.next();

      Optional<Instant> closeTime = stub.closeTime();
      if (closeTime.isPresent()) {
        // Removes the closed stub and updates the latest close time.
        stubIterator.remove();
        if (closeTime.get().isAfter(latestStubCloseTime)) {
          latestStubCloseTime = closeTime.get();
        }
      }
    }
    return openStubs.isEmpty() && Instant.now().isAfter(latestStubCloseTime.plus(expirationTime));
  }
}
