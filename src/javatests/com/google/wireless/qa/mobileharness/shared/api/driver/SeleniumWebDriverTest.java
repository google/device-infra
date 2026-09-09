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

/** Unit tests for {@link SeleniumWebDriver}. */
@RunWith(JUnit4.class)
public class SeleniumWebDriverTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String SELENIUM_ADDRESS = "http://127.0.0.1:4444/wd/hub";
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

  @Mock private TestInfos subTests;
  @Mock private TestInfo subTestInfo;
  @Mock private TestInfos childSubTests;
  @Mock private Timing timing;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private TestXmlParser testXmlParser;

  private SeleniumWebDriver driver;
  private File testFile;
  private File genFileDir;

  @Before
  public void setUp() throws Exception {
    testFile = tempFolder.newFile("selenium_test.sh");
    genFileDir = tempFolder.newFolder("genfiles");

    driver = new SeleniumWebDriver(device, testInfo, cmdExecutor, localFileUtil, testXmlParser);

    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.files()).thenReturn(files);
    when(files.get(SeleniumWebDriver.TAG_SELENIUM_TEST_FILE))
        .thenReturn(ImmutableSet.of(testFile.getAbsolutePath()));
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.getAbsolutePath());
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.resultWithCause()).thenReturn(testResult);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.timer()).thenReturn(testTimer);

    when(log.atInfo()).thenReturn(loggingApi);
    when(log.atWarning()).thenReturn(loggingApi);
    when(loggingApi.alsoTo(any(FluentLogger.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(any(Throwable.class))).thenReturn(loggingApi);
    when(loggingApi.withCause(nullable(Throwable.class))).thenReturn(loggingApi);

    when(testInfo.subTests()).thenReturn(subTests);
    when(subTests.add(any(String.class))).thenReturn(subTestInfo);
    when(subTestInfo.timing()).thenReturn(timing);
    when(subTestInfo.resultWithCause()).thenReturn(subTestResult);
    when(subTestInfo.properties()).thenReturn(properties);
    when(subTestInfo.subTests()).thenReturn(childSubTests);

    ListMultimap<String, TestInfo> emptyFinalized = LinkedListMultimap.create();
    when(subTests.getFinalized()).thenReturn(emptyFinalized);
    when(childSubTests.getFinalized()).thenReturn(emptyFinalized);

    ResultTypeWithCause defaultResult = ResultTypeWithCause.create(TestResult.PASS, null);
    when(testResult.get()).thenReturn(defaultResult);
    when(subTestResult.get()).thenReturn(defaultResult);
  }

  @Test
  public void run_success_generatesCorrectCommandAndEnv() throws Exception {
    when(params.get("SELENIUM_ADDRESS", null)).thenReturn(SELENIUM_ADDRESS);
    when(params.get("BASE_URL", null)).thenReturn(BASE_URL);

    ListMultimap<String, TestInfo> finalizedList = LinkedListMultimap.create();
    finalizedList.put("company#basic test", subTestInfo);
    when(subTests.getFinalized()).thenReturn(finalizedList);

    when(cmdExecutor.run(any(Command.class))).thenReturn("Selenium logs");

    driver.run(testInfo);

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(commandCaptor.capture());
    Command executedCommand = commandCaptor.getValue();

    assertThat(executedCommand.getCommand())
        .containsExactly(
            testFile.getAbsolutePath(),
            "--seleniumAddress=" + SELENIUM_ADDRESS,
            "--baseUrl=" + BASE_URL);

    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry("MH_GEN_FILE_DIR", genFileDir.getAbsolutePath());
    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry("SELENIUM_ADDRESS", SELENIUM_ADDRESS);
    assertThat(executedCommand.getExtraEnvironment()).containsEntry("BASE_URL", BASE_URL);
    assertThat(executedCommand.getExtraEnvironment())
        .containsEntry(
            "XML_OUTPUT_FILE",
            Path.of(genFileDir.getAbsolutePath(), "test-results", "results.xml").toString());

    verify(localFileUtil).grantFileOrDirFullAccess(testFile.getAbsolutePath());
  }

  @Test
  public void run_commandFailure_marksFail() throws Exception {
    CommandException commandException = Mockito.mock(CommandException.class);
    when(commandException.getMessage()).thenReturn("Selenium exited with 1");
    when(cmdExecutor.run(any(Command.class))).thenThrow(commandException);

    driver.run(testInfo);

    verify(testResult).setNonPassing(eq(TestResult.FAIL), eq(commandException));
  }

  @Test
  public void run_missingTestFile_throwsException() {
    when(files.get(SeleniumWebDriver.TAG_SELENIUM_TEST_FILE)).thenReturn(ImmutableSet.of());

    assertThrows(Exception.class, () -> driver.run(testInfo));
  }
}
