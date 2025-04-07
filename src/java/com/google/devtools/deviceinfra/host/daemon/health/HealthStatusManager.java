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

package com.google.devtools.deviceinfra.host.daemon.health;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.ServingStatus;
import com.google.devtools.mobileharness.infra.daemon.health.handler.DrainHandler;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Health manager for server to maintain its status and notify when drain request come. */
@Singleton
public class HealthStatusManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval to check if sub-component has drained. */
  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

  /** Handlers list from sub-components. */
  private final Set<DrainHandler> drainHandlers;

  /** Status of current server. */
  private final AtomicReference<ServingStatus> servingStatus;

  /** Mockable {@code Sleeper} for putting thread to sleep. */
  private final Sleeper sleeper;

  /** Clock to get current Instant. */
  private final Clock clock;

  /** Executor service to run drain thread. */
  private final ExecutorService threadPool;

  @Inject
  HealthStatusManager(Set<DrainHandler> drainHandlers) {
    this(
        drainHandlers,
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  HealthStatusManager(
      Set<DrainHandler> drainHandlers, Sleeper sleeper, Clock clock, ExecutorService threadPool) {
    this.drainHandlers = new HashSet<>(drainHandlers);
    this.servingStatus = new AtomicReference<>(ServingStatus.SERVICING);
    this.sleeper = sleeper;
    this.clock = clock;
    this.threadPool = threadPool;
  }

  /** Add handler into status manager which will be checked when server going to drain. */
  public void addDrainHandler(DrainHandler handler) {
    drainHandlers.add(handler);
  }

  /** Check status of this server. */
  public ServingStatus check() {
    return servingStatus.get();
  }

  /** Drain current server. */
  public void drain(Optional<Duration> timeoutOpt) {
    ServingStatus currentStatus = servingStatus.get();
    // If server is already in DRAINING or DRAINED mode, skip operation.
    if (currentStatus != ServingStatus.SERVICING) {
      logger.atInfo().log("Skip drain server since it is already in %s mode", currentStatus);
      return;
    }

    // Set server status to DRAINING.
    servingStatus.set(ServingStatus.DRAINING);

    // Setting the drain timeout to the shorter one from request or value from flag.
    Duration drainTimeout = calculateTimeout(timeoutOpt);
    Future<?> drainFuture =
        threadPool.submit(
            () -> {
              try {
                // First to notify all handler to drain.
                notifyAllHandlersToDrain();

                // Then start to check if drained or timeout.
                Instant start = clock.instant();
                while (!Thread.currentThread().isInterrupted()) {
                  // First check if it is already timeout according to request or flag.
                  if (drainTimeout.compareTo(Duration.between(start, clock.instant())) <= 0) {
                    logger.atInfo().log("=============== Server Timeout to Drain ===============");
                    forceCleanup();
                    break;
                  }

                  sleeper.sleep(CHECK_INTERVAL);
                  if (allHandlersHasDrained()) {
                    logger.atInfo().log(
                        "=============== Server Successfully Drained ===============");
                    break;
                  }

                  if (anyHandlerHasExpired()) {
                    logger.atInfo().log(
                        "=============== Server Components Drain Timeout ===============");
                    forceCleanup();
                    break;
                  }
                }
              } catch (InterruptedException | RuntimeException e) {
                logger.atWarning().withCause(e).log(
                    "Error waiting for drain finished (ignored): %s", e.getMessage());
              } finally {
                // Set the server status to DRAINED once we are exiting drain thread.
                servingStatus.set(ServingStatus.DRAINED);
              }
            });
  }

  /** Check if all handlers has drained. */
  private boolean allHandlersHasDrained() {
    for (DrainHandler handler : drainHandlers) {
      if (!handler.hasDrained()) {
        return false;
      }
    }
    return true;
  }

  /** Check if any handler has expired for drain. */
  private boolean anyHandlerHasExpired() {
    for (DrainHandler handler : drainHandlers) {
      if (handler.hasExpired()) {
        return true;
      }
    }
    return false;
  }

  /** Notify all handlers to drain. */
  private void notifyAllHandlersToDrain() {
    drainHandlers.parallelStream().forEach(DrainHandler::drain);
  }

  private void forceCleanup() {
    logger.atInfo().log(
        "=============== Timeout to Drain. Triggering force clean up. ===============");
    for (DrainHandler handler : drainHandlers) {
      handler.forceCleanUp();
    }
  }

  /** Compares the given timeout with the one from the flag and returns the shorter one. */
  static Duration calculateTimeout(Optional<Duration> timeoutOpt) {
    Duration timeout = Flags.instance().maxDrainTimeout.getNonNull();
    if (timeoutOpt.isPresent() && timeoutOpt.get().compareTo(timeout) < 0) {
      timeout = timeoutOpt.get();
    }
    return timeout;
  }
}
