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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class TestSuiteInfoTest {

  private static final String XTS_TYPE = "cts";
  private static final String SUITE_INFO_PROPERTY = "test-suite-info.properties";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private JarFileUtil jarFileUtil;

  private String xtsRootDir;
  private Path toolsDir;

  @Before
  public void setUp() throws Exception {
    xtsRootDir = tmpFolder.getRoot().getAbsolutePath();
    toolsDir = tmpFolder.newFolder("android-cts", "tools").toPath();
  }

  @Test
  public void loadSuiteInfo_fallbackToJarProps() throws Exception {
    String propsContent =
        "build_number=12345\ntarget_arch=arm64\nname=CTS\nfullname=Compatibility Test Suite\n"
            + "version=14_r1";
    InputStream inputStream = new ByteArrayInputStream(propsContent.getBytes(UTF_8));
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.of(inputStream));

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getBuildNumber()).isEqualTo("12345");
    assertThat(testSuiteInfo.getTargetArchs()).containsExactly("arm64");
    assertThat(testSuiteInfo.getName()).isEqualTo("CTS");
    assertThat(testSuiteInfo.getFullName()).isEqualTo("Compatibility Test Suite");
    assertThat(testSuiteInfo.getVersion()).isEqualTo("14_r1");
  }

  @Test
  public void loadSuiteInfo_loadFromVersionTxt() throws Exception {
    String propsContent =
        "build_number=12345\ntarget_arch=arm64\nname=CTS\nfullname=Compatibility Test Suite\n"
            + "version=14_r1";
    InputStream inputStream = new ByteArrayInputStream(propsContent.getBytes(UTF_8));
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.of(inputStream));

    Path versionFile = toolsDir.resolve("version.txt");
    Files.writeString(versionFile, "67890\n");

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getBuildNumber()).isEqualTo("67890");
    assertThat(testSuiteInfo.getTargetArchs()).containsExactly("arm64");
    assertThat(testSuiteInfo.getName()).isEqualTo("CTS");
  }

  @Test
  public void loadSuiteInfo_versionTxtEmpty_fallbackToJarProps() throws Exception {
    String propsContent =
        "build_number=12345\ntarget_arch=arm64\nname=CTS\nfullname=Compatibility Test Suite\n"
            + "version=14_r1";
    InputStream inputStream = new ByteArrayInputStream(propsContent.getBytes(UTF_8));
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.of(inputStream));

    Path versionFile = toolsDir.resolve("version.txt");
    Files.writeString(versionFile, "");

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getBuildNumber()).isEqualTo("12345");
  }

  @Test
  public void getTestSuiteVersion_success() throws Exception {
    String propsContent =
        "build_number=12345\ntarget_arch=arm64\nname=CTS\nfullname=Compatibility Test Suite\n"
            + "version=14_r1";
    InputStream inputStream = new ByteArrayInputStream(propsContent.getBytes(UTF_8));
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.of(inputStream));

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getTestSuiteVersion()).hasValue(TestSuiteVersion.create(14, 0, 0, 1));
  }

  @Test
  public void getTestSuiteVersion_invalid_fallbackToEmpty() throws Exception {
    String propsContent =
        "build_number=12345\ntarget_arch=arm64\nname=CTS\nfullname=Compatibility Test Suite\n"
            + "version=invalid_format";
    InputStream inputStream = new ByteArrayInputStream(propsContent.getBytes(UTF_8));
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.of(inputStream));

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getTestSuiteVersion()).isEmpty();
  }

  @Test
  public void getTestSuiteVersion_stubVersion_returnsEmpty() throws Exception {
    when(jarFileUtil.getZipEntryInputStream(any(Path.class), eq(SUITE_INFO_PROPERTY)))
        .thenReturn(Optional.empty());

    TestSuiteInfo testSuiteInfo = new TestSuiteInfo(xtsRootDir, XTS_TYPE, jarFileUtil);

    assertThat(testSuiteInfo.getTestSuiteVersion()).isEmpty();
  }
}
