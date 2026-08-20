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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestReportPropertiesUtilTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void writeAndLoadTestReportProperties_success() throws Exception {
    File parentDir = temporaryFolder.newFolder("results");
    ImmutableMap<String, String> propertiesMap =
        ImmutableMap.of(
            SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE, "true",
            SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE, "false",
            SuiteCommon.TEST_REPORT_PROPERTY_TEST_PLAN, "cts");

    TestReportPropertiesUtil.writeTestReportProperties(propertiesMap, parentDir);

    Path propertiesFile = parentDir.toPath().resolve(SuiteCommon.TEST_REPORT_PROPERTIES_FILE_NAME);
    assertThat(propertiesFile.toFile().exists()).isTrue();

    Properties loadedProperties = TestReportPropertiesUtil.loadTestReportProperties(propertiesFile);

    assertThat(TestReportPropertiesUtil.hasTfModule(loadedProperties)).isTrue();
    assertThat(TestReportPropertiesUtil.hasNonTfModule(loadedProperties)).isFalse();
    assertThat(TestReportPropertiesUtil.getTestPlan(loadedProperties)).hasValue("cts");
  }

  @Test
  public void loadTestReportProperties_nonExistentFile_throwsException() {
    Path nonExistentFile = temporaryFolder.getRoot().toPath().resolve("non_existent.properties");

    assertThrows(
        MobileHarnessException.class,
        () -> TestReportPropertiesUtil.loadTestReportProperties(nonExistentFile));
  }

  @Test
  public void hasTfModule_propertyMissing_returnsFalse() {
    Properties emptyProperties = new Properties();

    assertThat(TestReportPropertiesUtil.hasTfModule(emptyProperties)).isFalse();
    assertThat(TestReportPropertiesUtil.hasNonTfModule(emptyProperties)).isFalse();
  }
}
