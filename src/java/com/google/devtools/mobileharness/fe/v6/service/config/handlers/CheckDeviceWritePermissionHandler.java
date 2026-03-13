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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader.DeviceData;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the CheckDeviceWritePermission RPC. */
@Singleton
public final class CheckDeviceWritePermissionHandler {

  private final DeviceDataLoader deviceDataLoader;
  private final GroupMembershipProvider groupMembershipProvider;
  private final ListeningExecutorService executor;

  @Inject
  CheckDeviceWritePermissionHandler(
      DeviceDataLoader deviceDataLoader,
      GroupMembershipProvider groupMembershipProvider,
      ListeningExecutorService executor) {
    this.deviceDataLoader = deviceDataLoader;
    this.groupMembershipProvider = groupMembershipProvider;
    this.executor = executor;
  }

  public ListenableFuture<CheckDeviceWritePermissionResponse> checkDeviceWritePermission(
      CheckDeviceWritePermissionRequest request, Optional<String> username) {
    if (username.isEmpty()) {
      return immediateFuture(
          CheckDeviceWritePermissionResponse.newBuilder().setHasPermission(false).build());
    }
    String user = username.get();
    String deviceId = request.getId();
    String universe = request.getUniverse();

    return Futures.transformAsync(
        deviceDataLoader.loadDeviceData(deviceId, universe),
        (DeviceData deviceData) -> {
          DeviceConfig config = deviceData.effectiveDeviceConfig();
          if (!config.hasBasicConfig()) {
            return immediateFuture(
                CheckDeviceWritePermissionResponse.newBuilder().setHasPermission(false).build());
          }

          List<String> owners = config.getBasicConfig().getOwnerList();
          if (owners.isEmpty()) {
            return immediateFuture(
                CheckDeviceWritePermissionResponse.newBuilder().setHasPermission(true).build());
          }

          if (owners.contains(user)) {
            return immediateFuture(
                CheckDeviceWritePermissionResponse.newBuilder().setHasPermission(true).build());
          }

          List<String> groups = new ArrayList<>(owners);

          return Futures.transform(
              groupMembershipProvider.isMemberOfAny(user, groups),
              (Boolean isMember) ->
                  CheckDeviceWritePermissionResponse.newBuilder()
                      .setHasPermission(isMember)
                      .build(),
              executor);
        },
        executor);
  }
}
