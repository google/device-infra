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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for getting host header info. */
@Singleton
public class GetHostHeaderInfoHandler {

  @Inject
  GetHostHeaderInfoHandler() {}

  public ListenableFuture<HostHeaderInfo> getHostHeaderInfo(
      GetHostHeaderInfoRequest request, UniverseScope universe) {
    if (universe instanceof UniverseScope.RoutedUniverse) {
      // Scenario 2: routed universe. Configuration is disabled.
      return immediateFuture(
          HostHeaderInfo.newBuilder()
              .setHostName(request.getHostName())
              .setActions(HostActions.getDefaultInstance()) // Empty actions
              .build());
    }

    // Scenario 1 & 3: self universe.
    // TODO: Implement this method fully.
    return immediateFuture(
        HostHeaderInfo.newBuilder()
            .setHostName(request.getHostName())
            .setActions(HostActions.getDefaultInstance())
            .build());
  }
}
