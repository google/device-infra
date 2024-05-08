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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Parser for file that contains set of command lines.
 *
 * <p>The syntax of the given file should be series of lines. Each line is a command; that is, a
 * configuration plus its options:
 *
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 */
class CommandFileParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * A pattern that matches valid macro usages and captures the name of the macro. Macro names must
   * start with an alpha character, and may contain alphanumerics, underscores, or hyphens.
   */
  private static final Pattern MACRO_PATTERN =
      Pattern.compile("([a-z][a-z0-9_-]*)\\(\\)", Pattern.CASE_INSENSITIVE);

  private final Map<String, CommandLine> macros = new HashMap<>();
  private final Map<String, List<CommandLine>> longMacros = new HashMap<>();
  private final List<CommandLine> lines = new ArrayList<>();

  private final Set<String> includedFiles = new HashSet<>();

  static class CommandLine extends ArrayList<String> {
    private final File file;
    private final int lineNumber;

    CommandLine(List<String> c, File file, int lineNumber) {
      super(c);
      this.file = file;
      this.lineNumber = lineNumber;
    }

    public File getFile() {
      return file;
    }

    public int getLineNumber() {
      return lineNumber;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof CommandLine) {
        CommandLine otherLine = (CommandLine) o;
        return super.equals(o)
            && Objects.equals(otherLine.getFile(), file)
            && otherLine.getLineNumber() == lineNumber;
      }
      return false;
    }

    @Override
    public int hashCode() {
      int listHash = super.hashCode();
      return Objects.hash(listHash, file, lineNumber);
    }
  }

  /** Represents a bitmask. Useful because it caches the number of bits which are set. */
  static class Bitmask {
    private final List<Boolean> bitmask = new ArrayList<>();
    private int numBitsSet = 0;

    public Bitmask(int nBits, boolean initialValue) {
      for (int i = 0; i < nBits; ++i) {
        bitmask.add(initialValue);
      }
      if (initialValue) {
        numBitsSet = nBits;
      }
    }

    /** Return the number of bits which are set (rather than unset) */
    public int getSetCount() {
      return numBitsSet;
    }

    public boolean get(int idx) {
      return bitmask.get(idx);
    }

    public boolean set(int idx) {
      boolean retVal = bitmask.set(idx, true);
      if (!retVal) {
        numBitsSet++;
      }
      return retVal;
    }

    public boolean unset(int idx) {
      boolean retVal = bitmask.set(idx, false);
      if (retVal) {
        numBitsSet--;
      }
      return retVal;
    }

    public boolean remove(int idx) {
      boolean retVal = bitmask.remove(idx);
      if (retVal) {
        numBitsSet--;
      }
      return retVal;
    }

    public void add(int idx, boolean val) {
      bitmask.add(idx, val);
      if (val) {
        numBitsSet++;
      }
    }

    /**
     * Insert a bunch of identical values in the specified spot in the mask
     *
     * @param idx the index where the first new value should be set.
     * @param count the number of new values to insert
     * @param val the parity of the new values
     */
    public void addN(int idx, int count, boolean val) {
      for (int i = 0; i < count; ++i) {
        add(idx, val);
      }
    }
  }

  /**
   * Checks if a line matches the expected format for a (short) macro: MACRO (name) = (token)
   * [(token)...] This method verifies that:
   *
   * <ol>
   *   <li>Line is at least four tokens long
   *   <li>The first token is "MACRO" (case-sensitive)
   *   <li>The third token is an equal-sign
   * </ol>
   *
   * @return {@code true} if the line matches the macro format, {@code false} otherwise
   */
  private static boolean isLineMacro(CommandLine line) {
    return line.size() >= 4 && "MACRO".equals(line.get(0)) && "=".equals(line.get(2));
  }

  /**
   * Checks if a line matches the expected format for the opening line of a long macro: LONG MACRO
   * (name)
   *
   * @return {@code true} if the line matches the long macro format, {@code false} otherwise
   */
  private static boolean isLineLongMacro(CommandLine line) {
    return line.size() == 3 && "LONG".equals(line.get(0)) && "MACRO".equals(line.get(1));
  }

  /**
   * Checks if a line matches the expected format for an INCLUDE directive
   *
   * @return {@code true} if the line is an INCLUDE directive, {@code false} otherwise
   */
  private static boolean isLineIncludeDirective(CommandLine line) {
    return line.size() == 2 && "INCLUDE".equals(line.get(0));
  }

  /**
   * Checks if a line should be parsed or ignored. Basically, ignore if the line is commented or is
   * empty.
   *
   * @param line A {@link String} containing the line of input to check
   * @return {@code true} if we should parse the line, {@code false} if we should ignore it.
   */
  private static boolean shouldParseLine(String line) {
    line = line.trim();
    return !(line.isEmpty() || line.startsWith("#"));
  }

  /** Return the command files included by the last parsed command file. */
  public Set<String> getIncludedFiles() {
    return includedFiles;
  }

  /**
   * Does a single pass of the input CommandFile, storing input lines as macros, long macros, or
   * commands.
   *
   * <p>Note that this method may call itself recursively to handle the INCLUDE directive.
   */
  private void scanFile(File file) throws MobileHarnessException {
    if (includedFiles.contains(file.getAbsolutePath())) {
      // Repeated include; ignore
      logger.atFiner().log("Skipping repeated include of file %s.", file);
      return;
    } else {
      includedFiles.add(file.getAbsolutePath());
    }

    int lineNumber = 0;
    try (BufferedReader fileReader = Files.newBufferedReader(file.toPath(), UTF_8)) {
      String inputLine;
      while ((inputLine = fileReader.readLine()) != null) {
        lineNumber++;
        inputLine = inputLine.trim();
        if (shouldParseLine(inputLine)) {
          CommandLine args = new CommandLine(ShellUtils.tokenize(inputLine), file, lineNumber);

          if (isLineMacro(args)) {
            // Expected format: MACRO <name> = <token> [<token>...]
            String name = args.get(1);
            CommandLine expansion = new CommandLine(args.subList(3, args.size()), file, lineNumber);
            CommandLine prev = macros.put(name, expansion);
            if (prev != null) {
              logger.atWarning().log(
                  "Overwrote short macro '%s' while parsing file %s", name, file);
              logger.atWarning().log("value '%s' replaced previous value '%s'", expansion, prev);
            }
          } else if (isLineLongMacro(args)) {
            // Expected format: LONG MACRO <name>\n(multiline expansion)\nEND MACRO
            String name = args.get(2);
            List<CommandLine> expansion = new ArrayList<>();

            inputLine = fileReader.readLine();
            lineNumber++;
            while (!Objects.equals(inputLine, "END MACRO")) {
              if (inputLine == null) {
                // Syntax error
                throw new MobileHarnessException(
                    InfraErrorId.ATSC_CMDFILE_PARSE_ERROR,
                    String.format(
                        "Syntax error: Unexpected EOF while reading definition "
                            + "for LONG MACRO %s.",
                        name));
              }
              if (shouldParseLine(inputLine)) {
                // Store the tokenized line
                CommandLine line =
                    new CommandLine(ShellUtils.tokenize(inputLine), file, lineNumber);
                expansion.add(line);
              }

              // Advance
              inputLine = fileReader.readLine();
              lineNumber++;
            }
            logger.atFine().log(
                "Parsed %d-line definition for long macro %s", expansion.size(), name);

            List<CommandLine> prev = longMacros.put(name, expansion);
            if (prev != null) {
              logger.atWarning().log("Overwrote long macro %s while parsing file %s", name, file);
              logger.atWarning().log(
                  "%d-line definition replaced previous %d-line definition",
                  expansion.size(), prev.size());
            }
          } else if (isLineIncludeDirective(args)) {
            File toScan = new File(args.get(1));
            if (toScan.isAbsolute()) {
              logger.atFine().log("Got an include directive for absolute path %s.", args.get(1));
            } else {
              File parent = file.getParentFile();
              toScan = new File(parent, args.get(1));
              logger.atFine().log(
                  "Got an include directive for relative path %s, using '%s' " + "for parent dir",
                  args.get(1), parent);
            }
            scanFile(toScan);
          } else {
            lines.add(args);
          }
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_CMDFILE_READ_ERROR, String.format("Failed to read file %s", file), e);
    } catch (TokenizationException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_CMDFILE_PARSE_ERROR,
          String.format("Failed to parse line %s of file %s", lineNumber, file),
          e);
    }
  }

  /**
   * Parses the commands contained in {@code file}, doing macro expansions as necessary
   *
   * @param file the {@link File} to parse
   * @return the list of parsed commands
   * @throws MobileHarnessException if content of file could not be parsed or failed to read file
   */
  @SuppressWarnings("unused")
  public List<CommandLine> parseFile(File file) throws MobileHarnessException {
    // clear state from last call
    includedFiles.clear();
    macros.clear();
    longMacros.clear();
    lines.clear();

    // Parse this cmdfile and all of its dependencies.
    scanFile(file);

    // remove original file from list of includes, as call above has side effect of adding it to
    // mIncludedFiles
    includedFiles.remove(file.getAbsolutePath());

    // Now perform macro expansion
    // inputBitmask is used to stop iterating when we're sure there are no more macros to expand.
    // It is a bitmask where the (k)th bit represents the (k)th element in mLines.
    //
    // Each bit starts as true, meaning that each line in mLines may have macro calls to
    // be expanded. We set bits of inputBitmask to false once we've determined that
    // the corresponding lines of mLines have been fully expanded, which allows us to skip
    // those lines on subsequent scans.
    //
    // inputBitmaskCount stores the quantity of true bits in
    // inputBitmask. Once inputBitmaskCount == 0, we are done expanding macros.
    Bitmask inputBitmask = new Bitmask(lines.size(), true);

    // Do a maximum of 20 iterations of expansion
    for (int iCount = 0; iCount < 20 && inputBitmask.getSetCount() > 0; ++iCount) {
      logger.atFine().log("### Expansion iteration %d", iCount);

      int inputIdx = 0;
      while (inputIdx < lines.size()) {
        if (!inputBitmask.get(inputIdx)) {
          // Skip this line; we've already determined that it doesn't contain any macro
          // calls to be expanded.
          logger.atFine().log("skipping input line %s", lines.get(inputIdx));
          ++inputIdx;
          continue;
        }

        CommandLine line = lines.get(inputIdx);
        boolean sawMacro = expandMacro(line);
        List<CommandLine> longMacroExpansion = expandLongMacro(line, !sawMacro);

        if (longMacroExpansion == null) {
          if (sawMacro) {
            // We saw and expanded a short macro.  This may have pulled in another macro
            // to expand, so leave inputBitmask alone.
          } else {
            // We did not find any macros (long or short) to expand, thus all expansions
            // are done for this CommandLine.  Update inputBitmask appropriately.
            boolean unused = inputBitmask.unset(inputIdx);
          }

          // Finally, advance.
          ++inputIdx;
        } else {
          // We expanded a long macro.  First, actually insert the expansion in place of
          // the macro call
          CommandLine unused1 = lines.remove(inputIdx);
          boolean unused2 = inputBitmask.remove(inputIdx);
          lines.addAll(inputIdx, longMacroExpansion);
          inputBitmask.addN(inputIdx, longMacroExpansion.size(), true);

          // And advance past the end of the expanded macro
          inputIdx += longMacroExpansion.size();
        }
      }
    }
    return lines;
  }

  /** Performs one level of macro expansion for the first macro used in the line */
  @Nullable
  private List<CommandLine> expandLongMacro(CommandLine line, boolean checkMissingMacro)
      throws MobileHarnessException {
    for (int idx = 0; idx < line.size(); ++idx) {
      String token = line.get(idx);
      Matcher matchMacro = MACRO_PATTERN.matcher(token);
      if (matchMacro.matches()) {
        // we hit a macro; expand it
        List<CommandLine> expansion = new ArrayList<>();
        String name = matchMacro.group(1);
        List<CommandLine> longMacro = longMacros.get(name);
        if (longMacro == null) {
          if (checkMissingMacro) {
            // If the expandMacro method hits an unrecognized macro, it will leave it in
            // the stream for this method.  If it's not recognized here, throw an
            // exception
            throw new MobileHarnessException(
                InfraErrorId.ATSC_CMDFILE_PARSE_ERROR,
                String.format("Macro call '%s' does not match any macro definitions.", name));
          } else {
            // At this point, it may just be a short macro
            logger.atFine().log("Macro call '%s' doesn't match any long macro definitions.", name);
            return null;
          }
        }

        List<String> prefix = new ArrayList<>(line.subList(0, idx));
        List<String> suffix = new ArrayList<>(line.subList(idx, line.size()));
        suffix.remove(0);
        for (CommandLine macroLine : longMacro) {
          CommandLine expanded =
              new CommandLine(ImmutableList.of(), line.getFile(), line.getLineNumber());
          expanded.addAll(prefix);
          expanded.addAll(macroLine);
          expanded.addAll(suffix);
          expansion.add(expanded);
        }

        // Only expand a single macro usage at a time
        return expansion;
      }
    }
    return null;
  }

  /**
   * Performs one level of macro expansion for every macro used in the line
   *
   * @return {@code true} if a macro was found and expanded, {@code false} if no macro was found
   */
  private boolean expandMacro(CommandLine line) {
    boolean sawMacro = false;

    int idx = 0;
    while (idx < line.size()) {
      String token = line.get(idx);
      Matcher matchMacro = MACRO_PATTERN.matcher(token);
      if (matchMacro.matches() && macros.containsKey(matchMacro.group(1))) {
        // we hit a macro; expand it
        String name = matchMacro.group(1);
        CommandLine macro = macros.get(name);
        logger.atFine().log("Gotcha!  Expanding macro '%s' to '%s'", name, macro);
        line.remove(idx);
        line.addAll(idx, macro);
        idx += macro.size();
        sawMacro = true;
      } else {
        ++idx;
      }
    }
    return sawMacro;
  }
}
