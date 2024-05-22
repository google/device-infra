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

package com.google.devtools.mobileharness.infra.ats.console.util.result;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Helper for listing results. */
public class ResultListerHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CompatibilityReportParser compatibilityReportParser;
  private final LocalFileUtil localFileUtil;

  @Inject
  ResultListerHelper(
      CompatibilityReportParser compatibilityReportParser, LocalFileUtil localFileUtil) {
    this.compatibilityReportParser = compatibilityReportParser;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Lists all the results and their directories directly in the "results" dir, ordered by dir name.
   *
   * @param resultsDir the directory to list results from.
   * @param shallow whether to parse the full result or just the summary.
   */
  public Map<Result, File> listResults(String resultsDir, boolean shallow)
      throws MobileHarnessException {
    LinkedHashMap<Result, File> results = new LinkedHashMap<>();
    // Lists all dirs under XTS_ROOT_DIR/android-cts/results and sorts by dir name.
    ImmutableList<File> resultDirs =
        stream(localFileUtil.listDirs(resultsDir))
            .sorted(comparing(File::getName))
            .collect(toImmutableList());

    // Parses test_result.xml under each result dir.
    for (File resultDir : resultDirs) {
      if (resultDir.getName().equals("latest")) {
        continue;
      }
      File resultFile = new File(resultDir, "test_result.xml");
      try {
        Optional<Result> result = compatibilityReportParser.parse(resultFile.toPath(), shallow);
        if (result.isEmpty()) {
          // TODO: Remove the legacy result support.
          // Legacy result xml file locates at resultsDir/sessionId/sessionId/test_result.xml.
          File legacyResultFile =
              new File(resultDir, String.format("%s/test_result.xml", resultDir.getName()));
          result = compatibilityReportParser.parse(legacyResultFile.toPath(), shallow);
        }

        result.ifPresent(value -> results.put(value, resultDir));
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to parse result file: %s", resultFile.getAbsolutePath());
      }
    }
    return results;
  }

  /** Lists all the result directories directly in the "results" dir, ordered by dir name. */
  public List<File> listResultDirsInOrder(String resultsDir) throws MobileHarnessException {
    return ImmutableList.copyOf(listResults(resultsDir, /* shallow= */ false).values());
  }
}
