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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.ISetter;
import picocli.CommandLine.Model.OptionSpec;

/** Manager for managing flags. */
public final class FlagsManager {

  public static final Class<?> FLAGS_CLASS = Flags.class;

  private static final Object FLAG_SCAN_LOCK = new Object();
  private static volatile Class<?> flagsClass = FLAGS_CLASS;
  private static volatile boolean flagsScanned;
  private static volatile ImmutableMap<String, FlagEntry> allFlags;

  @SuppressWarnings("AvoidObjectArrays")
  public static void parse(String[] args) {
    ensureFlagsScanned();

    CommandSpec spec = CommandSpec.create();
    for (FlagEntry entry : allFlags.values()) {
      spec.addOption(entry.metadata().optionSpec());
    }

    CommandLine cmd = new CommandLine(spec);
    cmd.setUnmatchedArgumentsAllowed(true);
    cmd.setOverwrittenOptionsAllowed(true);

    cmd.parseArgs(args);
  }

  public static void resetAllFlagsForTest() {
    ensureFlagsScanned();

    for (FlagEntry entry : allFlags.values()) {
      entry.flag().resetForTest();
    }
  }

  @VisibleForTesting
  static void setFlagsClassForTest(Class<?> flagsClassForTest) {
    flagsClass = flagsClassForTest;
    flagsScanned = false;
    allFlags = null;
  }

  static FlagEntry getFlagByName(String name) {
    ensureFlagsScanned();

    FlagEntry entry = allFlags.get(name);
    checkArgument(entry != null, "Unknown flag name: %s", name);
    return entry;
  }

  static void setFromString(FlagEntry entry, String valueString) {
    CommandLine cmd =
        new CommandLine(CommandSpec.create().addOption(entry.metadata().optionSpec()));
    // Uses "--option=value" to ensure boolean flags are set correctly.
    cmd.parseArgs(
        String.format("%s=%s", toOptionName(entry.metadata().spec().name()), valueString));
  }

  private static void ensureFlagsScanned() {
    if (flagsScanned) {
      return;
    }
    synchronized (FLAG_SCAN_LOCK) {
      if (flagsScanned) {
        return;
      }
      scanFlags();
      flagsScanned = true;
    }
  }

  private static void scanFlags() {
    try {
      ImmutableMap.Builder<String, FlagEntry> builder = ImmutableMap.builder();

      for (Field field : flagsClass.getDeclaredFields()) {
        if (field.isAnnotationPresent(FlagSpec.class)) {
          FlagSpec spec = field.getAnnotation(FlagSpec.class);

          checkState(
              Modifier.isStatic(field.getModifiers()),
              "Flag field %s must be static",
              field.getName());

          checkState(
              field.getType().equals(Flag.class),
              "Field %s annotated with @FlagSpec must be of type Flag",
              field.getName());

          checkState(
              field.getGenericType() instanceof ParameterizedType,
              "Flag field %s must have a generic type parameter",
              field.getName());

          ParameterizedType genericType = (ParameterizedType) field.getGenericType();
          Class<?> type = (Class<?>) genericType.getActualTypeArguments()[0];

          field.setAccessible(true);
          Flag<?> flag = (Flag<?>) field.get(null);

          checkState(flag != null, "Flag field %s must not be null", field.getName());

          OptionSpec optionSpec = createOptionSpec(flag, spec, type);
          FlagMetadata metadata = new FlagMetadata(type, spec, optionSpec);

          builder.put(spec.name(), new FlagEntry(flag, metadata));
        }
      }

      allFlags = builder.buildOrThrow();
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Failed to scan flags", e);
    }
  }

  private static OptionSpec createOptionSpec(Flag<?> flag, FlagSpec spec, Class<?> type) {
    return OptionSpec.builder(toOptionName(spec.name()))
        .description(spec.help())
        .type(type)
        .setter(new FlagSetter(flag))
        .build();
  }

  private static String toOptionName(String name) {
    return "--" + name;
  }

  record FlagMetadata(Class<?> type, FlagSpec spec, OptionSpec optionSpec) {}

  record FlagEntry(Flag<?> flag, FlagMetadata metadata) {}

  private record FlagSetter(Flag<?> flag) implements ISetter {

    @CanIgnoreReturnValue
    @Override
    public <V> V set(V value) {
      @SuppressWarnings("unchecked") // Type check is in scanFlags().
      Flag<V> flag = (Flag<V>) this.flag;
      flag.setValue(value);
      return value;
    }
  }

  private FlagsManager() {}
}
