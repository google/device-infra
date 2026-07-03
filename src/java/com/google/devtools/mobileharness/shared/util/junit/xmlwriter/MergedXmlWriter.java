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

package com.google.devtools.mobileharness.shared.util.junit.xmlwriter;

import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus.FAILED;
import static com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus.FLAKY;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimaps;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.StackTrace;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestCase.TestStatus;
import com.google.devtools.mobileharness.shared.util.testresult.rollup.TestSuiteOverview;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.kxml2.io.KXmlSerializer;

/**
 * Generates a merged XML file for a group of test executions that share the same job, such as
 * shards and reruns.
 *
 * <p>This MergedXMLWriter is for both Android and iOS instrumentation tests.
 */
public class MergedXmlWriter {
  private static final String UTF_8 = "UTF-8";

  private static final String TESTSUITES = "testsuites";
  private static final String TESTSUITE = "testsuite";
  private static final String TESTCASE = "testcase";
  private static final String FAILURE = "failure";
  private static final String SKIPPED = "skipped";
  private static final String FLAKY_TAG = "flaky";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_TIME = "time";
  private static final String ATTR_ERRORS = "errors";
  private static final String ATTR_FAILURES = "failures";
  private static final String ATTR_FLAKES = "flakes";
  private static final String ATTR_SKIPPED = "skipped";
  private static final String ATTR_TESTS = "tests";
  private static final String ATTR_CLASSNAME = "classname";
  private static final String HOSTNAME = "hostname";

  private static final String LOCALHOST = "localhost";
  private static final String XML_NAMESPACE = null;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  MergedXmlWriter() {}

  /**
   * Creates a report file and populates it with the report data from the completed tests.
   *
   * @param testSuiteOverviews rollup testSuiteOverviews.
   * @param testCases rollup testCases.
   * @param outputFile local path where XML file is written to.
   */
  public void generateDocument(
      List<TestSuiteOverview> testSuiteOverviews, List<TestCase> testCases, Path outputFile) {
    if (testSuiteOverviews.isEmpty()) {
      logger.atInfo().log("No test suite overview provided, skipping XML generation.");
      return;
    }
    try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
      KXmlSerializer serializer = new KXmlSerializer();
      serializer.setOutput(stream, UTF_8);
      serializer.startDocument(UTF_8, null);
      serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
      // This applies to iOS instrumentation tests that have multiple test suites.
      if (testSuiteOverviews.size() > 1) {
        serializer.startTag(XML_NAMESPACE, TESTSUITES);
      }
      ImmutableListMultimap<String, TestCase> testCaseByTestSuite =
          groupTestCasesUnderSameTestSuite(testCases);
      for (TestSuiteOverview testSuiteOverview : testSuiteOverviews) {
        printTestSuitesResults(
            serializer, testSuiteOverview, testCaseByTestSuite.get(testSuiteOverview.name()));
      }
      // This applies to iOS instrumentation tests that have multiple test suites.
      if (testSuiteOverviews.size() > 1) {
        serializer.endTag(XML_NAMESPACE, TESTSUITES);
      }
      serializer.endDocument();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to generate aggregated XML file for %s", testSuiteOverviews.get(0).name());
    }
  }

  private static void printTestSuitesResults(
      KXmlSerializer serializer, TestSuiteOverview testSuiteOverview, List<TestCase> testCases)
      throws IOException {
    ImmutableMultiset<TestStatus> numTestsInTestSuite = computeNumTests(testCases);
    serializer.startTag(XML_NAMESPACE, TESTSUITE);
    String testSuiteName = testSuiteOverview.name();
    serializer.attribute(XML_NAMESPACE, ATTR_NAME, testSuiteName);
    int numTotalTests = testCases.size();
    serializer.attribute(XML_NAMESPACE, ATTR_TESTS, Integer.toString(numTotalTests));
    serializer.attribute(
        XML_NAMESPACE, ATTR_FAILURES, Integer.toString(numTestsInTestSuite.count(FAILED)));
    serializer.attribute(
        XML_NAMESPACE, ATTR_FLAKES, Integer.toString(numTestsInTestSuite.count(FLAKY)));
    // legacy - there are no errors in JUnit4
    serializer.attribute(XML_NAMESPACE, ATTR_ERRORS, "0");
    serializer.attribute(
        XML_NAMESPACE,
        ATTR_SKIPPED,
        Integer.toString(numTestsInTestSuite.count(TestStatus.SKIPPED)));
    serializer.attribute(
        XML_NAMESPACE, ATTR_TIME, Double.toString(testSuiteOverview.elapsedTime().toNanos() / 1e9));
    serializer.attribute(XML_NAMESPACE, HOSTNAME, LOCALHOST);

    for (TestCase testCase : testCases) {
      printTestCaseResult(serializer, testCase);
    }
    serializer.endTag(XML_NAMESPACE, TESTSUITE);
  }

  private static void printTestCaseResult(KXmlSerializer serializer, TestCase testCase)
      throws IOException {
    serializer.startTag(XML_NAMESPACE, TESTCASE);
    serializer.attribute(XML_NAMESPACE, ATTR_NAME, testCase.testCaseReference().name());
    serializer.attribute(XML_NAMESPACE, ATTR_CLASSNAME, testCase.testCaseReference().className());
    serializer.attribute(
        XML_NAMESPACE, ATTR_TIME, Double.toString(testCase.elapsedTime().toNanos() / 1e9));

    switch (testCase.status()) {
      case FAILED -> printTest(serializer, FAILURE, testCase.stackTraces());
      case SKIPPED -> printTest(serializer, SKIPPED, testCase.stackTraces());
      case FLAKY -> {
        serializer.attribute(XML_NAMESPACE, FLAKY_TAG, "true");
        printFailedTest(serializer, FAILURE, testCase.stackTraces());
      }
      default -> {}
    }

    serializer.endTag(XML_NAMESPACE, TESTCASE);
  }

  private static void printTest(KXmlSerializer serializer, String tag, List<StackTrace> stackTraces)
      throws IOException {
    if (stackTraces.isEmpty()) {
      serializer.startTag(XML_NAMESPACE, tag);
      serializer.endTag(XML_NAMESPACE, tag);
    } else {
      printFailedTest(serializer, tag, stackTraces);
    }
  }

  private static void printFailedTest(
      KXmlSerializer serializer, String tag, List<StackTrace> stacks) throws IOException {
    for (StackTrace stack : stacks) {
      serializer.startTag(XML_NAMESPACE, tag);
      serializer.text(sanitize(stack.exception()));
      serializer.endTag(XML_NAMESPACE, tag);
    }
  }

  private static ImmutableMultiset<TestStatus> computeNumTests(List<TestCase> testCases) {
    return testCases.stream().map(TestCase::status).collect(toImmutableMultiset());
  }

  /** Returns the text in a format that is safe for use in an XML document. */
  private static String sanitize(String text) {
    return text.replace("\0", "<\\0>");
  }

  /** Groups test cases into their corresponding test suite name. */
  private static ImmutableListMultimap<String, TestCase> groupTestCasesUnderSameTestSuite(
      List<TestCase> testCases) {
    return Multimaps.index(testCases, testCase -> testCase.testCaseReference().testSuiteName());
  }
}
