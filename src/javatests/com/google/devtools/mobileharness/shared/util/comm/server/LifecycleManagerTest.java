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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager.ALLOW_PROCESS_AFTER_SHUTDOWN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.shared.util.comm.server.LifecycleManager.LabeledServer;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LifecycleManagerTest {

  private static final ServiceDescriptor SERVICE_A_1 = new ServiceDescriptor("service-a-1");
  private static final ServiceDescriptor SERVICE_A_2 = new ServiceDescriptor("service-a-2");
  private static final ServiceDescriptor SERVICE_B_1 = new ServiceDescriptor("service-b-1");
  private static final ServiceDescriptor SERVICE_B_2 = new ServiceDescriptor("service-b-2");
  public static final String LABEL_A = "labelA";
  public static final String LABEL_B = "labelB";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Server serverA;
  @Mock private Server serverB;
  private LifecycleManager manager;
  private final ListeningExecutorService executorService =
      listeningDecorator(Executors.newFixedThreadPool(1));

  @Before
  public void setUp() throws Exception {
    when(serverA.getServices())
        .thenReturn(
            ImmutableList.of(
                ServerServiceDefinition.builder(SERVICE_A_1).build(),
                ServerServiceDefinition.builder(SERVICE_A_2).build()));
    when(serverB.getServices())
        .thenReturn(
            ImmutableList.of(
                ServerServiceDefinition.builder(SERVICE_B_1).build(),
                ServerServiceDefinition.builder(SERVICE_B_2).build()));
    when(serverA.start()).thenReturn(serverA);
    when(serverB.start()).thenReturn(serverB);
    manager =
        new LifecycleManager(
            executorService,
            ImmutableList.of(
                LabeledServer.create(serverA, LABEL_A), LabeledServer.create(serverB, LABEL_B)));
  }

  @After
  public void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  public void start_inOrder() throws Exception {
    manager.start();

    InOrder inOrder = inOrder(serverA, serverB);
    inOrder.verify(serverA).start();
    inOrder.verify(serverB).start();
  }

  @Test
  public void start_shutDownWhenThrowException() throws Exception {
    when(serverB.start()).thenThrow(new IOException());

    assertThrows(UncheckedIOException.class, () -> manager.start());
    verify(serverA).shutdownNow();
  }

  @Test
  public void shutDown_inReverseOrder() throws Exception {
    when(serverA.awaitTermination(anyLong(), any())).thenReturn(true);
    when(serverB.awaitTermination(anyLong(), any())).thenReturn(false);

    manager.start();
    manager.shutdown();
    executorService.shutdown();
    assertThat(executorService.awaitTermination(ALLOW_PROCESS_AFTER_SHUTDOWN.toSeconds(), SECONDS))
        .isTrue();

    InOrder inOrder = inOrder(serverA, serverB);
    inOrder.verify(serverB).shutdown();
    inOrder.verify(serverA).shutdown();
    verify(serverB).shutdownNow();
    verify(serverA, never()).shutdownNow();
  }
}
