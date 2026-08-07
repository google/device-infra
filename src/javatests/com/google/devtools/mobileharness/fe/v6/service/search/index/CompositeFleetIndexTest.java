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

/** Unit tests for {@link CompositeFleetIndex}. */
@RunWith(JUnit4.class)
public final class CompositeFleetIndexTest {

  private CoreFleetIndex core;
  private FakeOverlayView overlay;
  private CompositeFleetIndex composite;

  @Before
  public void setUp() {
    core =
        CoreFleetIndex.builder()
            .setKeyIds(ImmutableSet.of("field::status", "dim::model"))
            .setSortedValuesMap(
                ImmutableMap.of(
                    "field::status", ImmutableList.of("busy", "idle"),
                    "dim::model", ImmutableList.of("pixel 7", "pixel 8")))
            .setValueCountsMap(
                ImmutableMap.of(
                    "field::status", ImmutableMap.of("idle", 10, "busy", 5),
                    "dim::model", ImmutableMap.of("pixel 8", 8, "pixel 7", 7)))
            .setValueDisplaysMap(
                ImmutableMap.of(
                    "field::status", ImmutableMap.of("idle", "IDLE", "busy", "BUSY"),
                    "dim::model", ImmutableMap.of("pixel 8", "Pixel 8", "pixel 7", "Pixel 7")))
            .setDisplayNamesMap(ImmutableMap.of("field::status", "Status", "dim::model", "Model"))
            .setSemanticGlobalSorted(
                ImmutableList.of(
                    new ValueKeyPair("pixel 7", "dim::model"),
                    new ValueKeyPair("pixel 8", "dim::model")))
            .setGlobalExact(
                ImmutableMap.of("pixel 8", ImmutableList.of(new KeyCount("dim::model", 8))))
            .build();

    overlay = new FakeOverlayView();
    overlay.loadedKeys = ImmutableSet.of("dim::carrier", "dim::battery_level");
    overlay.sortedValuesMap.put("dim::carrier", ImmutableList.of("att", "t-mobile", "verizon"));
    overlay.valueCountsMap.put("dim::carrier", ImmutableMap.of("verizon", 1200, "att", 500));
    overlay.valueDisplaysMap.put(
        "dim::carrier", ImmutableMap.of("verizon", "Verizon", "att", "AT&T"));

    composite = new CompositeFleetIndex(core, overlay);
  }

  @Test
  public void coreKey_delegatesToCore() {
    assertThat(composite.sortedValues("field::status")).containsExactly("busy", "idle").inOrder();
    assertThat(composite.valueCounts("field::status")).containsEntry("idle", 10);
    assertThat(composite.valueDisplays("field::status")).containsEntry("idle", "IDLE");
    assertThat(composite.displayName("field::status")).isEqualTo("Status");
    assertThat(composite.valueCount("field::status", "idle")).isEqualTo(10);
  }

  @Test
  public void overlayKey_delegatesToOverlay() {
    assertThat(composite.sortedValues("dim::carrier"))
        .containsExactly("att", "t-mobile", "verizon")
        .inOrder();
    assertThat(composite.valueCounts("dim::carrier")).containsEntry("verizon", 1200);
    assertThat(composite.valueDisplays("dim::carrier")).containsEntry("verizon", "Verizon");
    assertThat(composite.displayName("dim::carrier")).isEqualTo("Dimension carrier");
    assertThat(composite.valueCount("dim::carrier", "verizon")).isEqualTo(1200);
  }

  @Test
  public void absentKey_returnsEmptyAndDerivesDisplayName() {
    assertThat(composite.sortedValues("dim::unknown")).isEmpty();
    assertThat(composite.valueCounts("dim::unknown")).isEmpty();
    assertThat(composite.valueDisplays("dim::unknown")).isEmpty();
    assertThat(composite.displayName("dim::unknown")).isEqualTo("Dimension unknown");
    assertThat(composite.displayName("prop::rack")).isEqualTo("Host Property rack");
    assertThat(composite.valueCount("dim::unknown", "val")).isEqualTo(0);
  }

  @Test
  public void keyIds_returnsUnion() {
    assertThat(composite.keyIds())
        .containsExactly("field::status", "dim::model", "dim::carrier", "dim::battery_level");
  }

  @Test
  public void globalIndices_delegateToCoreOnly() {
    // Invariant D6: global bare-value search is strictly isolated from overlay data.
    assertThat(composite.semanticGlobalSorted())
        .containsExactly(
            new ValueKeyPair("pixel 7", "dim::model"), new ValueKeyPair("pixel 8", "dim::model"))
        .inOrder();
    assertThat(composite.globalExact())
        .containsExactly("pixel 8", ImmutableList.of(new KeyCount("dim::model", 8)));
  }

  private static final class FakeOverlayView implements OverlayView {
    ImmutableSet<String> loadedKeys = ImmutableSet.of();
    final Map<String, ImmutableList<String>> sortedValuesMap = new HashMap<>();
    final Map<String, ImmutableMap<String, Integer>> valueCountsMap = new HashMap<>();
    final Map<String, ImmutableMap<String, String>> valueDisplaysMap = new HashMap<>();

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
      return sortedValuesMap.getOrDefault(keyId, ImmutableList.of());
    }

    @Override
    public ImmutableMap<String, Integer> valueCounts(String keyId) {
      return valueCountsMap.getOrDefault(keyId, ImmutableMap.of());
    }

    @Override
    public ImmutableMap<String, String> valueDisplays(String keyId) {
      return valueDisplaysMap.getOrDefault(keyId, ImmutableMap.of());
    }

    @Override
    public int valueCount(String keyId, String value) {
      return valueCounts(keyId).getOrDefault(value, 0);
    }

    @Override
    public int[] getPostings(String keyId, String value) {
      return new int[0];
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
