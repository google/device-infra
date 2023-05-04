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

package com.google.devtools.atsconsole.command;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.atsconsole.ConsoleInfo;
import com.google.devtools.atsconsole.ConsoleUtil;
import com.google.devtools.atsconsole.GuiceFactory;
import com.google.devtools.atsconsole.TestUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
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

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  @Mock @Bind private ConsoleUtil consoleUtil;
  @Mock @Bind private LocalFileUtil localFileUtil;

  private CommandLine commandLine;
  private ConsoleInfo consoleInfo;

  @Before
  public void setUp() {
    consoleInfo = TestUtil.getNewConsoleInfoInstance();
    consoleInfo.setMoblyTestCasesDir(MOBLY_TESTCASES_DIR);
    consoleInfo.setResultsDirectory(TEST_RESULTS_DIR);
    Injector injector =
        Guice.createInjector(BoundFieldModule.of(this), new ConsoleCommandTestModule(consoleInfo));
    injector.injectMembers(this);
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));
    out.reset();
    err.reset();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));
    doAnswer(
            invocation -> {
              System.out.println(invocation.getArgument(0, String.class));
              return null;
            })
        .when(consoleUtil)
        .printLine(anyString());
    doAnswer(
            invocation -> {
              System.err.println(invocation.getArgument(0, String.class));
              return null;
            })
        .when(consoleUtil)
        .printErrorLine(anyString());
    when(consoleUtil.completeHomeDirectory(anyString())).thenCallRealMethod();
  }

  @After
  public void restoreStreams() {
    String output = out.toString(UTF_8);
    String error = err.toString(UTF_8);

    System.setOut(originalOut);
    System.setErr(originalErr);

    // Also prints out and err, so they can be shown on the sponge
    System.out.println(output);
    System.out.println(error);
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
  public void setMoblyTestCasesDir_givenDirNotExist_noChange() throws Exception {
    String newMoblyTestCasesDir = "/path/to/not_exist_mobly_testcases_dir";
    when(localFileUtil.isDirExist(newMoblyTestCasesDir)).thenReturn(false);

    int exitCode = commandLine.execute("set", "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(MOBLY_TESTCASES_DIR);
    assertThat(err.toString(UTF_8.name())).contains("doesn't exist");
  }

  @Test
  public void setMoblyTestCasesDir_givenDirEmpty() throws Exception {
    String newMoblyTestCasesDir = "";

    int exitCode = commandLine.execute("set", "--mobly_testcases_dir", newMoblyTestCasesDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getMoblyTestCasesDir().orElse("")).isEqualTo(MOBLY_TESTCASES_DIR);
    assertThat(err.toString(UTF_8.name())).contains("doesn't exist");
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
  public void setResultsDir_givenDirNotExist_noChange() throws Exception {
    String newResultsDir = "/path/to/not_exist_dir";
    when(localFileUtil.isDirExist(newResultsDir)).thenReturn(false);

    int exitCode = commandLine.execute("set", "--results_dir", newResultsDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(TEST_RESULTS_DIR);
    assertThat(err.toString(UTF_8.name())).contains("doesn't exist");
  }

  @Test
  public void setResultsDir_givenDirEmpty() throws Exception {
    String newResultsDir = "";

    int exitCode = commandLine.execute("set", "--results_dir", newResultsDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(consoleInfo.getResultsDirectory().orElse("")).isEqualTo(TEST_RESULTS_DIR);
    assertThat(err.toString(UTF_8.name())).contains("doesn't exist");
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
}
