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

package com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model;

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.shared.util.xml.XMLConstants;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.inject.Inject;

/**
 * Writes the JUnit test nodes and their results into Ant-JUnit XML. Ant-JUnit XML is not a
 * standardized format. For this implementation the <a
 * href="http://windyroad.com.au/dl/Open%20Source/JUnit.xsd">XML schema</a> that is generally
 * referred to as the best available source was used as a reference.
 */
public final class AntXmlResultWriter {
  private static final String JUNIT_ELEMENT_TESTSUITES = XMLConstants.ELEMENT_TESTSUITES;
  private static final String JUNIT_ELEMENT_TESTSUITE = XMLConstants.ELEMENT_TESTSUITE;
  private static final String JUNIT_ATTR_TESTSUITE_ERRORS = XMLConstants.ATTR_TESTSUITE_ERRORS;
  private static final String JUNIT_ATTR_TESTSUITE_FAILURES = XMLConstants.ATTR_TESTSUITE_FAILURES;
  private static final String JUNIT_ATTR_TESTSUITE_SKIPPED = "skipped";
  private static final String JUNIT_ATTR_TESTSUITE_HOSTNAME = "hostname";
  private static final String JUNIT_ATTR_TESTSUITE_NAME = XMLConstants.ATTR_TESTSUITE_NAME;
  private static final String JUNIT_ATTR_TESTSUITE_TESTS = XMLConstants.ATTR_TESTSUITE_TESTS;
  private static final String JUNIT_ATTR_TESTSUITE_TIME = XMLConstants.ATTR_TESTSUITE_TIME;
  private static final String JUNIT_ATTR_TESTSUITE_TIMESTAMP =
      XMLConstants.ATTR_TESTSUITE_TIMESTAMP;
  private static final String JUNIT_ATTR_TESTSUITE_PROPERTIES = "properties";
  // Leaving these here as they are used in the original.
  // private static final String JUNIT_ATTR_TESTSUITE_SYSTEM_OUT = "system-out";
  // private static final String JUNIT_ATTR_TESTSUITE_SYSTEM_ERR = "system-err";
  private static final String JUNIT_ELEMENT_PROPERTY = XMLConstants.ELEMENT_PROPERTY;
  private static final String JUNIT_ATTR_PROPERTY_NAME = XMLConstants.ATTR_PROPERTY_NAME;
  private static final String JUNIT_ATTR_PROPERTY_VALUE = XMLConstants.ATTR_PROPERTY_VALUE;
  private static final String JUNIT_ELEMENT_TESTCASE = XMLConstants.ELEMENT_TESTCASE;
  private static final String JUNIT_ELEMENT_FAILURE = XMLConstants.ELEMENT_FAILURE;
  private static final String JUNIT_ELEMENT_SKIPPED = XMLConstants.ELEMENT_SKIPPED;
  private static final String JUNIT_ATTR_TESTCASE_NAME = XMLConstants.ATTR_TESTCASE_NAME;
  private static final String JUNIT_ATTR_TESTCASE_CLASSNAME = XMLConstants.ATTR_TESTCASE_CLASSNAME;
  private static final String JUNIT_ATTR_TESTCASE_TIME = XMLConstants.ATTR_TESTCASE_TIME;

  private final boolean optionalTestSuitesNode;

  @Inject
  public AntXmlResultWriter() {
    this(false);
  }

  public AntXmlResultWriter(boolean optionalTestSuitesNode) {
    this.optionalTestSuitesNode = optionalTestSuitesNode;
  }

  public void writeTestSuites(XmlWriter writer, TestResult result) throws IOException {
    List<TestResult> childResults = result.getChildResults();
    boolean includeTestSuitesNode = !optionalTestSuitesNode || childResults.size() > 1;
    writer.startDocument();
    if (includeTestSuitesNode) {
      writer.startElement(JUNIT_ELEMENT_TESTSUITES);
    }
    for (TestResult child : childResults) {
      writeTestSuite(writer, child, result.getFailures());
    }
    if (includeTestSuitesNode) {
      writer.endElement();
    }
    writer.close();
  }

  private void writeTestSuite(XmlWriter writer, TestResult result, Iterable<String> parentFailures)
      throws IOException {
    List<String> allFailures = new ArrayList<>();
    for (String failure : parentFailures) {
      allFailures.add(failure);
    }
    allFailures.addAll(result.getFailures());
    parentFailures = allFailures;

    writer.startElement(JUNIT_ELEMENT_TESTSUITE);

    writeTestSuiteAttributes(writer, result);
    writeTestSuiteProperties(writer, result);
    writeTestCases(writer, result, parentFailures);
    // We don't use this in FTL at all, commenting the code to document it as it different in
    // this fork, i.e., from the original source.
    // writeTestSuiteOutput(writer);

    writer.endElement();

    for (TestResult child : result.getChildResults()) {
      if (!child.getChildResults().isEmpty()) {
        writeTestSuite(writer, child, parentFailures);
      }
    }
  }

  private void writeTestSuiteProperties(XmlWriter writer, TestResult result) throws IOException {
    writer.startElement(JUNIT_ATTR_TESTSUITE_PROPERTIES);
    for (Entry<String, String> entry : result.getProperties().entrySet()) {
      writer.startElement(JUNIT_ELEMENT_PROPERTY);
      writer.writeAttribute(JUNIT_ATTR_PROPERTY_NAME, entry.getKey());
      writer.writeAttribute(JUNIT_ATTR_PROPERTY_VALUE, entry.getValue());
      writer.endElement();
    }
    writer.endElement();
  }

  private void writeTestCases(XmlWriter writer, TestResult result, Iterable<String> parentFailures)
      throws IOException {
    for (TestResult child : result.getChildResults()) {
      if (child.getChildResults().isEmpty()) {
        writeTestCase(writer, child, parentFailures);
      }
    }
  }

  // We don't use these in FTL at all. Leaving the code as is from the original fork
  // for documentation
  /*
  private void writeTestSuiteOutput(XmlWriter writer) throws IOException {

    writer.startElement(JUNIT_ATTR_TESTSUITE_SYSTEM_OUT);
    writer.endElement();
    writer.startElement(JUNIT_ATTR_TESTSUITE_SYSTEM_ERR);
    writer.endElement();
  } */

  private void writeTestSuiteAttributes(XmlWriter writer, TestResult result) throws IOException {
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_NAME, result.getName());
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_HOSTNAME, "localhost");
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_TESTS, result.getNumTests());
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_FAILURES, result.getNumFailures());
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_SKIPPED, result.getNumSkips());
    // JUnit 4.x no longer distinguishes between errors and failures, so it should be safe to just
    // report errors as 0 and put everything into failures.
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_ERRORS, 0);
    writer.writeAttribute(JUNIT_ATTR_TESTSUITE_TIME, getFormattedRunTime(result.getRunTime()));
    if (result.getTimestamp().isPresent()) {
      writer.writeAttribute(
          JUNIT_ATTR_TESTSUITE_TIMESTAMP, getFormattedTimestamp(result.getTimestamp().get()));
    }
  }

  private void writeTestCase(XmlWriter writer, TestResult result, Iterable<String> parentFailures)
      throws IOException {
    writer.startElement(JUNIT_ELEMENT_TESTCASE);
    writer.writeAttribute(JUNIT_ATTR_TESTCASE_NAME, result.getName());
    writer.writeAttribute(JUNIT_ATTR_TESTCASE_CLASSNAME, result.getClassName());
    writer.writeAttribute(JUNIT_ATTR_TESTCASE_TIME, getFormattedRunTime(result.getRunTime()));

    for (String failure : parentFailures) {
      writeFailuresToXmlWriter(writer, failure);
    }

    for (String failure : result.getFailures()) {
      writeFailuresToXmlWriter(writer, failure);
    }

    if (result.getNumSkips() == 1) {
      writer.startElement(JUNIT_ELEMENT_SKIPPED);
      for (String message : result.getSkippedMessages()) {
        writer.writeCharacters(message);
      }
      writer.endElement();
    }

    writer.endElement();
  }

  private static void writeFailuresToXmlWriter(XmlWriter writer, String failure)
      throws IOException {
    if (Strings.isNullOrEmpty(failure)) {
      return;
    }

    writer.startElement(JUNIT_ELEMENT_FAILURE);
    // TODO: create failure types for different test types (Ex. ios, junit)
    writer.writeCharacters(failure);
    writer.endElement();
  }

  private static String getFormattedRunTime(long runTime) {
    return String.valueOf(runTime / 1000.0D);
  }

  private static String getFormattedTimestamp(LocalDateTime timestamp) {
    return timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }
}
