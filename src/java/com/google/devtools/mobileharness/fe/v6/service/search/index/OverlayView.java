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

  /** Lowercased values for a specific device index and overlay key. */
  ImmutableSet<String> valuesForKey(int deviceIndex, String keyId);

  /** Original display values for a specific device index and overlay key. */
  ImmutableList<String> displayValues(int deviceIndex, String keyId);

  /** Empty overlay view, used when no overlays are loaded. */
  static OverlayView empty() {
    return EmptyOverlayView.INSTANCE;
  }

  /** Creates an overlay view binding the given overlays to the specified snapshot. */
  static OverlayView bind(FleetSnapshot snapshot, ImmutableMap<String, DimensionOverlay> overlays) {
    if (overlays.isEmpty()) {
      return empty();
    }
    return new BoundOverlayView(snapshot, overlays);
  }

  /** Bound implementation of {@link OverlayView} over a specific snapshot. */
  final class BoundOverlayView implements OverlayView {
    private final FleetSnapshot snapshot;
    private final ImmutableMap<String, DimensionOverlay> overlays;

    BoundOverlayView(FleetSnapshot snapshot, ImmutableMap<String, DimensionOverlay> overlays) {
      this.snapshot = checkNotNull(snapshot);
      this.overlays = checkNotNull(overlays);
    }

    @Override
    public boolean containsKey(String keyId) {
      return overlays.containsKey(keyId);
    }

    @Override
    public ImmutableSet<String> loadedKeys() {
      return overlays.keySet();
    }

    @Override
    public ImmutableList<String> sortedValues(String keyId) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.sortedValues() : ImmutableList.of();
    }

    @Override
    public ImmutableMap<String, Integer> valueCounts(String keyId) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.valueCounts() : ImmutableMap.of();
    }

    @Override
    public ImmutableMap<String, String> valueDisplays(String keyId) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.valueDisplays() : ImmutableMap.of();
    }

    @Override
    public int valueCount(String keyId, String value) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.valueCount(value) : 0;
    }

    @Override
    public int[] getPostings(String keyId, String value) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.bind(snapshot).get(value) : EMPTY_INT_ARRAY;
    }

    @Override
    public ImmutableMap<String, int[]> postingsForKey(String keyId) {
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.bind(snapshot).postings() : ImmutableMap.of();
    }

    @Override
    public ImmutableSet<String> valuesForKey(int deviceIndex, String keyId) {
      if (deviceIndex < 0 || deviceIndex >= snapshot.devices().size()) {
        return ImmutableSet.of();
      }
      String uuid = snapshot.devices().get(deviceIndex).deviceId();
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.lowerValuesForUuid(uuid) : ImmutableSet.of();
    }

    @Override
    public ImmutableList<String> displayValues(int deviceIndex, String keyId) {
      if (deviceIndex < 0 || deviceIndex >= snapshot.devices().size()) {
        return ImmutableList.of();
      }
      String uuid = snapshot.devices().get(deviceIndex).deviceId();
      DimensionOverlay overlay = overlays.get(keyId);
      return overlay != null ? overlay.valuesForUuid(uuid) : ImmutableList.of();
    }

    private static final int[] EMPTY_INT_ARRAY = new int[0];
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
