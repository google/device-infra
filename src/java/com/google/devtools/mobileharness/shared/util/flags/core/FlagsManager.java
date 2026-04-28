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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.flags.core.converter.DurationConverter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import picocli.CommandLine;
import picocli.CommandLine.IParameterPreprocessor;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.ISetter;
import picocli.CommandLine.Model.OptionSpec;

/** Manager for managing flags. */
public final class FlagsManager {

  public static final Class<?> FLAGS_CLASS = Flags.class;

  private static final ImmutableMap<Class<?>, ITypeConverter<?>> TYPE_CONVERTERS =
      ImmutableMap.of(Duration.class, new DurationConverter());

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
    registerConverters(cmd);

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
    registerConverters(cmd);
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
          TypeToken<?> type = TypeToken.of(genericType.getActualTypeArguments()[0]);

          field.setAccessible(true);
          Flag<?> flag = (Flag<?>) field.get(null);

          checkState(flag != null, "Flag field %s must not be null", field.getName());

          OptionSpec optionSpec = createOptionSpec(type, spec, flag);
          FlagMetadata metadata = new FlagMetadata(type, spec, optionSpec);

          builder.put(spec.name(), new FlagEntry(flag, metadata));
        }
      }

      allFlags = builder.buildOrThrow();
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Failed to scan flags", e);
    }
  }

  private static void registerConverters(CommandLine cmd) {
    for (Map.Entry<Class<?>, ITypeConverter<?>> entry : TYPE_CONVERTERS.entrySet()) {
      @SuppressWarnings("unchecked") // The type of the converter matches the key class.
      Class<Object> type = (Class<Object>) entry.getKey();
      @SuppressWarnings("unchecked") // The type of the converter matches the key class.
      ITypeConverter<Object> converter = (ITypeConverter<Object>) entry.getValue();
      cmd.registerConverter(type, converter);
    }
  }

  private static OptionSpec createOptionSpec(TypeToken<?> type, FlagSpec spec, Flag<?> flag) {
    OptionSpec.Builder builder =
        OptionSpec.builder(toOptionName(spec.name()))
            .description(spec.help())
            .type(type.getRawType())
            .setter(new FlagSetter(flag));

    // Handles collection types.
    if (type.isSubtypeOf(TypeToken.of(Collection.class))) {
      builder.splitRegex(",");
      Class<?> componentType =
          type.resolveType(Collection.class.getTypeParameters()[0]).getRawType();
      builder.auxiliaryTypes(componentType);
      builder.preprocessor(new CollectionFlagPreprocessor());
    }

    return builder.build();
  }

  private static String toOptionName(String name) {
    return "--" + name;
  }

  private static Object getEmptyCollection(Class<?> type) {
    if (Set.class.isAssignableFrom(type)) {
      return ImmutableSet.of();
    } else {
      return ImmutableList.of();
    }
  }

  record FlagMetadata(TypeToken<?> type, FlagSpec spec, OptionSpec optionSpec) {}

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

  private static class CollectionFlagPreprocessor implements IParameterPreprocessor {

    @Override
    public boolean preprocess(
        Stack<String> args,
        CommandSpec commandSpec,
        ArgSpec argSpec,
        Map<String, Object> parsedArgs) {
      if (args.isEmpty()) {
        return false;
      }

      // For a collection flag, if its text is an empty string, returns an empty collection.
      String value = args.peek();
      if (value.isEmpty()) {
        args.pop();
        argSpec.setValue(getEmptyCollection(argSpec.type()));
        return true;
      }

      return false;
    }
  }

  private FlagsManager() {}
}
