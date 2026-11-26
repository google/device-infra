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

package com.google.devtools.mobileharness.shared.util.file.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LocalFileUtilTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void resolveFileInPath_bareNameInPath_returnsFirstMatchingFileInPathOrder()
      throws Exception {
    Path firstDir = tempFolder.newFolder("dir_with_subdir_named_adb").toPath();
    Files.createDirectory(firstDir.resolve("adb"));
    Path customSdkToolsDir = tempFolder.newFolder("custom_sdk_tools").toPath();
    Path customAdb = Files.createFile(customSdkToolsDir.resolve("adb"));
    Path sdkToolsDir = tempFolder.newFolder("sdk_tools").toPath();
    Files.createFile(sdkToolsDir.resolve("adb"));
    String pathEnv =
        firstDir
            + File.pathSeparator
            + File.pathSeparator
            + customSdkToolsDir
            + File.pathSeparator
            + sdkToolsDir;
    LocalFileUtil localFileUtil = new LocalFileUtil(new CommandExecutor(), () -> pathEnv);

    assertThat(localFileUtil.resolveFileInPath("adb")).hasValue(customAdb.toAbsolutePath());
    assertThat(localFileUtil.isFileExistInPath("adb")).isTrue();
  }

  @Test
  public void resolveFileInPath_explicitFilePath_doesNotSearchPathWhenMissing() throws Exception {
    Path sdkToolsDir = tempFolder.newFolder("sdk_tools").toPath();
    Path sdkAdb = Files.createFile(sdkToolsDir.resolve("adb"));
    LocalFileUtil localFileUtil = new LocalFileUtil(new CommandExecutor(), sdkToolsDir::toString);

    assertThat(localFileUtil.resolveFileInPath(sdkAdb.toString()))
        .hasValue(sdkAdb.toAbsolutePath());
    assertThat(
            localFileUtil.resolveFileInPath(
                tempFolder.getRoot().toPath().resolve("missing/adb").toString()))
        .isEmpty();

    Path subdir = tempFolder.newFolder("subdir").toPath();
    Path subdirAdb = Files.createFile(subdir.resolve("adb"));
    Path relativeSubdirAdb = Path.of("").toAbsolutePath().relativize(subdirAdb.toAbsolutePath());
    assertThat(relativeSubdirAdb.isAbsolute()).isFalse();
    Optional<Path> resolvedRelative = localFileUtil.resolveFileInPath(relativeSubdirAdb.toString());
    assertThat(resolvedRelative).hasValue(relativeSubdirAdb.toAbsolutePath());
    assertThat(resolvedRelative.get().normalize()).isEqualTo(subdirAdb.toAbsolutePath());
  }

  @Test
  public void resolveFileInPath_missingOrNullPathEnv_returnsEmpty() {
    LocalFileUtil localFileUtil = new LocalFileUtil(new CommandExecutor(), () -> null);

    assertThat(localFileUtil.resolveFileInPath("nonexistent_adb_binary_xyz")).isEmpty();
    assertThat(localFileUtil.resolveFileInPath("")).isEmpty();
    assertThat(localFileUtil.isFileExistInPath("nonexistent_adb_binary_xyz")).isFalse();
  }
}
