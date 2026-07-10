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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DecommissionHostHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabSyncStub labSyncStub;
  @Bind @Mock private Environment environment;

  @Inject private DecommissionHostHandler handler;

  @Before
  public void setUp() {
    when(environment.isAts()).thenReturn(false);

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(labSyncStub.removeMissingHost(any(), eq(true)))
        .thenReturn(immediateFuture(RemoveMissingHostResponse.getDefaultInstance()));
  }

  @Test
  public void decommissionHost_selfUniverse_callsMaster() throws Exception {
    DecommissionHostRequest request =
        DecommissionHostRequest.newBuilder().setHostName("test_host").build();

    DecommissionHostResponse response = handler.decommissionHost(request, UniverseScope.SELF).get();

    assertThat(response).isEqualToDefaultInstance();
    verify(labSyncStub)
        .removeMissingHost(
            RemoveMissingHostRequest.newBuilder().setLabHostName("test_host").build(),
            /* useClientRpcAuthority= */ true);
  }

  @Test
  public void decommissionHost_routedUniverse_fails() throws Exception {
    DecommissionHostRequest request =
        DecommissionHostRequest.newBuilder().setHostName("test_host").build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                handler
                    .decommissionHost(request, new UniverseScope.RoutedUniverse("other_controller"))
                    .get());

    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(labSyncStub);
  }

  @Test
  public void decommissionHost_atsEnvironment_fails() throws Exception {
    when(environment.isAts()).thenReturn(true);

    DecommissionHostRequest request =
        DecommissionHostRequest.newBuilder().setHostName("test_host").build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> handler.decommissionHost(request, UniverseScope.SELF).get());

    assertThat(e).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    verifyNoInteractions(labSyncStub);
  }
}
