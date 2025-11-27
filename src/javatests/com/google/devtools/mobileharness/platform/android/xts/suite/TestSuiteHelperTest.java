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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
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
public final class TestSuiteHelperTest {

  private static final String XTS_ROOT_DIR = "/path/to/xts_root";
  private static final String XTS_TYPE = "cts";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private LocalFileUtil localFileUtil;

  private TestSuiteHelper testSuiteHelper;

  @Before
  public void setUp() {
    testSuiteHelper = new TestSuiteHelper(XTS_ROOT_DIR, XTS_TYPE, localFileUtil);
  }

  @Test
  public void getAbis() throws Exception {
    testSuiteHelper = spy(testSuiteHelper);
    doReturn(ImmutableSet.of("arm64-v8a", "armeabi-v7a"))
        .when(testSuiteHelper)
        .getAbisForBuildTargetArchFromSuite();
    DeviceInfo deviceInfo =
        DeviceInfo.builder()
            .setDeviceId("device_id")
            .setSupportedAbiList("arm64-v8a,armeabi-v7a,armeabi")
            .setSupportedAbi("arm64-v8a")
            .build();

    assertThat(testSuiteHelper.getAbis(deviceInfo))
        .containsExactly(Abi.of("arm64-v8a", "64"), Abi.of("armeabi-v7a", "32"));
  }

  @Test
  public void getAbis_useGivenAbis() throws Exception {
    testSuiteHelper = spy(testSuiteHelper);
    doReturn(ImmutableSet.of("arm64-v8a", "armeabi-v7a"))
        .when(testSuiteHelper)
        .getAbisForBuildTargetArchFromSuite();
    DeviceInfo deviceInfo =
        DeviceInfo.builder()
            .setDeviceId("device_id")
            .setSupportedAbiList("arm64-v8a,armeabi-v7a,armeabi")
            .setSupportedAbi("arm64-v8a")
            .build();
    testSuiteHelper.setAbis(ImmutableSet.of(Abi.of("arm64-v8a", "64")));

    assertThat(testSuiteHelper.getAbis(deviceInfo)).containsExactly(Abi.of("arm64-v8a", "64"));
  }

  @Test
  public void getAbis_noDeviceAbis_throwException() throws Exception {
    testSuiteHelper = spy(testSuiteHelper);
    doReturn(ImmutableSet.of("arm64-v8a", "armeabi-v7a"))
        .when(testSuiteHelper)
        .getAbisForBuildTargetArchFromSuite();
    doReturn(ImmutableSet.of()).when(testSuiteHelper).getHostAbis();

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> testSuiteHelper.getAbis(/* deviceInfo= */ null))
                .getErrorId())
        .isEqualTo(ExtErrorId.TEST_SUITE_DEVICE_ABIS_MISSING);
  }

  @Test
  public void getAbis_useHostAbis() throws Exception {
    testSuiteHelper = spy(testSuiteHelper);
    doReturn(ImmutableSet.of("arm64-v8a", "armeabi-v7a"))
        .when(testSuiteHelper)
        .getAbisForBuildTargetArchFromSuite();
    doReturn(ImmutableSet.of("arm64-v8a")).when(testSuiteHelper).getHostAbis();

    assertThat(testSuiteHelper.getAbis(/* deviceInfo= */ null))
        .containsExactly(Abi.of("arm64-v8a", "64"));
  }

  @Test
  public void getAbis_noSupportedAbis_throwException() throws Exception {
    testSuiteHelper = spy(testSuiteHelper);
    doReturn(ImmutableSet.of("arm64-v8a", "armeabi-v7a"))
        .when(testSuiteHelper)
        .getAbisForBuildTargetArchFromSuite();
    DeviceInfo deviceInfo =
        DeviceInfo.builder()
            .setDeviceId("device_id")
            .setSupportedAbiList("x86_64")
            .setSupportedAbi("x86_64")
            .build();

    assertThat(
            assertThrows(MobileHarnessException.class, () -> testSuiteHelper.getAbis(deviceInfo))
                .getErrorId())
        .isEqualTo(ExtErrorId.TEST_SUITE_NO_ABIS_SUPPORTED);
  }

  @Test
  public void loadTestsUsingAbisForArchFromSuite_success() throws Exception {
    File xtsRootDir = tmpFolder.getRoot();
    when(localFileUtil.isDirExist(xtsRootDir.getAbsolutePath())).thenReturn(true);
    File toolsDir = tmpFolder.newFolder("android-cts", "tools");
    File testcasesDir = tmpFolder.newFolder("android-cts", "testcases");
    File tradefedJar = new File(toolsDir, "cts-tradefed.jar");
    // Mock the test-suite-info.properties file in the tradefed.jar to have a target architecture
    // of value of arm64.
    try (JarOutputStream target = new JarOutputStream(new FileOutputStream(tradefedJar))) {
      target.putNextEntry(new ZipEntry("test-suite-info.properties"));
      byte[] propertiesBytes = "target_arch=arm64".getBytes(UTF_8);
      target.write(propertiesBytes, 0, propertiesBytes.length);
      target.closeEntry();
    }
    // Create a dummy module config file so that SuiteModuleLoader can load it.
    File fooConfig = new File(testcasesDir, "Foo.config");
    String configContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<configuration description=\"Dummy module config\">\n"
            + "    <option name=\"config-descriptor:metadata\" key=\"component\" value=\"dummy\""
            + " />\n"
            + "</configuration>";
    try (FileOutputStream fos = new FileOutputStream(fooConfig)) {
      fos.write(configContent.getBytes(UTF_8));
    }

    testSuiteHelper = new TestSuiteHelper(xtsRootDir.getAbsolutePath(), XTS_TYPE, localFileUtil);

    assertThat(testSuiteHelper.loadTestsUsingAbisForArchFromSuite().keySet())
        .containsExactly("arm64-v8a Foo", "armeabi-v7a Foo");
  }
}
