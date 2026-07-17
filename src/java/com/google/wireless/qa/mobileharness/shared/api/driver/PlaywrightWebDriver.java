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
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Driver for running Playwright tests on Android devices via CDP. */
@DriverAnnotation(help = "For running Playwright tests on Android devices.")
@TestAnnotation(
    required = false,
    help = "Leave it empty and Mobile Harness will simply use your job name as test name.")
public class PlaywrightWebDriver extends BaseWebTestDriver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FileAnnotation(required = true, help = "The Playwright test binary or wrapper script.")
  public static final String TAG_PLAYWRIGHT_TEST_FILE = "playwright_test_file";

  private final CommandExecutor cmdExecutor;

  @Inject
  PlaywrightWebDriver(Device device, TestInfo testInfo) {
    this(device, testInfo, new CommandExecutor());
  }

  PlaywrightWebDriver(Device device, TestInfo testInfo, CommandExecutor cmdExecutor) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String testFile =
        Iterables.getOnlyElement(testInfo.jobInfo().files().get(TAG_PLAYWRIGHT_TEST_FILE));

    // Ensure wrapper script has execute permissions
    new LocalFileUtil().grantFileOrDirFullAccess(testFile);

    String debuggerAddress = getDebuggerAddress(testInfo);
    String baseUrl = getBaseUrl(testInfo);

    Map<String, String> extraEnv = new HashMap<>();
    extraEnv.put("MH_GEN_FILE_DIR", testInfo.getGenFileDir());
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Set MH_GEN_FILE_DIR to %s", testInfo.getGenFileDir());

    if (debuggerAddress != null) {
      // Playwright expects ws:// scheme for CDP WebSocket connection
      String wsEndpoint = "ws://" + debuggerAddress;
      extraEnv.put("PLAYWRIGHT_WS_ENDPOINT", wsEndpoint);
      testInfo.log().atInfo().alsoTo(logger).log("Set PLAYWRIGHT_WS_ENDPOINT to %s", wsEndpoint);
    }

    if (baseUrl != null) {
      extraEnv.put("BASE_URL", baseUrl);
      testInfo.log().atInfo().alsoTo(logger).log("Set BASE_URL to %s", baseUrl);
    }

    Path junitXmlPath = Path.of(testInfo.getGenFileDir(), "test-results", "results.xml");
    extraEnv.put("XML_OUTPUT_FILE", junitXmlPath.toString());
    testInfo.log().atInfo().alsoTo(logger).log("Set XML_OUTPUT_FILE to %s", junitXmlPath);

    List<String> commandList = new ArrayList<>();
    commandList.add(testFile);

    // We can also forward these as params if the wrapper script expects them as flags
    if (debuggerAddress != null) {
      commandList.add("--debuggerAddress=" + debuggerAddress);
    }
    if (baseUrl != null) {
      commandList.add("--baseUrl=" + baseUrl);
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Executing Playwright test: %s with extra env: %s", commandList, extraEnv);

    try {
      try {
        Command command =
            Command.of(commandList)
                .extraEnv(extraEnv)
                .timeout(testInfo.timer())
                .onStdout(
                    LineCallback.does(
                        line ->
                            testInfo.log().atInfo().alsoTo(logger).log("[Playwright] %s", line)))
                .onStderr(
                    LineCallback.does(
                        line ->
                            testInfo
                                .log()
                                .atWarning()
                                .alsoTo(logger)
                                .log("[Playwright] %s", line)));
        cmdExecutor.run(command);
        testInfo.resultWithCause().setPass();
      } catch (CommandException e) {
        testInfo.log().atWarning().alsoTo(logger).log("Playwright test failed: %s", e.getMessage());
        testInfo.resultWithCause().setNonPassing(TestResult.FAIL, e);
      }
    } finally {
      // Post-process and parse JUnit XML results.
      parseJUnitResults(testInfo);
    }
  }

  private void parseJUnitResults(TestInfo testInfo) {
    try {
      Path junitXmlPath = Path.of(testInfo.getGenFileDir(), "test-results", "results.xml");
      testInfo.log().atInfo().alsoTo(logger).log("Using JUnit results path: %s", junitXmlPath);

      if (Files.exists(junitXmlPath)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Found JUnit results at %s, parsing...", junitXmlPath);
        postProcessXmlFile(junitXmlPath, testInfo);
        TestXmlParser parser = new TestXmlParser();
        parser.parseTestXmlFileToTestInfo(
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

  private void postProcessXmlFile(Path xmlPath, TestInfo testInfo) {
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
          String newClassName = className.replaceAll("(?i)\\.(test|spec)?\\.ts$", "");
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
}
