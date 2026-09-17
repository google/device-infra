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

package com.google.devtools.mobileharness.shared.util.testresult.loader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.xml.XMLConstants;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Loader for JUnit (Ant) XML test results written to the gen file directory of a test.
 *
 * <p>Unlike {@code instrument_test_result.pb}, which is only produced by the {@code
 * AndroidInstrumentation} driver, JUnit XML is produced on both platforms, and is the only
 * machine-readable result the iOS {@code IosNativeXcTest} driver emits.
 */
public final class JunitXmlResultLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Candidate JUnit XML file names, in preference order.
   *
   * <p>Keep in sync with {@code
   * com.google.devtools.mobileharness.infra.client.api.plugin.ddp.TestResultsProcessor}. {@code
   * test_result.xml} is still accepted for lab servers which have not picked up the rename to
   * {@code junit.xml} yet (b/556567181).
   */
  private static final ImmutableList<String> XML_RESULT_FILE_NAMES =
      ImmutableList.of("junit.xml", "test_result.xml", "test.xml");

  private static final String DISALLOW_DOCTYPE_DECL_FEATURE =
      "http://apache.org/xml/features/disallow-doctype-decl";

  private final LocalFileUtil localFileUtil;

  public JunitXmlResultLoader() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  public JunitXmlResultLoader(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Loads the JUnit XML test suites from the gen file directory of the given test.
   *
   * @return the parsed test suites, or empty if no JUnit XML file is present or it cannot be parsed
   */
  public Optional<ImmutableList<JunitXmlTestSuite>> loadTestSuites(TestInfo testInfo) {
    String genFileDir;
    try {
      genFileDir = testInfo.getGenFileDir();
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get gen file directory for test %s.", testInfo.locator().getId());
      return Optional.empty();
    }

    for (String fileName : XML_RESULT_FILE_NAMES) {
      Optional<ImmutableList<JunitXmlTestSuite>> testSuites =
          parseFile(testInfo, PathUtil.join(genFileDir, fileName));
      if (testSuites.isPresent()) {
        return testSuites;
      }
    }
    logger.atInfo().log(
        "No JUnit XML file in %s found for test %s.",
        XML_RESULT_FILE_NAMES, testInfo.locator().getId());
    return Optional.empty();
  }

  private Optional<ImmutableList<JunitXmlTestSuite>> parseFile(TestInfo testInfo, String xmlPath) {
    if (!localFileUtil.isFileExist(xmlPath)) {
      return Optional.empty();
    }
    try (InputStream inputStream = localFileUtil.newInputStream(Path.of(xmlPath))) {
      return Optional.of(parse(inputStream));
    } catch (InvalidPathException
        | IOException
        | MobileHarnessException
        | ParserConfigurationException
        | SAXException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse JUnit XML %s for test %s.", xmlPath, testInfo.locator().getId());
      return Optional.empty();
    }
  }

  /** Parses JUnit XML from the given input stream. */
  @VisibleForTesting
  static ImmutableList<JunitXmlTestSuite> parse(InputStream inputStream)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(DISALLOW_DOCTYPE_DECL_FEATURE, true);
    Document document = factory.newDocumentBuilder().parse(inputStream);
    document.getDocumentElement().normalize();

    // Matches all <testsuite> elements in the document (root, children of <testsuites>, or nested).
    ImmutableList.Builder<JunitXmlTestSuite> testSuites = ImmutableList.builder();
    NodeList testSuiteNodes = document.getElementsByTagName(XMLConstants.ELEMENT_TESTSUITE);
    for (int i = 0; i < testSuiteNodes.getLength(); i++) {
      Element testSuiteElement = (Element) testSuiteNodes.item(i);
      testSuites.add(
          JunitXmlTestSuite.create(
              testSuiteElement.getAttribute(XMLConstants.ATTR_TESTSUITE_NAME),
              toTestCases(testSuiteElement)));
    }
    return testSuites.build();
  }

  private static ImmutableList<JunitXmlTestCase> toTestCases(Element testSuiteElement) {
    ImmutableList.Builder<JunitXmlTestCase> testCases = ImmutableList.builder();
    for (Element testCaseElement :
        getChildElements(testSuiteElement, XMLConstants.ELEMENT_TESTCASE)) {
      testCases.add(toTestCase(testCaseElement));
    }
    return testCases.build();
  }

  private static JunitXmlTestCase toTestCase(Element testCaseElement) {
    JunitXmlTestCase.Builder testCase =
        JunitXmlTestCase.builder()
            .setName(testCaseElement.getAttribute(XMLConstants.ATTR_TESTCASE_NAME))
            .setClassName(testCaseElement.getAttribute(XMLConstants.ATTR_TESTCASE_CLASSNAME))
            .setElapsedTime(
                toElapsedTime(testCaseElement.getAttribute(XMLConstants.ATTR_TESTCASE_TIME)));

    // A test case carries either <failure> elements (assertion failures) or a single <error>
    // element (unexpected exception), never both. Only the first failure is reported, matching
    // AndroidInstrumentationTestSuiteResultConverter which reports a single stack trace.
    ImmutableList<Element> failures =
        getChildElements(testCaseElement, XMLConstants.ELEMENT_FAILURE);
    if (!failures.isEmpty()) {
      return testCase
          .setStatus(JunitXmlTestCase.Status.FAILED)
          .setStackTrace(toStackTrace(failures.get(0), XMLConstants.ATTR_FAILURE_MESSAGE))
          .build();
    }
    ImmutableList<Element> errors = getChildElements(testCaseElement, XMLConstants.ELEMENT_ERROR);
    if (!errors.isEmpty()) {
      return testCase
          .setStatus(JunitXmlTestCase.Status.ERROR)
          .setStackTrace(toStackTrace(errors.get(0), XMLConstants.ATTR_ERROR_MESSAGE))
          .build();
    }
    if (!getChildElements(testCaseElement, XMLConstants.ELEMENT_SKIPPED).isEmpty()) {
      return testCase.setStatus(JunitXmlTestCase.Status.SKIPPED).build();
    }
    return testCase.setStatus(JunitXmlTestCase.Status.PASSED).build();
  }

  private static String toStackTrace(Element element, String messageAttributeName) {
    String message = element.getAttribute(messageAttributeName);
    String body = element.getTextContent().trim();
    if (message.isEmpty()) {
      return body;
    }
    if (body.isEmpty()) {
      return message;
    }
    return message + "\n" + body;
  }

  /** Converts the {@code time} attribute, which is a number of seconds, to a {@link Duration}. */
  private static Duration toElapsedTime(String time) {
    if (time.isEmpty()) {
      return Duration.ZERO;
    }
    try {
      return Duration.ofMillis(Math.round(Double.parseDouble(time) * 1000.0));
    } catch (NumberFormatException e) {
      logger.atWarning().withCause(e).log("Failed to parse JUnit XML time attribute %s.", time);
      return Duration.ZERO;
    }
  }

  /**
   * Returns the direct child elements of {@code parent} with the given tag name.
   *
   * <p>Direct children only, so that test cases are not counted twice when a {@code <testsuite>}
   * nests another {@code <testsuite>}.
   */
  private static ImmutableList<Element> getChildElements(Element parent, String tagName) {
    ImmutableList.Builder<Element> elements = ImmutableList.builder();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
        elements.add((Element) child);
      }
    }
    return elements.build();
  }
}
