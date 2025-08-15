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

package com.google.devtools.mobileharness.platform.android.inventory;

import chromiumos.test.lab.api.Dut;
import chromiumos.test.lab.api.DutTopology;
import chromiumos.test.lab.api.GetDutTopologyRequest;
import chromiumos.test.lab.api.GetDutTopologyResponse;
import chromiumos.test.lab.api.InventoryServiceGrpc;
import chromiumos.test.lab.api.InventoryServiceGrpc.InventoryServiceStub;
import com.google.common.flogger.FluentLogger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client for interacting with the InventoryService.
 *
 * <p>This client provides methods to connect to the InventoryService and make gRPC calls, such as
 * getting DutTopology information.
 *
 * <p>This class is not thread-safe.
 */
public class InventoryClient {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ManagedChannel channel;
  private InventoryServiceStub asyncStub;

  public void connect(String serverHost, int serverPort) {
    channel = ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build();
    asyncStub = InventoryServiceGrpc.newStub(channel);
  }

  /**
   * Shuts down the gRPC channel.
   *
   * <p>This method is used to clean up the gRPC channel after the client is no longer needed.
   */
  public void shutdown() {
    // Clean can be called multiple times so we need to check if it is already shutdown.
    if (channel != null) {
      try {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        channel = null;
        asyncStub = null;
      }
    }
  }

  /**
   * Returns all DUTs associated with the given {@code dutName}.
   *
   * @param dutName The name of the DUT, which is typically the hostname extracted from a device ID
   *     in the format "hostname:port".
   * @return A list of {@link Dut} instances. Returns {@code null} if the topology could not be
   *     retrieved or if no DUTs are found.
   * @throws InterruptedException if the gRPC call is interrupted. Errors during the gRPC stream
   *     (e.g., network issues, server errors) are logged to stderr, and this method will return
   *     {@code null}.
   */
  public List<Dut> getDuts(String dutName) throws InterruptedException {
    GetDutTopologyRequest request =
        GetDutTopologyRequest.newBuilder()
            .setId(DutTopology.Id.newBuilder().setValue(dutName))
            .build();

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<List<Dut>> dutsRef = new AtomicReference<>();
    StreamObserver<GetDutTopologyResponse> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(GetDutTopologyResponse response) {
            if (response.hasSuccess()) {
              DutTopology dutTopology = response.getSuccess().getDutTopology();
              List<Dut> duts = dutTopology.getDutsList();
              if (duts.isEmpty()) {
                logger.atWarning().log("No DUTs found for %s", dutName);
              } else {
                dutsRef.set(duts);
              }
            } else {
              logger.atWarning().log("Failed to get DUT topology: %s", response.getFailure());
            }
          }

          @Override
          public void onError(Throwable t) {
            logger.atWarning().withCause(t).log("Failed to get DUT topology for %s", dutName);
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            logger.atInfo().log("Successfully retrieved DUT topology for %s", dutName);
            latch.countDown();
          }
        };

    asyncStub.withDeadlineAfter(10, TimeUnit.SECONDS).getDutTopology(request, responseObserver);
    latch.await();
    return dutsRef.get();
  }

  // TODO: Remove this once the InventoryService is integrated with MobileHarness.
  public static void main(String[] args) throws InterruptedException {
    InventoryClient client = new InventoryClient();
    String host = "localhost"; // Replace with your server host
    int port = 8080; // Replace with your server port
    String dutTopologyId = "my_dut_id"; // Replace with the desired id

    try {
      client.connect(host, port);
      List<Dut> duts = client.getDuts(dutTopologyId);
      if (duts != null && !duts.isEmpty()) {
        logger.atInfo().log("Successfully retrieved %d DUTs: %s", duts.size(), duts);
      } else {
        logger.atWarning().log("Could not retrieve DUTs for id: %s", dutTopologyId);
      }
    } finally {
      client.shutdown();
    }
  }
}
