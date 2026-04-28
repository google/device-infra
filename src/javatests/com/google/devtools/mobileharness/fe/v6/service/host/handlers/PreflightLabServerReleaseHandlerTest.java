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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
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
public final class PreflightLabServerReleaseHandlerTest {

  private static final String USERNAME = "test-user";
  private static final String HOST_NAME = "test-host";
  private static final PreflightLabServerReleaseRequest REQUEST =
      PreflightLabServerReleaseRequest.newBuilder().setHostName(HOST_NAME).build();
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private PreflightLabServerReleaseActionHelper actionHelper;

  @Inject private PreflightLabServerReleaseHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void preflight_delegatesToActionHelper() throws Exception {
    PreflightLabServerReleaseResponse expectedResponse =
        PreflightLabServerReleaseResponse.newBuilder()
            .setReady(PreflightLabServerReleaseResponse.ReleaseReady.getDefaultInstance())
            .build();

    when(actionHelper.preflightLabServerRelease(
            eq(REQUEST), eq(UNIVERSE), eq(Optional.of(USERNAME))))
        .thenReturn(immediateFuture(expectedResponse));

    ListenableFuture<PreflightLabServerReleaseResponse> resultFuture =
        handler.preflightLabServerRelease(REQUEST, UNIVERSE, Optional.of(USERNAME));

    PreflightLabServerReleaseResponse response = Futures.getDone(resultFuture);
    assertThat(response).isEqualTo(expectedResponse);
  }
}
