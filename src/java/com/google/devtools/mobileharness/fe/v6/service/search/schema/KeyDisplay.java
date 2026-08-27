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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.auto.value.AutoValue;

/**
 * The user-facing label for a search key in one search context, plus its grammatical number.
 *
 * <p>The {@link #name()} is the display shown in every surface (search-bar suggestions, column
 * selector, column header, value list). For a built-in key it is the curated name (for example
 * {@code "Model"}); for a long-tail key it is the raw dimension or host-property name. The one
 * surface that differs is the value-picker (chip-detail) title, which prepends a {@code "Dimension
 * "} / {@code "Host Property "} category prefix for long-tail keys only; that prefix is derived
 * from the key id namespace at render time, so it is not stored here.
 *
 * <p>{@link #isPlural()} drives the value-picker polarity grammar ({@code "are"} vs {@code "is"}).
 * It is a hand-picked display attribute, true only for keys whose label reads as a plural noun
 * (Owners, Supported Drivers, Supported Decorators, Executors); it is not the same axis as whether
 * a device can carry multiple values.
 */
@AutoValue
public abstract class KeyDisplay {

  /** The display name (curated for a built-in key, the raw key name for a long-tail key). */
  public abstract String name();

  /** Whether the label is grammatically plural, e.g. "Owners are" vs "Model is". */
  public abstract boolean isPlural();

  /** A singular-grammar display name (for example {@code "Model"}). */
  public static KeyDisplay of(String name) {
    return new AutoValue_KeyDisplay(name, /* isPlural= */ false);
  }

  /** A plural-grammar display name (for example {@code "Owners"}). */
  public static KeyDisplay plural(String name) {
    return new AutoValue_KeyDisplay(name, /* isPlural= */ true);
  }
}
