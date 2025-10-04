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

package com.google.devtools.mobileharness.platform.android.instrumentation.parser;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Parses the raw output of an Instrumentation and provides results via {@link
 * AmInstrumentationListener}.
 *
 * <p>The parser works for the output of `am instrument -w -r ...`. It handles individual test
 * results, as well as custom instrumentations.
 *
 * <p>Instances of this class are mutable. To use them concurrently, clients must surround each
 * method invocation (or invocation sequence) with external synchronization of the clients choosing.
 *
 * <p>## Reporting test results
 *
 * <p>For individual test results, the parser expects a series of status key/value pairs, followed
 * by a status code. Status codes identify the individual test phase: started(1), pass(0),
 * error(-1), fail(-2), ignored(-3), or assumption failure(-4).
 *
 * <p>## Reporting (custom) instrumentation results
 *
 * <p>At the end, the parser expects an optional series of result key/value pairs, followed by an
 * instrumentation code. Instrumentation codes identify the end state: ok(-1), cancelled(0). These
 * codes map to `Activity.RESULT_*` constants.
 *
 * <p>## Sample output
 *
 * <p>``` ... INSTRUMENTATION_STATUS_CODE: 1 INSTRUMENTATION_STATUS: class=com.example.Tests
 * INSTRUMENTATION_STATUS: test=testExample INSTRUMENTATION_STATUS: current=2
 * INSTRUMENTATION_STATUS: numtests=2 INSTRUMENTATION_STATUS:
 * stack=com.example.Tests#testExample:123 at com.example.Tests INSTRUMENTATION_STATUS_CODE: -2
 * INSTRUMENTATION_RESULT: infor1=value INSTRUMENTATION_RESULT: info2=multi line value
 * INSTRUMENTATION_CODE: -1 ```
 */
public class AmInstrumentationParser {

  private static final String ABORTED = "INSTRUMENTATION_ABORTED: ";
  private static final String CODE = "INSTRUMENTATION_CODE: ";
  private static final String ON_ERROR = "onError: ";
  private static final String RESULT = "INSTRUMENTATION_RESULT: ";
  private static final String STATUS = "INSTRUMENTATION_STATUS: ";
  private static final String STATUS_CODE = "INSTRUMENTATION_STATUS_CODE: ";
  private static final String STATUS_FAILED = "INSTRUMENTATION_FAILED: ";
  private static final String PREFIX = "INSTRUMENTATION_";
  private static final String STATUS_CLASS = "class";
  private static final String STATUS_CURRENT = "current";
  private static final String STATUS_ERROR = "Error";
  private static final String STATUS_ID = "id";
  private static final String STATUS_NUMTESTS = "numtests";
  private static final String STATUS_SHORTMSG = "shortMsg";
  private static final String STATUS_STACK = "stack";
  private static final String STATUS_STREAM = "stream";
  private static final String STATUS_TEST = "test";
  private static final ImmutableSet<String> KNOWN_STATUS =
      ImmutableSet.of(
          STATUS_CLASS,
          STATUS_CURRENT,
          STATUS_ERROR,
          STATUS_ID,
          STATUS_NUMTESTS,
          STATUS_SHORTMSG,
          STATUS_STACK,
          STATUS_STREAM,
          STATUS_TEST);

  // Status codes copied from frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java.
  /** Test is starting */
  private static final int STATUS_CODE_START = 1;

  /** Test reported status while in progress */
  private static final int STATUS_CODE_IN_PROGRESS = 2;

  /** Test completed successfully */
  private static final int STATUS_CODE_OK = 0;

  /** Test completed with an error (JUnit3 only) */
  private static final int STATUS_CODE_ERROR = -1;

  /** Test completed with a failure */
  private static final int STATUS_CODE_FAILURE = -2;

  /** Test was ignored */
  private static final int STATUS_CODE_IGNORED = -3;

  /** Test completed with an assumption failure */
  private static final int STATUS_CODE_ASSUMPTION_FAILURE = -4;

  /** Error message when no test results were received from the instrumentation */
  private static final String ERROR_NO_TEST_RESULTS = "No test results";

  /** Message reported by test runner when some critical failure occurred */
  private static final String FATAL_ERROR_MSG = "Fatal exception when running tests";

  /** Message reported by orchestrator when the instrumentation aborted abnormally */
  private static final String STREAM_INSTRUMENTATION_PROCESS_CRASHED =
      "Test instrumentation process crashed";

  /** Regex for reported errors (fatal & non-fatal) printed at the end of the instrumentation. */
  private static final Pattern STREAM_FAILURES_REGEX =
      Pattern.compile("There (?:was|were) \\d+ failure");

  private final Set<AmInstrumentationListener> listeners;
  private final Supplier<TestTimeTracker> testTimeTrackerFactory;

  /** The result of the Instrumentation, available after parse completion. */
  private @Nullable InstrumentationResult result;

  /**
   * Potential error reported by the Instrumentation, available after parse completion.
   *
   * <p>Same value as reported through [AmInstrumentationListener.instrumentationFailed].
   */
  private @Nullable String instrumentationError;

  /** The currently parsed, potentially multi-line, key. */
  private @Nullable String currentKey;

  /** The currently parsed, potentially multi-line, instrumentation result value. */
  private @Nullable StringBuilder currentResultValue;

  /** The currently parsed, potentially multi-line, test status value. */
  private @Nullable StringBuilder currentStatusValue;

  private @Nullable Integer code;
  private final Map<String, String> bundle = new HashMap<>();
  private @Nullable Integer expectedTestsCount;
  private int completedTestsCount = 0;
  private @Nullable TestRecord currentTestRecord;

  /** Message from the Instrument.StatusReporter.onError callback */
  private @Nullable String onErrorMessage;

  /** Whether the caller signaled via [done()] that parsing has completed. */
  private boolean parsingEnded = false;

  /** Whether the end terminal `INSTRUMENTATION_CODE` was observed. */
  private boolean instrumentationEnded = false;

  private boolean instrumentationStartedReported = false;
  private boolean instrumentationEndedReported = false;
  private boolean testStartedReported = false;

  public AmInstrumentationParser(
      Set<AmInstrumentationListener> listeners, Supplier<TestTimeTracker> testTimeTrackerFactory) {
    this.listeners = listeners;
    this.testTimeTrackerFactory = testTimeTrackerFactory;
  }

  public AmInstrumentationParser() {
    this(ImmutableSet.of(), TestTimeTracker::create);
  }

  /** Parses an individual Instrumentation output line. */
  public void parse(String line) {
    checkState(!parsingEnded, "Parsing was completed, but parse() was called again!");
    if (line.startsWith(PREFIX) || line.startsWith(ON_ERROR)) {
      storeCurrentValue();
    }
    if (line.startsWith(STATUS)) {
      parseStatusKeyValue(line, STATUS.length());
    } else if (line.startsWith(STATUS_CODE)) {
      Integer statusCode = toIntOrNull(line.substring(STATUS_CODE.length()).trim());
      handleStatusCode(statusCode);
    } else if (line.startsWith(STATUS_FAILED)) {
      handleInstrumentationEnded();
    } else if (line.startsWith(ON_ERROR)) {
      onErrorMessage = line.trim();
    } else if (line.startsWith(ABORTED)) {
      // Messages from ON_ERROR have precedence as they usually contain more information
      if (onErrorMessage == null) {
        onErrorMessage = line.substring(ABORTED.length()).trim();
      }
    } else if (line.startsWith(CODE)) {
      code = toIntOrNull(line.substring(CODE.length()).trim());
      instrumentationEnded = true;
      handleInstrumentationEnded();
    } else if (line.startsWith(RESULT)) {
      parseResultKeyValue(line, RESULT.length());
    } else if (currentStatusValue != null) {
      currentStatusValue.append("\n").append(line);
    } else if (currentResultValue != null) {
      currentResultValue.append("\n").append(line);
    }
  }

  /**
   * Signals the parser that no additional input will be provided.
   *
   * <p>The parser will call any outstanding listener methods to complete the listener lifecycle.
   */
  public void done() {
    checkState(!parsingEnded, "Parsing was completed, but done() was called again!");
    parsingEnded = true;
    // In error cases the parser can be in the middle of parsing a multi-line value.
    storeCurrentValue();
    handleInstrumentationEnded();
  }

  private void parseResultKeyValue(String line, int startIndex) {
    int idx = line.indexOf('=', startIndex);
    if (idx != -1) {
      currentKey = line.substring(startIndex, idx).trim();
      currentResultValue = new StringBuilder().append(line, idx + 1, line.length());
    }
  }

  private void parseStatusKeyValue(String line, int startIndex) {
    int idx = line.indexOf('=', startIndex);
    if (idx != -1) {
      currentKey = line.substring(startIndex, idx).trim();
      currentStatusValue = new StringBuilder().append(line, idx + 1, line.length());
    }
  }

  private void storeCurrentValue() {
    if (currentKey == null) {
      return;
    }
    if (currentStatusValue != null) {
      getOrCreateCurrentTestRecord().storeStatus(currentKey, currentStatusValue.toString());
      currentStatusValue = null;
      return;
    }
    if (currentResultValue != null) {
      bundle.put(currentKey, currentResultValue.toString());
      currentResultValue = null;
    }
  }

  /**
   * Handles the reporting when an individual test result has been parsed. This can be the result of
   * a started test case, or a completed test case.
   */
  private void handleStatusCode(@Nullable Integer statusCode) {
    TestRecord testRecord = currentTestRecord;
    if (testRecord == null || !testRecord.isComplete()) {
      // An error occurred that is not understood by the parser. The record is dropped and we rely
      // on the check for expected number of executed tests to report the instrumentation failure.
      return;
    }
    if (!instrumentationStartedReported) {
      reportInstrumentationStarted(testRecord);
    }
    if (statusCode != null) {
      if (statusCode == STATUS_CODE_IN_PROGRESS) {
        // Not used by any known Android test runner, and thus not supported by the parser.
        return;
      }
      if (statusCode == STATUS_CODE_START) {
        reportTestStarted(testRecord);
        testRecord.markTestStarted();
        return;
      }
    }
    // Either the end of a test case was reached, or the test case never started successfully.
    currentTestRecord = null;
    if (testStartedReported) {
      reportTestEnded(testRecord.toTestResult(statusCode));
    }
    if (testRecord.error != null) {
      handleInstrumentationEnded(testRecord.error);
    }
  }

  private void handleInstrumentationEnded() {
    handleInstrumentationEnded(null);
  }

  private void handleInstrumentationEnded(@Nullable String errorCause) {
    if (instrumentationEndedReported) {
      return;
    }
    String shortMsg = bundle.get(STATUS_SHORTMSG);
    String streamMsg = bundle.getOrDefault(STATUS_STREAM, "");
    boolean wasInstrumentationStartedReported = instrumentationStartedReported;
    String notEnoughTestsError = null;
    if (expectedTestsCount != null && expectedTestsCount > completedTestsCount) {
      notEnoughTestsError =
          String.format("Expected %d tests, received %d", expectedTestsCount, completedTestsCount);
    }
    String error = null;
    if (errorCause != null) {
      error = errorCause;
    } else if (shortMsg != null) {
      // ActivityManagerService or custom instrumentation reported an instrumentation failure
      error = "Instrumentation run failed due to " + shortMsg;
    } else if (!wasInstrumentationStartedReported && !instrumentationEnded) {
      // Parsing completed without observing the instrumentation start and end output
      error = ERROR_NO_TEST_RESULTS;
    } else if (notEnoughTestsError != null) {
      // Less than the expected number of tests were seen/completed
      error = notEnoughTestsError;
    } else if (streamMsg.contains(FATAL_ERROR_MSG)) {
      // The instrumentation fatally failed while being in -e log true mode. Resulting in only the
      // stream containing the exception.
      error = streamMsg;
    }
    if (error != null) {
      // In certain cases the causing error does not have enough information. If possible, attach
      // additional information.
      if (onErrorMessage != null) {
        error = error + ". " + onErrorMessage;
      } else if (STREAM_FAILURES_REGEX.matcher(streamMsg).find()) {
        error = error + ". " + streamMsg;
      }
    }
    // Instrumentation crashed before any test case information was reported, but the end stream
    // has details about the crash.
    if (error == null
        && expectedTestsCount == null
        && STREAM_FAILURES_REGEX.matcher(streamMsg).find()
        && streamMsg.contains(STREAM_INSTRUMENTATION_PROCESS_CRASHED)) {
      error = streamMsg.trim();
    }
    if (!instrumentationStartedReported) {
      // If the instrumentation start wasn't reported yet it can be:
      //  * A custom instrumentation
      //  * A test run with no test cases
      //  * An error launching the instrumentation
      reportInstrumentationStarted(currentTestRecord);
    }
    if (testStartedReported) {
      // Reported the start of a test case, but never the end.
      TestRecord testRecord = getOrCreateCurrentTestRecord();
      currentTestRecord = null;
      reportTestEnded(testRecord.toTestResult(STATUS_CODE_ERROR));
    }
    this.instrumentationError = error;
    if (error != null) {
      reportInstrumentationFailed("Test run failed to complete. " + error);
    }
    Map<String, String> bundleMap = new HashMap<>(bundle);
    bundleMap.keySet().removeAll(KNOWN_STATUS);
    InstrumentationResult result =
        InstrumentationResult.builder()
            .setCode(code)
            .setBundle(ImmutableMap.copyOf(bundleMap))
            .build();
    this.result = result;
    reportInstrumentationEnded(result);
  }

  private void reportInstrumentationStarted(@Nullable TestRecord testRecord) {
    expectedTestsCount = testRecord != null ? testRecord.numTests : null;
    instrumentationStartedReported = true;
    for (AmInstrumentationListener listener : listeners) {
      listener.instrumentationStarted(expectedTestsCount == null ? 0 : expectedTestsCount);
    }
  }

  private void reportTestStarted(TestRecord testRecord) {
    TestIdentifier testIdentifier = testRecord.toTestIdentifier();
    testStartedReported = true;
    for (AmInstrumentationListener listener : listeners) {
      listener.testStarted(testIdentifier);
    }
  }

  private void reportTestEnded(TestResult testResult) {
    completedTestsCount += 1;
    testStartedReported = false;
    for (AmInstrumentationListener listener : listeners) {
      listener.testEnded(testResult);
    }
  }

  private void reportInstrumentationFailed(String message) {
    for (AmInstrumentationListener listener : listeners) {
      listener.instrumentationFailed(message);
    }
  }

  private void reportInstrumentationEnded(InstrumentationResult result) {
    instrumentationEndedReported = true;
    for (AmInstrumentationListener listener : listeners) {
      listener.instrumentationEnded(result);
    }
  }

  private TestRecord getOrCreateCurrentTestRecord() {
    if (currentTestRecord == null) {
      currentTestRecord = new TestRecord(testTimeTrackerFactory.get());
    }
    return currentTestRecord;
  }

  public @Nullable InstrumentationResult getResult() {
    return result;
  }

  public @Nullable String getInstrumentationError() {
    return instrumentationError;
  }

  /** Data holder for in-progress test information. */
  private static class TestRecord {
    private final TestTimeTracker testTimeTracker;
    private @Nullable Integer numTests;
    private @Nullable String error;
    private @Nullable String testClass;
    private @Nullable String testMethod;
    private @Nullable String stackTrace;
    private final Map<String, String> statusBundle = new LinkedHashMap<>();
    private @Nullable String startedTestClass;
    private @Nullable String startedTestMethod;

    TestRecord(TestTimeTracker testTimeTracker) {
      this.testTimeTracker = testTimeTracker;
      testTimeTracker.testStart();
    }

    void markTestStarted() {
      // We reset the class and method to detect if the "test-end" output was written, and thus
      // we consider the record as complete. This is needed as custom test code can report status
      // information via `Instrumentation.sendStatus()` and use a final status code. E.g. the
      // AndroidX benchmark library v1.0.0 has this bug.
      // A copy of the class and method is stored for creating a `TestResult` from this record that
      // might not have the "test-end" output, as it can happen in exceptional cases.
      startedTestClass = testClass;
      startedTestMethod = testMethod;
      testClass = null;
      testMethod = null;
    }

    boolean isComplete() {
      return (testClass != null && testMethod != null) || error != null;
    }

    void storeStatus(String key, String value) {
      switch (key) {
        case STATUS_CLASS:
          testClass = value.trim();
          break;
        case STATUS_TEST:
          testMethod = value.trim();
          break;
        case STATUS_STACK:
          stackTrace = value;
          break;
        case STATUS_NUMTESTS:
          numTests = toIntOrNull(value.trim());
          break;
        case STATUS_ERROR:
          error = value;
          break;
        default:
          if (!KNOWN_STATUS.contains(key)) {
            statusBundle.put(key, value);
          }
          break;
      }
    }

    TestIdentifier toTestIdentifier() {
      String currentTestClass =
          Strings.nullToEmpty(testClass != null ? testClass : startedTestClass);
      return TestIdentifier.create(
          substringBeforeLast(currentTestClass, '.'),
          substringAfterLast(currentTestClass, '.'),
          testMethod != null ? testMethod : Strings.nullToEmpty(startedTestMethod));
    }

    TestResult toTestResult(@Nullable Integer statusCode) {
      testTimeTracker.testEnd();
      TestStatus status = TestStatus.ERROR;
      if (statusCode != null) {
        status =
            switch (statusCode) {
              case STATUS_CODE_OK -> TestStatus.PASSED;
              case STATUS_CODE_ERROR, STATUS_CODE_FAILURE -> TestStatus.FAILED;
              case STATUS_CODE_ASSUMPTION_FAILURE, STATUS_CODE_IGNORED -> TestStatus.IGNORED;
              default -> TestStatus.ERROR;
            };
      }
      return TestResult.builder()
          .setTestIdentifier(toTestIdentifier())
          .setStatus(status)
          .setStartTime(Instant.ofEpochMilli(testTimeTracker.getTestTimingData().startTime()))
          .setEndTime(Instant.ofEpochMilli(testTimeTracker.getTestTimingData().endTime()))
          .setStackTrace(stackTrace)
          .setStatusBundle(ImmutableMap.copyOf(statusBundle))
          .build();
    }
  }

  private static @Nullable Integer toIntOrNull(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String substringBeforeLast(String s, char delimiter) {
    int index = s.lastIndexOf(delimiter);
    return index == -1 ? "" : s.substring(0, index);
  }

  private static String substringAfterLast(String s, char delimiter) {
    int index = s.lastIndexOf(delimiter);
    return index == -1 ? s : s.substring(index + 1);
  }
}
