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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MoblyAospPackageTestSetupUtilTest {

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Captor private ArgumentCaptor<Command> commandCapture;

  @Mock private CommandExecutor commandExecutor;
  @Mock private CommandResult commandResult;
  @Mock private LocalFileUtil localFileUtil;

  private MoblyAospPackageTestSetupUtil moblyAospTestSetupUtil;

  @Before
  public void setUp() throws Exception {
    moblyAospTestSetupUtil = new MoblyAospPackageTestSetupUtil(localFileUtil, commandExecutor);
  }

  @Test
  public void setUpEnvAndGenerateTestCommand_generatesCommandWithVenv() throws Exception {
    MoblyAospPackageTestSetupUtil setupUtilSpy = spy(moblyAospTestSetupUtil);
    doReturn(true).when(setupUtilSpy).hasDeps(any());
    doReturn(null).when(setupUtilSpy).getPythonPath(any());
    doReturn(Path.of("mobly_venv/bin/python3")).when(setupUtilSpy).createVenv(any(), any());
    doReturn(Path.of("mobly/sample_test.py"))
        .when(setupUtilSpy)
        .resolveMoblyTestBin(any(), any(), any());
    doNothing().when(setupUtilSpy).installMoblyTestDeps(any(), any(), any());

    assertThat(
            setupUtilSpy.setupEnvAndGenerateTestCommand(
                Path.of("sample_test.zip"),
                Path.of("mobly"),
                Path.of("mobly_venv"),
                Path.of("sample_config.yaml"),
                "sample_test.py",
                "test1 test2",
                "python3",
                /* installMoblyTestDepsArgs= */ null))
        .asList()
        .containsExactly(
            "mobly_venv/bin/python3",
            "mobly/sample_test.py",
            "--config=sample_config.yaml",
            "--test_case",
            "test1",
            "test2");
  }

  @Test
  public void setUpEnvAndGenerateTestCommand_generatesCommandWithoutVenv() throws Exception {
    MoblyAospPackageTestSetupUtil setupUtilSpy = spy(moblyAospTestSetupUtil);
    doReturn(false).when(setupUtilSpy).hasDeps(any());

    assertThat(
            setupUtilSpy.setupEnvAndGenerateTestCommand(
                Path.of("sample_test"),
                Path.of("mobly"),
                Path.of("mobly_venv"),
                Path.of("sample_config.yaml"),
                null,
                "test1 test2",
                "python3",
                /* installMoblyTestDepsArgs= */ null))
        .asList()
        .containsExactly(
            "sample_test", "--config=sample_config.yaml", "--test_case", "test1", "test2");
  }

  @Test
  public void createVenv_runsCorrectCommand() throws Exception {
    Path venv =
        moblyAospTestSetupUtil.createVenv(Path.of("sample/bin/python3"), Path.of("mobly_venv"));
    ImmutableList<String> expectedCmd =
        ImmutableList.of("sample/bin/python3", "-m", "venv", "mobly_venv");

    verify(commandExecutor).run(Command.of(expectedCmd));
    assertThat(venv.toString()).isEqualTo("mobly_venv/bin/python3");
  }

  @Test
  public void getPythonPath_findsPythonPath_returnsPath() throws Exception {
    when(commandResult.exitCode()).thenReturn(0);
    when(commandResult.stdout()).thenReturn("sample/bin/python3");
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    assertThat(moblyAospTestSetupUtil.getPythonPath("python3").toString())
        .isEqualTo("sample/bin/python3");
  }

  @Test
  public void getPythonPath_cannotFindPythonPath_throwsException() throws Exception {
    when(commandResult.exitCode()).thenReturn(1);
    when(commandExecutor.exec(Command.of("which", "python3"))).thenReturn(commandResult);
    when(commandExecutor.run(Command.of("whereis", "python"))).thenReturn("sample/bin/python3");

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> moblyAospTestSetupUtil.getPythonPath("python3"));
    assertThat(exception.getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_AOSP_PYTHON_VERSION_NOT_FOUND_ERROR);
  }

  @Test
  public void resolveMoblyTestBin_returnsPath() throws Exception {
    when(localFileUtil.unzipFile(any(Path.class), any(Path.class))).thenReturn("");
    when(localFileUtil.isFileExist(any(Path.class))).thenReturn(true);
    doNothing().when(localFileUtil).grantFileOrDirFullAccessRecursively(any(Path.class));

    assertThat(
            moblyAospTestSetupUtil
                .resolveMoblyTestBin(Path.of("sample_test.zip"), Path.of("mobly"), "sample_test.py")
                .toString())
        .isEqualTo("mobly/sample_test.py");
  }

  @Test
  public void resolveMoblyTestBin_nullTestPath_runsPkgAsExecutable() throws Exception {
    when(localFileUtil.unzipFile(any(Path.class), any(Path.class))).thenReturn("");
    when(localFileUtil.isFileExist(any(Path.class))).thenReturn(true);
    doNothing().when(localFileUtil).grantFileOrDirFullAccessRecursively(any(Path.class));

    assertThat(
            moblyAospTestSetupUtil
                .resolveMoblyTestBin(Path.of("sample_test.zip"), Path.of("mobly"), null)
                .toString())
        .isEqualTo("sample_test.zip");
  }

  @Test
  public void resolveMoblyTestBin_noFileExist_throwsResolveException() throws Exception {
    when(localFileUtil.unzipFile(any(Path.class), any(Path.class))).thenReturn("");
    when(localFileUtil.isFileExist(any(Path.class))).thenReturn(false);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () ->
                moblyAospTestSetupUtil.resolveMoblyTestBin(
                    Path.of("sample_test.zip"), Path.of("mobly"), "sample_test.py"));
    assertThat(exception.getErrorId()).isEqualTo(ExtErrorId.MOBLY_AOSP_RESOLVE_TEST_PATH_ERROR);
  }

  @Test
  public void installMoblyTestBin_withRequirementsFile_usesRequirementsFile() throws Exception {
    String requirementsTxt =
        Path.of("mobly").resolve(MoblyAospPackageTestSetupUtil.REQUIREMENTS_TXT).toString();
    when(localFileUtil.isFileExist(requirementsTxt)).thenReturn(true);

    moblyAospTestSetupUtil.installMoblyTestDeps(
        Path.of("venv/python3"), Path.of("mobly"), /* installMoblyTestDepsArgs= */ null);
    ImmutableList<String> expectedCmd =
        ImmutableList.of("venv/python3", "-m", "pip", "install", "-r", requirementsTxt);

    verify(commandExecutor).run(Command.of(expectedCmd));
  }

  @Test
  public void installMoblyTestBin_withPyprojectToml_usesDirectory() throws Exception {
    String pyprojectToml =
        Path.of("mobly").resolve(MoblyAospPackageTestSetupUtil.PYPROJECT_TOML).toString();
    when(localFileUtil.isFileExist(pyprojectToml)).thenReturn(true);

    moblyAospTestSetupUtil.installMoblyTestDeps(
        Path.of("venv/python3"), Path.of("mobly"), /* installMoblyTestDepsArgs= */ null);
    ImmutableList<String> expectedCmd =
        ImmutableList.of("venv/python3", "-m", "pip", "install", "mobly");

    verify(commandExecutor).run(Command.of(expectedCmd));
  }

  @Test
  public void installMoblyTestBin_noDepsFile_skipsInstallation() throws Exception {
    moblyAospTestSetupUtil.installMoblyTestDeps(
        Path.of("venv/python3"), Path.of("mobly"), /* installMoblyTestDepsArgs= */ null);

    verify(commandExecutor, never()).run(any(Command.class));
  }

  @Test
  public void installMoblyTestBin_usesGivenDefaultTimeoutAndIndexUrl() throws Exception {
    String requirementsTxt =
        Path.of("mobly").resolve(MoblyAospPackageTestSetupUtil.REQUIREMENTS_TXT).toString();
    when(localFileUtil.isFileExist(requirementsTxt)).thenReturn(true);

    InstallMoblyTestDepsArgs installMoblyTestDepsArgs =
        InstallMoblyTestDepsArgs.builder()
            .setDefaultTimeout(Duration.ofMinutes(20))
            .setIndexUrl("index-url")
            .build();

    moblyAospTestSetupUtil.installMoblyTestDeps(
        Path.of("venv/python3"), Path.of("mobly"), installMoblyTestDepsArgs);
    ImmutableList<String> expectedCmd =
        ImmutableList.of(
            "venv/python3",
            "-m",
            "pip",
            "--default-timeout=1200",
            "install",
            "-i",
            "index-url",
            "-r",
            requirementsTxt);

    verify(commandExecutor).run(commandCapture.capture());

    Command cmd = commandCapture.getValue();
    assertThat(cmd.getCommand()).isEqualTo(expectedCmd);
    assertThat(cmd.getTimeout().get().getPeriod().get()).isEqualTo(Duration.ofMinutes(21));
  }

  @Test
  public void getTestCommand_generatesBaseCommand() throws Exception {
    assertThat(
            moblyAospTestSetupUtil.getTestCommand(
                Path.of("venv/python3"),
                Path.of("mobly/sample_test.py"),
                Path.of("config.yaml"),
                ""))
        .asList()
        .containsAtLeast("venv/python3", "mobly/sample_test.py", "--config=config.yaml");
  }

  @Test
  public void getTestCommand_customTestSelector_setsTestCase() throws Exception {
    assertThat(
            moblyAospTestSetupUtil.getTestCommand(
                Path.of("venv/python3"),
                Path.of("mobly/sample_test.py"),
                Path.of("config.yaml"),
                "test1 test2"))
        .asList()
        .containsAtLeast("--test_case", "test1", "test2");
  }

  @Test
  public void getTestCommand_allTestSelector_doesNotSetTestCase() throws Exception {
    assertThat(
            moblyAospTestSetupUtil.getTestCommand(
                Path.of("venv/python3"),
                Path.of("mobly/sample_test.py"),
                Path.of("config.yaml"),
                MoblyAospPackageTestSetupUtil.TEST_SELECTOR_ALL))
        .asList()
        .doesNotContain("--test_case");
  }
}
