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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import chromiumos.test.api.GenericServiceGrpc;
import chromiumos.test.api.GenericStartRequest;
import chromiumos.test.api.GenericStartResponse;
import chromiumos.test.api.GenericStopRequest;
import chromiumos.test.api.GenericStopResponse;
import com.google.protobuf.Any;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GenericServiceManager}. */
@RunWith(JUnit4.class)
public class GenericServiceManagerTest {

  private Server server;
  private int port;
  private GenericServiceManager serviceManager;

  private final GenericServiceGrpc.GenericServiceImplBase serviceImpl =
      new GenericServiceGrpc.GenericServiceImplBase() {
        @Override
        public void start(
            GenericStartRequest request, StreamObserver<GenericStartResponse> responseObserver) {
          if (request.getMessage().getValuesMap().containsKey("error")) {
            responseObserver.onError(
                Status.INTERNAL.withDescription("Test start error").asRuntimeException());
          } else {
            responseObserver.onNext(GenericStartResponse.getDefaultInstance());
            responseObserver.onCompleted();
          }
        }

        @Override
        public void stop(
            GenericStopRequest request, StreamObserver<GenericStopResponse> responseObserver) {
          if (request.getMessage().getValuesMap().containsKey("error")) {
            responseObserver.onError(
                Status.INTERNAL.withDescription("Test stop error").asRuntimeException());
          } else {
            responseObserver.onNext(GenericStopResponse.getDefaultInstance());
            responseObserver.onCompleted();
          }
        }
      };

  @Before
  public void setUp() throws IOException {
    server = ServerBuilder.forPort(0).addService(serviceImpl).build().start();
    port = server.getPort();
    serviceManager = new GenericServiceManager();
    serviceManager.connect("localhost", port);
  }

  @After
  public void tearDown() throws InterruptedException {
    serviceManager.shutdown();
    server.shutdownNow();
    server.awaitTermination();
  }

  @Test
  public void start_success() throws Exception {
    Map<String, Any> message = new HashMap<>();
    serviceManager.start(message);
  }

  @Test
  public void start_error() {
    Map<String, Any> message = new HashMap<>();
    message.put("error", Any.getDefaultInstance());
    StatusRuntimeException e =
        assertThrows(StatusRuntimeException.class, () -> serviceManager.start(message));
    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
  }

  @Test
  public void start_unavailable_thenSuccess() throws Exception {
    server.shutdownNow();
    server.awaitTermination();

    Thread serverThread =
        new Thread(
            () -> {
              try {
                Thread.sleep(150); // Start server after a delay
                server = ServerBuilder.forPort(port).addService(serviceImpl).build().start();
              } catch (IOException | InterruptedException e) {
                // ignore
              }
            });
    serverThread.start();

    Map<String, Any> message = new HashMap<>();
    serviceManager.start(message);
    serverThread.join();
  }

  @Test
  public void stop_success() throws Exception {
    Map<String, Any> message = new HashMap<>();
    serviceManager.stop(message);
  }

  @Test
  public void stop_error() {
    Map<String, Any> message = new HashMap<>();
    message.put("error", Any.getDefaultInstance());
    StatusRuntimeException e =
        assertThrows(StatusRuntimeException.class, () -> serviceManager.stop(message));
    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
  }
}
