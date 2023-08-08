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

package com.google.devtools.deviceaction.common.schemas;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import java.io.PrintStream;
import java.text.BreakIterator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/** A value class for command help info. */
@AutoValue
public abstract class CommandHelp {
  private static final String LINE_SEPARATOR = System.lineSeparator();
  private static final int MAX_WIDTH = 80;
  private static final int INDENT_SIZE = 4;

  abstract String commandName();

  abstract String commandDescription();

  abstract ImmutableSetMultimap<String, OptionDescription> flags();

  public static Builder builder() {
    return new AutoValue_CommandHelp.Builder();
  }

  /** Bulder for {@link CommandHelp}. */
  @AutoValue.Builder
  public abstract static class Builder {
    private final ImmutableSetMultimap.Builder<String, OptionDescription> flagsBuilder =
        ImmutableSetMultimap.<String, OptionDescription>builder()
            .orderKeysBy(Comparator.naturalOrder())
            .orderValuesBy(Comparator.naturalOrder());

    public abstract Builder setCommandName(String value);

    public abstract Builder setCommandDescription(String value);

    public abstract Builder setFlags(ImmutableSetMultimap<String, OptionDescription> flags);

    public Builder addFlag(String fieldName, OptionDescription flag) {
      flagsBuilder.put(fieldName, flag);
      return this;
    }

    abstract CommandHelp autoBuild();

    public CommandHelp build() {
      // Work around the fact that AutoValue doesn't work with ImmutableSortedSet.
      setFlags(flagsBuilder.build());
      return autoBuild();
    }
  }

  private void printDescription(PrintStream output) {
    output.println(
        wrap(
            commandDescription(),
            MAX_WIDTH,
            /* firstLineIndent= */ INDENT_SIZE,
            /* otherLinesIndent= */ INDENT_SIZE));
    output.println();
  }

  /** Prints summary of the command. */
  public void printSummary(PrintStream output) {
    output.printf("%s command:%n", commandName());
    printDescription(output);
  }

  /** Prints details of the command. */
  public void printDetails(PrintStream output) {
    output.println("Description:");
    printDescription(output);

    output.println("Synopsis:");
    output.println(
        wrap(
            String.format("DeviceActionMain %s", commandName()),
            MAX_WIDTH,
            INDENT_SIZE,
            INDENT_SIZE));
    for (String key : flags().keySet()) {
      ImmutableSet<OptionDescription> flags = flags().get(key);
      if (flags.size() > 1) {
        StringJoiner sj = new StringJoiner("|", "(", ")");
        for (OptionDescription flag : flags) {
          sj.add(flag.showSyntax());
        }
        output.println(
            wrap(
                sj.toString(),
                MAX_WIDTH,
                /* firstLineIndent= */ INDENT_SIZE * 2,
                /* otherLinesIndent= */ INDENT_SIZE * 3));
      } else {
        output.println(
            wrap(
                flags.stream().findAny().get().showSyntax(),
                MAX_WIDTH,
                /* firstLineIndent= */ INDENT_SIZE * 2,
                /* otherLinesIndent= */ INDENT_SIZE * 3));
      }
    }
    output.println();
    output.println("Flags:");
    for (OptionDescription flag : flags().values()) {
      output.println(
          wrap(
              flag.showDescription(),
              MAX_WIDTH,
              /* firstLineIndent= */ INDENT_SIZE,
              /* otherLinesIndent= */ INDENT_SIZE * 2));
      output.println();
    }
  }

  /** Full description of an option in cli. */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  public abstract static class OptionDescription implements Comparable<OptionDescription> {
    /** Flags like --device1, --action. */
    abstract String flag();

    /** The keys of the options. */
    @Nullable
    abstract String key();

    /** Is the option optional. */
    abstract boolean isOptional();

    /** Description of the option. */
    abstract String description();

    /** Example values of the option. */
    abstract ImmutableList<String> exampleValues();

    public static Builder builder() {
      return new AutoValue_CommandHelp_OptionDescription.Builder().setIsOptional(true);
    }

    /** Builder for the {@link OptionDescription} object. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract ImmutableList.Builder<String> exampleValuesBuilder();

      /** Sets the name of the flag (without the "--"). */
      public abstract Builder setFlag(String value);

      public abstract Builder setKey(String value);

      public abstract Builder setIsOptional(boolean value);

      /**
       * Sets example values for the option.
       *
       * <p>The example value will be printed as {@code --flag
       * "key=<exampleValue1>,key=<exampleValue2>"}
       *
       * <p>If not set, the option is a boolean option printed as {@code --flag key}
       */
      @CanIgnoreReturnValue
      public Builder addExampleValues(String... values) {
        exampleValuesBuilder().add(values);
        return this;
      }

      /** Sets the description of the option. */
      public abstract Builder setDescription(String value);

      /** Same as {@link #setDescription(String)} but allowing formatted string. */
      @FormatMethod
      Builder setDescription(@FormatString String description, Object... args) {
        return setDescription(String.format(description, args));
      }

      public abstract OptionDescription build();
    }

    public String showSyntax() {
      StringBuilder builder = new StringBuilder();
      if (isOptional()) {
        builder.append("[");
      }
      builder.append('\"').append("--").append(flag());
      if (key() == null) {
        exampleValues().stream()
            .findAny()
            .map(e -> String.format("<%s>", e))
            .ifPresent(
                st -> builder.append(" ").append(st)); // The config flags are never repeated.
      } else if (!exampleValues().isEmpty()) {
        builder
            .append(" ")
            .append(
                exampleValues().stream()
                    .map(e -> String.format("%s=<%s>", key(), e))
                    .collect(joining(",")));
      } else {
        builder.append(" ").append(key());
      }
      builder.append('\"');
      if (isOptional()) {
        builder.append("]");
      }
      return builder.toString();
    }

    public String showDescription() {
      StringBuilder builder = new StringBuilder();
      builder.append(Optional.ofNullable(key()).orElse("--" + flag())).append(": ");
      if (isOptional()) {
        builder.append("(Optional) ");
      }
      builder.append(description());
      return builder.toString();
    }

    /** Order in which we display the flags: First mandatory flags, then ordered alphabetically. */
    @Override
    public int compareTo(OptionDescription other) {
      return ComparisonChain.start()
          .compare(this.flag(), other.flag())
          .compareFalseFirst(this.isOptional(), other.isOptional())
          .compare(this.key(), other.key())
          .result();
    }
  }

  /**
   * Wraps {@code text} so it fits within {@code maxWidth} columns.
   *
   * <p>The first line will be indented by {@code firstLineIndent} spaces while all the other lines
   * will be indented by {@code otherLinesIndent} spaces.
   */
  @CheckReturnValue
  public static String wrap(String text, int maxWidth, int firstLineIndent, int otherLinesIndent) {
    int newLineIdx = text.indexOf(LINE_SEPARATOR);
    if (newLineIdx != -1) {
      // If a line break is found in the sentence, then we wrap the text recursively for each part.
      return wrap(text.substring(0, newLineIdx), maxWidth, firstLineIndent, otherLinesIndent)
          + LINE_SEPARATOR
          + wrap(
              text.substring(newLineIdx + LINE_SEPARATOR.length()),
              maxWidth,
              firstLineIndent,
              otherLinesIndent);
    }

    BreakIterator boundary = BreakIterator.getLineInstance(Locale.ENGLISH);
    boundary.setText(text);

    int start = boundary.first();
    int end = boundary.next();
    // The text wrapped as it will be returned.
    StringBuilder wrappedText = new StringBuilder();
    // The current line being built.
    StringBuilder line = new StringBuilder(Strings.repeat(" ", firstLineIndent));

    while (end != BreakIterator.DONE) {
      String word = text.substring(start, end);
      if (line.length() + word.trim().length() > maxWidth) {
        wrappedText
            .append(CharMatcher.whitespace().trimTrailingFrom(line.toString()))
            .append(LINE_SEPARATOR);
        line = new StringBuilder(Strings.repeat(" ", otherLinesIndent));
      }
      line.append(word);
      start = end;
      end = boundary.next();
    }
    wrappedText.append(line);
    return wrappedText.toString();
  }
}
