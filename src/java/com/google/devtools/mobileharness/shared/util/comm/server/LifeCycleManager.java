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

package com.google.devtools.mobileharness.shared.util.comm.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Server;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.VisibleForTesting;

/** Manages life cycles of multiple servers. */
public final class LifeCycleManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final Duration ALLOW_PROCESS_AFTER_SHUTDOWN = Duration.ofSeconds(3L);

  // ImmutableMap respects the order when created.
  private final ImmutableMap<Server, String> serverMap;
  private final Object lock = new Object();

  private CountDownLatch shutDownSignal;

  private final AtomicBoolean shutDown = new AtomicBoolean(false);
  private final ConcurrentLinkedDeque<Server> startedServers = new ConcurrentLinkedDeque<>();

  /**
   * Builder for {@link LifeCycleManager}.
   *
   * <p>If servers A, B, C are added in order, they will be started in order and shut down in the
   * reverse order.
   */
  public static class Builder {
    // A LinkedHashMap respects the insertion-order.
    private final LinkedHashMap<Server, String> registerMap = new LinkedHashMap<>();

    @CanIgnoreReturnValue
    public Builder add(Server server, String label) {
      registerMap.put(server, label);
      return this;
    }

    public LifeCycleManager build() {
      return new LifeCycleManager(registerMap);
    }
  }

  /** Listens to signals to trigger actions. */
  public final class ManagerListener extends LifeCycleListener {

    @Override
    public void onKillServer() {
      triggerShutdown();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Creates a new listener to get life cycle change signals. */
  public ManagerListener createListener() {
    return new ManagerListener();
  }

  /**
   * Starts all servers in order.
   *
   * <p>If any server fails to start, all servers will be shut down.
   */
  @CanIgnoreReturnValue
  public LifeCycleManager start() {
    synchronized (lock) {
      if (startedServers.isEmpty()) {
        logger.atInfo().log("Starting servers %s", serverMap.values());
        String starting = null;
        try {
          for (Map.Entry<Server, String> entry : serverMap.entrySet()) {
            starting = entry.getValue();
            startedServers.push(entry.getKey().start());
          }
        } catch (IOException e) {
          shutdownNow();
          throw new IllegalStateException("Failed to start the server: " + starting, e);
        }
        shutDownSignal = new CountDownLatch(1);
      }
    }
    return this;
  }

  /** Waits for all servers to terminate. */
  public void awaitTermination() throws InterruptedException {
    logger.atInfo().log("Start to wait for the termination.");
    shutDownSignal.await();
    for (Server server : serverMap.keySet()) {
      logger.atInfo().log("Wait for the termination of %s", serverMap.get(server));
      server.awaitTermination(ALLOW_PROCESS_AFTER_SHUTDOWN.toMillis() + 500, TimeUnit.MILLISECONDS);
    }
    shutdownNow();
    for (Server server : serverMap.keySet()) {
      logger.atInfo().log(
          "Wait for the termination of %s after possible kill.", serverMap.get(server));
      server.awaitTermination();
    }
  }

  /** Gets all servers with their labels. */
  public ImmutableMap<Server, String> getServersWithLabels() {
    return serverMap;
  }

  private void triggerShutdown() {
    synchronized (lock) {
      if (!shutDown.get()) {
        shutdown();
        shutDown.set(true);
      }
    }
  }

  /** Shuts down all servers immediately. */
  @VisibleForTesting
  void shutdownNow() {
    synchronized (lock) {
      for (Server server : startedServers) {
        if (!server.isTerminated()) {
          logger.atWarning().log("Need to shut down server %s now", serverMap.get(server));
          server.shutdownNow();
        }
      }
    }
  }

  /** Shuts down all servers gracefully. */
  @VisibleForTesting
  void shutdown() {
    synchronized (lock) {
      logger.atInfo().log("Shut down all servers");
      startedServers.forEach(Server::shutdown);
    }
    shutDownSignal.countDown();
  }

  private LifeCycleManager(Map<Server, String> serverMap) {
    this.serverMap = ImmutableMap.copyOf(serverMap);
  }
}
