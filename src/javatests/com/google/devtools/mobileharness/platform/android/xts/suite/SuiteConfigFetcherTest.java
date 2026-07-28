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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteConfigFetcher.SuiteConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SuiteConfigFetcherTest {

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Test
  public void fetchConfig_success() throws Exception {
    File xtsRootDir = tmpFolder.getRoot();
    File toolsDir = tmpFolder.newFolder("android-cts-v-host", "tools");
    File tradefedJar = new File(toolsDir, "cts-v-host-tradefed.jar");

    String xmlContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<configuration description=\"CTS-V Host Test Suite\">\n"
            + "    <option name=\"compatibility:primary-abi-only\" value=\"true\" />\n"
            + "    <option name=\"compatibility:enable-parameterized-modules\" value=\"false\" />\n"
            + "    <option name=\"compatibility:enable-optional-parameterization\" value=\"true\""
            + " />\n"
            + "</configuration>";

    try (JarOutputStream target = new JarOutputStream(new FileOutputStream(tradefedJar))) {
      target.putNextEntry(new ZipEntry("config/cts-v-host.xml"));
      target.write(xmlContent.getBytes(UTF_8));
      target.closeEntry();
    }

    SuiteConfigFetcher fetcher = new SuiteConfigFetcher();
    SuiteConfig suiteConfig = fetcher.fetchConfig(xtsRootDir.getAbsolutePath(), "cts-v-host");

    assertThat(suiteConfig.primaryAbiOnly()).hasValue(true);
    assertThat(suiteConfig.allowParameterizedModules()).hasValue(false);
    assertThat(suiteConfig.allowOptionalParameterizedModules()).hasValue(true);
  }

  @Test
  public void fetchConfig_jarNotFound_returnsEmptyConfig() throws Exception {
    File xtsRootDir = tmpFolder.getRoot();
    SuiteConfigFetcher fetcher = new SuiteConfigFetcher();
    SuiteConfig suiteConfig = fetcher.fetchConfig(xtsRootDir.getAbsolutePath(), "nonexistent");

    assertThat(suiteConfig.primaryAbiOnly()).isEmpty();
    assertThat(suiteConfig.allowParameterizedModules()).isEmpty();
    assertThat(suiteConfig.allowOptionalParameterizedModules()).isEmpty();
  }
}
