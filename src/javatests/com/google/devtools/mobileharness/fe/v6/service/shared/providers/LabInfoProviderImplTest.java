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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.proto.common.RoutedUniverse;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.SelfUniverse;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.Universe;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LabInfoProviderImplTest {

  @Rule(order = 0)
  public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private RoutedUniverseLabInfoStubFactory routedUniverseLabInfoStubFactory;
  @Mock private LabInfoStub defaultLabInfoStub;
  @Mock private LabInfoStub atsLabInfoStub;

  private LabInfoProviderImpl labInfoProvider;

  @Before
  public void setUp() {
    labInfoProvider = new LabInfoProviderImpl(defaultLabInfoStub, routedUniverseLabInfoStubFactory);
  }

  private static final Universe SELF_UNIVERSE =
      Universe.newBuilder().setSelfUniverse(SelfUniverse.getDefaultInstance()).build();

  @Test
  public void getLabInfoAsync_selfUniverse_success() throws Exception {
    GetLabInfoRequest request = GetLabInfoRequest.getDefaultInstance();
    GetLabInfoResponse expectedResponse = GetLabInfoResponse.getDefaultInstance();
    when(defaultLabInfoStub.getLabInfoAsync(request)).thenReturn(immediateFuture(expectedResponse));

    assertThat(labInfoProvider.getLabInfoAsync(request, SELF_UNIVERSE).get())
        .isEqualTo(expectedResponse);
  }

  @Test
  public void getLabInfoAsync_routedUniverse_success() throws Exception {
    GetLabInfoRequest request = GetLabInfoRequest.getDefaultInstance();
    GetLabInfoResponse expectedResponse = GetLabInfoResponse.getDefaultInstance();
    String atsControllerId = "ats_id";
    Universe routedUniverse =
        Universe.newBuilder()
            .setRoutedUniverse(RoutedUniverse.newBuilder().setAtsControllerId(atsControllerId))
            .build();

    when(routedUniverseLabInfoStubFactory.getLabInfoStub(routedUniverse.getRoutedUniverse()))
        .thenReturn(Optional.of(atsLabInfoStub));
    when(atsLabInfoStub.getLabInfoAsync(request)).thenReturn(immediateFuture(expectedResponse));

    assertThat(labInfoProvider.getLabInfoAsync(request, routedUniverse).get())
        .isEqualTo(expectedResponse);
  }
}
