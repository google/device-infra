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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** Concatenated flags string and tokenized flags. */
@AutoValue
public abstract class FlagsString {

  public abstract String flagsString();

  public abstract ImmutableList<String> flags();

  public FlagsString addToHead(ImmutableList<String> flags) {
    if (flags.isEmpty()) {
      return of(flagsString(), flags());
    }
    return of(
        String.join(" ", flags) + " " + flagsString(),
        ImmutableList.<String>builderWithExpectedSize(flags().size() + flags.size())
            .addAll(flags)
            .addAll(flags())
            .build());
  }

  public FlagsString addToEnd(ImmutableList<String> flags) {
    if (flags.isEmpty()) {
      return of(flagsString(), flags());
    }
    return of(
        flagsString() + " " + String.join(" ", flags),
        ImmutableList.<String>builderWithExpectedSize(flags().size() + flags.size())
            .addAll(flags())
            .addAll(flags)
            .build());
  }

  public static FlagsString of(String flagsString, ImmutableList<String> flags) {
    return new AutoValue_FlagsString(flagsString, flags);
  }
}
