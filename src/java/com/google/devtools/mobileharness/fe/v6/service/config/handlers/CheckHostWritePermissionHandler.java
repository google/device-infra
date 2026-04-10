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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigConverter;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the CheckHostWritePermission RPC. */
@Singleton
public final class CheckHostWritePermissionHandler {

  private final ConfigurationProvider configurationProvider;
  private final GroupMembershipProvider groupMembershipProvider;
  private final ListeningExecutorService executor;

  @Inject
  CheckHostWritePermissionHandler(
      ConfigurationProvider configurationProvider,
      GroupMembershipProvider groupMembershipProvider,
      ListeningExecutorService executor) {
    this.configurationProvider = configurationProvider;
    this.groupMembershipProvider = groupMembershipProvider;
    this.executor = executor;
  }

  public ListenableFuture<CheckHostWritePermissionResponse> checkHostWritePermission(
      String hostName, UniverseScope universe, Optional<String> username) {
    if (username.isEmpty()) {
      return immediateFuture(
          CheckHostWritePermissionResponse.newBuilder().setHasPermission(false).build());
    }
    String user = username.get();

    return Futures.transformAsync(
        configurationProvider.getLabConfig(hostName, universe),
        (Optional<LabConfig> labConfig) -> {
          if (labConfig.isEmpty()) {
            return immediateFuture(
                CheckHostWritePermissionResponse.newBuilder().setHasPermission(false).build());
          }

          List<String> hostAdmins =
              ConfigConverter.toFeHostConfig(labConfig.get()).getPermissions().getHostAdminsList();
          if (hostAdmins.contains(user)) {
            return immediateFuture(
                CheckHostWritePermissionResponse.newBuilder().setHasPermission(true).build());
          }

          // hostAdmins is stored as the owner in the default device config in labConfig, so we can
          // use the owner check.
          if (ConfigUtil.isOwnerEmptyOrDefault(hostAdmins)) {
            return immediateFuture(
                CheckHostWritePermissionResponse.newBuilder().setHasPermission(true).build());
          }

          return Futures.transform(
              groupMembershipProvider.isMemberOfAny(user, hostAdmins),
              (Boolean isMember) ->
                  CheckHostWritePermissionResponse.newBuilder().setHasPermission(isMember).build(),
              executor);
        },
        executor);
  }
}
