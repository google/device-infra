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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
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

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private CheckHostWritePermissionHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void checkHostWritePermission_noUser_returnsFalse() throws Exception {
    CheckHostWritePermissionRequest request =
        CheckHostWritePermissionRequest.newBuilder().setHostName("host").build();

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission(request, Optional.empty()).get();

    assertThat(response.getHasPermission()).isFalse();
  }

  @Test
  public void checkHostWritePermission_noLabConfig_returnsFalse() throws Exception {
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.empty()));

    CheckHostWritePermissionRequest request =
        CheckHostWritePermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isFalse();
  }

  @Test
  public void checkHostWritePermission_userInAdmins_returnsTrue() throws Exception {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder().addOwner("admin1").addOwner("admin2").build())
            .build();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    CheckHostWritePermissionRequest request =
        CheckHostWritePermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission(request, Optional.of("admin1")).get();

    assertThat(response.getHasPermission()).isTrue();
  }

  @Test
  public void checkHostWritePermission_noAdmins_returnsTrue() throws Exception {
    LabConfig labConfig = LabConfig.getDefaultInstance();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    CheckHostWritePermissionRequest request =
        CheckHostWritePermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
  }

  @Test
  public void checkHostWritePermission_userInGroup_returnsTrue() throws Exception {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("group1").build())
            .build();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));
    when(groupMembershipProvider.isMemberOfAny(eq("user"), any()))
        .thenReturn(immediateFuture(true));

    CheckHostWritePermissionRequest request =
        CheckHostWritePermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    CheckHostWritePermissionResponse response =
        handler.checkHostWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
  }
}
