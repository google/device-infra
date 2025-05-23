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

package com.google.wireless.qa.mobileharness.shared.sponge;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.UserErrorId;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.SpongeNode.Property;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Class to parse test.xml generated by test runner/test framework. */
public class TestXmlParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Pattern of xml file that contains test name. */
  private static final Pattern MANEKI_PATTERN =
      Pattern.compile("(?<testname>[A-Za-z_]*)_testoutput\\.xml");

  /** All ISO control characters, except , , and . */
  private static final String ISO_CONTROL_CHAR =
      "[\u0000-\u0008\u000B-\u000C\u000E-\u001F\u007F-\u009F]";

  private final LocalFileUtil localFileUtil;

  private final boolean isMoblyTest;

  public TestXmlParser() {
    this.localFileUtil = new LocalFileUtil();
    this.isMoblyTest = false;
  }

  public TestXmlParser(boolean isMoblyTest) {
    this.localFileUtil = new LocalFileUtil();
    this.isMoblyTest = isMoblyTest;
  }

  /**
   * Parses the testsuite or testsuites node as a {@code TestInfo} and add it as a subTests of
   * {@param testInfo}.
   *
   * @param reduceProperties whether to add non name attributes of {@code node} as properties of the
   *     returned TestInfo.
   */
  private void parseTestContainerNodeToTestInfo(
      TestInfo testInfo, Node node, boolean reduceProperties) throws MobileHarnessException {
    NodeList childNodes = node.getChildNodes();
    int childNodesLength = childNodes.getLength();
    for (int i = 0; i < childNodesLength; i++) {
      Node childNode = childNodes.item(i);
      if (childNode.getNodeName().equals(XmlConstantsHelper.getElementTestsuite())) {
        TestInfo subTestInfo = parseTestNodeToSubTestInfo(testInfo, childNode, reduceProperties);
        parseTestContainerNodeToTestInfo(subTestInfo, childNode, reduceProperties);
        setTestResultAccordingToSubTests(
            subTestInfo, /* logChangedResult= */ !reduceProperties, /* isTestFromXml= */ true);
      } else if (childNode.getNodeName().equals(XmlConstantsHelper.getElementTestcase())) {
        TestInfo subTestInfo = parseTestNodeToSubTestInfo(testInfo, childNode, reduceProperties);
        if (!subTestInfo.resultWithCause().get().type().equals(TestResult.SKIP)) {
          parseTestCaseToTestInfo(subTestInfo, childNode, reduceProperties);
        }
      }
    }
  }

  /**
   * Parses the test node (testsuite or testcase) as a {@code TestInfo} and add it as a subTests of
   * {@param testInfo}.
   *
   * @param reduceProperties whether to add non name attributes of {@code node} as properties of the
   *     returned TestInfo.
   */
  private static TestInfo parseTestNodeToSubTestInfo(
      TestInfo testInfo, Node node, boolean reduceProperties) throws MobileHarnessException {
    NamedNodeMap nodeMap = node.getAttributes();
    int attributesLength = nodeMap.getLength();
    String subTestName = null;
    String className = null;
    boolean skipped = false;
    Optional<Duration> time = Optional.empty();
    Optional<Instant> timestamp = Optional.empty();
    Map<String, String> propertiesMap = new HashMap<>();
    for (int j = 0; j < attributesLength; j++) {
      if (nodeMap.item(j).getNodeName().equals(XmlConstantsHelper.getAttrTestcomponentName())) {
        subTestName = nodeMap.item(j).getNodeValue();
      } else {
        if (!reduceProperties) {
          propertiesMap.put(nodeMap.item(j).getNodeName(), nodeMap.item(j).getNodeValue());
        }
      }
      if (nodeMap.item(j).getNodeName().equals(XmlConstantsHelper.getAttrTestcaseClassname())) {
        className = nodeMap.item(j).getNodeValue();
      } else if (nodeMap.item(j).getNodeName().equals(XmlConstantsHelper.getAttrTestcaseResult())) {
        if (nodeMap.item(j).getNodeValue().equals(XmlConstantsHelper.getResultSkipped())) {
          skipped = true;
        }
      } else if (nodeMap.item(j).getNodeName().equals(XmlConstantsHelper.getAttrTestcaseTime())) {
        // Test case time is in milliseconds and with long format or seconds with double format.
        try {
          time = Optional.of(Duration.ofMillis(Long.parseLong(nodeMap.item(j).getNodeValue())));
        } catch (NumberFormatException e) {
          try {
            time =
                Optional.of(
                    Duration.ofMillis(
                        (long) (Double.parseDouble(nodeMap.item(j).getNodeValue()) * 1000)));
          } catch (NumberFormatException e2) {
            logger.atWarning().withCause(e2).log("Failed to parse time. Skip setting time.");
          }
        }
      } else if (nodeMap
          .item(j)
          .getNodeName()
          .equals(XmlConstantsHelper.getAttrTestcaseTimestamp())) {
        String timestampStr = nodeMap.item(j).getNodeValue();
        try {
          timestamp = Optional.of(Instant.parse(timestampStr));
        } catch (DateTimeParseException e) {
          try {
            // The timestamp might be a local time, c.f. xml files generated by gtest.
            // Assuming here that the device and host are on the same timezone.
            timestamp =
                Optional.of(
                    LocalDateTime.parse(timestampStr).atZone(ZoneId.systemDefault()).toInstant());
          } catch (DateTimeParseException e2) {
            logger.atWarning().withCause(e).log(
                "Failed to parse timestamp as an Instant ('%s') and local time ('%s'). Skip setting"
                    + " timestamp.",
                e.getMessage(), e2.getMessage());
          }
        }
      }
    }
    for (int j = 0; j < node.getChildNodes().getLength(); j++) {
      if (node.getChildNodes()
          .item(j)
          .getNodeName()
          .equals(XmlConstantsHelper.getElementProperties())) {
        NodeList properties = node.getChildNodes().item(j).getChildNodes();
        for (int k = 0; k < properties.getLength(); k++) {
          if (properties.item(k).getNodeName().equals(XmlConstantsHelper.getElementProperty())) {
            Element e = (Element) properties.item(k);
            propertiesMap.put(
                e.getAttribute(XmlConstantsHelper.getAttrPropertyName()),
                e.getAttribute(XmlConstantsHelper.getAttrPropertyValue()));
          }
        }
      }
    }
    if (subTestName == null) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  BasicErrorId.SPONGE_PARSE_XML_ERROR,
                  String.format("Failed to parse the name from xml node %s.", nodeMap)),
              logger);
    }
    TestInfo subTestInfo =
        testInfo
            .subTests()
            .add(
                Strings.isNullOrEmpty(className)
                    ? subTestName
                    : String.format("%s#%s", className, subTestName));
    if (timestamp.isPresent()) {
      boolean unused = subTestInfo.timing().start(timestamp.get());
      if (time.isPresent()) {
        unused = subTestInfo.timing().end(timestamp.get().plus(time.get()));
      }
    }
    if (skipped) {
      subTestInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.SKIP,
              new MobileHarnessException(
                  BasicErrorId.TEST_RESULT_SKIPPED_IN_TEST_XML, "Test skipped."));
    }
    subTestInfo.properties().addAll(propertiesMap);
    return subTestInfo;
  }

  /**
   * Parses the test case node as a {@code TestInfo}.
   *
   * @param reduceProperties false to record the caller stack if test passed. True won't record the
   *     caller stack.
   */
  private void parseTestCaseToTestInfo(TestInfo testInfo, Node testCase, boolean reduceProperties) {
    NodeList childNodes = testCase.getChildNodes();
    boolean failed = false;
    boolean errored = false;
    Optional<String> failureMessage = Optional.empty();
    Optional<String> errorMessage = Optional.empty();
    for (int j = 0; j < childNodes.getLength(); j++) {
      Node childNode = childNodes.item(j);
      String nodeName = childNode.getNodeName();
      if (nodeName.equals(XmlConstantsHelper.getElementError())) {
        errored = true;
        errorMessage = Optional.of(childNode.getTextContent());
        break;
      } else if (nodeName.equals(XmlConstantsHelper.getElementFailure())) {
        failed = true;
        failureMessage = Optional.of(childNode.getTextContent());
      }
    }
    if (errored) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new MobileHarnessException(
                  isMoblyTest
                      ? ExtErrorId.MOBLY_TEST_CASE_ERROR
                      : BasicErrorId.TEST_RESULT_ERRORED_IN_TEST_XML,
                  errorMessage.get()));
    } else if (failed) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  isMoblyTest
                      ? ExtErrorId.MOBLY_TEST_CASE_FAILURE
                      : BasicErrorId.TEST_RESULT_FAILED_IN_TEST_XML,
                  failureMessage.get()));
    } else {
      testInfo.resultWithCause().setPass(/* logChangedResult= */ !reduceProperties);
    }
  }

  /**
   * Parses the testsuites item in each file in the {@code testXmlPaths} to {@code TestInfo}, and
   * add those new {@code TestInfo} as subTests of {@code testInfo}. When {@code ignoreException} is
   * false, exceptions will be thrown if the xml files cannot be found or the xml files are invalid.
   *
   * @param testInfo the {@code TestInfo} to stores all the test results
   * @param testXmlPaths the list of xml file names. The name should follow the pattern
   *     <test_name>_testoutput.xml
   * @param ignoreException not throws exceptions when set to true
   * @param reduceProperties whether to add non name attributes of nodes in the XML files as
   *     properties of the returned {@code TestInfo}.
   */
  public void parseTestXmlFilesToTestInfo(
      TestInfo testInfo,
      List<String> testXmlPaths,
      boolean ignoreException,
      boolean reduceProperties)
      throws MobileHarnessException {
    for (String testXmlPath : testXmlPaths) {
      String subTestName = "NONAME";
      try {
        subTestName = extractTestNameFromFileName(testXmlPath);
        logger.atInfo().log("Find test name [%s] from file name [%s]", subTestName, testXmlPath);
      } catch (MobileHarnessException e) {
        testInfo.warnings().addAndLog(e, logger);
      }
      logger.atInfo().log("Add a subtest %s", subTestName);
      TestInfo subTestInfo = testInfo.subTests().add(subTestName);
      parseTestXmlFileToTestInfo(subTestInfo, testXmlPath, ignoreException, reduceProperties);
    }
    // Set the result of the testInfo.
    setTestResultAccordingToSubTests(
        testInfo, /* logChangedResult= */ true, /* isTestFromXml= */ false);
  }

  private void setTestResultAccordingToSubTests(
      TestInfo testInfo, boolean logChangedResult, boolean isTestFromXml) {
    boolean failed = false;
    boolean error = false;
    for (TestInfo subTestInfo : testInfo.subTests().getFinalized().values()) {
      if (subTestInfo.resultWithCause().get().type() == TestResult.FAIL) {
        failed = true;
      } else if (subTestInfo.resultWithCause().get().type() == TestResult.ERROR) {
        error = true;
        break;
      }
    }

    // From the MH perspective, error means an infra level error.
    // So when the test result xml has been generated by test runner, we can say that there's no
    // infra error and the MH generated test(not extracted from xml) result is either FAIL or PASS.
    if (error) {
      if (isTestFromXml) {
        testInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.ERROR,
                new MobileHarnessException(
                    isMoblyTest
                        ? ExtErrorId.MOBLY_TEST_FAILURE
                        : BasicErrorId.TEST_RESULT_ERRORED_IN_TEST_XML,
                    "Test suites errored"));
      } else {
        testInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.FAIL,
                new MobileHarnessException(
                    isMoblyTest
                        ? ExtErrorId.MOBLY_TEST_FAILURE
                        : BasicErrorId.TEST_RESULT_FAILED_IN_TEST_XML,
                    "Test suites failed"));
      }
    } else if (failed) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  isMoblyTest
                      ? ExtErrorId.MOBLY_TEST_FAILURE
                      : BasicErrorId.TEST_RESULT_FAILED_IN_TEST_XML,
                  "Test suites failed"));
    }
    // When empty sub-tests, only update the test result if it isn't set before.
    if (testInfo.resultWithCause().get().type() == TestResult.UNKNOWN) {
      testInfo.resultWithCause().setPass(logChangedResult);
    }
  }

  private String extractTestNameFromFileName(String testXmlPath) throws MobileHarnessException {
    String filename = PathUtil.basename(testXmlPath);
    Matcher matcher = MANEKI_PATTERN.matcher(filename);
    if (matcher.matches()) {
      return matcher.group("testname").trim();
    } else {
      throw new MobileHarnessException(
          UserErrorId.UNABLE_TO_EXTRACT_TEST_NAME_FROM_FILE_NAME,
          String.format(
              "The file name [%s] is not ended with _testoutput.xml, so not able to extract test"
                  + " name.",
              testXmlPath));
    }
  }

  /**
   * Parses the testcase items in the {@code testXmlPath} to {@code TestInfo} and add those new
   * {@code TestInfo} as subTests of {@code testInfo}. Throws exception when {@code ignoreException}
   * is false.
   *
   * @param reduceProperties whether to add non name attributes of nodes in the XML file to the
   *     properties of the TestInfo.
   */
  private void parseTestXmlFileToTestInfo(
      TestInfo testInfo, String testXmlPath, boolean ignoreException, boolean reduceProperties)
      throws MobileHarnessException {
    Optional<NodeList> nodes =
        extractTestSuitesNodesFromTestXml(testXmlPath, ignoreException, testInfo.warnings());
    if (nodes.isEmpty()) {
      logger.atInfo().log("Didn't parse any nodes from file [%s].", testXmlPath);
      return;
    }
    logger.atInfo().log("Start to parse %s", testXmlPath);
    int nodesLength = nodes.get().getLength();
    for (int i = 0; i < nodesLength; i++) {
      parseTestContainerNodeToTestInfo(testInfo, nodes.get().item(i), reduceProperties);
    }
    setTestResultAccordingToSubTests(
        testInfo, /* logChangedResult= */ !reduceProperties, /* isTestFromXml= */ false);
  }

  /**
   * Parses the testcase items in the {@code testXmlPath} to {@code TestInfo} and add those new
   * {@code TestInfo} as subTests of {@code testInfo}.
   */
  public void parseTestXmlFileToTestInfo(TestInfo testInfo, String testXmlPath)
      throws MobileHarnessException {
    parseTestXmlFileToTestInfo(
        testInfo, testXmlPath, /* ignoreException= */ true, /* reduceProperties= */ false);
  }

  /**
   * Parses the testcase items in the {@code testXmlPath} to {@code TestInfo} and add those new
   * {@code TestInfo} as subTests of {@code testInfo}.
   *
   * @throws exception when {@code ignoreException} is false.
   */
  public void parseTestXmlFileToTestInfo(
      TestInfo testInfo, String testXmlPath, boolean ignoreException)
      throws MobileHarnessException {
    parseTestXmlFileToTestInfo(
        testInfo, testXmlPath, ignoreException, /* reduceProperties= */ false);
  }

  /**
   * Extracts the testsuites nodes from the give xml.
   *
   * @param testXmlPath the xml file path.
   * @param ignoreException whether to ignore exception during the extracting.
   * @param warnings warnings of the currently running test, usually from {@code TestInfo}
   */
  private Optional<NodeList> extractTestSuitesNodesFromTestXml(
      String testXmlPath, boolean ignoreException, Warnings warnings)
      throws MobileHarnessException {
    String xmlString;
    try {
      xmlString = localFileUtil.readFile(testXmlPath).replaceAll(ISO_CONTROL_CHAR, "");
    } catch (MobileHarnessException e) {
      warnings.addAndLog(e, logger);
      if (!ignoreException) {
        throw e;
      } else {
        return Optional.empty();
      }
    }

    Optional<Document> doc = loadTestXmlToDocument(xmlString, ignoreException, warnings);
    if (doc.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(doc.get().getElementsByTagName(XmlConstantsHelper.getElementTestsuites()));
  }

  /** Parses the testsuite or testsuites node as a {@link SpongeNode} */
  private List<SpongeTestCase> extractSpongeTestCasesFromXmlNode(Node node) {
    String nodeName = node.getNodeName();
    List<SpongeTestCase> testCases = new ArrayList<>();
    if (nodeName.equals(XmlConstantsHelper.getElementTestsuite())
        || nodeName.equals(XmlConstantsHelper.getElementTestsuites())) {
      NodeList childNodes = node.getChildNodes();
      int childNodesLength = childNodes.getLength();

      for (int i = 0; i < childNodesLength; i++) {
        testCases.addAll(extractSpongeTestCasesFromXmlNode(childNodes.item(i)));
      }
    } else if (nodeName.equals(XmlConstantsHelper.getElementTestcase())) {
      testCases.add(extractSpongeTestCaseFromXmlNode(node));
    }
    return testCases;
  }

  /** Parses the testcase node as a {@link SpongeNode}. */
  private SpongeTestCase extractSpongeTestCaseFromXmlNode(Node testCase) {
    SpongeTestCase spongeTestCase = createSpongeNodeFromXmlNode(testCase, SpongeTestCase::new);
    NodeList childNodes = testCase.getChildNodes();
    for (int j = 0; j < childNodes.getLength(); j++) {
      Node childNode = childNodes.item(j);
      if (childNode.getNodeName().equals(XmlConstantsHelper.getElementFailure())
          || childNode.getNodeName().equals(XmlConstantsHelper.getElementError())) {
        ErrorId errorId;
        if (childNode.getNodeName().equals(XmlConstantsHelper.getElementFailure())) {
          errorId =
              isMoblyTest
                  ? ExtErrorId.MOBLY_TEST_CASE_FAILURE
                  : BasicErrorId.TEST_RESULT_FAILED_IN_TEST_XML;
          spongeTestCase.setFailure(childNode.getTextContent(), "", errorId.name());
        } else {
          errorId =
              isMoblyTest
                  ? ExtErrorId.MOBLY_TEST_CASE_ERROR
                  : BasicErrorId.TEST_RESULT_ERRORED_IN_TEST_XML;
          spongeTestCase.setError(childNode.getTextContent(), "", errorId.name());
        }
        spongeTestCase.addProperty(
            Property.create("test_error_code", String.valueOf(errorId.code())));
        spongeTestCase.addProperty(Property.create("test_error_name", errorId.name()));
        spongeTestCase.addProperty(Property.create("test_error_type", errorId.type().name()));
        break;
      }
    }
    return spongeTestCase;
  }

  private static <T extends SpongeNode> T createSpongeNodeFromXmlNode(
      Node node, Function<String, T> spongeNodeCreator) {
    Map<String, String> propertiesMap = getProperties(node);
    String testName = propertiesMap.getOrDefault(XmlConstantsHelper.getAttrTestcaseName(), "");
    String testClass = propertiesMap.get(XmlConstantsHelper.getAttrTestcaseClassname());
    propertiesMap.remove(XmlConstantsHelper.getAttrTestcaseName());
    propertiesMap.remove(XmlConstantsHelper.getAttrTestcaseClassname());

    T spongeNode =
        spongeNodeCreator.apply(
            Strings.isNullOrEmpty(testClass)
                ? testName
                : String.format("%s#%s", testClass, testName));
    String timeSec = propertiesMap.get(XmlConstantsHelper.getAttrTestcaseTime());
    if (timeSec != null) {
      long timeMillis = (long) (Double.parseDouble(timeSec) * 1000);
      spongeNode.setRunTime(Duration.ofMillis(timeMillis));
      propertiesMap.remove(XmlConstantsHelper.getAttrTestcaseTime());
    }
    for (Map.Entry<String, String> property : propertiesMap.entrySet()) {
      spongeNode.addProperty(Property.create(property.getKey(), property.getValue()));
    }
    return spongeNode;
  }

  /** Parses the properties map from a Node. */
  private static Map<String, String> getProperties(Node node) {
    NamedNodeMap nodeMap = node.getAttributes();
    int attributesLength = nodeMap.getLength();
    Map<String, String> propertiesMap = new HashMap<>();
    for (int j = 0; j < attributesLength; j++) {
      propertiesMap.put(nodeMap.item(j).getNodeName(), nodeMap.item(j).getNodeValue());
    }
    for (int j = 0; j < node.getChildNodes().getLength(); j++) {
      if (node.getChildNodes()
          .item(j)
          .getNodeName()
          .equals(XmlConstantsHelper.getElementProperties())) {
        NodeList properties = node.getChildNodes().item(j).getChildNodes();
        for (int k = 0; k < properties.getLength(); k++) {
          if (properties.item(k).getNodeName().equals(XmlConstantsHelper.getElementProperty())) {
            Element e = (Element) properties.item(k);
            propertiesMap.put(
                e.getAttribute(XmlConstantsHelper.getAttrPropertyName()),
                e.getAttribute(XmlConstantsHelper.getAttrPropertyValue()));
          }
        }
      }
    }
    return propertiesMap;
  }

  /** Parses the testcase items in the {@code testXmlPath} to {@link SpongeTestCase}s. */
  public List<SpongeTestCase> extractTestCasesFromTestXml(String testXmlPath)
      throws MobileHarnessException {
    return getSpongeTestCasesFromXmlDom(loadTestXmlToDocument(new File(testXmlPath)));
  }

  private List<SpongeTestCase> getSpongeTestCasesFromXmlDom(Document doc) {
    NodeList nodes = doc.getChildNodes();
    int nodesLength = nodes.getLength();
    List<SpongeTestCase> results = new ArrayList<>();
    for (int i = 0; i < nodesLength; i++) {
      results.addAll(extractSpongeTestCasesFromXmlNode(nodes.item(i)));
    }
    return results;
  }

  /** Parses the testcase items in the {@code testXmlContent} to {@link SpongeTestCase}s. */
  public List<SpongeTestCase> extractTestCasesFromTestXmlContent(String testXmlContent)
      throws MobileHarnessException {
    // doc is not empty when ignoreException is false
    Optional<Document> doc =
        loadTestXmlToDocument(testXmlContent, /* ignoreException= */ false, /* warnings= */ null);
    return getSpongeTestCasesFromXmlDom(doc.get());
  }

  /** Parses the {@code testXmlPath} to {@link SpongeNode} tree. */
  public Optional<SpongeNode> extractSpongeNodeTreeFromTestXml(String testXmlPath)
      throws MobileHarnessException {
    return extractSpongeNodeTreeFromXmlDom(loadTestXmlToDocument(new File(testXmlPath)));
  }

  /** Parses the {@code testXmlContent} to {@link SpongeNode} tree. */
  public Optional<SpongeNode> extractSpongeNodeTreeFromTestXmlContent(String testXmlContent)
      throws MobileHarnessException {
    // doc is not empty when ignoreException is false
    Optional<Document> doc =
        loadTestXmlToDocument(testXmlContent, /* ignoreException= */ false, /* warnings= */ null);
    return extractSpongeNodeTreeFromXmlDom(doc.get());
  }

  private Optional<SpongeNode> extractSpongeNodeTreeFromXmlDom(Document doc) {
    NodeList nodes = doc.getChildNodes();
    int nodesLength = nodes.getLength();
    if (nodesLength == 0) {
      return Optional.empty();
    } else if (nodesLength == 1) {
      return extractSpongeNodeFromXmlNode(nodes.item(0));
    } else {
      SpongeFakeRoot fakeRoot = new SpongeFakeRoot("root");
      for (int i = 0; i < nodesLength; i++) {
        extractSpongeNodeFromXmlNode(nodes.item(i)).ifPresent(fakeRoot::addChildNode);
      }
      return Optional.of(fakeRoot);
    }
  }

  /** Parses the xml node tree to a {@link SpongeNode} tree. */
  private Optional<SpongeNode> extractSpongeNodeFromXmlNode(Node node) {
    String nodeName = node.getNodeName();
    if (nodeName.equals(XmlConstantsHelper.getElementTestsuite())
        || nodeName.equals(XmlConstantsHelper.getElementTestsuites())) {
      SpongeTestSuite testSuite;
      if (nodeName.equals(XmlConstantsHelper.getElementTestsuites())) {
        testSuite = createSpongeNodeFromXmlNode(node, SpongeFakeRoot::new);
      } else {
        testSuite = createSpongeNodeFromXmlNode(node, SpongeTestSuite::new);
      }
      NodeList childNodes = node.getChildNodes();
      int childNodesLength = childNodes.getLength();
      for (int i = 0; i < childNodesLength; i++) {
        extractSpongeNodeFromXmlNode(childNodes.item(i)).ifPresent(testSuite::addChildNode);
      }
      return Optional.of(testSuite);
    } else if (nodeName.equals(XmlConstantsHelper.getElementTestcase())) {
      return Optional.of(extractSpongeTestCaseFromXmlNode(node));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Loads the test XML file content to a document.
   *
   * <p>It only returns an empty Optional when {@code ignoreException} is true and an exception is
   * thrown.
   */
  private Optional<Document> loadTestXmlToDocument(
      String testXmlFileContent, boolean ignoreException, @Nullable Warnings warnings)
      throws MobileHarnessException {
    return loadTestXmlToDocumentOss(testXmlFileContent, ignoreException, warnings);
  }

  /** Loads the test XML file to a document. */
  private Document loadTestXmlToDocument(File testXmlFile) throws MobileHarnessException {
    return loadTestXmlToDocumentOss(testXmlFile);
  }

  private Optional<Document> loadTestXmlToDocumentOss(
      String testXmlFileContent, boolean ignoreException, @Nullable Warnings warnings)
      throws MobileHarnessException {
    DocumentBuilderFactory documentBuilderFactory = createSecureDocumentBuilderFactory();
    try {
      return Optional.of(
          documentBuilderFactory
              .newDocumentBuilder()
              .parse(new InputSource(new StringReader(testXmlFileContent))));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      MobileHarnessException ex =
          new MobileHarnessException(
              BasicErrorId.SPONGE_PARSE_XML_ERROR,
              String.format("Parse document from test xml content fail. Caused by:%s\n", e),
              e);
      if (warnings != null) {
        warnings.addAndLog(ex, logger);
      }
      if (!ignoreException) {
        throw ex;
      }
    }
    return Optional.empty();
  }

  private Document loadTestXmlToDocumentOss(File testXmlFile) throws MobileHarnessException {
    FileInputStream fileInputStream;
    try {
      fileInputStream = new FileInputStream(testXmlFile);
    } catch (FileNotFoundException fileNotFoundException) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
          String.format("File %s not found. Caused by:%s\n", testXmlFile, fileNotFoundException),
          fileNotFoundException);
    }
    DocumentBuilderFactory documentBuilderFactory = createSecureDocumentBuilderFactory();
    try {
      return documentBuilderFactory.newDocumentBuilder().parse(fileInputStream);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new MobileHarnessException(BasicErrorId.SPONGE_PARSE_XML_ERROR, e.getMessage(), e);
    }
  }

  private static DocumentBuilderFactory createSecureDocumentBuilderFactory()
      throws MobileHarnessException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    try {
      // Process XML securely, avoid attacks like XML External Entities (XXE)
      documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      return documentBuilderFactory;
    } catch (ParserConfigurationException e) {
      throw new MobileHarnessException(
          BasicErrorId.SPONGE_DOCUMENT_BUILDER_FACTORY_SET_FEATURE_ERROR, e.getMessage(), e);
    }
  }
}
