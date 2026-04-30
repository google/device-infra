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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThan;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThanNullable;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThanOrEqualTo;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.greaterThanOrEqualToNullable;
import static com.google.devtools.mobileharness.shared.util.flags.core.Flag.Validator.nonNull;

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

  /**
   * Validator for flag values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Flag<Integer> portFlag = Flag.value(8080, val ->
   *     checkArgument(val >= 0 && val <= 65535, "Port must be between 0 and 65535"));
   * }</pre>
   */
  @FunctionalInterface
  public interface Validator<T> {

    /**
     * Validates the given value.
     *
     * <p>Throws an unchecked exception if validation fails.
     */
    void validate(T value);

    /** Returns a validator that checks the value is not null. */
    static <T> Validator<T> nonNull() {
      return value -> checkNotNull(value, "Flag value must not be null");
    }

    /** Returns a validator that checks the value is greater than the given threshold. */
    static <T extends Comparable<T>> Validator<T> greaterThan(T threshold) {
      checkNotNull(threshold);
      return value -> {
        checkNotNull(value, "Flag value must not be null");
        checkArgument(
            value.compareTo(threshold) > 0,
            "Flag value must be greater than %s (provided: %s)",
            threshold,
            value);
      };
    }

    /** Returns a validator that checks the value is greater than or equal to the threshold. */
    static <T extends Comparable<T>> Validator<T> greaterThanOrEqualTo(T threshold) {
      checkNotNull(threshold);
      return value -> {
        checkNotNull(value, "Flag value must not be null");
        checkArgument(
            value.compareTo(threshold) >= 0,
            "Flag value must be greater than or equal to %s (provided: %s)",
            threshold,
            value);
      };
    }

    /** Returns a validator that checks the value is greater than the threshold, allowing null. */
    static <T extends Comparable<T>> Validator<T> greaterThanNullable(T threshold) {
      return allowNull(greaterThan(threshold));
    }

    /** Returns a validator that checks value is >= threshold, allowing null. */
    static <T extends Comparable<T>> Validator<T> greaterThanOrEqualToNullable(T threshold) {
      return allowNull(greaterThanOrEqualTo(threshold));
    }

    /** Returns a validator wrapper that bypasses checking if the value is null. */
    static <T> Validator<T> allowNull(Validator<T> baseValidator) {
      checkNotNull(baseValidator);
      return value -> {
        if (value == null) {
          return;
        }
        baseValidator.validate(value);
      };
    }
  }

  public static <T> Flag<T> value(@Nullable T defaultValue) {
    return new Flag<>(defaultValue, /* validator= */ null);
  }

  public static <T> Flag<T> value(@Nullable T defaultValue, Validator<T> validator) {
    return new Flag<>(defaultValue, validator);
  }

  public static Flag<String> value(String defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<String> nullString() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<Integer> value(int defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<Integer> nullInteger() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<Integer> nonnegativeValue(int defaultValue) {
    return new Flag<>(defaultValue, greaterThanOrEqualTo(0));
  }

  public static Flag<Integer> nullNonnegativeValue() {
    return new Flag<>(null, greaterThanOrEqualToNullable(0));
  }

  public static Flag<Integer> positiveValue(int defaultValue) {
    return new Flag<>(defaultValue, greaterThan(0));
  }

  public static Flag<Integer> nullPositiveValue() {
    return new Flag<>(null, greaterThanNullable(0));
  }

  public static Flag<Long> value(long defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<Long> nullLong() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<Long> nonnegativeValue(long defaultValue) {
    return new Flag<>(defaultValue, greaterThanOrEqualTo(0L));
  }

  public static Flag<Long> positiveValue(long defaultValue) {
    return new Flag<>(defaultValue, greaterThan(0L));
  }

  public static Flag<Float> value(float defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<Float> nullFloat() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<Double> value(double defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<Double> nullDouble() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<Boolean> value(boolean defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<Boolean> nullBoolean() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<BigDecimal> value(BigDecimal defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static Flag<BigDecimal> nullBigDecimal() {
    return new Flag<>(null, /* validator= */ null);
  }

  public static <T extends Enum<T>> Flag<T> value(T defaultValue) {
    return new Flag<>(defaultValue, nonNull());
  }

  public static <E extends Enum<E>, T extends E> Flag<T> value(Class<E> enumType, T defaultValue) {
    checkNotNull(enumType);
    return new Flag<>(defaultValue, /* validator= */ null);
  }

  public static <E extends Enum<E>> Flag<E> nullEnum(Class<E> enumType) {
    checkNotNull(enumType);
    return new Flag<>(null, /* validator= */ null);
  }

  public static Flag<List<Integer>> integerList(int... defaultValues) {
    ImmutableList.Builder<Integer> builder = ImmutableList.builder();
    for (int i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build(), nonNull());
  }

  public static Flag<List<Long>> longList(long... defaultValues) {
    ImmutableList.Builder<Long> builder = ImmutableList.builder();
    for (long i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build(), nonNull());
  }

  public static Flag<List<Double>> doubleList(double... defaultValues) {
    ImmutableList.Builder<Double> builder = ImmutableList.builder();
    for (double i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build(), nonNull());
  }

  public static <T extends Enum<T>> Flag<List<T>> enumList(Class<T> enumType, T... defaultValues) {
    checkNotNull(enumType);
    return new Flag<>(
        defaultValues == null ? null : ImmutableList.copyOf(defaultValues), nonNull());
  }

  public static Flag<List<String>> stringList(String... defaultValues) {
    return new Flag<>(
        defaultValues == null ? null : ImmutableList.copyOf(defaultValues), nonNull());
  }

  public static Flag<Set<String>> stringSet(String... defaultValues) {
    return new Flag<>(defaultValues == null ? null : ImmutableSet.copyOf(defaultValues), nonNull());
  }

  public static Flag<Set<Integer>> integerSet(int... defaultValues) {
    ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
    for (int i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build(), nonNull());
  }

  public static Flag<Set<Long>> longSet(long... defaultValues) {
    ImmutableSet.Builder<Long> builder = ImmutableSet.builder();
    for (long i : defaultValues) {
      builder.add(i);
    }
    return new Flag<>(builder.build(), nonNull());
  }

  public static Flag<Map<String, String>> stringMap() {
    return new Flag<>(ImmutableMap.of(), nonNull());
  }

  public static Flag<Map<String, String>> stringMap(Map<String, String> defaultValue) {
    return new Flag<>(defaultValue == null ? null : ImmutableMap.copyOf(defaultValue), nonNull());
  }

  public static Flag<Map<String, String>> nullableStringMap(Map<String, String> defaultValue) {
    return new Flag<>(
        defaultValue == null ? null : ImmutableMap.copyOf(defaultValue), /* validator= */ null);
  }

  @Nullable private final T defaultValue;
  @Nullable private final Validator<T> validator;
  @Nullable private volatile T value;
  private volatile boolean setFromString;

  private Flag(@Nullable T defaultValue, @Nullable Validator<T> validator) {
    // Validates the default value.
    if (validator != null) {
      validator.validate(defaultValue);
    }

    this.defaultValue = defaultValue;
    this.validator = validator;
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
    // Validates the new value.
    if (validator != null) {
      validator.validate(value);
    }

    this.value = value;
    this.setFromString = true;
  }

  void resetForTest() {
    this.value = this.defaultValue;
    this.setFromString = false;
  }
}
