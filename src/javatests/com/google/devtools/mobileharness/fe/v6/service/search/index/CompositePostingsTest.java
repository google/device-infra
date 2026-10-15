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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CompositePostings}. */
@RunWith(JUnit4.class)
public final class CompositePostingsTest {

  private FakePostings corePostings;
  private FakeOverlayView overlayView;
  private CompositePostings composite;

  @Before
  public void setUp() {
    corePostings = new FakePostings();
    corePostings.postingsMap.put(
        "field::status", ImmutableMap.of("idle", new int[] {0, 2}, "busy", new int[] {1}));
    corePostings.statsMap.put("field::status", new KeyStats(99, 42));

    overlayView = new FakeOverlayView();
    overlayView.loadedKeys = ImmutableSet.of("dimension::carrier", "dimension::tag");
    overlayView.postingsMap.put(
        "dimension::carrier", ImmutableMap.of("verizon", new int[] {0, 1}, "att", new int[] {2}));
    overlayView.postingsMap.put(
        "dimension::tag", ImmutableMap.of("a", new int[] {0, 1}, "b", new int[] {0}));
    overlayView.valuesByDeviceAndKey.put("0:dimension::tag", ImmutableSet.of("a", "b"));
    overlayView.valuesByDeviceAndKey.put("1:dimension::tag", ImmutableSet.of("a"));

    composite = new CompositePostings(corePostings, overlayView);
  }

  @Test
  public void coreKey_delegatesToCore() {
    assertThat(composite.get("field::status", "idle")).asList().containsExactly(0, 2).inOrder();
    assertThat(composite.forKey("field::status")).containsKey("idle");
  }

  @Test
  public void overlayKey_delegatesToOverlay() {
    assertThat(composite.get("dimension::carrier", "verizon"))
        .asList()
        .containsExactly(0, 1)
        .inOrder();
    assertThat(composite.forKey("dimension::carrier")).containsKey("verizon");
  }

  @Test
  public void absentKey_returnsEmpty() {
    assertThat(composite.get("dimension::unknown", "val")).isEmpty();
    assertThat(composite.forKey("dimension::unknown")).isEmpty();
    assertThat(composite.stats("dimension::unknown")).isEqualTo(KeyStats.EMPTY);
  }

  @Test
  public void stats_delegatesToCoreForCoreKeysAndSummarizesOverlayForOverlayKeys() {
    assertThat(composite.stats("field::status")).isEqualTo(new KeyStats(99, 42));
    assertThat(composite.stats("dimension::carrier")).isEqualTo(new KeyStats(3, 2));
    assertThat(composite.stats("dimension::tag")).isEqualTo(new KeyStats(2, 2));
  }

  private static final class FakePostings implements Postings {
    final Map<String, ImmutableMap<String, int[]>> postingsMap = new HashMap<>();
    final Map<String, KeyStats> statsMap = new HashMap<>();

    @Override
    public int[] get(String keyId, String value) {
      ImmutableMap<String, int[]> keyPostings = postingsMap.get(keyId);
      if (keyPostings != null) {
        int[] posting = keyPostings.get(value);
        if (posting != null) {
          return posting;
        }
      }
      return new int[0];
    }

    @Override
    public ImmutableMap<String, int[]> forKey(String keyId) {
      ImmutableMap<String, int[]> keyPostings = postingsMap.get(keyId);
      return keyPostings != null ? keyPostings : ImmutableMap.of();
    }

    @Override
    public KeyStats stats(String keyId) {
      return statsMap.getOrDefault(keyId, KeyStats.EMPTY);
    }
  }

  private static final class FakeOverlayView implements OverlayView {
    ImmutableSet<String> loadedKeys = ImmutableSet.of();
    final Map<String, ImmutableMap<String, int[]>> postingsMap = new HashMap<>();
    final Map<String, ImmutableSet<String>> valuesByDeviceAndKey = new HashMap<>();

    @Override
    public boolean containsKey(String keyId) {
      return loadedKeys.contains(keyId);
    }

    @Override
    public ImmutableSet<String> loadedKeys() {
      return loadedKeys;
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
      ImmutableMap<String, int[]> keyPostings = postingsMap.get(keyId);
      if (keyPostings != null) {
        int[] posting = keyPostings.get(value);
        if (posting != null) {
          return posting;
        }
      }
      return new int[0];
    }

    @Override
    public ImmutableMap<String, int[]> postingsForKey(String keyId) {
      ImmutableMap<String, int[]> keyPostings = postingsMap.get(keyId);
      return keyPostings != null ? keyPostings : ImmutableMap.of();
    }

    @Override
    public ImmutableSet<String> valuesForKey(int deviceIndex, String keyId) {
      return valuesByDeviceAndKey.getOrDefault(deviceIndex + ":" + keyId, ImmutableSet.of());
    }

    @Override
    public ImmutableList<String> displayValues(int deviceIndex, String keyId) {
      return ImmutableList.of();
    }
  }
}
