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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link PlaywrightWebDriver}. */
@RunWith(JUnit4.class)
public class PlaywrightWebDriverTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String DEBUGGER_ADDRESS = "127.0.0.1:9876";
  private static final String BASE_URL = "https://example.com";

  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private Params params;
  @Mock private Properties properties;
  @Mock private Result testResult;
  @Mock private Result subTestResult;
  @Mock private Log log;
  @Mock private Api loggingApi;
  @Mock private CommandExecutor cmdExecutor;
  @Mock private CountDownTimer testTimer;

  // For TestXmlParser mutations:
  @Mock private TestInfos subTests;
  @Mock private TestInfo subTestInfo;
  @Mock private TestInfos childSubTests;
  @Mock private Timing timing;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private TestXmlParser testXmlParser;

  private PlaywrightWebDriver driver;
  private File testFile;
  private File genFileDir;

  @Before
  public void setUp() throws Exception {
    testFile = tempFolder.newFile("playwright_test.sh");
    genFileDir = tempFolder.newFolder("genfiles");

    driver = new PlaywrightWebDriver(device, testInfo, cmdExecutor, localFileUtil, testXmlParser);

    // Common mock setup
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.files()).thenReturn(files);
    when(files.get(PlaywrightWebDriver.TAG_PLAYWRIGHT_TEST_FILE))
        .thenReturn(ImmutableSet.of(testFile.getAbsolutePath()));
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.getAbsolutePath());
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.resultWithCause()).thenReturn(testResult);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.timer()).thenReturn(testTimer);

    // Mock logs to avoid NPEs
    when(log.atInfo()).thenReturn(loggingApi);
    when(log.atWarning()).thenReturn(loggingApi);
    when(loggingApi.alsoTo(any(FluentLogger.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(any(Throwable.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(nullable(Throwable.class))).thenReturn(loggingApi);

    // Mock TestXmlParser interactions
    when(testInfo.subTests()).thenReturn(subTests);
    when(subTests.add(any(String.class))).thenReturn(subTestInfo);
    when(subTestInfo.timing()).thenReturn(timing);
    when(subTestInfo.resultWithCause()).thenReturn(subTestResult);
    when(subTestInfo.properties()).thenReturn(properties);
    when(subTestInfo.subTests()).thenReturn(childSubTests);

    // Mock getFinalized to return empty by default to prevent NPE
    ListMultimap<String, TestInfo> emptyFinalized = LinkedListMultimap.create();
    when(subTests.getFinalized()).thenReturn(emptyFinalized);
    when(childSubTests.getFinalized()).thenReturn(emptyFinalized);

    // Mock Result.get() to return default result type
    ResultTypeWithCause defaultResult = ResultTypeWithCause.create(TestResult.PASS, null);
    when(testResult.get()).thenReturn(defaultResult);
    when(subTestResult.get()).thenReturn(defaultResult);
  }

  @Test
  public void run_success_generatesCorrectCommandAndEnv() throws Exception {
    // Setup params and properties
    when(params.get("DEBUGGER_ADDRESS", null)).thenReturn(DEBUGGER_ADDRESS);
    when(params.get("BASE_URL", null)).thenReturn(BASE_URL);

    // Setup subtests returned by parser
    ListMultimap<String, TestInfo> finalizedList = LinkedListMultimap.create();
    finalizedList.put("company#basic test", subTestInfo);
    when(subTests.getFinalized()).thenReturn(finalizedList);

    ResultTypeWithCause passResult = ResultTypeWithCause.create(TestResult.PASS, null);
    when(testResult.get()).thenReturn(passResult);
    when(subTestResult.get()).thenReturn(passResult);

    // Setup command execution to write output XML file
    when(cmdExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              writeDummyJUnitXml(genFileDir, "company.spec.ts", "basic test", /* failed= */ false);
              return "Playwright logs";
            });

    driver.run(testInfo);

    // Capture executed command to verify structure
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(commandCaptor.capture());
    Command executedCommand = commandCaptor.getValue();

    assertThat(executedCommand.getCommand())
        .containsExactly(
            testFile.getAbsolutePath(),
            "--debuggerAddress=" + DEBUGGER_ADDRESS,
            "--baseUrl=" + BASE_URL);

    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry("MH_GEN_FILE_DIR", genFileDir.getAbsolutePath());
    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry("PLAYWRIGHT_WS_ENDPOINT", "ws://" + DEBUGGER_ADDRESS);
    assertThat(executedCommand.getExtraEnvironment()).containsEntry("BASE_URL", BASE_URL);
    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry(
            "XML_OUTPUT_FILE",
            Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString());

    // Trigger stdout LineCallback
    assertThat(executedCommand.getStdoutLineCallback()).isPresent();
    executedCommand.getStdoutLineCallback().get().onLine("Mock stdout line");

    // Trigger stderr LineCallback
    assertThat(executedCommand.getStderrLineCallback()).isPresent();
    executedCommand.getStderrLineCallback().get().onLine("Mock stderr line");

    // Verify logs were called with the mock line contents
    verify(loggingApi, Mockito.atLeastOnce()).log(eq("[Playwright] %s"), eq("Mock stdout line"));
    verify(loggingApi, Mockito.atLeastOnce()).log(eq("[Playwright] %s"), eq("Mock stderr line"));

    // Verify dependencies were called
    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
  }

  @Test
  public void run_commandFailure_marksFailAndParsesPartialResults() throws Exception {
    when(params.get("DEBUGGER_ADDRESS", null)).thenReturn(null);
    when(params.get("BASE_URL", null)).thenReturn(null);

    // Setup subtests returned by parser
    ListMultimap<String, TestInfo> finalizedList = LinkedListMultimap.create();
    finalizedList.put("company#failed test", subTestInfo);
    when(subTests.getFinalized()).thenReturn(finalizedList);

    ResultTypeWithCause failResult =
        ResultTypeWithCause.create(
            TestResult.FAIL,
            new MobileHarnessException(BasicErrorId.SPONGE_PARSE_XML_ERROR, "failed"));
    when(subTestResult.get()).thenReturn(failResult);

    // Setup CommandExecutor to throw CommandException, but write a failed XML file
    CommandException commandException = Mockito.mock(CommandException.class);
    when(cmdExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              writeDummyJUnitXml(genFileDir, "company.spec.ts", "failed test", /* failed= */ true);
              throw commandException;
            });

    driver.run(testInfo);

    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    // Verify it marks non-passing
    verify(testResult).setNonPassing(eq(TestResult.FAIL), eq(commandException));

    // Verify it still tries to parse JUnit results
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
  }

  @Test
  public void run_interrupted_propagatesInterruptedButParsesResults() throws Exception {
    when(params.get("DEBUGGER_ADDRESS", null)).thenReturn(null);
    when(params.get("BASE_URL", null)).thenReturn(null);

    // Setup subtests returned by parser
    ListMultimap<String, TestInfo> finalizedList = LinkedListMultimap.create();
    finalizedList.put("company#partial test", subTestInfo);
    when(subTests.getFinalized()).thenReturn(finalizedList);

    ResultTypeWithCause passResult = ResultTypeWithCause.create(TestResult.PASS, null);
    when(subTestResult.get()).thenReturn(passResult);

    // Setup CommandExecutor to throw InterruptedException, but write a partial XML file before
    // exiting
    when(cmdExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              writeDummyJUnitXml(
                  genFileDir, "company.spec.ts", "partial test", /* failed= */ false);
              throw new InterruptedException("Thread interrupted!");
            });

    assertThrows(InterruptedException.class, () -> driver.run(testInfo));

    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    // Verify it still parsed the partial results in the finally block
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
    verify(testResult, Mockito.never()).setPass();
  }

  @Test
  public void getSeleniumAddress_returnsAddressFromParams() {
    when(params.get(PlaywrightWebDriver.PARAM_SELENIUM_ADDRESS, null))
        .thenReturn("http://params-address:4444/wd/hub");
    assertThat(driver.getSeleniumAddress(testInfo)).hasValue("http://params-address:4444/wd/hub");
  }

  @Test
  public void getSeleniumAddress_returnsAddressFromProperties() {
    when(params.get(PlaywrightWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_SELENIUM_ADDRESS))
        .thenReturn("http://properties-address:4444/wd/hub");
    assertThat(driver.getSeleniumAddress(testInfo))
        .hasValue("http://properties-address:4444/wd/hub");
  }

  @Test
  public void getSeleniumAddress_returnsEmptyWhenNotConfigured() {
    when(params.get(PlaywrightWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_SELENIUM_ADDRESS)).thenReturn(null);
    assertThat(driver.getSeleniumAddress(testInfo)).isEmpty();
  }

  @Test
  public void getDebuggerAddress_returnsAddressFromParams() {
    when(params.get(PlaywrightWebDriver.PARAM_DEBUGGER_ADDRESS, null)).thenReturn("127.0.0.1:1234");
    assertThat(driver.getDebuggerAddress(testInfo)).hasValue("127.0.0.1:1234");
  }

  @Test
  public void getDebuggerAddress_returnsAddressFromProperties() {
    when(params.get(PlaywrightWebDriver.PARAM_DEBUGGER_ADDRESS, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_DEBUGGER_ADDRESS)).thenReturn("127.0.0.1:1234");
    assertThat(driver.getDebuggerAddress(testInfo)).hasValue("127.0.0.1:1234");
  }

  @Test
  public void getDebuggerAddress_returnsEmptyWhenNotConfigured() {
    when(params.get(PlaywrightWebDriver.PARAM_DEBUGGER_ADDRESS, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_DEBUGGER_ADDRESS)).thenReturn(null);
    assertThat(driver.getDebuggerAddress(testInfo)).isEmpty();
  }

  @Test
  public void getBaseUrl_returnsUrlFromParams() {
    when(params.get(PlaywrightWebDriver.PARAM_BASE_URL, null)).thenReturn("http://example.com");
    assertThat(driver.getBaseUrl(testInfo)).hasValue("http://example.com");
  }

  @Test
  public void getBaseUrl_returnsUrlFromProperties() {
    when(params.get(PlaywrightWebDriver.PARAM_BASE_URL, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_BASE_URL)).thenReturn("http://example.com");
    assertThat(driver.getBaseUrl(testInfo)).hasValue("http://example.com");
  }

  @Test
  public void getBaseUrl_returnsEmptyWhenNotConfigured() {
    when(params.get(PlaywrightWebDriver.PARAM_BASE_URL, null)).thenReturn(null);
    when(properties.get(PlaywrightWebDriver.PARAM_BASE_URL)).thenReturn(null);
    assertThat(driver.getBaseUrl(testInfo)).isEmpty();
  }

  @Test
  public void constructor_default_injectsSuccessfully() {
    PlaywrightWebDriver defaultDriver = new PlaywrightWebDriver(device, testInfo);
    assertThat(defaultDriver).isNotNull();
  }

  @Test
  public void run_missingXmlResults_logsWarning() throws Exception {
    when(params.get("DEBUGGER_ADDRESS", null)).thenReturn(DEBUGGER_ADDRESS);
    when(params.get("BASE_URL", null)).thenReturn(BASE_URL);

    // Setup command execution to NOT write output XML file (do nothing)
    when(cmdExecutor.run(any(Command.class))).thenReturn("Playwright logs");

    driver.run(testInfo);

    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    // Verify it logged the info message
    verify(log, Mockito.atLeastOnce()).atInfo();
    // Verify parser was never called
    verify(testXmlParser, Mockito.never())
        .parseTestXmlFileToTestInfo(any(), any(), Mockito.anyBoolean());
  }

  @Test
  public void run_corruptXmlResults_logsWarning() throws Exception {
    when(params.get("DEBUGGER_ADDRESS", null)).thenReturn(DEBUGGER_ADDRESS);
    when(params.get("BASE_URL", null)).thenReturn(BASE_URL);

    // Setup command execution to write corrupted XML output
    when(cmdExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Path resultsPath =
                  Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml");
              java.nio.file.Files.createDirectories(resultsPath.getParent());
              java.nio.file.Files.writeString(
                  resultsPath, "<corrupt-invalid-xml-without-closing-tags");
              return "Playwright logs";
            });

    Mockito.doThrow(
            new MobileHarnessException(BasicErrorId.SPONGE_PARSE_XML_ERROR, "Failed to parse XML"))
        .when(testXmlParser)
        .parseTestXmlFileToTestInfo(any(), any(), Mockito.anyBoolean());

    driver.run(testInfo);

    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    // Verify it caught the exception and logged a warning
    verify(log, Mockito.times(2)).atWarning();
    // Verify parser was called
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
  }

  private void writeDummyJUnitXml(
      File baseGenDir, String className, String testName, boolean failed) throws IOException {
    Path resultsPath = Path.of(baseGenDir.getAbsolutePath(), "test-results", "results.xml");
    java.nio.file.Files.createDirectories(resultsPath.getParent());

    String xmlContent =
        "<testsuites>"
            + "  <testcase classname=\""
            + className
            + "\" name=\""
            + testName
            + "\" time=\"0.5\">"
            + (failed ? "    <failure message=\"Assertion failed\"></failure>" : "")
            + "  </testcase>"
            + "</testsuites>";

    java.nio.file.Files.writeString(resultsPath, xmlContent);
  }
}
