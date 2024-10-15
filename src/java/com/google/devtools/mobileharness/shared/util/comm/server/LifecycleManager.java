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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.grpc.Server;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;

/** Manages lifecycles of multiple servers. */
public final class LifecycleManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final Duration ALLOW_PROCESS_AFTER_SHUTDOWN = Duration.ofSeconds(3L);

  private final ListeningExecutorService executorService;
  private final ImmutableList<LabeledServer> managedServers;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private volatile boolean started;

  @GuardedBy("lock")
  private volatile boolean shutdown;

  /** Data structure to hold both a server and its label. */
  @AutoValue
  public abstract static class LabeledServer {
    public abstract Server server();

    public abstract String label();

    public static LabeledServer create(Server server, String label) {
      return new AutoValue_LifecycleManager_LabeledServer(server, label);
    }

    @Memoized
    @Override
    public String toString() {
      return String.format("Server{label=%s} %s", label(), server());
    }
  }

  /**
   * Constructor for {@link LifecycleManager}.
   *
   * @param executorService the executor service to forcefully shut down the managed servers.
   * @param managedServers Servers will be started in the order of the list and shutdown in the
   *     reverse order.
   */
  public LifecycleManager(
      ListeningExecutorService executorService, List<LabeledServer> managedServers) {
    this.executorService = executorService;
    this.managedServers = ImmutableList.copyOf(managedServers);
  }

  /**
   * Starts all servers in order.
   *
   * <p>If any server fails to start, all servers will be shut down forcefully.
   */
  public void start() {
    synchronized (lock) {
      checkState(!started, "The servers already started. Don't call start again!");
      started = true;
      for (LabeledServer labeledServer : managedServers) {
        String label = labeledServer.label();
        try {
          logger.atInfo().log(
              "Starting RPC server [%s], services=%s",
              labeledServer,
              labeledServer.server().getImmutableServices().stream()
                  .map(service -> service.getServiceDescriptor().getName())
                  .sorted()
                  .collect(toImmutableList()));
          labeledServer.server().start();
        } catch (IOException e) {
          managedServers.forEach(server -> server.server().shutdownNow());
          throw new UncheckedIOException(
              String.format("Failed to start the server [%s]", label), e);
        }
      }
      for (LabeledServer labeledServer : managedServers) {
        logger.atInfo().log("%s has started", labeledServer);
      }
    }
  }

  /**
   * Shuts down all servers gracefully.
   *
   * <p>After the normal shutdown, each server will be shut down forcefully after {@link
   * #ALLOW_PROCESS_AFTER_SHUTDOWN} asynchronously.
   */
  public void shutdown() {
    synchronized (lock) {
      checkState(!shutdown, "The servers are already shutdown. Don't call shutdown again!");
      shutdown = true;
      for (LabeledServer labeledServer : managedServers.reverse()) {
        logger.atInfo().log("Shutting down server [%s]", labeledServer.label());
        labeledServer.server().shutdown();
      }
    }
    logFailure(
        executorService.submit(this::guaranteeTermination),
        Level.SEVERE,
        "Fatal error while shutting down server");
  }

  /** Waits for all servers to terminate. */
  public void awaitTermination() throws InterruptedException {
    for (LabeledServer labeledServer : managedServers) {
      logger.atInfo().log("Waiting for the termination of server(label=%s)", labeledServer.label());
      labeledServer.server().awaitTermination();
    }
  }

  private Void guaranteeTermination() throws InterruptedException {
    for (LabeledServer labeledServer : managedServers) {
      if (!labeledServer
          .server()
          .awaitTermination(ALLOW_PROCESS_AFTER_SHUTDOWN.toMillis(), MILLISECONDS)) {
        logger.atWarning().log("Need to shut down server %s now", labeledServer.label());
        labeledServer.server().shutdownNow();
      }
    }
    return null;
  }
}
