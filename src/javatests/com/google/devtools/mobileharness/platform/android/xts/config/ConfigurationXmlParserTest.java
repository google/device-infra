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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.TextFormat;
import java.io.File;
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

  private static final String DEVICE_PROPERTIES_FILE_PATH =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/config/testdata/cts/testcases/CtsBluetoothMultiDevicesTestCases/CtsBluetoothMultiDevicesTestCases.config";
  private static final String DEVICE_PROPERTIES_PROTO_PATH =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/config/testdata/cts/testcases/CtsBluetoothMultiDevicesTestCases/CtsBluetoothMultiDevicesTestCasesProto.txt";

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
    String filePath = RunfilesUtil.getRunfilesLocation(DEVICE_PROPERTIES_FILE_PATH);
    String protoPath = RunfilesUtil.getRunfilesLocation(DEVICE_PROPERTIES_PROTO_PATH);
    File file = new File(filePath);
    Configuration expectedConfiguration =
        TextFormat.parse(localFileUtil.readFile(protoPath), Configuration.class);

    Configuration configuration = ConfigurationXmlParser.parse(file);

    assertThat(configuration).isEqualTo(expectedConfiguration);
  }

  @Test
  public void parse_emptyConfigurationXml_throwsException() throws Exception {
    Path testcasesDir = Paths.get(temporaryDirectroryPath.toString(), "testcases");
    Path file = testcasesDir.resolve("config_file.xml");
    localFileUtil.writeToFile(file.toString(), "");

    assertThrows(MobileHarnessException.class, () -> ConfigurationXmlParser.parse(file.toFile()));
  }
}
