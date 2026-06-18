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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.fe.v6.service.shared.auth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DevicePermissionCheckerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private GroupMembershipProvider groupMembershipProvider;

  private final ListeningExecutorService executor = MoreExecutors.newDirectExecutorService();
  private DevicePermissionChecker checker;

  @Before
  public void setUp() {
    checker = new DevicePermissionChecker(groupMembershipProvider, executor);
  }

  @Test
  public void hasPermission_emptyUser_returnsFalse() throws Exception {
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().build();
    assertThat(checker.hasPermission("", deviceInfo).get()).isFalse();
  }

  @Test
  public void hasPermission_noOwnersOrExecutors_returnsTrue() throws Exception {
    DeviceInfo deviceInfo = DeviceInfo.newBuilder().build();
    assertThat(checker.hasPermission("user", deviceInfo).get()).isTrue();
  }

  @Test
  public void hasPermission_userIsOwner_returnsTrue() throws Exception {
    DeviceInfo deviceInfo = createDeviceInfo(ImmutableList.of("user"), ImmutableList.of());
    assertThat(checker.hasPermission("user", deviceInfo).get()).isTrue();
  }

  @Test
  public void hasPermission_userIsExecutor_returnsTrue() throws Exception {
    DeviceInfo deviceInfo = createDeviceInfo(ImmutableList.of(), ImmutableList.of("user"));
    assertThat(checker.hasPermission("user", deviceInfo).get()).isTrue();
  }

  @Test
  public void hasPermission_userInOwnerGroup_returnsTrue() throws Exception {
    DeviceInfo deviceInfo = createDeviceInfo(ImmutableList.of("owner_group"), ImmutableList.of());
    when(groupMembershipProvider.isMemberOfAny("user", ImmutableList.of("owner_group")))
        .thenReturn(immediateFuture(true));

    assertThat(checker.hasPermission("user", deviceInfo).get()).isTrue();
  }

  @Test
  public void hasPermission_userInExecutorGroup_returnsTrue() throws Exception {
    DeviceInfo deviceInfo =
        createDeviceInfo(ImmutableList.of(), ImmutableList.of("executor_group"));
    when(groupMembershipProvider.isMemberOfAny("user", ImmutableList.of("executor_group")))
        .thenReturn(immediateFuture(true));

    assertThat(checker.hasPermission("user", deviceInfo).get()).isTrue();
  }

  @Test
  public void hasPermission_userNotInAnyGroup_returnsFalse() throws Exception {
    DeviceInfo deviceInfo =
        createDeviceInfo(ImmutableList.of("owner_group"), ImmutableList.of("executor_group"));
    when(groupMembershipProvider.isMemberOfAny("user", ImmutableList.of("owner_group")))
        .thenReturn(immediateFuture(false));
    when(groupMembershipProvider.isMemberOfAny("user", ImmutableList.of("executor_group")))
        .thenReturn(immediateFuture(false));

    assertThat(checker.hasPermission("user", deviceInfo).get()).isFalse();
  }

  private DeviceInfo createDeviceInfo(List<String> owners, List<String> executors) {
    return DeviceInfo.newBuilder()
        .setDeviceFeature(DeviceFeature.newBuilder().addAllOwner(owners).addAllExecutor(executors))
        .build();
  }
}
