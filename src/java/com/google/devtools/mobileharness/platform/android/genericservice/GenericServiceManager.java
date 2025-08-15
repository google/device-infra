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

/** A static helper class to manage the lifecycle of a GenericService gRPC service. */
public final class GenericServiceManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long GRPC_RETRY_INTERVAL_SECONDS = 1;
  private static final Duration GRPC_START_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration GRPC_STOP_TIMEOUT = Duration.ofSeconds(60);

  // Private constructor to prevent instantiation
  private GenericServiceManager() {}

  /**
   * Starts the GenericService and waits for the operation to complete.
   *
   * @param host The host of the gRPC server.
   * @param port The port of the gRPC server.
   * @param message A map of key-value pairs to send with the start request.
   * @throws InterruptedException If the thread is interrupted while waiting for the response.
   * @throws StatusRuntimeException If there is an error during the gRPC call.
   */
  public static void start(String host, int port, Map<String, Any> message)
      throws InterruptedException {
    String serviceAddress = String.format("%s:%d", host, port);
    logger.atInfo().log("Starting Generic Service at: %s", serviceAddress);

    GenericStartRequest request =
        GenericStartRequest.newBuilder()
            .setMessage(GenericMessage.newBuilder().putAllValues(message).build())
            .build();

    long deadline = InstantSource.system().instant().plus(GRPC_START_TIMEOUT).toEpochMilli();
    StatusRuntimeException lastException = null;

    ManagedChannel channel = null;
    try {
      channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      Instant now = InstantSource.system().instant();
      while (now.toEpochMilli() < deadline) {
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
              serviceAddress, GRPC_RETRY_INTERVAL_SECONDS);
        }
        SECONDS.sleep(GRPC_RETRY_INTERVAL_SECONDS);
      }
    } finally {
      channel.shutdown().awaitTermination(5, SECONDS);
    }

    if (lastException != null) {
      throw lastException;
    }
    throw new StatusRuntimeException(
        Status.DEADLINE_EXCEEDED.withDescription(
            "Failed to connect to Generic Service at "
                + serviceAddress
                + " within "
                + GRPC_START_TIMEOUT
                + " seconds."));
  }

  /**
   * Stops the GenericService and waits for the operation to complete.
   *
   * @param host The host of the gRPC server.
   * @param port The port of the gRPC server.
   * @param message A map of key-value pairs to send with the stop request.
   * @throws InterruptedException If the thread is interrupted while waiting for the response.
   * @throws StatusRuntimeException If there is an error during the gRPC call.
   */
  public static void stop(String host, int port, Map<String, Any> message)
      throws InterruptedException {
    String serviceAddress = String.format("%s:%d", host, port);
    logger.atInfo().log("Stopping Generic Service at: %s", serviceAddress);

    GenericStopRequest request =
        GenericStopRequest.newBuilder()
            .setMessage(GenericMessage.newBuilder().putAllValues(message).build())
            .build();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    GenericServiceGrpc.GenericServiceBlockingStub blockingStub =
        GenericServiceGrpc.newBlockingStub(channel).withDeadlineAfter(GRPC_STOP_TIMEOUT);
    try {
      blockingStub.stop(request);
      logger.atInfo().log("Generic Service stopped successfully.");
    } finally {
      channel.shutdown().awaitTermination(5, SECONDS);
    }
  }

  // TODO: Remove this once the GenericService is integrated with MobileHarness.
  public static void main(String[] args) {
    String host = args.length > 0 ? args[0] : "localhost";
    int port = 39753;
    if (args.length > 1) {
      port = Integer.parseInt(args[1]);
    }
    logger.atInfo().log("Using host: %s, port: %d", host, port);

    Map<String, Any> startMessage = new HashMap<>();
    startMessage.put(
        "dut", Any.pack(Dut.newBuilder().addPools("pool2").addPools("another_pool").build()));
    startMessage.put("board", Any.pack(Value.newBuilder().setStringValue("brya").build()));
    startMessage.put(
        "model", Any.pack(Value.newBuilder().setStringValue("some-brya-model").build()));

    try {
      GenericServiceManager.start(host, port, startMessage);
      logger.atInfo().log("Service started.");

      Map<String, Any> stopMessage = new HashMap<>();
      GenericServiceManager.stop(host, port, stopMessage);
      logger.atInfo().log("Service stopped.");
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      logger.atSevere().withCause(e).log("Error during service lifecycle");
    }
  }
}
