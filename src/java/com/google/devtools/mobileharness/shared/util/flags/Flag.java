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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/** A flag class providing similar interfaces of com.google.common.flags.Flag. */
public class Flag<T> {
  private static final Splitter ENTRY_SPLITTER = Splitter.on(',');

  /** Converter for creating boolean flag from JCommander. */
  public static class BooleanConverter implements IStringConverter<Flag<Boolean>> {

    @Override
    public Flag<Boolean> convert(String value) {
      return value(Boolean.parseBoolean(value));
    }
  }

  /** Converter for creating double flag from JCommander. */
  public static class DoubleConverter implements IStringConverter<Flag<Double>> {

    @Override
    public Flag<Double> convert(String value) {
      return value(Double.parseDouble(value));
    }
  }

  /** Converter for creating integer flag from JCommander. */
  public static class IntegerConverter implements IStringConverter<Flag<Integer>> {

    @Override
    public Flag<Integer> convert(String value) {
      return value(Integer.parseInt(value));
    }
  }

  /** Converter for creating long flag from JCommander. */
  public static class LongConverter implements IStringConverter<Flag<Long>> {

    @Override
    public Flag<Long> convert(String value) {
      return value(Long.parseLong(value));
    }
  }

  /** Converter for creating string flag from JCommander. */
  public static class StringConverter implements IStringConverter<Flag<String>> {

    @Override
    public Flag<String> convert(String value) {
      return value(value);
    }
  }

  /** Converter for creating string list flag from JCommander. */
  public static class StringListConverter implements IStringConverter<Flag<List<String>>> {
    @Override
    public Flag<List<String>> convert(String value) {
      if (value.isEmpty()) {
        return value(ImmutableList.of());
      }
      return value(ImmutableList.copyOf(ENTRY_SPLITTER.split(value)));
    }
  }

  static Flag<List<String>> stringList(String... defaultValues) {
    return value(ImmutableList.copyOf(defaultValues));
  }

  /** Converter for creating string map flag from JCommander. */
  public static class StringMapConverter implements IStringConverter<Flag<Map<String, String>>> {

    @Override
    public Flag<Map<String, String>> convert(String value) {
      if (value.isEmpty()) {
        return value(ImmutableMap.of());
      }
      ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
      for (String s : ENTRY_SPLITTER.split(value)) {
        int index = s.indexOf('=');
        if (index == -1) {
          throw new IllegalArgumentException("Invalid map entry syntax " + s);
        } else {
          result.put(s.substring(0, index).trim(), s.substring(index + 1).trim());
        }
      }
      return value(result.buildKeepingLast());
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
