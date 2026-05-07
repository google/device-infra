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

package com.google.devtools.mobileharness.fe.v6.service.host.builders;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetPopularFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetPopularFlagsResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/** Builder for popular pass-through flags. */
public interface PopularFlagsBuilder {

  /** Builds popular pass-through flags for the host. */
  ListenableFuture<GetPopularFlagsResponse> getPopularFlags(
      GetPopularFlagsRequest request, UniverseScope universe);
}
