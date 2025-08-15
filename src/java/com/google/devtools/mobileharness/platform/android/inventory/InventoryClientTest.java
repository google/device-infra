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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import chromiumos.test.lab.api.Dut;
import chromiumos.test.lab.api.DutTopology;
import chromiumos.test.lab.api.GetDutTopologyRequest;
import chromiumos.test.lab.api.GetDutTopologyResponse;
import chromiumos.test.lab.api.InventoryServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link InventoryClient}. */
@RunWith(JUnit4.class)
public class InventoryClientTest {

  private InventoryClient client;
  private Server server;

  private final InventoryServiceGrpc.InventoryServiceImplBase serviceImpl =
      new InventoryServiceGrpc.InventoryServiceImplBase() {
        @Override
        public void getDutTopology(
            GetDutTopologyRequest request,
            StreamObserver<GetDutTopologyResponse> responseObserver) {
          if (request.getId().getValue().equals("test_dut_success")) {
            Dut dut = Dut.newBuilder().setId(Dut.Id.newBuilder().setValue("dut1")).build();
            DutTopology topology = DutTopology.newBuilder().addDuts(dut).build();
            GetDutTopologyResponse response =
                GetDutTopologyResponse.newBuilder()
                    .setSuccess(
                        GetDutTopologyResponse.Success.newBuilder().setDutTopology(topology))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          } else if (request.getId().getValue().equals("test_dut_empty")) {
            DutTopology topology = DutTopology.getDefaultInstance();
            GetDutTopologyResponse response =
                GetDutTopologyResponse.newBuilder()
                    .setSuccess(
                        GetDutTopologyResponse.Success.newBuilder().setDutTopology(topology))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
          } else if (request.getId().getValue().equals("test_dut_error")) {
            responseObserver.onError(
                Status.INTERNAL.withDescription("Test error").asRuntimeException());
          }
        }
      };

  @Before
  public void setUp() throws IOException {
    server = ServerBuilder.forPort(0).addService(serviceImpl).build().start();
    client = new InventoryClient();
    client.connect("localhost", server.getPort());
  }

  @After
  public void tearDown() throws InterruptedException {
    client.close();
    server.shutdownNow();
    server.awaitTermination();
  }

  @Test
  public void getDuts_success() throws Exception {
    Dut expectedDut = Dut.newBuilder().setId(Dut.Id.newBuilder().setValue("dut1")).build();
    assertThat(client.getDuts("test_dut_success")).containsExactly(expectedDut);
  }

  @Test
  public void getDuts_emptyResult() throws Exception {
    assertThat(client.getDuts("test_dut_empty")).isNull();
  }

  @Test
  public void getDuts_serverError() throws Exception {
    assertThat(client.getDuts("test_dut_error")).isNull();
  }

  @Test
  public void getDuts_notConnected() {
    InventoryClient newClient = new InventoryClient();
    assertThrows(IllegalStateException.class, () -> newClient.getDuts("any_id"));
  }

  @Test
  public void connect_invalidPort_getDutsReturnsNull() throws Exception {
    InventoryClient newClient = new InventoryClient();
    newClient.connect("localhost", 1); // Use a port that is unlikely to be open.
    assertThat(newClient.getDuts("any_id")).isNull();
    newClient.close();
  }
}
