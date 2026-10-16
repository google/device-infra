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

package com.google.devtools.mobileharness.fe.v6.service.search.query.suggest;

/**
 * How strongly a suggestion answers what the user typed, declared best first so the natural enum
 * order is the ranking order.
 *
 * <p>The ranker orders filter rows by curated key priority first and by this tier second, whereas
 * the group-by ranker orders key-name matches by this tier first, then grouping usefulness, and
 * curated key priority third. A tier describes rank, not the string operation that found the row,
 * which is why rows without a direct text match can sit in any tier.
 */
enum MatchTier {
  /**
   * The strongest match: the typed text is the whole key name or the whole value, a
   * non-scope-covering emptiness condition on a resolved key, a top-value condition when the value
   * is still pending ({@code <key> is}), or a curated group-by row when {@code group by} has no key
   * term.
   */
  EXACT,

  /**
   * A key name or value starts with the typed text, or an emptiness condition covers the whole
   * filtered scope and is stepped down one tier via {@link #demoted()}.
   */
  PREFIX,

  /**
   * The weakest evidence of what the user meant: a key name or value that only contains the typed
   * text, a collapsed {@code <Key> starts with} row that opens the picker for an identifier key
   * instead of applying a value, or a literal condition on a cold long-tail key that the index
   * cannot confirm.
   */
  WEAK;

  /** The next tier down, or {@link #WEAK} when already at the bottom. */
  MatchTier demoted() {
    return switch (this) {
      case EXACT -> PREFIX;
      case PREFIX, WEAK -> WEAK;
    };
  }
}
