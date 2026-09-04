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

package com.google.devtools.mobileharness.fe.v6.service.search.summary;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GetGlobalSummaryRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GlobalSummary;
import javax.inject.Inject;

/**
 * OSS NoOp implementation of {@link GlobalSummaryProvider}.
 *
 * <p>Standalone ATS deployments have no First-Party fleet, so the global summary is unsupported.
 */
public final class NoOpGlobalSummaryProvider implements GlobalSummaryProvider {

  @Inject
  NoOpGlobalSummaryProvider() {}

  @Override
  public ListenableFuture<GlobalSummary> getGlobalSummary(GetGlobalSummaryRequest request) {
    return immediateFailedFuture(
        FeServiceException.unimplemented("Global summary is not supported in standalone ATS"));
  }
}
