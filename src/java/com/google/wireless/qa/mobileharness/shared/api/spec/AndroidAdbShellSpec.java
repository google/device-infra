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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/** Spec for AndroidAdbShellDecorator. */
public final class AndroidAdbShellSpec {

  /**
   * Parses the user-supplied, comma-separated list of commands into discrete commands to be
   * executed.
   */
  public static Iterable<String> parseCommands(@Nullable String commandList) {
    if (Strings.isNullOrEmpty(commandList)) {
      return ImmutableList.of();
    }
    // Split commands on unescaped commas, then undo any user-supplied comma escapes.
    Iterable<String> commands = COMMANDS_SPLITTER.split(commandList);
    return stream(commands).map(command -> command.replace("\\,", ",")).collect(toImmutableList());
  }

  /** Parses discrete commands to comma-separated list of commands. */
  public static String parseDiscreteCommands(Iterable<String> commands) {
    return StreamSupport.stream(commands.spliterator(), /* parallel= */ false)
        .map(command -> command.replace(",", "\\,"))
        .collect(joining(","));
  }

  // Commands are separated by an unescaped comma.
  private static final Splitter COMMANDS_SPLITTER =
      Splitter.on(Pattern.compile("(?<!\\\\),")).omitEmptyStrings().trimResults();

  private AndroidAdbShellSpec() {}
}
