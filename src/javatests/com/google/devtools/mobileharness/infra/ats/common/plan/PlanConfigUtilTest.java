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

package com.google.devtools.mobileharness.infra.ats.common.plan;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.plan.PlanConfigUtil.PlanConfigInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PlanConfigUtilTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String TEST_DATA_JAR_DIRECTORY =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/plan/testdata/jar";

  private static final String TEST_JAR_DIR =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_JAR_DIRECTORY);

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();

  @Inject private PlanConfigUtil planConfigUtil;
  private Path testJarDir;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector().injectMembers(this);
    prepareTestJars();
  }

  @Test
  public void loadAllConfigs_success() {
    ImmutableMap<String, PlanConfigInfo> configInfo = planConfigUtil.loadAllConfigsInfo(testJarDir);

    assertThat(configInfo)
        .containsExactly(
            "cts",
            PlanConfigInfo.of(
                "cts",
                testJarDir.resolve("test_app.jar"),
                "Runs CTS from a pre-existing CTS installation"),
            "cts-sim",
            PlanConfigInfo.of(
                "cts-sim",
                testJarDir.resolve("test_app.jar"),
                "Runs CTS-sim on device with SIM card"),
            "util/wifi",
            PlanConfigInfo.of(
                "util/wifi",
                testJarDir.resolve("test_app.jar"),
                "Utility config to configure wifi on device"));
  }

  private void prepareTestJars() throws Exception {
    testJarDir = temporaryFolder.newFolder("temp_jar").toPath();

    List<Path> testJars =
        realLocalFileUtil.listFilePaths(Path.of(TEST_JAR_DIR), /* recursively= */ false);
    for (Path testJar : testJars) {
      realLocalFileUtil.copyFileOrDir(
          testJar, testJarDir.resolve(testJar.getFileName().toString().replace("_jar", ".jar")));
    }
  }
}
