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

package com.google.devtools.mobileharness.platform.android.genericservice;

import static java.util.concurrent.TimeUnit.SECONDS;

import chromiumos.test.api.GenericMessage;
import chromiumos.test.api.GenericServiceGrpc;
import chromiumos.test.api.GenericStartRequest;
import chromiumos.test.api.GenericStopRequest;
import chromiumos.test.lab.api.Dut;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.Any;
import com.google.protobuf.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;

/** A helper class to manage the lifecycle of a GenericService gRPC service. */
public class GenericServiceManager implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long GRPC_RETRY_INTERVAL_SECONDS = 1;
  private static final Duration GRPC_START_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration GRPC_STOP_TIMEOUT = Duration.ofSeconds(60);

  private String host;
  private int port;
  private ManagedChannel channel;

  /**
   * Connects to the gRPC server.
   *
   * @param host The host of the gRPC server.
   * @param port The port of the gRPC server.
   */
  public void connect(String host, int port) {
    this.host = host;
    this.port = port;
    this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
  }

  /** Shuts down the gRPC channel. */
  @Override
  public void close() {
    if (this.channel != null) {
      try {
        this.channel.shutdown().awaitTermination(5, SECONDS);
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Generic Service channel shutdown was interrupted.");
      }
      this.channel = null;
    }
  }

  /**
   * Starts the GenericService and waits for the operation to complete.
   *
   * @param message A map of key-value pairs to send with the start request.
   * @throws InterruptedException If the thread is interrupted while waiting for the response.
   * @throws StatusRuntimeException If there is an error during the gRPC call.
   */
  public void start(Map<String, Any> message) throws InterruptedException {
    logger.atInfo().log("Starting Generic Service at: %s", getAddress());

    GenericStartRequest request =
        GenericStartRequest.newBuilder()
            .setMessage(GenericMessage.newBuilder().putAllValues(message).build())
            .build();

    long deadline = InstantSource.system().instant().plus(GRPC_START_TIMEOUT).toEpochMilli();
    StatusRuntimeException lastException = null;

    Instant now = InstantSource.system().instant();
    while (now.isBefore(Instant.ofEpochMilli(deadline))) {
      long remainingMillis = deadline - now.toEpochMilli();
      if (remainingMillis <= 0) {
        break;
      }
      try {
        GenericServiceGrpc.GenericServiceBlockingStub blockingStub =
            GenericServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(Duration.ofMillis(remainingMillis));

        blockingStub.start(request);
        logger.atInfo().log("Generic Service started successfully.");
        return; // Success
      } catch (StatusRuntimeException e) {
        lastException = e;
        if (e.getStatus().getCode() != Code.UNAVAILABLE) {
          throw e;
        }
        logger.atInfo().log(
            "Service not available at %s, retrying in %d seconds...",
            getAddress(), GRPC_RETRY_INTERVAL_SECONDS);
      }
      SECONDS.sleep(GRPC_RETRY_INTERVAL_SECONDS);
    }

    if (lastException != null) {
      throw lastException;
    }
    throw new StatusRuntimeException(
        Status.DEADLINE_EXCEEDED.withDescription(
            "Failed to connect to Generic Service at "
                + getAddress()
                + " within "
                + GRPC_START_TIMEOUT
                + " seconds."));
  }

  /**
   * Stops the GenericService and waits for the operation to complete.
   *
   * @param message A map of key-value pairs to send with the stop request.
   * @throws InterruptedException If the thread is interrupted while waiting for the response.
   * @throws StatusRuntimeException If there is an error during the gRPC call.
   */
  public void stop(Map<String, Any> message) throws InterruptedException {
    logger.atInfo().log("Stopping Generic Service at: %s", getAddress());

    GenericStopRequest request =
        GenericStopRequest.newBuilder()
            .setMessage(GenericMessage.newBuilder().putAllValues(message).build())
            .build();

    GenericServiceGrpc.GenericServiceBlockingStub blockingStub =
        GenericServiceGrpc.newBlockingStub(channel).withDeadlineAfter(GRPC_STOP_TIMEOUT);

    blockingStub.stop(request);
    logger.atInfo().log("Generic Service stopped successfully.");
  }

  String getAddress() {
    return String.format("%s:%d", host, port);
  }

  // TODO: Remove this once the GenericService is integrated with MobileHarness.
  // This test design to work with LSNexus service.
  public static void main(String[] args) throws InterruptedException {
    String host = "localhost";
    int port = 36307;
    if (args.length > 0) {
      port = Integer.parseInt(args[0]);
    }
    logger.atInfo().log("Using host: %s, port: %d", host, port);

    Map<String, Any> startMessage = new HashMap<>();
    startMessage.put(
        "dut", Any.pack(Dut.newBuilder().addPools("pool2").addPools("another_pool").build()));
    startMessage.put("board", Any.pack(Value.newBuilder().setStringValue("brya").build()));
    startMessage.put(
        "model", Any.pack(Value.newBuilder().setStringValue("some-brya-model").build()));

    try (GenericServiceManager serviceManager = new GenericServiceManager()) {
      serviceManager.connect(host, port);
      serviceManager.start(startMessage);
      logger.atInfo().log("Service started.");

      Map<String, Any> stopMessage = new HashMap<>();
      serviceManager.stop(stopMessage);
      logger.atInfo().log("Service stopped.");
    }
  }
}
