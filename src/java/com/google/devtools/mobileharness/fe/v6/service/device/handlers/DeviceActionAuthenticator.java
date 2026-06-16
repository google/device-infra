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

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.DevicePermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Authenticator for device action eligibility and permissions. */
@Singleton
public final class DeviceActionAuthenticator {
  private final DevicePermissionChecker devicePermissionChecker;
  private final LabInfoProvider labInfoProvider;
  private final ListeningExecutorService executor;

  @Inject
  DeviceActionAuthenticator(
      DevicePermissionChecker devicePermissionChecker,
      LabInfoProvider labInfoProvider,
      ListeningExecutorService executor) {
    this.devicePermissionChecker = devicePermissionChecker;
    this.labInfoProvider = labInfoProvider;
    this.executor = executor;
  }

  /**
   * Authenticates the user, checks permission, and runs the action if authorized.
   *
   * @param deviceId The ID of the target device.
   * @param username The authenticated username, if present.
   * @param universe The scope of the request.
   * @param actionNameForErrorMessage The action name to use in error messages.
   * @param actionSupplier The supplier that returns the future representing the action execution.
   * @param errorResponseFactory A factory to construct the response proto in case of errors.
   */
  public <T> ListenableFuture<T> authenticateAndRun(
      String deviceId,
      Optional<String> username,
      UniverseScope universe,
      String actionNameForErrorMessage,
      Supplier<ListenableFuture<T>> actionSupplier,
      BiFunction<IneligibilityReasonCode, String, T> errorResponseFactory) {
    if (username.isEmpty()) {
      return immediateFuture(
          errorResponseFactory.apply(
              IneligibilityReasonCode.PERMISSION_DENIED, "User identity not found."));
    }

    ListenableFuture<DeviceInfo> deviceInfoFuture =
        DeviceInfoLookupHelper.lookUpDeviceInfoAsync(labInfoProvider, deviceId, universe, executor);

    return Futures.transformAsync(
        deviceInfoFuture,
        deviceInfo ->
            Futures.transformAsync(
                devicePermissionChecker.hasPermission(username.get(), deviceInfo),
                hasPermission -> {
                  if (!hasPermission) {
                    return immediateFuture(
                        errorResponseFactory.apply(
                            IneligibilityReasonCode.PERMISSION_DENIED,
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
