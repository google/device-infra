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

package com.google.devtools.mobileharness.infra.ats.console.command;

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleId;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.annotation.Nullable;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

@RunWith(JUnit4.class)
public final class SetCommandTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String MOBLY_TESTCASES_DIR = "/path/to/mobly_testcases_dir";
  private static final String TEST_RESULTS_DIR = "/path/to/test_results";

  @Bind @ConsoleId private static final String CONSOLE_ID = "fake_console_id";

  @Mock @Bind private LocalFileUtil localFileUtil;
  @Mock @Bind @Nullable @ConsoleLineReader private LineReader lineReader;

  private CommandLine commandLine;
  private ConsoleInfo consoleInfo;

  @Before
  public void setUp() {
    consoleInfo =
        new ConsoleInfo(
            ImmutableMap.of(
                "MOBLY_TESTCASES_DIR", MOBLY_TESTCASES_DIR, "TEST_RESULTS_DIR", TEST_RESULTS_DIR));
    Injector injector =
        Guice.createInjector(BoundFieldModule.of(this), new ConsoleCommandTestModule(consoleInfo));
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));
  }

  @Test
  public void setMoblyTestCasesDir_success() {
    String newMoblyTestCasesDir = "/path/to/another_mobly_testcases_dir";
    when(localFileUtil.isDirExist(newMoblyTestCasesDir)).thenReturn(true);

    int exitCode = commandLine.execute("set", "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(0);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(newMoblyTestCasesDir);
  }

  @Test
  public void setMoblyTestCasesDir_givenDirNotExist_noChange() {
    String newMoblyTestCasesDir = "/path/to/not_exist_mobly_testcases_dir";
    when(localFileUtil.isDirExist(newMoblyTestCasesDir)).thenReturn(false);

    int exitCode = commandLine.execute("set", "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(MOBLY_TESTCASES_DIR);
    verifyStderrContains("doesn't exist");
  }

  @Test
  public void setMoblyTestCasesDir_givenDirEmpty() {
    String newMoblyTestCasesDir = "";

    int exitCode = commandLine.execute("set", "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(MOBLY_TESTCASES_DIR);
    verifyStderrContains("doesn't exist");
  }

  @Test
  public void setResultsDir_success() {
    String newResultsDir = "/path/to/another_test_results_dir";
    when(localFileUtil.isDirExist(newResultsDir)).thenReturn(true);

    int exitCode = commandLine.execute("set", "--results_dir", newResultsDir);

    assertThat(exitCode).isEqualTo(0);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(newResultsDir);
  }

  @Test
  public void setResultsDir_givenDirNotExist_noChange() {
    String newResultsDir = "/path/to/not_exist_dir";
    when(localFileUtil.isDirExist(newResultsDir)).thenReturn(false);

    int exitCode = commandLine.execute("set", "--results_dir", newResultsDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(TEST_RESULTS_DIR);
    verifyStderrContains("doesn't exist");
  }

  @Test
  public void setResultsDir_givenDirEmpty() {
    String newResultsDir = "";

    int exitCode = commandLine.execute("set", "--results_dir", newResultsDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(TEST_RESULTS_DIR);
    verifyStderrContains("doesn't exist");
  }

  @Test
  public void setBothResultsDirAndMoblyTestCasesDir_success() {
    String newMoblyTestCasesDir = "/path/to/another_mobly_testcases_dir";
    String newResultsDir = "/path/to/another_test_results_dir";
    when(localFileUtil.isDirExist(newResultsDir)).thenReturn(true);
    when(localFileUtil.isDirExist(newMoblyTestCasesDir)).thenReturn(true);

    int exitCode =
        commandLine.execute(
            "set", "--results_dir", newResultsDir, "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(0);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(newResultsDir);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(newMoblyTestCasesDir);
    verify(localFileUtil).isDirExist(newResultsDir);
    verify(localFileUtil).isDirExist(newMoblyTestCasesDir);
  }

  @Test
  public void set_resultsDirSuccess_moblyTestCasesDirFail() {
    String newMoblyTestCasesDir = "/path/to/another_mobly_testcases_dir";
    String newResultsDir = "/path/to/another_test_results_dir";
    when(localFileUtil.isDirExist(newResultsDir)).thenReturn(true);
    when(localFileUtil.isDirExist(newMoblyTestCasesDir)).thenReturn(false);

    int exitCode =
        commandLine.execute(
            "set", "--results_dir", newResultsDir, "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(newResultsDir);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(MOBLY_TESTCASES_DIR);
    verify(localFileUtil).isDirExist(newResultsDir);
    verify(localFileUtil).isDirExist(newMoblyTestCasesDir);
  }

  @Test
  public void setPythonPackageIndexUrl_success() {
    String newPythonPackageIndexUrl = "https://pypi.tuna.tsinghua.edu.cn/simple";

    int exitCode = commandLine.execute("set", "python-package-index-url", newPythonPackageIndexUrl);

    assertThat(exitCode).isEqualTo(0);
    assertThat(consoleInfo.getPythonPackageIndexUrl().orElse(""))
        .isEqualTo(newPythonPackageIndexUrl);
  }

  @Test
  public void setPythonPackageIndexUrl_skipIfGivenUrlIsWhitespace() {
    String newPythonPackageIndexUrl = "  ";

    int exitCode = commandLine.execute("set", "python-package-index-url", newPythonPackageIndexUrl);

    assertThat(exitCode).isEqualTo(0);
    assertWithMessage("consoleInfo.getPythonPackageIndexUrl()")
        .about(optionals())
        .that(consoleInfo.getPythonPackageIndexUrl())
        .isEmpty();
  }

  private void verifyStderrContains(@SuppressWarnings("SameParameterValue") String substring) {
    verify(lineReader)
        .printAbove(
            (AttributedString) argThat(argument -> argument.toString().contains(substring)));
  }
}
