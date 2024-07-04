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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil.EntryFilter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JarFileUtilTest {

  /** A {@link EntryFilter} for plan configuration XML files. */
  private static class TestFilter implements EntryFilter {

    private String prefix = null;

    public TestFilter(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean accept(String pathName) {
      return pathName.startsWith(prefix) && !pathName.endsWith("/");
    }

    @Override
    public String transform(String pathName) {
      return pathName;
    }
  }

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/infra/ats/common/plan/testdata/";

  private static final String TEST_JAR =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "jar/test_app_jar");

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();
  private static Path testJar;

  @Inject private JarFileUtil jarFileUtil;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector().injectMembers(this);
    prepareTestJars();
  }

  @Test
  public void getEntriesFromJars_filterWithGivenPrefix() throws Exception {
    Map<String, Path> entries =
        jarFileUtil.getEntriesFromJars(ImmutableList.of(testJar), new TestFilter("config/util"));

    assertThat(entries).containsExactly("config/util/wifi.xml", testJar);

    entries = jarFileUtil.getEntriesFromJars(ImmutableList.of(testJar), new TestFilter("config"));

    assertThat(entries)
        .containsExactly(
            "config/cts.xml",
            testJar,
            "config/cts-sim.xml",
            testJar,
            "config/util/wifi.xml",
            testJar);
  }

  private void prepareTestJars() throws Exception {
    File tempDir = temporaryFolder.newFolder("temp");
    testJar = tempDir.toPath().resolve("test_app.jar");
    realLocalFileUtil.copyFileOrDir(Paths.get(TEST_JAR), testJar);
  }
}
