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
import java.util.List;
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
  private static final String RESULT_SIGNATURE = "Signature";
  private static final String RESULT_RESULT = "Result";
  private static final String RESULT_STACKTRACE = "Stacktrace";
  private static final String RESULT_BEGIN_TIME = "Begin Time";
  private static final String RESULT_END_TIME = "End Time";
  private static final String RESULT_UID = "UID";
  private static final String RESULT_DETAILS = "Details";
  private static final String RESULT_EXTRAS = "Extras";
  private static final String RESULT_RETRY_PARENT = "Retry Parent";

  // Key constants found within the body of "Extra Errors".
  public static final String RESULT_EXTRA_ERRORS_DETAILS = "Details";
  public static final String RESULT_EXTRA_ERRORS_EXTRAS = "Extras";
  public static final String RESULT_EXTRA_ERRORS_STACKTRACE = "Stacktrace";
  private static final String RESULT_EXTRA_ERRORS = "Extra Errors";
  private static final String RESULT_EXTRA_ERRORS_POSITION = "Position";

  private static final String USERDATA_TIMESTAMP = "timestamp";

  private static final String RESULT_PASS = "PASS";
  private static final String RESULT_FAIL = "FAIL";
  private static final String RESULT_ERROR = "ERROR";
  private static final String RESULT_SKIP = "SKIP";
  private static final String RESULT_NULL = "null";

  private static final String SUMMARY_REQUESTED = "Requested";
  private static final String SUMMARY_EXECUTED = "Executed";
  private static final String SUMMARY_PASSED = "Passed";
  private static final String SUMMARY_FAILED = "Failed";

  // Special names for Mobly test stages, reported in the same way as a test case record
  private static final String STAGE_SETUP_CLASS = "setup_class";
  private static final String STAGE_PRE_RUN = "pre_run";
  private static final String STAGE_TEARDOWN_CLASS = "teardown_class";
  private static final String STAGE_CLEAN_UP = "clean_up";

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

    String currentTestClass = null;
    boolean hasSetupClassError = false;
    int errorTestCount = 0;
    int skippedTestCount = 0;

    // Parse all yaml documents
    for (Object document : yamlDocuments) {
      @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
      Map<String, Object> documentMap = (Map<String, Object>) document;
      switch (String.valueOf(documentMap.get(RESULT_TYPE))) {
        case RESULT_TYPE_RECORD:
          // Reset hasSetupClassError upon encountering a new test class.
          String newTestClass = String.valueOf(documentMap.get(RESULT_TESTCLASS));
          if (!newTestClass.equals(currentTestClass)) {
            hasSetupClassError = false;
            currentTestClass = newTestClass;
          }
          String testName = String.valueOf(documentMap.get(RESULT_TESTNAME));
          if ((testName.equals(STAGE_SETUP_CLASS) || testName.equals(STAGE_PRE_RUN))
              && String.valueOf(documentMap.get(RESULT_RESULT)).equals(RESULT_ERROR)) {
            hasSetupClassError = true;
            continue;
          }
          if (testName.equals(STAGE_TEARDOWN_CLASS) || testName.equals(STAGE_CLEAN_UP)) {
            continue;
          }
          MoblyTestEntry testEntry = parseRecord(documentMap, hasSetupClassError);
          // Count the number of error and skipped tests since the result may be overridden.
          if (testEntry.getResult() == MoblyResult.ERROR) {
            errorTestCount++;
          } else if (testEntry.getResult() == MoblyResult.SKIP) {
            skippedTestCount++;
          }
          results.add(testEntry);
          break;
        case RESULT_TYPE_CONTROLLERINFO:
          results.add(parseControllerInfo(documentMap));
          break;
        case RESULT_TYPE_USERDATA:
          results.add(parseUserData(documentMap));
          break;
        case RESULT_TYPE_SUMMARY:
          results.add(parseSummary(documentMap, errorTestCount, skippedTestCount));
          break;
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
  private MoblyTestEntry parseRecord(Map<String, Object> record, boolean hasSetupClassError)
      throws MobileHarnessException {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    // Build MoblyTestEntry for Mobly result
    MoblyTestEntry.Builder builder = MoblyTestEntry.builder();
    builder.setTestName(String.valueOf(record.get(RESULT_TESTNAME)));
    builder.setTestClass(String.valueOf(record.get(RESULT_TESTCLASS)));

    setResult(builder, record, hasSetupClassError);
    setTiming(builder, record);
    setExtraErrors(builder, record);

    if (record.get(RESULT_DETAILS) != null) {
      builder.setDetails(String.valueOf(record.get(RESULT_DETAILS)));
    }
    if (record.get(RESULT_STACKTRACE) != null) {
      builder.setStacktrace(String.valueOf(record.get(RESULT_STACKTRACE)));
    }
    if (record.get(RESULT_EXTRAS) != null) {
      builder.setExtras(yaml.dump(record.get(RESULT_EXTRAS)));
    }
    if (record.get(RESULT_UID) != null) {
      builder.setUid(String.valueOf(record.get(RESULT_UID)));
    }
    if (record.get(RESULT_RETRY_PARENT) != null) {
      builder.setRetryParent(String.valueOf(record.get(RESULT_RETRY_PARENT)));
    }
    if (record.get(RESULT_SIGNATURE) != null) {
      builder.setSignature(String.valueOf(record.get(RESULT_SIGNATURE)));
    }

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

  private void setResult(
      MoblyTestEntry.Builder builder, Map<String, Object> record, boolean hasSetupClassError)
      throws MobileHarnessException {
    // If the test's setup_class failed, set the test case result to ERROR.
    if (hasSetupClassError) {
      builder.setResult(MoblyResult.ERROR);
      return;
    }
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
        builder.setDetails("Test was interrupted manually.");
        break;
      default:
        logger.atSevere().log("Unrecognized result for test %s", testName);
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_OUTPUT_PARSING_ERROR, "Unrecognized result: " + result);
    }
  }

  /**
   * Parses the Extra Errors in the Mobly summary file.
   *
   * <p>The Extra Errors portion of the Mobly summary file can either be a Map or a List. The format
   * will determine how this section will be parsed. If the Extra Error content is neither a Map or
   * a List, an ExtraError object will still be built where the entire content will be stored in
   * ExtraError.getDetails().
   */
  private void setExtraErrors(MoblyTestEntry.Builder builder, Map<String, Object> record) {
    Object extraErrors = record.get(RESULT_EXTRA_ERRORS);
    if (extraErrors != null) {
      if (extraErrors instanceof Map) {
        @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
        Map<String, Object> extraErrorObj = (Map<String, Object>) extraErrors;
        if (!extraErrorObj.isEmpty()) {
          extraErrorObj.forEach(
              (k, v) -> {
                @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
                Map<String, Object> valMap = (Map<String, Object>) v;
                builder.addExtraError(buildExtraError(valMap));
              });
        }
      } else if (extraErrors instanceof List) {
        @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
        List<Object> extraErrorArray = (List<Object>) extraErrors;
        if (!extraErrorArray.isEmpty()) {
          extraErrorArray.forEach(
              elem -> {
                @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
                Map<String, Object> valMap = (Map<String, Object>) elem;
                builder.addExtraError(buildExtraError(valMap));
              });
        }
      } else {
        // If unrecognized format, log bug
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("Unable to parse the content of extra errors:\n");
        strBuilder.append(extraErrors);
        logger.atSevere().log("%s", strBuilder);

        // Create ExtraError to hold entire extra error object body in the Details string. We still
        // want to display this on sponge
        strBuilder.append("\nCheck \"Extra Errors\" sections in test_summary.yaml to make sure ");
        strBuilder.append("they are either JSONObject or JSONArray.");
        builder.addExtraError(
            MoblyTestEntry.ExtraError.builder()
                .setPosition("N/A")
                .setDetails(strBuilder.toString())
                .build());
      }
    }
  }

  private MoblyTestEntry.ExtraError buildExtraError(Map<String, Object> extraErrorMap) {
    MoblyTestEntry.ExtraError.Builder builder = MoblyTestEntry.ExtraError.builder();
    builder.setPosition(String.valueOf(extraErrorMap.get(RESULT_EXTRA_ERRORS_POSITION)));
    if (extraErrorMap.get(RESULT_EXTRA_ERRORS_DETAILS) != null) {
      builder.setDetails(String.valueOf(extraErrorMap.get(RESULT_EXTRA_ERRORS_DETAILS)));
    }
    if (extraErrorMap.get(RESULT_EXTRA_ERRORS_EXTRAS) != null) {
      builder.setExtras(String.valueOf(extraErrorMap.get(RESULT_EXTRA_ERRORS_EXTRAS)));
    }
    if (extraErrorMap.get(RESULT_EXTRA_ERRORS_STACKTRACE) != null) {
      builder.setStacktrace(String.valueOf(extraErrorMap.get(RESULT_EXTRA_ERRORS_STACKTRACE)));
    }
    return builder.build();
  }

  /**
   * Create container class for Mobly ControllerInfo.
   *
   * <p>Because the ControllerInfo document has no standard structure, the entire yaml document is
   * stored in MoblyControllerInfoEntry.getDevices(). The yaml document is read is as a Map<String,
   * Object>, so that is what is stored.
   */
  private MoblyControllerInfoEntry parseControllerInfo(Map<String, Object> record) {
    MoblyControllerInfoEntry.Builder builder = MoblyControllerInfoEntry.builder();
    builder.setDevices(record);
    return builder.build();
  }

  private MoblyUserDataEntry parseUserData(Map<String, Object> record) {
    MoblyUserDataEntry.Builder builder = MoblyUserDataEntry.builder();
    builder.setTimestamp(String.valueOf(record.get(USERDATA_TIMESTAMP)));
    builder.setUserDataMap(record);
    return builder.build();
  }

  private MoblySummaryEntry parseSummary(
      Map<String, Object> record, int errorTestCount, int skippedTestCount) {
    try {
      return MoblySummaryEntry.builder()
          .setRequested(Integer.parseInt(String.valueOf(record.get(SUMMARY_REQUESTED))))
          .setExecuted(Integer.parseInt(String.valueOf(record.get(SUMMARY_EXECUTED))))
          .setPassed(Integer.parseInt(String.valueOf(record.get(SUMMARY_PASSED))))
          .setFailed(Integer.parseInt(String.valueOf(record.get(SUMMARY_FAILED))))
          .setError(errorTestCount)
          .setSkipped(skippedTestCount)
          .build();
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log("Failed to parse summary stats: %s", e.getMessage());
      return MoblySummaryEntry.builder().build();
    }
  }
}
