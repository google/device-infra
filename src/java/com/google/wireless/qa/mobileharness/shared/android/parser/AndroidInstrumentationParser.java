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

package com.google.wireless.qa.mobileharness.shared.android.parser;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.util.ResultUtil;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Android Instrumentation Output parser class */
public class AndroidInstrumentationParser {

  /** Match all records from the test output. */
  private static final Pattern OUTPUT_PATTERN =
      Pattern.compile("INSTRUMENTATION_STATUS:[\\s\\S]+?INSTRUMENTATION_STATUS_CODE:.+");

  /** Match test method from a record */
  private static final Pattern METHOD_NAME_PATTERN =
      Pattern.compile("INSTRUMENTATION_STATUS: test=(.*)");

  /** Match test class name from a record */
  private static final Pattern CLASS_PATTERN =
      Pattern.compile("INSTRUMENTATION_STATUS: class=(.*)");

  /** Match status code from a record */
  private static final Pattern STATUS_CODE_PATTERN =
      Pattern.compile("INSTRUMENTATION_STATUS_CODE: (.*)");

  /** LongMsg Key of the instrumentation result message. */
  private static final Pattern INSTRUMENTATION_RESULT_ERROR_MESSAGE_PATTERN =
      Pattern.compile("INSTRUMENTATION_RESULT:.*(longMsg|shortMsg)=(.*)");

  /** Signal of a failed instrument case. */
  private static final String SIGNAL_INSTRUMENT_FAIL = "INSTRUMENTATION_FAILED:";

  /** Signal of a failed test case. */
  private static final String SIGNAL_TEST_FAIL = "FAILURES!!!";

  /** Signal of a passed test case. */
  private static final String SIGNAL_TEST_PASS = "OK (";

  private static final String SIGNAL_NO_TEST_RAN = "OK (0";

  /** Instrumentation Status Code return value */
  private static class InstrumentStatusCode {
    private static final String OK = "0";
    private static final String START = "1";
    private static final String IN_PROGRESS = "2";
    private static final String ERROR = "-1";
    private static final String FAILURE = "-2";
    private static final String IGNORED = "-3";
    private static final String ASSUMPTION_FAILURE = "-4";
  }

  /**
   * Parse the output message or exception message after running Android instrumentation.
   *
   * @param instrumentationOutput the output from command or exception
   * @param errorMsg return the error message after parsing.
   * @param e the returned MH exception after running command
   * @return test result (PASS/ERROR/FAIL/TIMEOUT)
   *     <p>Branched from function parseInstrumentationError() in:
   *     java/com/google/wireless/qa/mobileharness/shared/api/driver/AndroidInstrumentation.java
   */
  public TestResult parseOutput(
      String instrumentationOutput,
      StringBuilder errorMsg,
      @Nullable MobileHarnessException e,
      boolean failIfNoTestsRan) {
    String output = instrumentationOutput;
    TestResult result;

    if (output == null || errorMsg == null) {
      return TestResult.ERROR;
    }

    /* Handle the exception first */
    if (e != null) {
      if (e.getErrorId() == AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT) {
        result = TestResult.TIMEOUT;
      } else {
        result = ResultUtil.getResultByException(e);
      }
      if (result != TestResult.UNKNOWN) {
        errorMsg.append(output);
        return result;
      }
    }

    /*
     * Handle the obvious fail/pass in output first
     *
     * For some failure case, Instrument output like:
     * There was 1 failure:
     * testSearchAndSave_background (class name ...)
     * ... (stack trace)
     * ... (stack trace)
     * FAILURES!!!
     * Tests run: 1, Failures: 1
     *
     * <p>For some failure case, Instrument throw exception like: android.util.AndroidException:
     * INSTRUMENTATION_FAILED: com.google.android.apps.dragonfly.tests/
     */
    if (output.contains(SIGNAL_TEST_FAIL) || output.contains(SIGNAL_INSTRUMENT_FAIL)) {
      errorMsg.append(output);
      return TestResult.FAIL;
    }
    // Returns TestResult.FAIL if no tests ran. For example, user specified test methods do not
    // exist, or using Parameterized runner with ShardLevel.METHOD
    if (failIfNoTestsRan && output.contains(SIGNAL_NO_TEST_RAN)) {
      errorMsg.append("No tests ran. Set test result to FAIL as configured.");
      return TestResult.FAIL;
    }
    /*
     * For most passed case, Instrument output simply like:
     * com.google.codelab.mobileharness.android.hellomobileharness.HelloMobileHarnessTest:
     *
     * <p>Time: 0.71
     *
     * <p>OK (1 test)
     */
    if (output.contains(SIGNAL_TEST_PASS)) {
      return TestResult.PASS;
    }

    /*
     * Test by test analysis of the error message from Instrument. This can also be easily extended
     * in future if we need to understand the output of Instrument
     *
     * <p>According to am.Instrument.java and InstrumentationResultParser.java:
     * /android/frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java
     * /java/com/android/ddmlib/testrunner/InstrumentationResultParser.java
     *
     * <p>There are couple of pattern for Instrument output:
     * onInstrumentationFinished: (regardless of success or failed, but no fatal exception)
     * INSTRUMENTATION_RESULT:
     * shortMsg=java.lang.RuntimeException INSTRUMENTATION_RESULT:
     * longMsg=java.lang.RuntimeException: No content provider regist... INSTRUMENTATION_CODE: 0
     *
     * <p>onInstrumentationStatus Changed:
     * INSTRUMENTATION_STATUS: Error=Permission Denial: starting instrumentation ...
     * INSTRUMENTATION_STATUS_CODE: -1
     *
     * <p>Some fatal error
     * INSTRUMENTATION_FAILED: Unsupported instruction set
     *
     * <p>The meaning of INSTRUMENTATION_STATUS_CODE:
     * Test START = 1;
     * Test IN_PROGRESS = 2;
     * <p> below codes used for test completed
     * Test ASSUMPTION_FAILURE = -4;
     * Test IGNORED = -3;
     * Test FAILURE = -2;
     * Test ERROR = -1;
     * Test OK = 0;
     */

    Map<String, TestResult> results = new HashMap<>();
    Matcher outputMatcher = OUTPUT_PATTERN.matcher(output);
    while (outputMatcher.find()) {
      String record = outputMatcher.group();
      Matcher methodNameMatcher = METHOD_NAME_PATTERN.matcher(record);
      Matcher classMatcher = CLASS_PATTERN.matcher(record);
      Matcher statusCodeMatcher = STATUS_CODE_PATTERN.matcher(record);
      while (methodNameMatcher.find() && classMatcher.find() && statusCodeMatcher.find()) {
        // The INSTRUMENTATION_STATUS_CODE must be the last entry in the record
        if (statusCodeMatcher.start() < methodNameMatcher.start()
            || statusCodeMatcher.start() < classMatcher.start()) {
          errorMsg.append("Unexpected Instrument Output\n").append(output);
          return TestResult.ERROR;
        }
        String methodName = classMatcher.group(1) + "#" + methodNameMatcher.group(1);
        if (statusCodeMatcher.group(1).equals(InstrumentStatusCode.OK)
            || statusCodeMatcher.group(1).equals(InstrumentStatusCode.IGNORED)
            || statusCodeMatcher.group(1).equals(InstrumentStatusCode.ASSUMPTION_FAILURE)) {
          // Mark it as success unless it was marked as a failure earlier
          if (!TestResult.FAIL.equals(results.get(methodName))
              && !TestResult.ERROR.equals(results.get(methodName))) {
            results.put(methodName, TestResult.PASS);
          }
        } else if (statusCodeMatcher.group(1).equals(InstrumentStatusCode.FAILURE)) {
          results.put(methodName, TestResult.FAIL);
          errorMsg.append(
              String.format(
                  "%s resulted in FAIL with instrumentation output: %s", methodName, record));
        } else if (statusCodeMatcher.group(1).equals(InstrumentStatusCode.START)
            || statusCodeMatcher.group(1).equals(InstrumentStatusCode.IN_PROGRESS)) {
          results.put(methodName, TestResult.UNKNOWN);
        } else if (statusCodeMatcher.group(1).equals(InstrumentStatusCode.ERROR)) {
          // All other status codes correspond to errors
          results.put(methodName, TestResult.ERROR);
          errorMsg.append(
              String.format(
                  "%s resulted in ERROR with instrumentation output: %s", methodName, record));
        } else {
          errorMsg.append(
              String.format("%s resulted in unrecognized status code: %s", methodName, record));
          results.put(methodName, TestResult.ERROR);
        }
      }
    }

    List<String> errors =
        results.entrySet().stream()
            .filter(entry -> entry.getValue().equals(TestResult.ERROR))
            .map(Entry::getKey)
            .collect(Collectors.toList());
    List<String> failures =
        results.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().equals(TestResult.FAIL)
                        || entry.getValue().equals(TestResult.UNKNOWN))
            .map(Entry::getKey)
            .collect(Collectors.toList());
    List<String> passes =
        results.entrySet().stream()
            .filter(entry -> entry.getValue().equals(TestResult.PASS))
            .map(Entry::getKey)
            .collect(Collectors.toList());

    // Explicitly search for this pattern if test failure hasn't been detected by parsing
    // individual tests
    Matcher crashMatcher = INSTRUMENTATION_RESULT_ERROR_MESSAGE_PATTERN.matcher(output);
    if (!errors.isEmpty()) {
      result = TestResult.ERROR;
    } else if (!failures.isEmpty()) {
      result = TestResult.FAIL;
    } else if (crashMatcher.find()) {
      // If the outputmatcher above did not find an error or failure but we have an error message
      // in the output.
      errorMsg.append(crashMatcher.group(2));
      result = TestResult.FAIL;
    } else if (!passes.isEmpty()) {
      // If we have no failures/errors mark it as pass
      result = TestResult.PASS;
    } else {
      // Didn't find an 'OK (' message or parse any passing tests
      errorMsg.append("Couldn't parse any tests in instrumentation output:\n").append(output);
      result = TestResult.ERROR;
    }
    return result;
  }
}
