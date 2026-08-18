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
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the CheckDeviceConfigPermission RPC. */
@Singleton
public final class CheckDeviceConfigPermissionHandler {

  private final IamPermissionChecker iamPermissionChecker;
  private final ListeningExecutorService executor;

  @Inject
  CheckDeviceConfigPermissionHandler(
      IamPermissionChecker iamPermissionChecker, ListeningExecutorService executor) {
    this.iamPermissionChecker = iamPermissionChecker;
    this.executor = executor;
  }

  public ListenableFuture<CheckDeviceConfigPermissionResponse> checkDeviceConfigPermission(
      String deviceId, UniverseScope universe, Optional<String> username) {
    if (username.isEmpty()) {
      return immediateFuture(
          CheckDeviceConfigPermissionResponse.newBuilder().setHasPermission(false).build());
    }
    String user = username.get();

    return Futures.transform(
        iamPermissionChecker.canConfigDevice(deviceId, universe),
        hasPermission ->
            CheckDeviceConfigPermissionResponse.newBuilder()
                .setHasPermission(hasPermission)
                .setUserName(user)
                .build(),
        executor);
  }
}
