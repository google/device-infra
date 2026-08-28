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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Composite implementation of {@link FleetIndex} combining a core index with an {@link
 * OverlayView}.
 *
 * <p>Delegates per-key lookups to the overlay view when the key is managed by overlay, and falls
 * back to the core index for base fleet keys.
 *
 * <p>In accordance with decision D6, global bare-value search indices ({@link
 * #semanticGlobalSorted} and {@link #globalExact}) are strictly delegated to the core index only,
 * isolating global value search from long-tail dimension noise and ensuring replica consistency.
 */
public final class CompositeFleetIndex implements FleetIndex {

  private final FleetIndex core;
  private final OverlayView overlay;

  public CompositeFleetIndex(FleetIndex core, OverlayView overlay) {
    this.core = checkNotNull(core);
    this.overlay = checkNotNull(overlay);
  }

  @Override
  public ImmutableList<String> sortedValues(String keyId) {
    if (overlay.containsKey(keyId)) {
      return overlay.sortedValues(keyId);
    }
    return core.sortedValues(keyId);
  }

  @Override
  public ImmutableMap<String, Integer> valueCounts(String keyId) {
    if (overlay.containsKey(keyId)) {
      return overlay.valueCounts(keyId);
    }
    return core.valueCounts(keyId);
  }

  @Override
  public ImmutableMap<String, String> valueDisplays(String keyId) {
    if (overlay.containsKey(keyId)) {
      return overlay.valueDisplays(keyId);
    }
    return core.valueDisplays(keyId);
  }

  @Override
  public int valueCount(String keyId, String value) {
    if (overlay.containsKey(keyId)) {
      return overlay.valueCount(keyId, value);
    }
    return core.valueCount(keyId, value);
  }

  @Override
  public ImmutableSet<String> keyIds() {
    return ImmutableSet.<String>builder()
        .addAll(core.keyIds())
        .addAll(overlay.loadedKeys())
        .build();
  }

  @Override
  public ImmutableList<ValueKeyPair> semanticGlobalSorted() {
    return core.semanticGlobalSorted();
  }

  @Override
  public ImmutableMap<String, ImmutableList<KeyCount>> globalExact() {
    return core.globalExact();
  }
}
