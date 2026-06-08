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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.shared.util.comm.dualconduit.client.DualConduitClient;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishConduitResponse;
import com.google.devtools.mobileharness.shared.util.comm.dualconduit.proto.DualConduitProto.EstablishSessionResponse;
import io.grpc.Server;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ReverseProxiedServerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Server delegate;
  @Mock private DualConduitClient client;

  private ReverseProxiedServer proxyServer;

  @Before
  public void setUp() {
    proxyServer =
        new ReverseProxiedServer(
            delegate, client, "my-server", DualConduitProxyConfig.of("localhost", "backend"));
  }

  @Test
  public void start_success() throws Exception {
    when(client.establishReverseGrpcConduitSession(anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(
            EstablishSessionResponse.newBuilder()
                .setSessionId("fake-session-id")
                .addEstablishConduitResponses(
                    EstablishConduitResponse.newBuilder().setConduitId("fake-conduit-id").build())
                .build());
    when(delegate.start()).thenReturn(delegate);
    when(delegate.getPort()).thenReturn(50051);

    var unused = proxyServer.start();

    verify(delegate).start();
    verify(client).establishReverseGrpcConduitSession("my-server", "localhost", "backend:50051", 1);
  }

  @Test
  public void start_establishConduitFailed_shutdownDelegateAndTriggerCallbacks() throws Exception {
    when(delegate.start()).thenReturn(delegate);
    when(delegate.getPort()).thenReturn(50051);
    when(client.establishReverseGrpcConduitSession(anyString(), anyString(), anyString(), anyInt()))
        .thenThrow(new RuntimeException("Failed to establish session"));

    AtomicBoolean callbackCalled = new AtomicBoolean(false);

    assertThrows(
        IOException.class, () -> proxyServer.startWithTeardown(() -> callbackCalled.set(true)));

    verify(delegate).start();
    verify(delegate).shutdown();
    assertThat(callbackCalled.get()).isTrue();
  }
}
