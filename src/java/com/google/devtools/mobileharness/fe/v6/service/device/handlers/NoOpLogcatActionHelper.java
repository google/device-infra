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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/** No-op implementation of {@link LogcatActionHelper} for OSS environment. */
public final class NoOpLogcatActionHelper implements LogcatActionHelper {

  @Override
  public ListenableFuture<GetLogcatResponse> getLogcat(
      GetLogcatRequest request, UniverseScope universe) {
    return immediateFuture(
        GetLogcatResponse.newBuilder()
            .setErrorMsg("Getting logcat is not supported in the current environment.")
            .build());
  }
}
