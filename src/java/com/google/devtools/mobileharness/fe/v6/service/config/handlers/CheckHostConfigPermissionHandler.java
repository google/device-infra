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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the CheckHostConfigPermission RPC. */
@Singleton
public final class CheckHostConfigPermissionHandler {

  private final IamPermissionChecker iamPermissionChecker;
  private final ListeningExecutorService executor;

  @Inject
  CheckHostConfigPermissionHandler(
      IamPermissionChecker iamPermissionChecker, ListeningExecutorService executor) {
    this.iamPermissionChecker = iamPermissionChecker;
    this.executor = executor;
  }

  /**
   * Checks whether the current user has permission to configure the host.
   *
   * @param hostName the host name
   * @param universe the universe scope
   * @param username the authenticated caller username from the request context, used to populate
   *     the response and reject unauthenticated requests. Cloud IAM itself evaluates ambient caller
   *     credentials (ValidatedSecurityContext).
   */
  public ListenableFuture<CheckHostConfigPermissionResponse> checkHostConfigPermission(
      String hostName, UniverseScope universe, Optional<String> username) {
    if (username.isEmpty()) {
      return immediateFuture(
          CheckHostConfigPermissionResponse.newBuilder().setHasPermission(false).build());
    }
    String user = username.get();

    return Futures.transform(
        iamPermissionChecker.canConfigHost(hostName, universe),
        hasPermission ->
            CheckHostConfigPermissionResponse.newBuilder()
                .setHasPermission(hasPermission)
                .setUserName(user)
                .build(),
        executor);
  }
}
