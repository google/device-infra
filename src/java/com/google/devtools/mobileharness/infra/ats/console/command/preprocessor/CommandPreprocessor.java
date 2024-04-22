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
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Preprocessor for preprocessing commands. */
public class CommandPreprocessor {

  /** Preprocessing result. */
  @AutoValue
  public abstract static class PreprocessingResult {

    public abstract Optional<ImmutableList<String>> modifiedTokens();

    public abstract Optional<String> errorMessage();

    public static PreprocessingResult of(
        @Nullable ImmutableList<String> modifiedTokens, @Nullable String errorMessage) {
      return new AutoValue_CommandPreprocessor_PreprocessingResult(
          Optional.ofNullable(modifiedTokens), Optional.ofNullable(errorMessage));
    }
  }

  private static final String OPTION_EXIT_AFTER_RUN = "--exit-after-run=true";

  private final LocalFileUtil localFileUtil;

  @Inject
  CommandPreprocessor(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  public PreprocessingResult preprocess(ImmutableList<String> tokens) {
    if (tokens.size() < 2) {
      return PreprocessingResult.of(/* modifiedTokens= */ null, /* errorMessage= */ null);
    }
    if (!equalsIgnoreCase(tokens.get(0), "run")) {
      return PreprocessingResult.of(/* modifiedTokens= */ null, /* errorMessage= */ null);
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
        return PreprocessingResult.of(/* modifiedTokens= */ null, /* errorMessage= */ null);
    }
  }

  private static PreprocessingResult preprocessRunCommandCommand(
      ImmutableList<String> tokens, boolean exitAfterRun) {
    ImmutableList.Builder<String> modifiedTokens =
        ImmutableList.<String>builder().add(tokens.get(0)).addAll(tokens.subList(2, tokens.size()));
    if (exitAfterRun) {
      modifiedTokens.add(OPTION_EXIT_AFTER_RUN);
    }
    return PreprocessingResult.of(modifiedTokens.build(), /* errorMessage= */ null);
  }

  private PreprocessingResult preprocessRunCmdfileCommand(
      ImmutableList<String> tokens, boolean exitAfterRun) {
    if (tokens.size() < 3) {
      return PreprocessingResult.of(/* modifiedTokens= */ null, "Cmdfile path is not specified");
    }
    String fileContent;
    try {
      fileContent = localFileUtil.readFile(tokens.get(2)).trim();
    } catch (MobileHarnessException e) {
      return PreprocessingResult.of(
          /* modifiedTokens= */ null,
          String.format("Failed to read cmdfile: %s", shortDebugString(e)));
    }
    ImmutableList<String> tokensFromFile;
    try {
      tokensFromFile = ShellUtils.tokenize(fileContent);
    } catch (TokenizationException e) {
      return PreprocessingResult.of(
          /* modifiedTokens= */ null,
          String.format(
              "Invalid input from cmdfile: [%s], error=[%s]", fileContent, shortDebugString(e)));
    }
    ImmutableList.Builder<String> modifiedTokens =
        ImmutableList.<String>builder()
            .add(tokens.get(0))
            .addAll(tokensFromFile)
            .addAll(tokens.subList(3, tokens.size()));
    if (exitAfterRun) {
      modifiedTokens.add(OPTION_EXIT_AFTER_RUN);
    }
    return PreprocessingResult.of(modifiedTokens.build(), /* errorMessage= */ null);
  }
}
