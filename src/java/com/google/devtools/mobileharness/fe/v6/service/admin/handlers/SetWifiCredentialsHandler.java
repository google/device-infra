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

package com.google.devtools.mobileharness.fe.v6.service.admin.handlers;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.WifiCredentialStore;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the SetWifiCredentials RPC. */
@Singleton
public final class SetWifiCredentialsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final WifiCredentialsStore wifiCredentialsStore;

  @Inject
  @VisibleForTesting
  public SetWifiCredentialsHandler(WifiCredentialsStore wifiCredentialsStore) {
    this.wifiCredentialsStore = wifiCredentialsStore;
  }

  /**
   * Sets (replaces) all Wi-Fi credentials.
   *
   * @param request the request containing the new credentials
   * @return the response indicating the number of credentials stored
   */
  public ListenableFuture<SetWifiCredentialsResponse> setWifiCredentials(
      SetWifiCredentialsRequest request) {
    logger.atInfo().log(
        "SetWifiCredentials: writing %d credentials", request.getWifiCredentialsCount());

    WifiCredentialStore store =
        WifiCredentialStore.newBuilder().addAllEntries(request.getWifiCredentialsList()).build();

    return Futures.transform(
        wifiCredentialsStore.write(store),
        unused ->
            SetWifiCredentialsResponse.newBuilder()
                .setCredentialsCount(store.getEntriesCount())
                .build(),
        directExecutor());
  }
}
