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

/** Unit tests for {@link ProtractorWebDriver}. */
@RunWith(JUnit4.class)
public class ProtractorWebDriverTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String SELENIUM_ADDRESS = "http://localhost:4444/wd/hub";
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

  private ProtractorWebDriver driver;
  private File testFile;
  private File genFileDir;

  @Before
  public void setUp() throws Exception {
    testFile = tempFolder.newFile("protractor_test.sh");
    genFileDir = tempFolder.newFolder("genfiles");

    driver = new ProtractorWebDriver(device, testInfo, cmdExecutor, localFileUtil, testXmlParser);

    // Common mock setup
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.files()).thenReturn(files);
    when(files.get(ProtractorWebDriver.TAG_PROTRACTOR_TEST_FILE))
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
    when(params.get(ProtractorWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(SELENIUM_ADDRESS);
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
              return "Protractor logs";
            });

    driver.run(testInfo);

    // Capture executed command to verify structure
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(commandCaptor.capture());
    Command executedCommand = commandCaptor.getValue();

    assertThat(executedCommand.getCommand())
        .containsExactly(
            testFile.getAbsolutePath(),
            "--seleniumAddress=" + SELENIUM_ADDRESS,
            "--params.debuggerAddress=" + DEBUGGER_ADDRESS,
            "--capabilities.chromeOptions.debuggerAddress=" + DEBUGGER_ADDRESS,
            "--params.baseUrl=" + BASE_URL);

    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry("MH_GEN_FILE_DIR", genFileDir.getAbsolutePath());
    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry(
            "XML_OUTPUT_FILE",
            Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString());

    // Verify dependencies were called
    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
  }

  @Test
  public void run_withDriverSpecificParams_appendsParamsToCommand() throws Exception {
    when(params.get(ProtractorWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(SELENIUM_ADDRESS);
    when(params.get(ProtractorWebDriver.PARAM_SPECS, null)).thenReturn("spec1.ts,spec2.ts");
    when(params.get(ProtractorWebDriver.PARAM_TARGET_TYPE, null)).thenReturn("webview");
    when(params.get(ProtractorWebDriver.PARAM_PACKAGE_NAME, null)).thenReturn("com.example.app");
    when(device.getDeviceId()).thenReturn("emulator-5554");

    ListMultimap<String, TestInfo> finalizedList = LinkedListMultimap.create();
    finalizedList.put("company#basic test", subTestInfo);
    when(subTests.getFinalized()).thenReturn(finalizedList);

    ResultTypeWithCause passResult = ResultTypeWithCause.create(TestResult.PASS, null);
    when(testResult.get()).thenReturn(passResult);
    when(subTestResult.get()).thenReturn(passResult);

    when(cmdExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              writeDummyJUnitXml(genFileDir, "company.spec.ts", "basic test", /* failed= */ false);
              return "Protractor logs";
            });

    driver.run(testInfo);

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(commandCaptor.capture());
    Command executedCommand = commandCaptor.getValue();

    assertThat(executedCommand.getCommand())
        .containsExactly(
            testFile.getAbsolutePath(),
            "--seleniumAddress=" + SELENIUM_ADDRESS,
            "--specs=spec1.ts,spec2.ts",
            "--params.target_type=webview",
            "--params.package_name=com.example.app",
            "--params.device_id=emulator-5554");
  }

  @Test
  public void run_commandFailure_marksFailAndParsesPartialResults() throws Exception {
    when(params.get(ProtractorWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(null);
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
    when(params.get(ProtractorWebDriver.PARAM_SELENIUM_ADDRESS, null)).thenReturn(null);
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

    // Verify it still parsed the partial results in the finally block
    verify(testXmlParser)
        .parseTestXmlFileToTestInfo(
            eq(testInfo),
            eq(Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString()),
            eq(false));
    verify(testResult, Mockito.never()).setPass();
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
