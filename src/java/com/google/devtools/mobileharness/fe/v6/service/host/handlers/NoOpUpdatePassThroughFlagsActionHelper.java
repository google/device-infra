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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse.Error;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse.Error.Code;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/** No-op implementation of {@link UpdatePassThroughFlagsActionHelper}. */
public final class NoOpUpdatePassThroughFlagsActionHelper
    implements UpdatePassThroughFlagsActionHelper {

  NoOpUpdatePassThroughFlagsActionHelper() {}

  @Override
  public ListenableFuture<UpdatePassThroughFlagsResponse> updatePassThroughFlags(
      UpdatePassThroughFlagsRequest request, UniverseScope universe) {
    return immediateFuture(
        UpdatePassThroughFlagsResponse.newBuilder()
            .setSuccess(false)
            .setError(
                Error.newBuilder()
                    .setCode(Code.PERMISSION_DENIED)
                    .setMessage("Only supported internally")
                    .build())
            .build());
  }
}
