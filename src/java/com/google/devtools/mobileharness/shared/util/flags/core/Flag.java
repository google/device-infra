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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** A flag. */
@SuppressWarnings("UngroupedOverloads") // several common overloads are grouped by type instead
public final class Flag<T> {

  public static <T> Flag<T> value(@Nullable T defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<String> value(String defaultValue) {
    return new Flag<>(checkNotNull(defaultValue));
  }

  public static Flag<String> nullString() {
    return new Flag<>(null);
  }

  public static Flag<Integer> value(int defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<Integer> nullInteger() {
    return new Flag<>(null);
  }

  public static Flag<Long> value(long defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<Long> nullLong() {
    return new Flag<>(null);
  }

  public static Flag<Float> value(float defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<Float> nullFloat() {
    return new Flag<>(null);
  }

  public static Flag<Double> value(double defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<Double> nullDouble() {
    return new Flag<>(null);
  }

  public static Flag<Boolean> value(boolean defaultValue) {
    return new Flag<>(defaultValue);
  }

  public static Flag<Boolean> nullBoolean() {
    return new Flag<>(null);
  }

  public static Flag<BigDecimal> value(BigDecimal defaultValue) {
    return new Flag<>(checkNotNull(defaultValue));
  }

  public static Flag<BigDecimal> nullBigDecimal() {
    return new Flag<>(null);
  }

  public static <T extends Enum<T>> Flag<T> value(T defaultValue) {
    return new Flag<>(checkNotNull(defaultValue));
  }

  public static <E extends Enum<E>, T extends E> Flag<T> value(Class<E> enumType, T defaultValue) {
    checkNotNull(enumType);
    return new Flag<>(defaultValue);
  }

  public static <E extends Enum<E>> Flag<E> nullEnum(Class<E> enumType) {
    checkNotNull(enumType);
    return new Flag<>(null);
  }

  public static Flag<List<Integer>> integerList(int... defaultValues) {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder();
    for (int i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build());
  }

  public static Flag<List<Long>> longList(long... defaultValues) {
    ImmutableList.Builder<Long> builder = ImmutableList.builder();
    for (long i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build());
  }

  public static Flag<List<Double>> doubleList(double... defaultValues) {
    ImmutableList.Builder<Double> builder = ImmutableList.builder();
    for (double i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build());
  }

  public static <T extends Enum<T>> Flag<List<T>> enumList(Class<T> enumType, T... defaultValues) {
    checkNotNull(enumType);
    return new Flag<>(ImmutableList.copyOf(defaultValues));
  }

  public static Flag<List<String>> stringList(String... defaultValues) {
    return new Flag<>(ImmutableList.copyOf(defaultValues));
  }

  public static Flag<Set<String>> stringSet(String... defaultValues) {
    return new Flag<>(ImmutableSet.copyOf(defaultValues));
  }

  public static Flag<Set<Integer>> integerSet(int... defaultValues) {
    ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
    for (int i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build());
  }

  public static Flag<Set<Long>> longSet(long... defaultValues) {
    ImmutableSet.Builder<Long> builder = ImmutableSet.builder();
    for (long i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build());
  }

  public static Flag<Map<String, String>> stringMap() {
    return new Flag<>(ImmutableMap.of());
  }

  public static Flag<Map<String, String>> stringMap(Map<String, String> defaultValue) {
    return new Flag<>(ImmutableMap.copyOf(defaultValue));
  }

  public static Flag<Map<String, String>> nullableStringMap(Map<String, String> defaultValue) {
    return new Flag<>(defaultValue == null ? null : ImmutableMap.copyOf(defaultValue));
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
