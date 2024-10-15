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

package com.google.devtools.mobileharness.shared.util.comm.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.comm.testing.DummyClient.toNames;
import static com.google.devtools.mobileharness.shared.util.comm.testing.DummyClient.toRequests;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyRequest;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyResponse;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DummyClientTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  Server dummyServer;
  DummyClient dummyClient;
  ExecutorService clientExecutor = Executors.newWorkStealingPool(2);

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    dummyServer =
        grpcCleanup.register(
            InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new DummyServiceImpl())
                .build()
                .start());
    Channel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    dummyClient = new DummyClient(channel, clientExecutor);
  }

  @After
  public void tearDown() {
    clientExecutor.shutdownNow();
  }

  @Test
  public void blockingUnaryMethod_expectedResult() {
    DummyRequest request = DummyRequest.newBuilder().setName("Foo").build();

    DummyResponse response = dummyClient.blockingUnaryMethod(request);

    assertThat(response.getName()).isEqualTo("Hello, Foo.");
  }

  @Test
  public void blockingServerStreamingMethod_expectedResult() {
    DummyRequest request = DummyRequest.newBuilder().setName("Foo").build();

    ImmutableList<DummyResponse> responses = dummyClient.blockingServerStreamingMethod(request);

    assertThat(toNames(responses)).containsExactly("Hello, Foo.", "Hello, Foo.", "Hello, Foo.");
  }

  @Test
  public void asyncUnaryMethod_expectedResult() throws Exception {
    DummyRequest request = DummyRequest.newBuilder().setName("Foo").build();

    DummyResponse response = dummyClient.asyncUnaryMethod(request).waitAndGetOnlyResponse();

    assertThat(response.getName()).isEqualTo("Hello, Foo.");
  }

  @Test
  public void asyncServerStreamingMethod_expectedResult() throws Exception {
    DummyRequest request = DummyRequest.newBuilder().setName("Foo").build();

    ImmutableList<DummyResponse> responses =
        dummyClient.asyncServerStreamingMethod(request).waitAndGetResponses();

    assertThat(toNames(responses)).containsExactly("Hello, Foo.", "Hello, Foo.", "Hello, Foo.");
  }

  @Test
  public void clientStreamingMethod_expectedResult() throws Exception {
    ImmutableList<DummyRequest> requests = toRequests(ImmutableList.of("Foo", "Bar", "Baz"));

    DummyResponse response = dummyClient.clientStreamingMethod(requests).waitAndGetOnlyResponse();

    assertThat(response.getName()).isEqualTo("Hello, Foo and Bar and Baz.");
  }

  @Test
  public void bidiStreamingMethod_expectedResult() throws Exception {
    ImmutableList<DummyRequest> requests = toRequests(ImmutableList.of("Foo", "Bar", "Baz"));

    ImmutableList<DummyResponse> responses =
        dummyClient.bidiStreamingMethod(requests).waitAndGetResponses();

    assertThat(toNames(responses))
        .containsExactly("Hello, Foo.", "Hello, Foo and Bar.", "Hello, Foo and Bar and Baz.");
  }
}
