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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.flags.core.converter.BooleanConverter;
import com.google.devtools.mobileharness.shared.util.flags.core.converter.DurationConverter;
import com.google.devtools.mobileharness.shared.util.reflection.TypeUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.IParameterPreprocessor;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.ISetter;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

/** Manager for managing flags. */
public final class FlagsManager {

  public static final Class<?> FLAGS_CLASS = Flags.class;

  private static final String ENTRY_SEPARATOR = ",";
  private static final char KEY_VALUE_SEPARATOR = '=';
  private static final String BOOLEAN_NEGATIVE_PREFIX = "no";
  private static final Splitter ENTRY_SPLITTER = Splitter.on(ENTRY_SEPARATOR);

  private static final ImmutableMap<Class<?>, ITypeConverter<?>> TYPE_CONVERTERS =
      ImmutableMap.of(
          Duration.class, new DurationConverter(),
          Boolean.class, new BooleanConverter());

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
    OptionSpec option = entry.metadata().optionSpec();

    if (option.arity().max() > 0 && valueString == null) {
      entry.flag().setValue(null);
      return;
    }

    CommandLine cmd = new CommandLine(CommandSpec.create().addOption(option));
    registerConverters(cmd);

    if (option.arity().max() == 0) {
      // Arity 0 options (e.g., --nofoo) take no arguments. Passing a value is invalid.
      checkArgument(
          valueString == null,
          "Invalid boolean syntax for %s: %s",
          option.longestName(),
          valueString);
      cmd.parseArgs(option.longestName());
    } else {
      // Uses "--option=value" to ensure boolean flags are set correctly.
      cmd.parseArgs(String.format("%s=%s", option.longestName(), valueString));
    }
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

          // Checks type completeness.
          try {
            TypeUtil.checkCompleteness(type.getType());
          } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                String.format(
                    "Flag field %s must not contain raw types, wildcards or type variables",
                    field.getName()),
                e);
          }

          field.setAccessible(true);
          Flag<?> flag = (Flag<?>) field.get(null);

          checkState(flag != null, "Flag field %s must not be null", field.getName());

          addFlagEntry(builder, type, spec, flag, /* isPositive= */ true);

          // Supports --nofoo for boolean flags.
          if (type.getRawType() == Boolean.class) {
            addFlagEntry(builder, type, spec, flag, /* isPositive= */ false);
          }
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

  private static void addFlagEntry(
      ImmutableMap.Builder<String, FlagEntry> builder,
      TypeToken<?> type,
      FlagSpec spec,
      Flag<?> flag,
      boolean isPositive) {
    String flagName = isPositive ? spec.name() : BOOLEAN_NEGATIVE_PREFIX + spec.name();
    OptionSpec optionSpec = createOptionSpec(type, spec, flag, flagName, isPositive);
    FlagMetadata metadata = new FlagMetadata(type, spec, optionSpec, isPositive);
    builder.put(flagName, new FlagEntry(flag, metadata));
  }

  private static OptionSpec createOptionSpec(
      TypeToken<?> type, FlagSpec spec, Flag<?> flag, String flagName, boolean isPositive) {
    OptionSpec.Builder builder =
        OptionSpec.builder("--" + flagName)
            .description(spec.help())
            .type(type.getRawType())
            .setter(new FlagSetter(flag));

    // Handles negative boolean type.
    if (!isPositive) {
      builder.setter(new FalseSetter(flag));
      builder.arity("0");
    }

    // Handles collection types.
    if (type.isSubtypeOf(TypeToken.of(Collection.class))) {
      builder.splitRegex(ENTRY_SEPARATOR);
      Class<?> componentType =
          type.resolveType(Collection.class.getTypeParameters()[0]).getRawType();
      builder.auxiliaryTypes(componentType);
      builder.preprocessor(new CollectionFlagPreprocessor());
    }

    // Handles map type.
    if (type.isSubtypeOf(TypeToken.of(Map.class))) {
      builder.splitRegex(ENTRY_SEPARATOR);
      Class<?> keyType = type.resolveType(Map.class.getTypeParameters()[0]).getRawType();
      Class<?> valueType = type.resolveType(Map.class.getTypeParameters()[1]).getRawType();
      builder.auxiliaryTypes(keyType, valueType);
      builder.preprocessor(new MapFlagPreprocessor());
    }

    return builder.build();
  }

  private static Object getEmptyCollection(Class<?> type) {
    if (Set.class.isAssignableFrom(type)) {
      return ImmutableSet.of();
    } else {
      return ImmutableList.of();
    }
  }

  record FlagMetadata(
      TypeToken<?> type, FlagSpec spec, OptionSpec optionSpec, boolean isPositive) {}

  record FlagEntry(Flag<?> flag, FlagMetadata metadata) {}

  private record FlagSetter(Flag<?> flag) implements ISetter {

    @CanIgnoreReturnValue
    @Override
    public <V> V set(V value) {
      @SuppressWarnings("unchecked") // Type check is in scanFlags().
      Flag<V> flag = (Flag<V>) this.flag;

      // Sets value or overrides previous value if any.
      flag.setValue(value);
      return value;
    }
  }

  private record FalseSetter(Flag<?> flag) implements ISetter {

    @SuppressWarnings("unchecked") // Type check is in scanFlags().
    @CanIgnoreReturnValue
    @Override
    public <V> V set(V value) {
      Flag<Boolean> flag = (Flag<Boolean>) this.flag;
      flag.setValue(false);
      return (V) Boolean.FALSE;
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

  private static class MapFlagPreprocessor implements IParameterPreprocessor {

    @Override
    public boolean preprocess(
        Stack<String> args,
        CommandSpec commandSpec,
        ArgSpec argSpec,
        Map<String, Object> parsedArgs) {
      if (args.isEmpty()) {
        return false;
      }

      String text = args.pop();

      // For a map flag, if its text is an empty string, returns an empty map.
      if (text.trim().isEmpty()) {
        argSpec.setValue(ImmutableMap.of());
        return true;
      }

      Map<String, String> parsedEntries = new LinkedHashMap<>();
      for (String entry : ENTRY_SPLITTER.split(text)) {
        int keyValueSeparatorIndex = entry.indexOf(KEY_VALUE_SEPARATOR);
        if (keyValueSeparatorIndex == -1) {
          throw new ParameterException(
              commandSpec.commandLine(), "Invalid map entry syntax: " + entry);
        }
        String keyString = entry.substring(0, keyValueSeparatorIndex).trim();
        String valueString = entry.substring(keyValueSeparatorIndex + 1).trim();

        // Checks duplicated keys.
        if (parsedEntries.containsKey(keyString)) {
          throw new ParameterException(
              commandSpec.commandLine(),
              String.format("Duplicate map key '%s' in '%s'", keyString, text));
        }

        parsedEntries.put(keyString, valueString);
      }

      // Normalizes whitespace trimmed string and returns back to Picocli processor.
      String normalizedText =
          parsedEntries.entrySet().stream()
              .map(e -> e.getKey() + KEY_VALUE_SEPARATOR + e.getValue())
              .collect(Collectors.joining(ENTRY_SEPARATOR));
      args.push(normalizedText);

      return false;
    }
  }

  private FlagsManager() {}
}
