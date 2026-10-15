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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link KeyStats} and its caching in {@link LazyPostings}. */
@RunWith(JUnit4.class)
public final class KeyStatsTest {

  @Test
  public void compute_singleValuedKey_summarizesFromPostingsWithoutVisitingRecords() {
    ImmutableMap<String, int[]> postings =
        ImmutableMap.of("idle", new int[] {0, 2}, "busy", new int[] {1});
    AtomicInteger visits = new AtomicInteger();

    KeyStats stats =
        KeyStats.compute(
            postings,
            index -> {
              visits.incrementAndGet();
              return ImmutableSet.of();
            });

    assertThat(stats).isEqualTo(new KeyStats(3, 2));
    assertThat(visits.get()).isEqualTo(0);
    assertThat(stats.hasMissing(3)).isFalse();
    assertThat(stats.hasMissing(4)).isTrue();
    assertThat(stats.groupCount(3)).isEqualTo(2);
    assertThat(stats.groupCount(4)).isEqualTo(3);
  }

  @Test
  public void compute_multiValuedKey_countsDistinctCombinationsOverCarryingRecordsOnly() {
    // Record 0 carries {alice, bob}, record 1 carries {bob, alice}, record 2 carries {carol},
    // record 3 carries nothing. Two combinations: {alice, bob} and {carol}.
    ImmutableMap<String, int[]> postings =
        ImmutableMap.of("alice", new int[] {0, 1}, "bob", new int[] {0, 1}, "carol", new int[] {2});
    ImmutableList<ImmutableSet<String>> values =
        ImmutableList.of(
            ImmutableSet.of("alice", "bob"),
            ImmutableSet.of("bob", "alice"),
            ImmutableSet.of("carol"),
            ImmutableSet.of());
    AtomicInteger visits = new AtomicInteger();

    KeyStats stats =
        KeyStats.compute(
            postings,
            index -> {
              visits.incrementAndGet();
              return values.get(index);
            });

    assertThat(stats).isEqualTo(new KeyStats(3, 2));
    assertThat(visits.get()).isEqualTo(3);
    assertThat(stats.groupCount(4)).isEqualTo(3);
  }

  @Test
  public void compute_emptyPostings_isEmpty() {
    assertThat(KeyStats.compute(ImmutableMap.of(), index -> ImmutableSet.of()))
        .isEqualTo(KeyStats.EMPTY);
    assertThat(KeyStats.EMPTY.groupCount(5)).isEqualTo(1);
  }

  @Test
  public void lazyPostings_stats_areComputedOncePerKey() {
    ImmutableList<ImmutableMap<String, ImmutableSet<String>>> records =
        ImmutableList.of(
            ImmutableMap.of("owner", ImmutableSet.of("alice", "bob")),
            ImmutableMap.of("owner", ImmutableSet.of("alice")),
            ImmutableMap.of());
    AtomicInteger visits = new AtomicInteger();
    LazyPostings postings =
        new LazyPostings(
            new LazyPostings.RecordValues() {
              @Override
              public int recordCount() {
                return records.size();
              }

              @Override
              public ImmutableSet<String> valuesForKey(int index, String keyId) {
                visits.incrementAndGet();
                return records.get(index).getOrDefault(keyId, ImmutableSet.of());
              }
            });

    KeyStats first = postings.stats("owner");
    int visitsAfterFirst = visits.get();
    KeyStats second = postings.stats("owner");

    assertThat(first).isEqualTo(new KeyStats(2, 2));
    assertThat(second).isSameInstanceAs(first);
    assertThat(visits.get()).isEqualTo(visitsAfterFirst);
    assertThat(postings.stats("absent")).isEqualTo(KeyStats.EMPTY);
  }
}
