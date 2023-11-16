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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The class responsible for reading in the Mobly result summary yaml file and parsing the results
 * into {@link MoblyYamlDocEntry} objects.
 *
 * <p>Used for CTS tests only at this moment.
 */
public class MoblyYamlParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Key constants found within a Mobly Record.
  private static final String RESULT_TYPE = "Type";
  private static final String RESULT_TYPE_TESTNAMELIST = "TestNameList";
  private static final String RESULT_TYPE_RECORD = "Record";
  private static final String RESULT_TYPE_CONTROLLERINFO = "ControllerInfo";
  private static final String RESULT_TYPE_SUMMARY = "Summary";
  private static final String RESULT_TYPE_USERDATA = "UserData";

  private static final String RESULT_TESTNAME = "Test Name";
  private static final String RESULT_TESTCLASS = "Test Class";
  private static final String RESULT_RESULT = "Result";
  private static final String RESULT_BEGIN_TIME = "Begin Time";
  private static final String RESULT_END_TIME = "End Time";

  private static final String RESULT_PASS = "PASS";
  private static final String RESULT_FAIL = "FAIL";
  private static final String RESULT_ERROR = "ERROR";
  private static final String RESULT_SKIP = "SKIP";
  private static final String RESULT_NULL = "null";

  private static final String SUMMARY_REQUESTED = "Requested";
  private static final String SUMMARY_EXECUTED = "Executed";
  private static final String SUMMARY_SKIPPED = "Skipped";
  private static final String SUMMARY_PASSED = "Passed";
  private static final String SUMMARY_FAILED = "Failed";
  private static final String SUMMARY_ERROR = "Error";

  /**
   * Takes in the path to the test_summary.yaml file and parses the result into a list of
   * MoblyYamlDocEntry results. If the path given is invalid, a FileNotFoundException is thrown.
   */
  public ImmutableList<MoblyYamlDocEntry> parse(String moblySummaryFile)
      throws MobileHarnessException, IOException {
    try (InputStream input = new FileInputStream(new File(moblySummaryFile))) {
      return parse(input);
    }
  }

  /**
   * Reads in the {@link java.io.InputStream} of a Mobly test_summary.yaml file and parses the data
   * into a list of {@link MoblyYamlDocEntry} objects.
   */
  private ImmutableList<MoblyYamlDocEntry> parse(InputStream input) throws MobileHarnessException {
    // Load yaml summary file
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Iterable<Object> yamlDocuments = yaml.loadAll(input);

    ImmutableList.Builder<MoblyYamlDocEntry> results = new ImmutableList.Builder<>();

    // Parse all yaml documents
    for (Object document : yamlDocuments) {
      @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
      Map<String, Object> documentMap = (Map<String, Object>) document;
      switch (String.valueOf(documentMap.get(RESULT_TYPE))) {
        case RESULT_TYPE_RECORD:
          results.add(parseRecord(documentMap));
          break;
        case RESULT_TYPE_SUMMARY:
          results.add(parseSummary(documentMap));
          break;
        case RESULT_TYPE_CONTROLLERINFO:
        case RESULT_TYPE_USERDATA:
        case RESULT_TYPE_TESTNAMELIST:
          // Do nothing. We don't care about this for now
          break;
        default:
          StringBuilder errStrBuilder = new StringBuilder();
          if (documentMap.get(RESULT_TYPE) == null) {
            errStrBuilder.append("Yaml document is missing Type value!");
          } else {
            errStrBuilder.append(documentMap.get(RESULT_TYPE));
            errStrBuilder.append(" is not a known Type!");
          }
          errStrBuilder.append(" Document content: ");
          errStrBuilder.append(documentMap);
          logger.atWarning().log("%s", errStrBuilder);
          break;
      }
    }

    return results.build();
  }

  /**
   * Parses a Mobly record. A Mobly record contains the results of a single method run on a Mobly
   * test. The results of a record are parsed into a MoblyTestEntry object.
   */
  private MoblyTestEntry parseRecord(Map<String, Object> record) throws MobileHarnessException {
    // Build MoblyTestEntry for Mobly result
    MoblyTestEntry.Builder builder = MoblyTestEntry.builder();
    builder.setTestName(String.valueOf(record.get(RESULT_TESTNAME)));
    builder.setTestClass(String.valueOf(record.get(RESULT_TESTCLASS)));

    setResult(builder, record);
    setTiming(builder, record);

    return builder.build();
  }

  /**
   * Parses the timing info from the Mobly summary file. Begin time and end time are required to
   * build the MoblyTestEntry. If timing info doesn't exist or was not able to parse, then a
   * MobileHarnessException is thrown.
   */
  private void setTiming(MoblyTestEntry.Builder builder, Map<String, Object> record) {
    try {
      if (record.get(RESULT_BEGIN_TIME) != null) {
        builder.setBeginTime(Long.parseLong(String.valueOf(record.get(RESULT_BEGIN_TIME))));
      }
      if (record.get(RESULT_END_TIME) != null) {
        builder.setEndTime(Long.parseLong(String.valueOf(record.get(RESULT_END_TIME))));
      }
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log(
          "Cannot parse Mobly timing info from summary file for %s", record.get(RESULT_TESTNAME));
    }
  }

  private void setResult(MoblyTestEntry.Builder builder, Map<String, Object> record)
      throws MobileHarnessException {
    String testName = String.valueOf(record.get(RESULT_TESTNAME));
    String result = String.valueOf(record.get(RESULT_RESULT));
    switch (result) {
      case RESULT_PASS:
        builder.setResult(MoblyResult.PASS);
        break;
      case RESULT_FAIL:
        builder.setResult(MoblyResult.FAIL);
        break;
      case RESULT_ERROR:
        builder.setResult(MoblyResult.ERROR);
        break;
      case RESULT_SKIP:
        builder.setResult(MoblyResult.SKIP);
        break;
      case RESULT_NULL:
        // When a user manually interrupts a test, Mobly sets test result as null.
        builder.setResult(MoblyResult.NULL);
        break;
      default:
        logger.atSevere().log("Unrecognized result for test %s", testName);
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_OUTPUT_PARSING_ERROR, "Unrecognized result: " + result);
    }
  }

  private MoblySummaryEntry parseSummary(Map<String, Object> record) {
    try {
      return MoblySummaryEntry.builder()
          .setRequested(Integer.parseInt(String.valueOf(record.get(SUMMARY_REQUESTED))))
          .setExecuted(Integer.parseInt(String.valueOf(record.get(SUMMARY_EXECUTED))))
          .setSkipped(Integer.parseInt(String.valueOf(record.get(SUMMARY_SKIPPED))))
          .setPassed(Integer.parseInt(String.valueOf(record.get(SUMMARY_PASSED))))
          .setFailed(Integer.parseInt(String.valueOf(record.get(SUMMARY_FAILED))))
          .setError(Integer.parseInt(String.valueOf(record.get(SUMMARY_ERROR))))
          .build();
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log("Failed to parse summary stats: %s", e.getMessage());
      return MoblySummaryEntry.builder().build();
    }
  }
}
