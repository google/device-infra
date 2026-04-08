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

package com.google.devtools.mobileharness.shared.util.failureanalyzer;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.failureanalyzer.proto.FailureAnalysisConfigProto.FailureAnalysisConfig;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Utility for analyzing test failures by scanning log files for known error signatures. */
public class FailureAnalyzer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final String COBALT_LOG = "cobalt.log";
  @VisibleForTesting static final String TEST_OUTPUT = "test_output.txt";

  private static final String CONFIG_FILE = "failure_analysis_config.textproto";

  static final class AnalysisPattern {
    final String fileName;
    final Pattern pattern;
    final String message;

    AnalysisPattern(String fileName, Pattern pattern, String message) {
      this.fileName = fileName;
      this.pattern = pattern;
      this.message = message;
    }
  }

  private final List<AnalysisPattern> standardPatterns;

  public FailureAnalyzer() {
    this.standardPatterns = loadStandardPatterns();
  }

  @VisibleForTesting
  FailureAnalyzer(List<AnalysisPattern> standardPatterns) {
    this.standardPatterns = standardPatterns;
  }

  /**
   * Loads the standard failure analysis patterns from the configuration file.
   *
   * @return a list of compiled FailureAnalysisPattern objects
   */
  private ImmutableList<AnalysisPattern> loadStandardPatterns() {
    ImmutableList<AnalysisPattern> result = ImmutableList.of();
    InputStream inputStream = FailureAnalyzer.class.getResourceAsStream("/" + CONFIG_FILE);
    if (inputStream == null) {
      inputStream = FailureAnalyzer.class.getResourceAsStream(CONFIG_FILE);
    }

    if (inputStream == null) {
      logger.atWarning().log("Configuration file %s not found.", CONFIG_FILE);
    } else {
      try (InputStream is = inputStream) {
        FailureAnalysisConfig.Builder configBuilder = FailureAnalysisConfig.newBuilder();
        TextFormat.merge(new InputStreamReader(is, StandardCharsets.UTF_8), configBuilder);
        FailureAnalysisConfig config = configBuilder.build();
        result =
            config.getPatternList().stream()
                .map(
                    p ->
                        new AnalysisPattern(
                            p.getFileName(), Pattern.compile(p.getPattern()), p.getMessage()))
                .collect(toImmutableList());
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to load standard patterns from %s", CONFIG_FILE);
      }
    }
    return result;
  }

  /**
   * Analyzes the test failure by scanning log files and in-memory logs for known error patterns.
   *
   * @param testInfo the test information containing logs and output directory
   * @return an Optional containing a descriptive failure message if a pattern match is found,
   *     otherwise empty
   */
  public Optional<String> analyze(TestInfo testInfo) {
    Optional<String> result = Optional.empty();
    try {
      Path genFileDir = Path.of(testInfo.getGenFileDir());
      String testLog = testInfo.log().get(0);

      // First, scan in-memory test log for patterns targeting TEST_OUTPUT.
      // This is because this log file is generated from the in-memory log buffer after the test
      // ends, so the file might not be available on disk when the analyzer runs.
      result =
          standardPatterns.stream()
              .filter(p -> p.fileName.equals(TEST_OUTPUT))
              .filter(p -> p.pattern.matcher(testLog).find())
              .peek(
                  p ->
                      testInfo
                          .log()
                          .atInfo()
                          .alsoTo(logger)
                          .log("Failure Analysis (from memory): %s", p.message))
              .map(p -> p.message)
              .findFirst();

      // If no match in memory, scan files on disk.
      if (result.isEmpty()) {
        result =
            standardPatterns.stream()
                .map(
                    p ->
                        analyzeFile(testInfo, genFileDir.resolve(p.fileName), p.pattern, p.message))
                .flatMap(Optional::stream)
                .findFirst();
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to get gen file directory for analysis.");
    }

    return result;
  }

  /**
   * Scans a specific file on the filesystem for a matching pattern.
   *
   * @param testInfo the test information for logging findings
   * @param filePath the path to the file to be analyzed
   * @param pattern the regex pattern to search for
   * @param message the failure message to return if the pattern is found
   * @return an Optional containing the failure message if a match is found, empty otherwise
   */
  private Optional<String> analyzeFile(
      TestInfo testInfo, Path filePath, Pattern pattern, String message) {
    Optional<String> result = Optional.empty();
    if (Files.exists(filePath)) {
      try {
        String content = Files.readString(filePath);
        if (pattern.matcher(content).find()) {
          testInfo.log().atInfo().alsoTo(logger).log("Failure Analysis: %s", message);
          result = Optional.of(message);
        }
      } catch (IOException e) {
        testInfo
            .log()
            .atWarning()
            .withCause(e)
            .alsoTo(logger)
            .log("Failed to read file for analysis: %s", filePath);
      }
    }
    return result;
  }
}
