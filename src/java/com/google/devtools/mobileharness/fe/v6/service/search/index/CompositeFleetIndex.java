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
 * <p>Delegates per-key lookups to the core index when the key is present in core, and falls back to
 * the overlay view for on-demand long-tail dimensions.
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
    if (core.keyIds().contains(keyId)) {
      return core.sortedValues(keyId);
    }
    return overlay.sortedValues(keyId);
  }

  @Override
  public ImmutableMap<String, Integer> valueCounts(String keyId) {
    if (core.keyIds().contains(keyId)) {
      return core.valueCounts(keyId);
    }
    return overlay.valueCounts(keyId);
  }

  @Override
  public ImmutableMap<String, String> valueDisplays(String keyId) {
    if (core.keyIds().contains(keyId)) {
      return core.valueDisplays(keyId);
    }
    return overlay.valueDisplays(keyId);
  }

  @Override
  public String displayName(String keyId) {
    return core.displayName(keyId);
  }

  @Override
  public int valueCount(String keyId, String value) {
    if (core.keyIds().contains(keyId)) {
      return core.valueCount(keyId, value);
    }
    return overlay.valueCount(keyId, value);
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
