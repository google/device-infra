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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.ManagementMode;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
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
public final class CheckDeviceWritePermissionHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private CheckDeviceWritePermissionHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void checkDeviceWritePermission_noUser_returnsFalse() throws Exception {
    CheckDeviceWritePermissionRequest request =
        CheckDeviceWritePermissionRequest.newBuilder().setId("device").build();

    CheckDeviceWritePermissionResponse response =
        handler.checkDeviceWritePermission(request, Optional.empty()).get();

    assertThat(response.getHasPermission()).isFalse();
  }

  @Test
  public void checkDeviceWritePermission_noBasicConfig_returnsFalse() throws Exception {
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            DeviceConfig.getDefaultInstance(),
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.empty());
    when(deviceDataLoader.loadDeviceData("device", "universe"))
        .thenReturn(immediateFuture(deviceData));

    CheckDeviceWritePermissionRequest request =
        CheckDeviceWritePermissionRequest.newBuilder()
            .setId("device")
            .setUniverse("universe")
            .build();

    CheckDeviceWritePermissionResponse response =
        handler.checkDeviceWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isFalse();
  }

  @Test
  public void checkDeviceWritePermission_userInOwnersOrExecutors_returnsTrue() throws Exception {
    DeviceConfig config =
        DeviceConfig.newBuilder()
            .setBasicConfig(
                BasicDeviceConfig.newBuilder().addOwner("owner1").addExecutor("executor1").build())
            .build();
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            config,
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.of(config));
    when(deviceDataLoader.loadDeviceData("device", "universe"))
        .thenReturn(immediateFuture(deviceData));

    CheckDeviceWritePermissionRequest request =
        CheckDeviceWritePermissionRequest.newBuilder()
            .setId("device")
            .setUniverse("universe")
            .build();

    assertThat(
            handler
                .checkDeviceWritePermission(request, Optional.of("owner1"))
                .get()
                .getHasPermission())
        .isTrue();
    assertThat(
            handler
                .checkDeviceWritePermission(request, Optional.of("executor1"))
                .get()
                .getHasPermission())
        .isTrue();
  }

  @Test
  public void checkDeviceWritePermission_noOwners_returnsTrue() throws Exception {
    DeviceConfig config =
        DeviceConfig.newBuilder().setBasicConfig(BasicDeviceConfig.getDefaultInstance()).build();
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            config,
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.of(config));
    when(deviceDataLoader.loadDeviceData("device", "universe"))
        .thenReturn(immediateFuture(deviceData));

    CheckDeviceWritePermissionRequest request =
        CheckDeviceWritePermissionRequest.newBuilder()
            .setId("device")
            .setUniverse("universe")
            .build();

    CheckDeviceWritePermissionResponse response =
        handler.checkDeviceWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
  }

  @Test
  public void checkDeviceWritePermission_userInGroup_returnsTrue() throws Exception {
    DeviceConfig config =
        DeviceConfig.newBuilder()
            .setBasicConfig(BasicDeviceConfig.newBuilder().addOwner("group1").build())
            .build();
    DeviceData deviceData =
        DeviceData.create(
            DeviceInfo.getDefaultInstance(),
            config,
            ManagementMode.PER_DEVICE,
            Optional.empty(),
            Optional.of(config));
    when(deviceDataLoader.loadDeviceData("device", "universe"))
        .thenReturn(immediateFuture(deviceData));
    when(groupMembershipProvider.isMemberOfAny(eq("user"), any()))
        .thenReturn(immediateFuture(true));

    CheckDeviceWritePermissionRequest request =
        CheckDeviceWritePermissionRequest.newBuilder()
            .setId("device")
            .setUniverse("universe")
            .build();

    CheckDeviceWritePermissionResponse response =
        handler.checkDeviceWritePermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
  }
}
