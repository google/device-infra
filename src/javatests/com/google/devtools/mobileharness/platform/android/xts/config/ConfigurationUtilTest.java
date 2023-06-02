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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ConfigurationUtil}. */
@RunWith(JUnit4.class)
public class ConfigurationUtilTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Path tempDirPath;

  private ConfigurationUtil configurationUtil;
  private LocalFileUtil localFileUtil;

  private static final String TEST_CONFIG_DIR =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/config/testdata/cts/testcases";

  @Before
  public void setUpConfigurationUtil() throws Exception {
    configurationUtil = new ConfigurationUtil();
    localFileUtil = new LocalFileUtil();
  }

  @Before
  public void prepareTempDir() throws Exception {
    tempDirPath = tempFolder.getRoot().toPath();
  }

  @Test
  public void getConfigFromDirs_returnsConfigs() throws Exception {
    String testConfigDir = RunfilesUtil.getRunfilesLocation(TEST_CONFIG_DIR);
    ImmutableMap<String, Configuration> configs =
        configurationUtil.getConfigsFromDirs(ImmutableList.of(Paths.get(testConfigDir).toFile()));
    assertThat(configs).hasSize(2);
  }

  @Test
  public void getConfigPathsFromDirs_configContent_returnsConfigNames() throws Exception {
    Path testcasesDir = Paths.get(tempDirPath.toString(), "testcases");
    Path newFile1 = testcasesDir.resolve("new_file1.txt");
    Path newFile2 = testcasesDir.resolve("new_file2.config");
    Path newFile3 = testcasesDir.resolve("new_file3.xml");
    localFileUtil.writeToFile(newFile1.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile2.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile3.toString(), "<configuration>");

    ImmutableSet<String> configNames =
        configurationUtil.getConfigPathsFromDirs(ImmutableList.of(tempDirPath.toFile()));

    assertThat(configNames).containsExactly(newFile2.toString(), newFile3.toString());
  }

  @Test
  public void getConfigPathsFromDirs_multiDir_returnsConfigNames() throws Exception {
    Path testcasesDir1 = Paths.get(tempDirPath.toString(), "cts1");
    Path testcasesDir2 = Paths.get(tempDirPath.toString(), "cts2");
    Path newFile11 = testcasesDir1.resolve("testcases").resolve("new_file1.txt");
    Path newFile12 = testcasesDir1.resolve("testcases").resolve("new_file2.config");
    Path newFile21 = testcasesDir2.resolve("testcases").resolve("new_file3.xml");
    localFileUtil.writeToFile(newFile11.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile12.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile21.toString(), "<configuration>");

    ImmutableSet<String> configNames =
        configurationUtil.getConfigPathsFromDirs(
            ImmutableList.of(testcasesDir1.toFile(), testcasesDir2.toFile()));

    assertThat(configNames).containsExactly(newFile12.toString(), newFile21.toString());
  }

  @Test
  public void getConfigPathsFromDirs_duplicateModuleName_dedupConfigNames() throws Exception {
    Path testcasesDir1 = Paths.get(tempDirPath.toString(), "cts1");
    Path testcasesDir2 = Paths.get(tempDirPath.toString(), "cts2");
    Path newFile11 = testcasesDir1.resolve("testcases").resolve("new_file1.txt");
    Path newFile12 = testcasesDir1.resolve("testcases").resolve("new_file2.xml");
    Path newFile21 = testcasesDir2.resolve("testcases").resolve("new_file2.xml");
    localFileUtil.writeToFile(newFile11.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile12.toString(), "<configuration>");
    localFileUtil.writeToFile(newFile21.toString(), "<configuration>");

    ImmutableSet<String> configNames =
        configurationUtil.getConfigPathsFromDirs(
            ImmutableList.of(testcasesDir1.toFile(), testcasesDir2.toFile()));

    assertThat(configNames).containsExactly(newFile12.toString());
  }
}
