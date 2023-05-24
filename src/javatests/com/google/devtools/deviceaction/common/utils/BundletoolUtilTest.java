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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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

@RunWith(JUnit4.class)
public final class BundletoolUtilTest {

  private static final String SERIAL = "id";

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private CommandExecutor mockExecutor;

  private File adbFile;
  private File aaptFile;
  private File bundletoolJarFile;
  private File javaBin;
  private File genFileDir;
  private BundletoolUtil bundletoolUtil;
  private BundletoolUtil bundletoolUtilWithoutAdbAapt;
  private String workingDir;
  private final ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);

  @Before
  public void setUp() throws Exception {
    adbFile = tmpFolder.newFile("adb");
    aaptFile = tmpFolder.newFile("aapt");
    javaBin = tmpFolder.newFile("java");
    genFileDir = tmpFolder.newFolder("gen-files");
    bundletoolJarFile = tmpFolder.newFile("bundletool.jar");
    workingDir = bundletoolJarFile.getParent();
    bundletoolUtil =
        new BundletoolUtil(
            Optional.of(bundletoolJarFile.toPath()),
            Optional.of(aaptFile),
            Optional.of(adbFile),
            javaBin.toPath(),
            genFileDir.toPath(),
            mockExecutor,
            new LocalFileUtil());
    bundletoolUtilWithoutAdbAapt =
        new BundletoolUtil(
            Optional.of(bundletoolJarFile.toPath()),
            Optional.empty(),
            Optional.empty(),
            javaBin.toPath(),
            genFileDir.toPath(),
            mockExecutor,
            new LocalFileUtil());
  }

  @Test
  public void construct_missingBundletoolJarFile_throwException() {
    assertThrows(
        DeviceActionException.class,
        () ->
            new BundletoolUtil(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                javaBin.toPath(),
                genFileDir.toPath(),
                mockExecutor,
                new LocalFileUtil()));
  }

  @Test
  public void generateDeviceSpecFile_executeCommand_returnFilePath() throws Exception {
    String filePath = genFileDir.getAbsolutePath() + "/id/device-spec.json";
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "get-device-spec",
          "--output=" + filePath,
          "--adb=" + adbFile.getAbsolutePath(),
          "--device-id=" + "id",
          "--overwrite"
        };

    Path output = bundletoolUtil.generateDeviceSpecFile(SERIAL);

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output.toString()).isEqualTo(filePath);
  }

  @Test
  public void extractApks_executeCommand_returnDirPath() throws Exception {
    Path deviceSpecPath = Paths.get(genFileDir.getAbsolutePath(), "id/device-spec.json");
    File apksFile = tmpFolder.newFile("archive.apks");
    String outputDir = genFileDir.getAbsolutePath() + "/archive-Splits";
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "extract-apks",
          "--apks=" + apksFile.getAbsolutePath(),
          "--device-spec=" + deviceSpecPath,
          "--output-dir=" + outputDir,
          "--include-metadata"
        };

    Path output = bundletoolUtil.extractApks(apksFile, deviceSpecPath, "--include-metadata");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output.toString()).isEqualTo(outputDir);
    assertTrue(new File(outputDir).isDirectory());
  }

  @Test
  public void installApks_executeCommandWithAdb_returnOutput() throws Exception {
    File apksFile = tmpFolder.newFile("archive.apks");
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-apks",
          "--apks=" + apksFile.getAbsolutePath(),
          "--adb=" + adbFile.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000"
        };

    String output = bundletoolUtil.installApks(SERIAL, apksFile, "--timeout-millis=60000");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void installApks_executeCommandWithoutAdb_returnOutput() throws Exception {
    File apksFile = tmpFolder.newFile("archive.apks");
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-apks",
          "--apks=" + apksFile.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000"
        };

    String output =
        bundletoolUtilWithoutAdbAapt.installApks(SERIAL, apksFile, "--timeout-millis=60000");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void installMultiApks_executeCommandWithAdbAapt_returnOutput() throws Exception {
    File apksFile1 = tmpFolder.newFile("package1.apks");
    File apksFile2 = tmpFolder.newFile("package2.apks");
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-multi-apks",
          "--apks=" + apksFile1.getAbsolutePath() + "," + apksFile2.getAbsolutePath(),
          "--aapt2=" + aaptFile.getAbsolutePath(),
          "--adb=" + adbFile.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000",
          "--staged",
        };

    String output =
        bundletoolUtil.installMultiApks(
            SERIAL, ImmutableList.of(apksFile1, apksFile2), "--timeout-millis=60000", "--staged");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void installMultiApks_executeCommandWithoutAdbAapt_returnOutput() throws Exception {
    File apksFile1 = tmpFolder.newFile("package1.apks");
    File apksFile2 = tmpFolder.newFile("package2.apks");
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-multi-apks",
          "--apks=" + apksFile1.getAbsolutePath() + "," + apksFile2.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000",
          "--staged",
        };

    String output =
        bundletoolUtilWithoutAdbAapt.installMultiApks(
            SERIAL, ImmutableList.of(apksFile1, apksFile2), "--timeout-millis=60000", "--staged");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void installApksZip_executeCommandWithAdbAapt_returnOutput() throws Exception {
    File zipFile = tmpFolder.newFile("train.zip");
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-multi-apks",
          "--apks-zip=" + zipFile.getAbsolutePath(),
          "--aapt2=" + aaptFile.getAbsolutePath(),
          "--adb=" + adbFile.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000",
          "--staged",
        };

    String output =
        bundletoolUtil.installApksZip(SERIAL, zipFile, "--timeout-millis=60000", "--staged");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void installApksZip_executeCommandWithoutAdbAapt_returnOutput() throws Exception {
    File zipFile = tmpFolder.newFile("train.zip");
    String workingDir = bundletoolJarFile.getParent();
    String expect = "output";
    when(mockExecutor.run(any(Command.class))).thenReturn(expect);
    String[] expectedArgs =
        new String[] {
          "-jar",
          "bundletool.jar",
          "install-multi-apks",
          "--apks-zip=" + zipFile.getAbsolutePath(),
          "--device-id=id",
          "--timeout-millis=60000",
          "--staged",
        };

    String output =
        bundletoolUtilWithoutAdbAapt.installApksZip(
            SERIAL, zipFile, "--timeout-millis=60000", "--staged");

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }

  @Test
  public void getVersion_executeCommandWithoutAdbAapt_returnOutput() throws Exception {
    String expect = "1.8.0";
    String[] expectedArgs = new String[] {"-jar", "bundletool.jar", "version"};
    when(mockExecutor.run(any(Command.class))).thenReturn("1.8.0\n");

    String output = bundletoolUtil.getVersion();

    verify(mockExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecutable()).isEqualTo(javaBin.getAbsolutePath());
    assertThat(commandCaptor.getValue().getArguments()).containsExactlyElementsIn(expectedArgs);
    assertThat(commandCaptor.getValue().getWorkDirectory().map(Path::toString))
        .hasValue(workingDir);
    assertThat(output).isEqualTo(expect);
  }
}
