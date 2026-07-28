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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.transformAsync;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.DeviceAccessResolver;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Authorizer for device action eligibility and permissions. */
@Singleton
public final class DeviceActionAuthorizer {
  private final DeviceAccessResolver deviceAccessResolver;
  private final LabInfoProvider labInfoProvider;
  private final ListeningExecutorService executor;

  @Inject
  DeviceActionAuthorizer(
      DeviceAccessResolver deviceAccessResolver,
      LabInfoProvider labInfoProvider,
      ListeningExecutorService executor) {
    this.deviceAccessResolver = deviceAccessResolver;
    this.labInfoProvider = labInfoProvider;
    this.executor = executor;
  }

  /**
   * Authorizes the user, checks permission, and runs the action if authorized.
   *
   * @param deviceId The ID of the target device.
   * @param username The authenticated username, if present.
   * @param universe The scope of the request.
   * @param actionNameForErrorMessage The action name to use in error messages.
   * @param actionSupplier The supplier that returns the future representing the action execution.
   */
  public <T> ListenableFuture<T> authorizeAndRun(
      String deviceId,
      Optional<String> username,
      UniverseScope universe,
      String actionNameForErrorMessage,
      Supplier<ListenableFuture<T>> actionSupplier) {
    if (username.isEmpty()) {
      return immediateFailedFuture(FeServiceException.unauthenticated("User identity not found."));
    }

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        DeviceInfoLookupHelper.lookUpDeviceInfoAsync(labInfoProvider, deviceId, universe, executor);

    return transformAsync(
        deviceInfoFuture,
        deviceInfo ->
            transformAsync(
                deviceAccessResolver.hasExecutePermission(username.get(), deviceInfo),
                hasPermission -> {
                  if (!hasPermission) {
                    return immediateFailedFuture(
                        FeServiceException.permissionDenied(
                            String.format(
                                "User %s does not have permission to %s of device %s.",
                                username.get(), actionNameForErrorMessage, deviceId)));
                  }
                  return actionSupplier.get();
                },
                directExecutor()),
        executor);
  }
}
