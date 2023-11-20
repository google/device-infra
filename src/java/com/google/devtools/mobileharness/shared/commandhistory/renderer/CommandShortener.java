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

package com.google.devtools.mobileharness.shared.commandhistory.renderer;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Command shortener for making a command argument shorter, e.g., by replacing a long path with its
 * abbreviation.
 */
@AutoValue
public abstract class CommandShortener {

  /** Equivalent to "command = command.endsWith(suffix) ? commandReplacement : command;" */
  public static CommandShortener ifEndsWith(String suffix, String commandReplacement) {
    return new AutoValue_CommandShortener(
        /* endsWithSuffix= */ Optional.of(suffix),
        /* endsWithCommandReplacement= */ Optional.of(commandReplacement),
        /* replaceTarget= */ Optional.empty(),
        /* replaceReplacement= */ Optional.empty());
  }

  /** Equivalent to "command = command.replace(target, replacement);" */
  public static CommandShortener replace(String target, String replacement) {
    return new AutoValue_CommandShortener(
        /* endsWithSuffix= */ Optional.empty(),
        /* endsWithCommandReplacement= */ Optional.empty(),
        /* replaceTarget= */ Optional.of(target),
        /* replaceReplacement= */ Optional.of(replacement));
  }

  /** Makes a command argument shorter if possible. */
  public Optional<CommandShorteningResult> shortenCommand(String command) {
    if (endsWithSuffix().isPresent()) {
      return command.endsWith(endsWithSuffix().get())
          ? Optional.of(
              CommandShorteningResult.of(
                  /* resultCommand= */ endsWithCommandReplacement().orElseThrow(),
                  /* fromPart= */ command,
                  /* toPart= */ endsWithCommandReplacement().orElseThrow()))
          : Optional.empty();
    } else {
      String result =
          command.replace(replaceTarget().orElseThrow(), replaceReplacement().orElseThrow());
      return command.equals(result)
          ? Optional.empty()
          : Optional.of(
              CommandShorteningResult.of(
                  /* resultCommand= */ result,
                  /* fromPart= */ replaceTarget().orElseThrow(),
                  /* toPart= */ replaceReplacement().orElseThrow()));
    }
  }

  /** Result of a command shortening operation. */
  @AutoValue
  public abstract static class CommandShorteningResult {

    public static CommandShorteningResult of(String resultCommand, String fromPart, String toPart) {
      return new AutoValue_CommandShortener_CommandShorteningResult(
          resultCommand, CommandShorteningNote.of(/* fromPart= */ fromPart, /* toPart= */ toPart));
    }

    abstract String resultCommand();

    abstract CommandShorteningNote note();
  }

  /** Note of a command shortening operation. */
  @AutoValue
  public abstract static class CommandShorteningNote {

    public static CommandShorteningNote of(String fromPart, String toPart) {
      return new AutoValue_CommandShortener_CommandShorteningNote(fromPart, toPart);
    }

    abstract String fromPart();

    abstract String toPart();
  }

  /**
   * Belongs to the field group A.
   *
   * <p>If this field is present, all fields in the same field group must be present, and all fields
   * in the other field groups must be empty.
   */
  abstract Optional<String> endsWithSuffix();

  /**
   * Belongs to the field group A.
   *
   * <p>If this field is present, all fields in the same field group must be present, and all fields
   * in the other field groups must be empty.
   */
  abstract Optional<String> endsWithCommandReplacement();

  /**
   * Belongs to the field group B.
   *
   * <p>If this field is present, all fields in the same field group must be present, and all fields
   * in the other field groups must be empty.
   */
  abstract Optional<String> replaceTarget();

  /**
   * Belongs to the field group B.
   *
   * <p>If this field is present, all fields in the same field group must be present, and all fields
   * in the other field groups must be empty.
   */
  abstract Optional<String> replaceReplacement();
}
