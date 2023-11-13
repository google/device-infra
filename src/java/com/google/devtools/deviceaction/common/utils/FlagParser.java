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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.ActionOptions.Options;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A parser of cli flags. */
public final class FlagParser {

  /** The default flags for adb initializer. */
  private static final ImmutableList<String> MUST_HAVE_FLAGS =
      ImmutableList.of(
          "--adb_dont_kill_server",
          "--external_adb_initializer_template",
          "--adb_command_retry_attempts",
          "1");

  private static final String KEY_PREFIX = "--";

  private static final class OptionConsumer {

    private static final String KEY_VAL_SEPARATOR = "=";
    private static final String ENTRY_SEPARATOR = ",";
    private static final String FILE_PREFIX = "file_";

    final Options.Builder actionOptionsBuilder = Options.builder();
    final Options.Builder firstOptionsBuilder = Options.builder();
    final Options.Builder secondOptionsBuilder = Options.builder();

    OptionConsumer() {}

    boolean parseKeyValue(String key, String val) {
      switch (key) {
        case "--action":
          parseValueToBuilder(actionOptionsBuilder, val);
          return true;
        case "--device1":
          parseValueToBuilder(firstOptionsBuilder, val);
          return true;
        case "--device2":
          parseValueToBuilder(secondOptionsBuilder, val);
          return true;
        default:
          return false;
      }
    }

    boolean parseFlag(String flag) {
      if (flag.contains(KEY_VAL_SEPARATOR)) {
        String[] splits = flag.split(KEY_VAL_SEPARATOR, 2);
        return parseKeyValue(splits[0].trim(), splits[1].trim());
      }
      return false;
    }

    private void parseValueToBuilder(Options.Builder builder, String val) {
      for (String item : Splitter.onPattern(ENTRY_SEPARATOR).split(val)) {
        parseSingleValueToBuilder(builder, item.trim());
      }
    }

    private void parseSingleValueToBuilder(Options.Builder builder, String val) {
      if (val.contains(KEY_VAL_SEPARATOR)) {
        String[] splits = val.split(KEY_VAL_SEPARATOR, 2);
        parseKeyValueToBuilder(builder, splits[0].trim(), splits[1].trim());
      } else {
        parseBoolToBuilder(builder, val);
      }
    }

    private void parseKeyValueToBuilder(Options.Builder builder, String key, String val) {
      if (val.equals("true")) {
        builder.addTrueBoolOptions(key);
      } else if (val.equals("false")) {
        builder.addFalseBoolOptions(key);
      } else if (key.startsWith(FILE_PREFIX)) {
        key = key.substring(FILE_PREFIX.length());
        builder.addFileOptions(key, val);
      } else {
        builder.addKeyValues(key, val);
      }
    }

    private void parseBoolToBuilder(Options.Builder builder, String option) {
      if (option.startsWith("no")) {
        builder.addFalseBoolOptions(option.substring("no".length()));
      } else {
        builder.addTrueBoolOptions(option);
      }
    }

    Options getAction() throws DeviceActionException {
      return actionOptionsBuilder.build();
    }

    Options getFirstDevice() throws DeviceActionException {
      return firstOptionsBuilder.build();
    }

    Options getSecondDevice() throws DeviceActionException {
      return secondOptionsBuilder.build();
    }
  }

  /**
   * Parses flags and returns the structured action options.
   *
   * <p>All action-related options are returned in the structured {@link ActionOptions}. All the
   * rest flags are parsed by the standard flag parser.
   *
   * <p>The action-related options are signaled by "--action", "--device1" or "--device2". The rules
   * for the flags are:
   *
   * <ul>
   *   <li>`--action "key1 = val1, key2 = val2, bool_flag"`.
   *   <li>`"--action=key1 = val1, key2 = val2, bool_flag"`.
   *   <li>`--action key1=val1 --action key2=val2 --action bool_flag`.
   *   <li>If a flag is not a key value pair, it must be a bool flag like "bool_flag" or
   *       "nobool_flag".
   *   <li>a file flag is like "file_<tag>=<file path>".
   * </ul>
   *
   * @param args to parse.
   * @return {@link ActionOptions} containing all action-related options.
   * @throws DeviceActionException if the first arg is not a command, or any arg is in invalid
   *     format.
   */
  public static ActionOptions parse(String[] args) throws DeviceActionException {
    ArrayList<String> leftOvers = new ArrayList<>(Arrays.asList(args));
    ActionOptions options = parseOptions(leftOvers);
    args = leftOvers.toArray(new String[0]);
    Flags.parse(args);
    return options;
  }

  /**
   * Gets the {@link ActionOptions} and remove all action related args from the list {@code
   * leftOver}.
   *
   * @param leftOver An {@link ArrayList} to support remove operation and constant access operation.
   */
  @VisibleForTesting
  static ActionOptions parseOptions(ArrayList<String> leftOver) throws DeviceActionException {
    Conditions.checkArgument(
        !leftOver.isEmpty(), ErrorType.CUSTOMER_ISSUE, "A command should at least be provided.");
    ActionOptions.Builder builder = ActionOptions.builder();
    builder.setCommand(Command.of(leftOver.get(0)));
    leftOver.remove(0);
    OptionConsumer consumer = new OptionConsumer();
    int i = 0;
    while (i < leftOver.size()) {
      Conditions.checkArgument(
          isKey(leftOver.get(i)),
          ErrorType.CUSTOMER_ISSUE,
          "Flag %s should start with --.",
          leftOver.get(i));
      if (isValue(leftOver, /* index= */ i + 1)) {
        if (consumer.parseKeyValue(leftOver.get(i), leftOver.get(i + 1))) {
          leftOver.remove(i);
          leftOver.remove(i);
        } else {
          i += 2;
        }
      } else {
        if (consumer.parseFlag(leftOver.get(i))) {
          leftOver.remove(i);
        } else {
          i += 1;
        }
      }
    }
    leftOver.addAll(MUST_HAVE_FLAGS);
    return builder
        .setAction(consumer.getAction())
        .setFirstDevice(consumer.getFirstDevice())
        .setSecondDevice(consumer.getSecondDevice())
        .build();
  }

  private static boolean isValue(List<String> list, int index) {
    return index >= 0 && index < list.size() && !isKey(list.get(index));
  }

  private static boolean isKey(String option) {
    return option.startsWith(KEY_PREFIX);
  }

  private FlagParser() {}
}
