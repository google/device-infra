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

import com.google.common.collect.ImmutableMap;

/**
 * Composite implementation of {@link Postings} combining core postings with an {@link OverlayView}.
 *
 * <p>Delegates posting list lookups to the core postings when the key is not managed by overlay,
 * and resolves on-demand long-tail dimensions through the overlay view.
 */
public final class CompositePostings implements Postings {

  private final Postings core;
  private final OverlayView overlay;

  public CompositePostings(Postings core, OverlayView overlay) {
    this.core = checkNotNull(core);
    this.overlay = checkNotNull(overlay);
  }

  @Override
  public int[] get(String keyId, String value) {
    if (overlay.containsKey(keyId)) {
      return overlay.getPostings(keyId, value);
    }
    return core.get(keyId, value);
  }

  @Override
  public ImmutableMap<String, int[]> forKey(String keyId) {
    if (overlay.containsKey(keyId)) {
      return overlay.postingsForKey(keyId);
    }
    return core.forKey(keyId);
  }
}
