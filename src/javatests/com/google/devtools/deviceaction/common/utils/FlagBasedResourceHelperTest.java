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

import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FlagBasedResourceHelperTest {
  private static final String[] FLAGS =
      new String[] {
        "--da_bundletool",
        "path/bundletool",
        "--da_cred_file",
        "path/cred",
        "--da_gen_file_dir",
        "gen_file_dir",
        "--tmp_dir_root",
        "tmp_file_dir",
        "--java_command_path",
        "java"
      };

  static {
    Flags.parse(FLAGS);
  }

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private Path exist;
  private final Path nonExist = Paths.get("file2");

  @Before
  public void setUp() throws Exception {
    exist = tmpFolder.newFile("file1").toPath();
  }

  @Test
  public void getExistingDir_expectedResults() throws Exception {
    assertThat(FlagBasedResourceHelper.getExistingDir(exist)).isEqualTo(exist);
    assertThrows(
        DeviceActionException.class, () -> FlagBasedResourceHelper.getExistingDir(nonExist));
  }

  @Test
  public void checkedValue_expectedResults() {
    assertThat(FlagBasedResourceHelper.checkedValue(exist)).hasValue(exist);
    assertThat(FlagBasedResourceHelper.checkedValue(nonExist)).isEmpty();
  }

  @Test
  public void createRandomDir_expectedResults() throws Exception {
    File folder1 = tmpFolder.newFolder("folder1");
    File folder2 = tmpFolder.newFolder("folder2");

    Path random1 = FlagBasedResourceHelper.createRandomDir(folder1.toPath());
    Path random2 = FlagBasedResourceHelper.createRandomDir(folder2.toPath());

    assertTrue(random1.toFile().isDirectory());
    assertTrue(random2.toFile().isDirectory());
    assertThat(random1.getFileName()).isEqualTo(random2.getFileName());
  }
}
