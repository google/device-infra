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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.flags.core.ext.DurationFlag;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Flags for testing. */
@SuppressWarnings("NonPrivateFlag")
public class FlagsForTesting {

  @FlagSpec(name = "boolean_flag", help = "Boolean flag.")
  public static final Flag<Boolean> booleanFlag = Flag.value(false);

  @FlagSpec(name = "boolean_list_flag", help = "Boolean list flag.")
  public static final Flag<List<Boolean>> booleanListFlag = Flag.value(ImmutableList.of());

  @FlagSpec(name = "duration_flag", help = "Duration flag.")
  public static final Flag<Duration> durationFlag = DurationFlag.zero();

  @FlagSpec(name = "enum_flag", help = "Enum flag.")
  public static final Flag<TestEnum> enumFlag = Flag.value(TestEnum.VALUE_A);

  @FlagSpec(name = "enum_list_flag", help = "Enum list flag.")
  public static final Flag<List<TestEnum>> enumListFlag = Flag.enumList(TestEnum.class);

  @FlagSpec(name = "integer_flag", help = "Integer flag.")
  public static final Flag<Integer> integerFlag = Flag.value(123);

  @FlagSpec(name = "integer_list_flag", help = "Integer list flag.")
  public static final Flag<List<Integer>> integerListFlag = Flag.integerList();

  @FlagSpec(name = "integer_set_flag", help = "Integer set flag.")
  public static final Flag<Set<Integer>> integerSetFlag = Flag.integerSet();

  @FlagSpec(name = "integer_string_map_flag", help = "Integer string map flag.")
  public static final Flag<Map<Integer, String>> integerStringMapFlag =
      Flag.value(ImmutableMap.of());

  @FlagSpec(name = "nonnegative_integer_flag", help = "Non-negative integer flag.")
  public static final Flag<Integer> nonnegativeIntegerFlag = Flag.nonnegativeValue(0);

  @FlagSpec(name = "nonnegative_long_flag", help = "Non-negative long flag.")
  public static final Flag<Long> nonnegativeLongFlag = Flag.nonnegativeValue(0L);

  @FlagSpec(name = "nullable_string_flag", help = "Nullable string flag.")
  public static final Flag<String> nullableStringFlag = Flag.nullString();

  @FlagSpec(name = "null_nonnegative_integer_flag", help = "Null non-negative integer flag.")
  public static final Flag<Integer> nullNonnegativeIntegerFlag = Flag.nullNonnegativeValue();

  @FlagSpec(name = "null_positive_integer_flag", help = "Null positive integer flag.")
  public static final Flag<Integer> nullPositiveIntegerFlag = Flag.nullPositiveValue();

  @FlagSpec(name = "positive_integer_flag", help = "Positive integer flag.")
  public static final Flag<Integer> positiveIntegerFlag = Flag.positiveValue(10);

  @FlagSpec(name = "positive_long_flag", help = "Positive long flag.")
  public static final Flag<Long> positiveLongFlag = Flag.positiveValue(10L);

  @FlagSpec(name = "string_duration_map_flag", help = "String duration map flag.")
  public static final Flag<Map<String, Duration>> stringDurationMapFlag =
      Flag.value(ImmutableMap.of());

  @FlagSpec(name = "string_flag", help = "String flag.")
  public static final Flag<String> stringFlag = Flag.value("default_bar");

  @FlagSpec(name = "string_integer_map_flag", help = "String integer map flag.")
  public static final Flag<Map<String, Integer>> stringIntegerMapFlag =
      Flag.value(ImmutableMap.of());

  @FlagSpec(name = "string_list_flag", help = "String list flag.")
  public static final Flag<List<String>> stringListFlag = Flag.stringList();

  @FlagSpec(name = "string_string_map_flag", help = "String string map flag.")
  public static final Flag<Map<String, String>> stringStringMapFlag = Flag.stringMap();

  public enum TestEnum {
    VALUE_A,
    VALUE_B,
  }

  private FlagsForTesting() {}
}
