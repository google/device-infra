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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DimensionCatalogStoreTest {

  private final DimensionCatalogStore store = new DimensionCatalogStore();

  @Test
  public void getDimensionNames_emptyInitially() {
    assertThat(store.getDimensionNames(Fleet.FLEET_SELF)).isEmpty();
    assertThat(store.hasDimension(Fleet.FLEET_SELF, "build")).isFalse();
  }

  @Test
  public void setDimensionNames_storesPerFleet() {
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("build", "carrier"));
    store.setDimensionNames(Fleet.FLEET_ATS, ImmutableSet.of("board"));

    assertThat(store.getDimensionNames(Fleet.FLEET_SELF)).containsExactly("build", "carrier");
    assertThat(store.hasDimension(Fleet.FLEET_SELF, "build")).isTrue();
    assertThat(store.hasDimension(Fleet.FLEET_SELF, "board")).isFalse();

    assertThat(store.getDimensionNames(Fleet.FLEET_ATS)).containsExactly("board");
    assertThat(store.hasDimension(Fleet.FLEET_ATS, "board")).isTrue();
    assertThat(store.hasDimension(Fleet.FLEET_ATS, "build")).isFalse();
  }

  @Test
  public void getDimensionNames_unspecifiedDefaultsToSelf() {
    store.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("build"));

    assertThat(store.getDimensionNames(Fleet.FLEET_UNSPECIFIED)).containsExactly("build");
    assertThat(store.hasDimension(Fleet.FLEET_UNSPECIFIED, "build")).isTrue();
  }
}
