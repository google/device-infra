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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
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
public final class CheckHostWritePermissionHandlerTest {

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private CheckHostWritePermissionHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void checkHostWritePermission_noUser_returnsFalse() throws Exception {
    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission("host", SELF_UNIVERSE, Optional.empty()).get();

    assertThat(response.getHasPermission()).isFalse();
  }

  @Test
  public void checkHostWritePermission_hasPermission_returnsTrue() throws Exception {
    when(iamPermissionChecker.canConfigHost("host", SELF_UNIVERSE))
        .thenReturn(immediateFuture(true));

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission("host", SELF_UNIVERSE, Optional.of("admin1")).get();

    assertThat(response.getHasPermission()).isTrue();
    assertThat(response.getUserName()).isEqualTo("admin1");
  }

  @Test
  public void checkHostWritePermission_noPermission_returnsFalse() throws Exception {
    when(iamPermissionChecker.canConfigHost("host", SELF_UNIVERSE))
        .thenReturn(immediateFuture(false));

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission("host", SELF_UNIVERSE, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isFalse();
    assertThat(response.getUserName()).isEqualTo("user");
  }
}
