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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GetGlobalSummaryRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.GlobalSummary;

/**
 * Provider interface for the Home page global summary ("OmniLab Summary" card).
 *
 * <p>Serves First-Party host and device totals with device utilization, plus partner ATS labs
 * aggregate and per-controller breakdowns.
 */
public interface GlobalSummaryProvider {

  /** Computes or retrieves the global summary. */
  ListenableFuture<GlobalSummary> getGlobalSummary(GetGlobalSummaryRequest request);
}
