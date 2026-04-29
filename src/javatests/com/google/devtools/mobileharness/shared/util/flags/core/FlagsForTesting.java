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
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Flags for testing. */
public class FlagsForTesting {

  @FlagSpec(name = "foo", help = "An integer flag.")
  public static final Flag<Integer> foo = Flag.value(123);

  @FlagSpec(name = "bar", help = "A string flag.")
  public static final Flag<String> bar = Flag.value("default_bar");

  @FlagSpec(name = "baz", help = "A boolean flag.")
  public static final Flag<Boolean> baz = Flag.value(false);

  @FlagSpec(name = "timeout", help = "A duration flag.")
  public static final Flag<Duration> timeout = Flag.value(Duration.ZERO);

  @FlagSpec(name = "names", help = "A list of strings.")
  public static final Flag<List<String>> names = Flag.value(ImmutableList.of());

  @FlagSpec(name = "ids", help = "A list of integers.")
  public static final Flag<List<Integer>> ids = Flag.value(ImmutableList.of());

  @FlagSpec(name = "boolList", help = "A list of booleans.")
  public static final Flag<List<Boolean>> boolList = Flag.value(ImmutableList.of());

  @FlagSpec(name = "idsSet", help = "A set of integers.")
  public static final Flag<Set<Integer>> idsSet = Flag.value(ImmutableSet.of());

  @FlagSpec(name = "stringMap", help = "A map of strings.")
  public static final Flag<Map<String, String>> stringMap = Flag.value(ImmutableMap.of());

  @FlagSpec(name = "intMap", help = "A map of string keys to integer values.")
  public static final Flag<Map<String, Integer>> intMap = Flag.value(ImmutableMap.of());

  @FlagSpec(name = "durationMap", help = "A map of string keys to duration values.")
  public static final Flag<Map<String, Duration>> durationMap = Flag.value(ImmutableMap.of());

  @FlagSpec(name = "intKeyMap", help = "A map of integer keys to string values.")
  public static final Flag<Map<Integer, String>> intKeyMap = Flag.value(ImmutableMap.of());

  public enum TestEnum {
    VALUE_A,
    VALUE_B,
  }

  @FlagSpec(name = "testEnum", help = "A test enum flag.")
  public static final Flag<TestEnum> testEnum = Flag.value(TestEnum.VALUE_A);

  @FlagSpec(name = "enumList", help = "A list of test enums.")
  public static final Flag<List<TestEnum>> enumList = Flag.value(ImmutableList.of());

  private FlagsForTesting() {}
}
