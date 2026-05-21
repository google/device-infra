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

package com.google.devtools.mobileharness.shared.util.comm.dualconduit.proxy;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.client.DualConduitClient;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitResponse;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager.Priority;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A decorator for {@link io.grpc.Server} that establishes a DualConduit to proxy the server.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Server grpcServer = ServerBuilder.forPort(0)
 *     .addService(myService)
 *     .build();
 * ManagedChannel channel = ChannelFactory.createChannel("dcon-dialer-address", executor);
 * new ReverseProxiedServer(
 *         grpcServer, channel, "my-server", "instance-id", "localhost")
 *     .startWithTeardown(() -> channel.shutdown());
 * }</pre>
 */
public class ReverseProxiedServer extends Server {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Server delegate;
  private final DualConduitClient client;
  private final String serverName;
  private final String instanceId;
  private final String delegateHostname;
  private String conduitId;
  @Nullable private Runnable teardownCallback;

  /**
   * Constructs a new ReverseProxiedServer.
   *
   * @param delegate the actual gRPC server to be proxied
   * @param client the DualConduit client used to establish the reverse conduit
   * @param serverName the name of the server registered in the DualConduit dialer
   * @param instanceId the unique instance ID, used by external clients to target this specific
   *     server via the DualConduit proxy
   * @param delegateHostname the hostname/address that the DualConduit dialer can use to connect
   *     back to this delegate server locally (e.g., "localhost" or docker network alias)
   */
  public ReverseProxiedServer(
      Server delegate,
      DualConduitClient client,
      String serverName,
      String instanceId,
      String delegateHostname) {
    this.delegate = delegate;
    this.client = client;
    this.serverName = serverName;
    this.instanceId = instanceId;
    this.delegateHostname = delegateHostname;
  }

  /**
   * Constructs a new ReverseProxiedServer.
   *
   * @param delegate the actual gRPC server to be proxied
   * @param channel the externally created channel to the DualConduit dialer
   * @param serverName the name of the server registered in the DualConduit dialer
   * @param instanceId the unique instance ID, used by external clients to target this specific
   *     server via the DualConduit proxy
   * @param delegateHostname the hostname/address that the DualConduit dialer can use to connect
   *     back to this delegate server locally (e.g., "localhost" or docker network alias)
   */
  public ReverseProxiedServer(
      Server delegate,
      Channel channel,
      String serverName,
      String instanceId,
      String delegateHostname) {
    this(delegate, new DualConduitClient(channel), serverName, instanceId, delegateHostname);
  }

  private void triggerTeardownCallback() {
    if (teardownCallback != null) {
      try {
        teardownCallback.run();
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log("Failed to execute teardown callback");
      }
    }
  }

  @CanIgnoreReturnValue
  public Server startWithTeardown(Runnable teardownCallback) throws IOException {
    this.teardownCallback = teardownCallback;
    return start();
  }

  @CanIgnoreReturnValue
  @Override
  public Server start() throws IOException {
    delegate.start();
    logger.atInfo().log("Delegate server started, establishing conduit...");
    try {
      String destinationEndpoint = delegateHostname + ":" + delegate.getPort();
      EstablishConduitResponse response =
          client.establishReverseGrpcConduit(serverName, instanceId, destinationEndpoint);
      this.conduitId = response.getConduitId();
      logger.atInfo().log(
          "Conduit established, conduitId: %s, and service is provided at serviceLocator: %s",
          conduitId, response.getServiceLocator());
      // The conduit is only torn down via a shutdown hook because in our deviceinfra use cases,
      // the lifecycle of the proxied server is expected to be the same as the server process.
      ShutdownHookManager.getInstance()
          .addShutdownHook(
              () -> {
                // Wait for active RPCs to drain before closing conduit transport.
                try {
                  var unused = delegate.awaitTermination(10, SECONDS);
                } catch (InterruptedException e) {
                  logger.atWarning().withCause(e).log(
                      "Interrupted while waiting for delegate server termination");
                  Thread.currentThread().interrupt();
                }
                if (conduitId != null) {
                  try {
                    logger.atInfo().log("Tearing down conduit %s", conduitId);
                    var unused = client.teardownConduit(conduitId);
                  } catch (RuntimeException e) {
                    logger.atWarning().withCause(e).log("Failed to teardown conduit %s", conduitId);
                  }
                }
                triggerTeardownCallback();
              },
              "teardown-reverse-proxied-server-conduit",
              Priority.LOWEST);
    } catch (RuntimeException e) {
      delegate.shutdown();
      triggerTeardownCallback();
      throw new IOException("Failed to establish conduit", e);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public Server shutdown() {
    delegate.shutdown();
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public Server shutdownNow() {
    delegate.shutdownNow();
    return this;
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public void awaitTermination() throws InterruptedException {
    delegate.awaitTermination();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public int getPort() {
    return delegate.getPort();
  }

  @Override
  public List<ServerServiceDefinition> getServices() {
    return delegate.getServices();
  }

  @Override
  public List<ServerServiceDefinition> getImmutableServices() {
    return delegate.getImmutableServices();
  }
}
