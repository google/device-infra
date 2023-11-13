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

package com.google.devtools.mobileharness.shared.util.flags;

import com.beust.jcommander.IStringConverter;
import java.util.Objects;
import javax.annotation.Nullable;

/** A flag class providing similar interfaces of com.google.common.flags.Flag. */
public class Flag<T> {

  /** Converter for creating boolean flag from JCommander. */
  public static class BooleanConverter implements IStringConverter<Flag<Boolean>> {

    @Override
    public Flag<Boolean> convert(String value) {
      return value(Boolean.parseBoolean(value));
    }
  }

  /** Converter for creating integer flag from JCommander. */
  public static class IntegerConverter implements IStringConverter<Flag<Integer>> {

    @Override
    public Flag<Integer> convert(String value) {
      return value(Integer.parseInt(value));
    }
  }

  /** Converter for creating string flag from JCommander. */
  public static class StringConverter implements IStringConverter<Flag<String>> {

    @Override
    public Flag<String> convert(String value) {
      return value(value);
    }
  }

  static <T> Flag<T> value(@Nullable T defaultValue) {
    return new Flag<>(defaultValue);
  }

  @Nullable private final T value;

  Flag(@Nullable T value) {
    this.value = value;
  }

  @Nullable
  public T get() {
    return value;
  }

  public T getNonNull() {
    return Objects.requireNonNull(get());
  }
}
