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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerLifecycleRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerLifecycleResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the PreflightLabServerLifecycle RPC. */
@Singleton
public final class PreflightLabServerLifecycleHandler {

  private final PreflightLabServerLifecycleActionHelper preflightLabServerLifecycleActionHelper;

  @Inject
  PreflightLabServerLifecycleHandler(
      PreflightLabServerLifecycleActionHelper preflightLabServerLifecycleActionHelper) {
    this.preflightLabServerLifecycleActionHelper = preflightLabServerLifecycleActionHelper;
  }

  public ListenableFuture<PreflightLabServerLifecycleResponse> preflightLabServerLifecycle(
      PreflightLabServerLifecycleRequest request,
      UniverseScope universe,
      Optional<String> username) {
    return preflightLabServerLifecycleActionHelper.preflightLabServerLifecycle(
        request, universe, username);
  }
}
