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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Read view over loaded on-demand dimension overlays for one query/corpus context. */
public interface OverlayView {

  /** Returns true if the key is present in this overlay view. */
  boolean containsKey(String keyId);

  /** All key ids loaded in this overlay view. */
  ImmutableSet<String> loadedKeys();

  /** Sorted distinct values for an overlay key, or empty if absent. */
  ImmutableList<String> sortedValues(String keyId);

  /** Value counts for an overlay key, or empty if absent. */
  ImmutableMap<String, Integer> valueCounts(String keyId);

  /** Value displays for an overlay key, or empty if absent. */
  ImmutableMap<String, String> valueDisplays(String keyId);

  /** Value count for a specific (key, value), or 0 if absent. */
  int valueCount(String keyId, String value);

  /** Posting list for a specific (key, value), or empty array if absent. */
  int[] getPostings(String keyId, String value);

  /** All posting lists for an overlay key, or empty map if absent. */
  ImmutableMap<String, int[]> postingsForKey(String keyId);

  /** Values for a specific device index and overlay key. */
  ImmutableSet<String> valuesForKey(int deviceIndex, String keyId);

  /** Display values for a specific device index and overlay key. */
  ImmutableList<String> displayValues(int deviceIndex, String keyId);

  /** Empty overlay view, used when no overlays are loaded. */
  static OverlayView empty() {
    return EmptyOverlayView.INSTANCE;
  }

  /** Default empty implementation of {@link OverlayView}. */
  final class EmptyOverlayView implements OverlayView {
    static final EmptyOverlayView INSTANCE = new EmptyOverlayView();

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private EmptyOverlayView() {}

    @Override
    public boolean containsKey(String keyId) {
      return false;
    }

    @Override
    public ImmutableSet<String> loadedKeys() {
      return ImmutableSet.of();
    }

    @Override
    public ImmutableList<String> sortedValues(String keyId) {
      return ImmutableList.of();
    }

    @Override
    public ImmutableMap<String, Integer> valueCounts(String keyId) {
      return ImmutableMap.of();
    }

    @Override
    public ImmutableMap<String, String> valueDisplays(String keyId) {
      return ImmutableMap.of();
    }

    @Override
    public int valueCount(String keyId, String value) {
      return 0;
    }

    @Override
    public int[] getPostings(String keyId, String value) {
      return EMPTY_INT_ARRAY;
    }

    @Override
    public ImmutableMap<String, int[]> postingsForKey(String keyId) {
      return ImmutableMap.of();
    }

    @Override
    public ImmutableSet<String> valuesForKey(int deviceIndex, String keyId) {
      return ImmutableSet.of();
    }

    @Override
    public ImmutableList<String> displayValues(int deviceIndex, String keyId) {
      return ImmutableList.of();
    }
  }
}
