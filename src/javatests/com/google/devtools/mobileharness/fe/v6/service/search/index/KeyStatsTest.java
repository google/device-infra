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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link KeyStats}. */
@RunWith(JUnit4.class)
public final class KeyStatsTest {

  @Test
  public void groupCount_addsNoValueBucketOnlyWhenSomeRecordLacksKey() {
    KeyStats stats = new KeyStats(3, 2, /* multiValued= */ false);

    assertThat(stats.entityCount()).isEqualTo(3);
    assertThat(stats.distinctCombinations()).isEqualTo(2);
    assertThat(stats.hasMissing(3)).isFalse();
    assertThat(stats.hasMissing(4)).isTrue();
    assertThat(stats.groupCount(3)).isEqualTo(2);
    assertThat(stats.groupCount(4)).isEqualTo(3);
  }

  @Test
  public void empty_hasZeroEntitiesAndProducesSingleMissingGroupWhenCorpusNonEmpty() {
    assertThat(KeyStats.EMPTY.entityCount()).isEqualTo(0);
    assertThat(KeyStats.EMPTY.distinctCombinations()).isEqualTo(0);
    assertThat(KeyStats.EMPTY.multiValued()).isFalse();
    assertThat(KeyStats.EMPTY.hasMissing(0)).isFalse();
    assertThat(KeyStats.EMPTY.hasMissing(5)).isTrue();
    assertThat(KeyStats.EMPTY.groupCount(0)).isEqualTo(0);
    assertThat(KeyStats.EMPTY.groupCount(5)).isEqualTo(1);
  }
}
