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

package com.google.devtools.mobileharness.platform.android.instrumentation.parser;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Results reported at the end of an `am instrument` command.
 *
 * <p>The `code`, also called Session Result Code, reported by `am instrument` is defined in AOSP
 * `frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java` as:
 *
 * <ul>
 *   <li>-1: Success
 *   <li>other: Failure
 * </ul>
 */
@AutoValue
public abstract class InstrumentationResult {

  public abstract @Nullable Integer code();

  public abstract ImmutableMap<String, String> bundle();

  public static Builder builder() {
    return new AutoValue_InstrumentationResult.Builder().setBundle(ImmutableMap.of());
  }

  /** Builder for {@link InstrumentationResult}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCode(Integer code);

    public abstract Builder setBundle(ImmutableMap<String, String> bundle);

    public abstract InstrumentationResult build();
  }

  public boolean success() {
    return code() != null && code() == -1;
  }
}
