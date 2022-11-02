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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The device allocation info for a single test, with allocation statistic appended, such as
 * allocation latency data.
 */
public class AllocationWithStats {

  private final Allocation allocation;
  private final Map<String, String> stats;

  /**
   * Creates the allocation info for a single test, and allow to append extra statistic metrics,
   * such as allocation latency data, quota related information.
   */
  public AllocationWithStats(Allocation allocation) {
    this.allocation = allocation;
    this.stats = new HashMap<>();
  }

  public Allocation allocation() {
    return allocation;
  }

  public ImmutableMap<String, String> stats() {
    return ImmutableMap.copyOf(stats);
  }

  /**
   * Adds or updates the statistic data.
   *
   * @return the previous value associated with the given key
   */
  public Optional<String> upsertStats(String key, String value) {
    return Optional.ofNullable(stats.put(key, value));
  }
}
