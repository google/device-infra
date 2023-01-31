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

package com.google.devtools.atsconsole.testbed.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.atsconsole.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class YamlTestbedUpdaterTest {

  private static final String ANDROID_DEVICE_IS_JSON_PRIMITIVE =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/android_device_is_json_primitive.yaml");
  private static final String EXPECTED_UPDATED_ANDROID_DEVICE_IS_JSON_PRIMITIVE =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_updated_android_device_is_json_primitive.yaml");
  private static final String ANDROID_DEVICE_IS_JSON_ARRAY =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/android_device_is_json_array.yaml");
  private static final String EXPECTED_UPDATED_ANDROID_DEVICE_IS_JSON_ARRAY =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_updated_android_device_is_json_array.yaml");
  private static final String MULTIPLE_TEST_BEDS =
      TestRunfilesUtil.getRunfilesLocation("testbed/config/testdata/multiple_test_beds.yaml");
  private static final String EXPECTED_UPDATED_MULTIPLE_TEST_BEDS =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_updated_multiple_test_beds.yaml");
  private static final String NO_TEST_BEDS =
      TestRunfilesUtil.getRunfilesLocation("testbed/config/testdata/no_test_beds.yaml");
  private static final String NO_CONTROLLERS_IN_TEST_BED =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/no_controllers_in_test_bed.yaml");
  private static final String NO_ANDROID_DEVICE_IN_CONTROLLERS =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/no_android_device_in_controllers.yaml");

  private static final String EXPECTED_GENERATED_MOBLY_CONFIG_SINGLE_DEVICE =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_generated_mobly_config_single_device.yaml");
  private static final String EXPECTED_GENERATED_MOBLY_CONFIG_MULTIPLE_DEVICES =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_generated_mobly_config_multiple_devices.yaml");
  private static final String EXPECTED_GENERATED_MOBLY_CONFIG_SINGLE_LOCALHOST_DEVICE =
      TestRunfilesUtil.getRunfilesLocation(
          "testbed/config/testdata/expected_generated_mobly_config_single_localhost_device.yaml");

  private static final String UPSERTED_SERIAL = "11191FDD4002M4";

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();
  private YamlTestbedUpdater yamlTestbedUpdater;

  @Before
  public void setUp() {
    yamlTestbedUpdater = new YamlTestbedUpdater();
  }

  @Test
  public void upsertDeviceSerial_androidDeviceIsJsonPrimitive_updatedToJsonArray()
      throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(
            ANDROID_DEVICE_IS_JSON_PRIMITIVE, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(
            realLocalFileUtil.readFile(EXPECTED_UPDATED_ANDROID_DEVICE_IS_JSON_PRIMITIVE).trim());
  }

  @Test
  public void upsertDeviceSerial_androidDeviceIsJsonArray_updateFirstFoundDevice()
      throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(
            ANDROID_DEVICE_IS_JSON_ARRAY, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(
            realLocalFileUtil.readFile(EXPECTED_UPDATED_ANDROID_DEVICE_IS_JSON_ARRAY).trim());
  }

  @Test
  public void upsertDeviceSerial_updateFirstTestBedOnly() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(
            MULTIPLE_TEST_BEDS, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(realLocalFileUtil.readFile(EXPECTED_UPDATED_MULTIPLE_TEST_BEDS).trim());
  }

  @Test
  public void upsertDeviceSerial_noTestBeds_returnOriginalYaml() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(NO_TEST_BEDS, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(configPath).isEqualTo(NO_TEST_BEDS);
  }

  @Test
  public void upsertDeviceSerial_noControllersInTestBed_returnOriginalYaml() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(
            NO_CONTROLLERS_IN_TEST_BED, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(configPath).isEqualTo(NO_CONTROLLERS_IN_TEST_BED);
  }

  @Test
  public void upsertDeviceSerial_noAndroidDeviceInControllers_returnOriginalYaml()
      throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.upsertDeviceSerial(
            NO_ANDROID_DEVICE_IN_CONTROLLERS, UPSERTED_SERIAL, outputDir.getPath());
    assertThat(configPath).isEqualTo(NO_ANDROID_DEVICE_IN_CONTROLLERS);
  }

  @Test
  public void prepareMoblyConfig_singleDevice_success() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of("abc"), outputDir.getPath(), /* outputConfigFileName= */ null);
    assertThat(configPath).endsWith("/testbed_abc.yaml");
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(
            realLocalFileUtil.readFile(EXPECTED_GENERATED_MOBLY_CONFIG_SINGLE_DEVICE).trim());
  }

  @Test
  public void prepareMoblyConfig_customizedConfigFileName() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of("abc"), outputDir.getPath(), "my_testbed.yaml");
    assertThat(configPath).endsWith("/my_testbed.yaml");
  }

  @Test
  public void prepareMoblyConfig_multipleDevices_success() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of("abc", "xyz"), outputDir.getPath(), /* outputConfigFileName= */ null);
    assertThat(configPath).endsWith("/testbed_abc_xyz.yaml");
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(
            realLocalFileUtil.readFile(EXPECTED_GENERATED_MOBLY_CONFIG_MULTIPLE_DEVICES).trim());
  }

  @Test
  public void prepareMoblyConfig_singleLocalhostDevice_success() throws Exception {
    File outputDir = temporaryFolder.newFolder("output");
    String configPath =
        yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of("localhost:0001"),
            outputDir.getPath(),
            /* outputConfigFileName= */ null);
    assertThat(configPath).endsWith("/testbed_localhost-0001.yaml");
    assertThat(realLocalFileUtil.readFile(configPath).trim())
        .isEqualTo(
            realLocalFileUtil
                .readFile(EXPECTED_GENERATED_MOBLY_CONFIG_SINGLE_LOCALHOST_DEVICE)
                .trim());
  }
}
