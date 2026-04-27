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

package com.google.devtools.mobileharness.shared.util.flags.core;

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/** A flag. */
public class Flag<T> {

  public static <T> Flag<T> value(@Nullable T defaultValue) {
    return new Flag<>(defaultValue);
  }

  @Nullable private final T defaultValue;
  @Nullable private volatile T value;
  private volatile boolean setFromString;

  private Flag(@Nullable T defaultValue) {
    this.defaultValue = defaultValue;
    this.value = defaultValue;
  }

  @Nullable
  public T get() {
    return value;
  }

  public T getNonNull() {
    T value = get();
    checkState(value != null, "Flag value is null");
    return value;
  }

  @Nullable
  public T getDefault() {
    return defaultValue;
  }

  public boolean wasSetFromString() {
    return setFromString;
  }

  void setValue(@Nullable T value) {
    this.value = value;
    this.setFromString = true;
  }

  void resetForTest() {
    this.value = this.defaultValue;
    this.setFromString = false;
  }
}
