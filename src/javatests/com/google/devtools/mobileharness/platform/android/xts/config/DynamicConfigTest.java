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

import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DynamicConfigTest {

  private static final String TEST_CONFIG_DIR =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/config/testdata/dynamicconfig";

  private Path testdataDir;

  @Before
  public void prepareTempDir() throws Exception {
    testdataDir = Path.of(RunfilesUtil.getRunfilesLocation(TEST_CONFIG_DIR));
  }

  @Test
  public void createConfigMap_success() throws Exception {
    File localConfigFile =
        new File(testdataDir.resolve("local.dynamic").toAbsolutePath().toString());

    Map<String, List<String>> configMap = DynamicConfig.createConfigMap(localConfigFile);

    assertThat(configMap).hasSize(3);
    assertThat(configMap.get("business_logic_device_features"))
        .containsExactly("android.hardware.type.automotive", "android.hardware.type.television");
    assertThat(configMap.get("business_logic_device_properties"))
        .containsExactly("ro.build.fingerprint");
    assertThat(configMap.get("business_logic_device_packages")).isEmpty();
  }
}
