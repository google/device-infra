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
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
public class ConfigurationXmlParserTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Path temporaryDirectroryPath;

  private LocalFileUtil localFileUtil;

  private static final String CONFIG_FILE_DESCRIPTION =
      "Config for CTS Bluetooth multi devices test cases";
  private static final String CONFIG_FILE_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<configuration description=\""
          + CONFIG_FILE_DESCRIPTION
          + "\">\n"
          + "</configuration>\n";

  @Before
  public void setUp() throws Exception {
    localFileUtil = new LocalFileUtil();
  }

  @Before
  public void prepareTemporaryDirectory() throws Exception {
    temporaryDirectroryPath = tempFolder.getRoot().toPath();
  }

  @Test
  public void parse_normalConfigurationXml_returnsConfiguration() throws Exception {
    Path testcasesDir = Paths.get(temporaryDirectroryPath.toString(), "testcases");
    Path file = testcasesDir.resolve("config_file.xml");
    localFileUtil.writeToFile(file.toString(), CONFIG_FILE_CONTENT);

    Configuration configuration = ConfigurationXmlParser.parse(file.toFile());

    assertThat(configuration.getDescription()).isEqualTo(CONFIG_FILE_DESCRIPTION);
  }

  @Test
  public void parse_emptyConfigurationXml_throwsException() throws Exception {
    Path testcasesDir = Paths.get(temporaryDirectroryPath.toString(), "testcases");
    Path file = testcasesDir.resolve("config_file.xml");
    localFileUtil.writeToFile(file.toString(), "");

    assertThrows(MobileHarnessException.class, () -> ConfigurationXmlParser.parse(file.toFile()));
  }
}
