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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** Raw dimension data pulled from a data source for one on-demand long-tail dimension. */
@AutoValue
public abstract class DimensionOverlayRaw {

  /** The namespaced key id (e.g. "dim::carrier"). */
  public abstract String keyId();

  /** Map from device UUID to the list of display-cased values for this dimension. */
  public abstract ImmutableMap<String, ImmutableList<String>> uuidToValues();

  public static DimensionOverlayRaw create(
      String keyId, ImmutableMap<String, ImmutableList<String>> uuidToValues) {
    return new AutoValue_DimensionOverlayRaw(keyId, uuidToValues);
  }
}
