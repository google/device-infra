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

/*
 * Copyright 2024 Google LLC
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

package com.google.devtools.mobileharness.platform.androiddesktop.device;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link CrosCipdUtil}. */
@RunWith(JUnit4.class)
public class CrosCipdUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private CommandExecutor commandExecutor;
  @Mock private TestInfo testInfo;
  @Mock private Log testLogger;
  @Mock private Api atInfo;
  @Mock private CommandResult commandResult;

  private LocalFileUtil fileUtil;

  @Before
  public void setUp() {
    fileUtil = new LocalFileUtil();
    when(testInfo.log()).thenReturn(testLogger);
    when(testLogger.atInfo()).thenReturn(atInfo);
    when(testLogger.atWarning()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(atInfo.withCause(any(Throwable.class))).thenReturn(atInfo);
  }

  @Test
  public void defaultConstants_matchExpectedValues() {
    assertThat(CrosCipdUtil.DEFAULT_CIPD_TAG).isEqualTo("prod");
  }

  @Test
  public void getCipdBinary_returnsNonNullFile() {
    File binary = CrosCipdUtil.getCipdBinary();
    assertThat(binary).isNotNull();
    assertThat(binary.getName()).isEqualTo("cipd");
  }

  @Test
  public void getCipdCommand_returnsNonNullString() {
    String command = CrosCipdUtil.getCipdCommand();
    assertThat(command).isNotEmpty();
  }

  @Test
  public void printVersion_validBinary_executesVersion() throws Exception {
    when(commandResult.stdout()).thenReturn("lsnexus version 1.2.3");
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    CrosCipdUtil.printVersion(commandExecutor, "/path/to/lsnexus", testInfo);

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).exec(captor.capture());
    assertThat(captor.getValue().getCommand())
        .containsExactly("/path/to/lsnexus", "version")
        .inOrder();
  }

  @Test
  public void printVersion_nullOrEmptyBinaryPath_doesNothing() throws Exception {
    CrosCipdUtil.printVersion(commandExecutor, null, testInfo);
    CrosCipdUtil.printVersion(commandExecutor, "", testInfo);

    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void printVersion_execFails_logsWarningWithoutThrowing() throws Exception {
    when(commandExecutor.exec(any(Command.class))).thenThrow(CommandException.class);

    CrosCipdUtil.printVersion(commandExecutor, "/path/to/lsnexus", testInfo);

    verify(testLogger).atWarning();
  }

  @Test
  public void downloadPackage_nullExecutor_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                null, fileUtil, "pkg", "tag", null, "bin", testInfo, Duration.ofMinutes(1)));
  }

  @Test
  public void downloadPackage_nullOrEmptyPackageName_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                null,
                "tag",
                null,
                "bin",
                testInfo,
                Duration.ofMinutes(1)));
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "",
                "tag",
                null,
                "bin",
                testInfo,
                Duration.ofMinutes(1)));
  }

  @Test
  public void downloadPackage_nullOrEmptyVersion_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                null,
                null,
                "bin",
                testInfo,
                Duration.ofMinutes(1)));
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                "",
                null,
                "bin",
                testInfo,
                Duration.ofMinutes(1)));
  }

  @Test
  public void downloadPackage_nullOrEmptyBinaryName_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                "tag",
                null,
                null,
                testInfo,
                Duration.ofMinutes(1)));
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                "tag",
                null,
                "",
                testInfo,
                Duration.ofMinutes(1)));
  }

  @Test
  public void downloadPackage_absoluteBinaryName_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                "tag",
                null,
                "/absolute/path/bin",
                testInfo,
                Duration.ofMinutes(1)));
  }

  @Test
  public void downloadPackage_escapingBinaryName_throwsException() throws Exception {
    Path destDir = tempFolder.getRoot().toPath();

    assertThrows(
        MobileHarnessException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                fileUtil,
                "pkg",
                "tag",
                destDir,
                "../../bin",
                testInfo,
                Duration.ofMinutes(1)));
    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void downloadPackage_success_installsAndReturnsPath() throws Exception {
    Path destDir = tempFolder.newFolder("dest").toPath();
    Path binaryFile = destDir.resolve("mybin");
    Files.createFile(binaryFile);

    when(commandResult.stdout()).thenReturn("Install finished");
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    Path result =
        CrosCipdUtil.downloadPackage(
            commandExecutor,
            fileUtil,
            "chromiumos/infra/cft/lsnexus/linux-amd64",
            "prod",
            destDir,
            "mybin",
            testInfo,
            Duration.ofMinutes(5));

    assertThat(result).isEqualTo(binaryFile);

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).exec(captor.capture());
    Command cmd = captor.getValue();
    assertThat(cmd.getCommand())
        .containsAtLeast(
            "install",
            "chromiumos/infra/cft/lsnexus/linux-amd64",
            "prod",
            "-root",
            destDir.toAbsolutePath().toString());
  }

  @Test
  public void downloadPackage_subDirectoryInBinaryName_createsSanitizedTempDir() throws Exception {
    Path createdTempDir = tempFolder.newFolder("temp_sub").toPath();
    LocalFileUtil mockLocalFileUtil = mock(LocalFileUtil.class);
    when(mockLocalFileUtil.createTempDir(any(Path.class), anyString())).thenReturn(createdTempDir);
    Path subDir = createdTempDir.resolve("bin");
    Files.createDirectories(subDir);
    Files.createFile(subDir.resolve("mybin"));

    when(commandResult.stdout()).thenReturn("Install finished");
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    Path result =
        CrosCipdUtil.downloadPackage(
            commandExecutor,
            mockLocalFileUtil,
            "chromiumos/infra/cft/lsnexus/linux-amd64",
            "prod",
            null,
            "bin/mybin",
            testInfo,
            Duration.ofMinutes(5));

    assertThat(result).isEqualTo(subDir.resolve("mybin"));
    verify(mockLocalFileUtil).createTempDir(any(Path.class), eq("cipd_mybin"));
  }

  @Test
  public void downloadPackage_commandFails_cleansUpTempDirAndThrowsException() throws Exception {
    LocalFileUtil mockLocalFileUtil = mock(LocalFileUtil.class);
    when(mockLocalFileUtil.createTempDir(any(Path.class), anyString()))
        .thenReturn(tempFolder.newFolder("temp").toPath());
    when(commandExecutor.exec(any(Command.class))).thenThrow(CommandException.class);

    assertThrows(
        CommandException.class,
        () ->
            CrosCipdUtil.downloadPackage(
                commandExecutor,
                mockLocalFileUtil,
                "chromiumos/infra/cft/lsnexus/linux-amd64",
                "prod",
                /* destDir= */ null,
                "mybin",
                testInfo,
                Duration.ofMinutes(5)));

    verify(mockLocalFileUtil).removeFileOrDir(any(Path.class));
  }

  @Test
  public void downloadPackage_binaryNotFoundAfterInstall_throwsException() throws Exception {
    Path destDir = tempFolder.newFolder("dest").toPath();
    // Do NOT create the binary file in destDir
    when(commandResult.stdout()).thenReturn("Install succeeded but no file");
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                CrosCipdUtil.downloadPackage(
                    commandExecutor,
                    fileUtil,
                    "chromiumos/infra/cft/lsnexus/linux-amd64",
                    "prod",
                    destDir,
                    "mybin",
                    testInfo,
                    Duration.ofMinutes(5)));

    assertThat(e.getErrorId()).isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND);
  }

  @Test
  public void cleanupTempDir_validDirectory_removesDirectory() throws Exception {
    LocalFileUtil mockLocalFileUtil = mock(LocalFileUtil.class);
    Path dir = Path.of("/tmp/cipd_dir");

    CrosCipdUtil.cleanupTempDir(mockLocalFileUtil, dir, testInfo);

    verify(mockLocalFileUtil).removeFileOrDir(dir);
  }

  @Test
  public void cleanupTempDir_nullFileUtil_removesDirectory() throws Exception {
    Path dir = tempFolder.newFolder("to_cleanup").toPath();
    assertThat(Files.exists(dir)).isTrue();

    CrosCipdUtil.cleanupTempDir(null, dir, testInfo);

    assertThat(Files.exists(dir)).isFalse();
  }

  @Test
  public void cleanupTempDir_rootOrSystemOrWorkingDir_refusesDeletion() throws Exception {
    LocalFileUtil mockLocalFileUtil = mock(LocalFileUtil.class);

    CrosCipdUtil.cleanupTempDir(mockLocalFileUtil, Path.of("/"), testInfo);
    CrosCipdUtil.cleanupTempDir(mockLocalFileUtil, Path.of("/tmp"), testInfo);
    CrosCipdUtil.cleanupTempDir(mockLocalFileUtil, Path.of("/usr"), testInfo);
    CrosCipdUtil.cleanupTempDir(mockLocalFileUtil, Path.of("."), testInfo);

    verify(mockLocalFileUtil, never()).removeFileOrDir(any(Path.class));
  }

  @Test
  public void getPackageRootDir_rootOrEmptyParent_returnsNull() {
    assertThat(CrosCipdUtil.getPackageRootDir(Path.of("/lsnexus"), "lsnexus")).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(Path.of("/bin/mybin"), "bin/mybin")).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(Path.of("/tmp/mybin"), "mybin")).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(Path.of("/"), "")).isNull();
  }

  @Test
  public void getPackageRootDir_simpleBinaryName_returnsParentDir() {
    Path binaryPath = Path.of("/tmp/cipd_123/lsnexus");
    assertThat(CrosCipdUtil.getPackageRootDir(binaryPath, "lsnexus"))
        .isEqualTo(Path.of("/tmp/cipd_123"));
  }

  @Test
  public void getPackageRootDir_nestedBinaryName_returnsRootPackageDir() {
    Path binaryPath = Path.of("/tmp/cipd_123/bin/sub/mybin");
    assertThat(CrosCipdUtil.getPackageRootDir(binaryPath, "bin/sub/mybin"))
        .isEqualTo(Path.of("/tmp/cipd_123"));
  }

  @Test
  public void getPackageRootDir_emptyOrDotOrNullBinaryName_returnsNull() {
    Path binaryPath = Path.of("/tmp/cipd_123/lsnexus");
    assertThat(CrosCipdUtil.getPackageRootDir(binaryPath, "")).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(binaryPath, ".")).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(binaryPath, null)).isNull();
    assertThat(CrosCipdUtil.getPackageRootDir(null, "lsnexus")).isNull();
  }
}
