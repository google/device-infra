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

package com.google.devtools.mobileharness.infra.ats.console.command.preprocessor;

import static com.google.common.base.Ascii.equalsIgnoreCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandFileParser.CommandLine;
import java.io.File;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Preprocessor for preprocessing commands. */
public class CommandPreprocessor {

  /** Preprocessing result. */
  @AutoValue
  public abstract static class PreprocessingResult {

    public abstract Optional<ImmutableList<ImmutableList<String>>> modifiedCommands();

    public abstract Optional<String> errorMessage();

    public static PreprocessingResult of(
        @Nullable ImmutableList<ImmutableList<String>> modifiedCommands,
        @Nullable String errorMessage) {
      return new AutoValue_CommandPreprocessor_PreprocessingResult(
          Optional.ofNullable(modifiedCommands), Optional.ofNullable(errorMessage));
    }
  }

  private static final ImmutableList<String> EXIT_COMMAND = ImmutableList.of("exit", "-c", "-s");

  private final CommandFileParser commandFileParser;

  @Inject
  CommandPreprocessor(CommandFileParser commandFileParser) {
    this.commandFileParser = commandFileParser;
  }

  public PreprocessingResult preprocess(ImmutableList<String> tokens) {
    if (tokens.size() < 2) {
      return PreprocessingResult.of(/* modifiedCommands= */ null, /* errorMessage= */ null);
    }
    if (!equalsIgnoreCase(tokens.get(0), "run")) {
      return PreprocessingResult.of(/* modifiedCommands= */ null, /* errorMessage= */ null);
    }
    switch (tokens.get(1)) {
      case "command":
        return preprocessRunCommandCommand(tokens, /* exitAfterRun= */ false);
      case "cmdfile":
        return preprocessRunCmdfileCommand(tokens, /* exitAfterRun= */ false);
      case "commandAndExit":
        return preprocessRunCommandCommand(tokens, /* exitAfterRun= */ true);
      case "cmdfileAndExit":
        return preprocessRunCmdfileCommand(tokens, /* exitAfterRun= */ true);
      default:
        return PreprocessingResult.of(/* modifiedCommands= */ null, /* errorMessage= */ null);
    }
  }

  private static PreprocessingResult preprocessRunCommandCommand(
      ImmutableList<String> tokens, boolean exitAfterRun) {
    ImmutableList.Builder<String> modifiedTokens =
        ImmutableList.<String>builder().add(tokens.get(0)).addAll(tokens.subList(2, tokens.size()));
    ImmutableList.Builder<ImmutableList<String>> commandsBuilder = ImmutableList.builder();
    commandsBuilder.add(modifiedTokens.build());
    if (exitAfterRun) {
      commandsBuilder.add(EXIT_COMMAND);
    }
    return PreprocessingResult.of(commandsBuilder.build(), /* errorMessage= */ null);
  }

  private PreprocessingResult preprocessRunCmdfileCommand(
      ImmutableList<String> tokens, boolean exitAfterRun) {
    if (tokens.size() < 3) {
      return PreprocessingResult.of(/* modifiedCommands= */ null, "Cmdfile path is not specified");
    }
    ImmutableList.Builder<ImmutableList<String>> commandsBuilder = ImmutableList.builder();
    List<CommandLine> commandsFromFile;
    try {
      commandsFromFile = commandFileParser.parseFile(new File(tokens.get(2)));
    } catch (MobileHarnessException e) {
      return PreprocessingResult.of(
          /* modifiedCommands= */ null,
          String.format("Failed to read cmdfile: %s", shortDebugString(e)));
    }
    String firstToken = tokens.get(0);
    ImmutableList<String> extraArgs = tokens.subList(3, tokens.size());
    commandsBuilder.addAll(
        commandsFromFile.stream()
            .map(
                command -> {
                  ImmutableList.Builder<String> modifiedTokens =
                      ImmutableList.<String>builder()
                          .add(firstToken)
                          .addAll(command)
                          .addAll(extraArgs);
                  return modifiedTokens.build();
                })
            .collect(toImmutableList()));
    if (exitAfterRun) {
      commandsBuilder.add(EXIT_COMMAND);
    }
    return PreprocessingResult.of(commandsBuilder.build(), /* errorMessage= */ null);
  }
}
