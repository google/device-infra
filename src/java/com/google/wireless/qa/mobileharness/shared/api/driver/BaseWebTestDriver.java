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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Abstract base class for Web Test drivers supporting standard web testing environments.
 *
 * <p>Implements the Template Method pattern for executing web test runner scripts, setting up
 * standard OmniLab environment variables, capturing streaming logs, and post-processing/parsing
 * JUnit XML results into Sponge.
 */
public abstract class BaseWebTestDriver extends BaseDriver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PARAM_SELENIUM_ADDRESS = "SELENIUM_ADDRESS";
  public static final String PARAM_DEBUGGER_ADDRESS = "DEBUGGER_ADDRESS";
  public static final String PARAM_BASE_URL = "BASE_URL";

  private static final String ENV_MH_GEN_FILE_DIR = "MH_GEN_FILE_DIR";
  private static final String ENV_XML_OUTPUT_FILE = "XML_OUTPUT_FILE";
  private static final String ENV_TESTBRIDGE_TEST_ONLY = "TESTBRIDGE_TEST_ONLY";
  private static final String TEST_RESULTS_DIR = "test-results";
  private static final String RESULTS_XML_FILE = "results.xml";

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final TestXmlParser testXmlParser;

  protected BaseWebTestDriver(Device device, TestInfo testInfo) {
    this(device, testInfo, new CommandExecutor(), new LocalFileUtil(), new TestXmlParser());
  }

  protected BaseWebTestDriver(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      TestXmlParser testXmlParser) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.testXmlParser = testXmlParser;
  }

  /**
   * Template method defining the execution lifecycle of a web test runner.
   *
   * <ol>
   *   <li>Resolves the test binary/script from {@link #getTestFileTag()}.
   *   <li>Grants execute permissions to the script using {@link LocalFileUtil}.
   *   <li>Configures standard OmniLab environment variables (e.g. {@code MH_GEN_FILE_DIR}, {@code
   *       XML_OUTPUT_FILE}, {@code TESTBRIDGE_TEST_ONLY}).
   *   <li>Invokes {@link #populateEnvironment} for framework-specific env variables.
   *   <li>Builds the command line starting with the test file and appends args from {@link
   *       #populateCommandArgs}.
   *   <li>Executes the command with streamed log callbacks prefixed by {@link #getLogPrefix()}.
   *   <li>Handles {@link CommandException} by marking {@link TestResult#FAIL}.
   *   <li>Finally parses and post-processes JUnit XML results into Sponge {@link TestInfo}.
   * </ol>
   */
  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String testFile = Iterables.getOnlyElement(testInfo.jobInfo().files().get(getTestFileTag()));

    // Ensure wrapper script has execute permissions
    localFileUtil.grantFileOrDirFullAccess(testFile);

    Map<String, String> extraEnv = new HashMap<>();
    extraEnv.put(ENV_MH_GEN_FILE_DIR, testInfo.getGenFileDir());
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Set %s to %s", ENV_MH_GEN_FILE_DIR, testInfo.getGenFileDir());

    Path junitXmlPath = Path.of(testInfo.getGenFileDir(), TEST_RESULTS_DIR, RESULTS_XML_FILE);
    extraEnv.put(ENV_XML_OUTPUT_FILE, junitXmlPath.toString());
    testInfo.log().atInfo().alsoTo(logger).log("Set %s to %s", ENV_XML_OUTPUT_FILE, junitXmlPath);

    String testBridgeFilter = System.getenv(ENV_TESTBRIDGE_TEST_ONLY);
    if (testBridgeFilter != null && !testBridgeFilter.isEmpty()) {
      extraEnv.put(ENV_TESTBRIDGE_TEST_ONLY, testBridgeFilter);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Propagating %s: %s", ENV_TESTBRIDGE_TEST_ONLY, testBridgeFilter);
    }

    populateEnvironment(testInfo, extraEnv);

    List<String> commandList = new ArrayList<>();
    commandList.add(testFile);
    populateCommandArgs(testInfo, commandList);

    String logPrefix = getLogPrefix();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Executing %s test: %s with extra env: %s", logPrefix, commandList, extraEnv);

    try {
      Command command =
          Command.of(commandList)
              .extraEnv(extraEnv)
              .timeout(testInfo.timer())
              .onStdout(
                  LineCallback.does(
                      line -> testInfo.log().atInfo().alsoTo(logger).log("%s %s", logPrefix, line)))
              .onStderr(
                  LineCallback.does(
                      line ->
                          testInfo.log().atWarning().alsoTo(logger).log("%s %s", logPrefix, line)));
      cmdExecutor.run(command);
    } catch (CommandException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("%s test failed: %s", logPrefix, e.getMessage());
      testInfo.resultWithCause().setNonPassing(TestResult.FAIL, e);
    } finally {
      // Post-process and parse JUnit XML results.
      parseJUnitResults(testInfo);
    }
  }

  /** Tag name for the test binary or wrapper script declared in {@link FileAnnotation}. */
  protected abstract String getTestFileTag();

  /** Prefix string prepended to test runner log output (e.g. "[Playwright]", "[Protractor]"). */
  protected abstract String getLogPrefix();

  /**
   * Hook for subclasses to populate framework-specific environment variables.
   *
   * @param testInfo the test information container
   * @param extraEnv the mutable environment map to populate
   * @throws MobileHarnessException if an error occurs while configuring environment
   */
  protected void populateEnvironment(TestInfo testInfo, Map<String, String> extraEnv)
      throws MobileHarnessException {}

  /**
   * Hook for subclasses to append framework-specific flags and arguments to the command list.
   *
   * @param testInfo the test information container
   * @param commandList the mutable command line list to append to
   * @throws MobileHarnessException if an error occurs while configuring arguments
   */
  protected void populateCommandArgs(TestInfo testInfo, List<String> commandList)
      throws MobileHarnessException {}

  /**
   * Hook to sanitize XML classnames during JUnit post-processing. Default implementation strips
   * {@code .test.ts}, {@code .spec.ts}, {@code .test.js}, and {@code .spec.js} suffixes.
   *
   * @param className the original classname from JUnit XML
   * @return the sanitized classname
   */
  protected String cleanXmlClassName(String className) {
    return className.replaceAll("(?i)\\.(test|spec)?\\.[tj]s$", "");
  }

  /**
   * Parses JUnit test results XML and populates the test results into {@link TestInfo}.
   *
   * @param testInfo the test information container
   */
  protected void parseJUnitResults(TestInfo testInfo) {
    try {
      Path junitXmlPath = Path.of(testInfo.getGenFileDir(), TEST_RESULTS_DIR, RESULTS_XML_FILE);
      testInfo.log().atInfo().alsoTo(logger).log("Using JUnit results path: %s", junitXmlPath);

      if (Files.exists(junitXmlPath)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Found JUnit results at %s, parsing...", junitXmlPath);
        postProcessXmlFile(junitXmlPath, testInfo);
        testXmlParser.parseTestXmlFileToTestInfo(
            testInfo, junitXmlPath.toString(), /* ignoreException= */ false);
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("JUnit results file not found at %s", junitXmlPath);
      }
    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().withCause(e).alsoTo(logger).log("Failed to parse JUnit results.");
    }
  }

  /**
   * Post-processes the JUnit XML file before parsing to normalize class names for Sponge reporting.
   *
   * @param xmlPath path to the JUnit XML file
   * @param testInfo the test information container
   */
  protected void postProcessXmlFile(Path xmlPath, TestInfo testInfo) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Secure processing: disable external DTDs and entities to prevent XXE injection
      // vulnerabilities.
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = factory.newDocumentBuilder().parse(xmlPath.toFile());
      NodeList testCases = doc.getElementsByTagName("testcase");
      boolean modified = false;
      for (int i = 0; i < testCases.getLength(); i++) {
        Element testCase = (Element) testCases.item(i);
        if (testCase.hasAttribute("classname")) {
          String className = testCase.getAttribute("classname");
          String newClassName = cleanXmlClassName(className);
          if (!className.equals(newClassName)) {
            testCase.setAttribute("classname", newClassName);
            modified = true;
          }
        }
      }
      if (modified) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Post-processed JUnit XML to clean up classnames.");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory
            .newTransformer()
            .transform(new DOMSource(doc), new StreamResult(xmlPath.toFile()));
      }
    } catch (Exception e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to post-process JUnit XML file: %s", xmlPath);
    }
  }

  /**
   * Gets the Selenium hub address from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the Selenium address, or empty if not configured
   */
  protected Optional<String> getSeleniumAddress(TestInfo testInfo) {
    String seleniumAddress = testInfo.jobInfo().params().get(PARAM_SELENIUM_ADDRESS, null);
    if (seleniumAddress == null) {
      seleniumAddress = testInfo.properties().get(PARAM_SELENIUM_ADDRESS);
    }
    return Optional.ofNullable(seleniumAddress);
  }

  /**
   * Gets the debugger address (e.g., CDP socket address) from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the debugger address, or empty if not configured
   */
  protected Optional<String> getDebuggerAddress(TestInfo testInfo) {
    String debuggerAddress = testInfo.jobInfo().params().get(PARAM_DEBUGGER_ADDRESS, null);
    if (debuggerAddress == null) {
      debuggerAddress = testInfo.properties().get(PARAM_DEBUGGER_ADDRESS);
    }
    return Optional.ofNullable(debuggerAddress);
  }

  /**
   * Gets the base URL under test from test parameters or properties.
   *
   * @param testInfo the test information container
   * @return an Optional containing the base URL, or empty if not configured
   */
  protected Optional<String> getBaseUrl(TestInfo testInfo) {
    String baseUrl = testInfo.jobInfo().params().get(PARAM_BASE_URL, null);
    if (baseUrl == null) {
      baseUrl = testInfo.properties().get(PARAM_BASE_URL);
    }
    return Optional.ofNullable(baseUrl);
  }

  protected CommandExecutor getCommandExecutor() {
    return cmdExecutor;
  }

  protected LocalFileUtil getLocalFileUtil() {
    return localFileUtil;
  }

  protected TestXmlParser getTestXmlParser() {
    return testXmlParser;
  }
}
