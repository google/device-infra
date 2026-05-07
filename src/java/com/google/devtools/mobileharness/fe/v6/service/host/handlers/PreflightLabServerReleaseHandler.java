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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the PreflightLabServerRelease RPC. */
@Singleton
public final class PreflightLabServerReleaseHandler {

  private final PreflightLabServerReleaseActionHelper preflightLabServerReleaseActionHelper;

  @Inject
  PreflightLabServerReleaseHandler(
      PreflightLabServerReleaseActionHelper preflightLabServerReleaseActionHelper) {
    this.preflightLabServerReleaseActionHelper = preflightLabServerReleaseActionHelper;
  }

  public ListenableFuture<PreflightLabServerReleaseResponse> preflightLabServerRelease(
      PreflightLabServerReleaseRequest request, UniverseScope universe, Optional<String> username) {
    return preflightLabServerReleaseActionHelper.preflightLabServerRelease(
        request, universe, username);
  }
}
