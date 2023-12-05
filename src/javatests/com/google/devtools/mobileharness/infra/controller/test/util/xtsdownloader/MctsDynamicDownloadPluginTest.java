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

package com.google.devtools.mobileharness.infra.controller.test.util.xtsdownloader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.truth.Truth;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MctsDynamicDownloadPluginTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private LocalDriverStartingEvent mockEvent;
  @Mock private TestInfo mockTestInfo;
  @Mock private JobInfo mockJobInfo;
  @Mock private AndroidAdbUtil mockAdbUtil;
  @Mock private AndroidPackageManagerUtil mockAndroidPackageManagerUtil;
  @Mock private Device mockDevice;

  private MctsDynamicDownloadPlugin spyMctsDynamicDownloadPlugin;

  @Before
  public void setUp() throws MobileHarnessException, InterruptedException {
    when(mockEvent.getDriverName()).thenReturn("XtsTradefedTest");
    when(mockEvent.getTest()).thenReturn(mockTestInfo);
    when(mockEvent.getDevice()).thenReturn(mockDevice);
    when(mockTestInfo.jobInfo()).thenReturn(mockJobInfo);
    when(mockTestInfo.getTmpFileDir()).thenReturn("/tmp");
    when(mockDevice.getDeviceId()).thenReturn("device_id");
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.modulemetadata")))
        .thenReturn(341050004);
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.ABI))).thenReturn("arm64-v8a");
    when(mockAndroidPackageManagerUtil.listModuleInfos(any()))
        .thenReturn(
            ImmutableSortedSet.of(
                ModuleInfo.builder()
                    .setPackageName("com.google.android.captiveportallogin")
                    .setName("networking")
                    .build(),
                ModuleInfo.builder()
                    .setPackageName("com.google.android.conscrypt")
                    .setName("conscrypt")
                    .build(),
                ModuleInfo.builder()
                    .setPackageName("com.google.android.go.configinfrastructure")
                    .setName("configinfrastructure")
                    .build()));
    MctsDynamicDownloadPlugin mctsDynamicDownloadPlugin =
        new MctsDynamicDownloadPlugin(mockAdbUtil, mockAndroidPackageManagerUtil);
    spyMctsDynamicDownloadPlugin = Mockito.spy(mctsDynamicDownloadPlugin);

    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-networking.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/.*/.*/android-mcts-networking\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-conscrypt.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/.*/.*/android-mcts-conscrypt\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-configinfrastructure.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/.*/.*/android-mcts-configinfrastructure\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-mainline-infra.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/.*/.*/android-mcts-mainline-infra\\.zip");
  }

  /**
   * Generates a mock test zip file. The file structure is
   * {ZIP_NAME}/testcases/{TESTCASE}/{TESTCASE}.txt The content of {TESTCASE}.txt is the literal
   * string of the testcase name.
   */
  public void generateTestZipFile(String name, String testcaseName, String directory)
      throws IOException {
    File zipFile = new File(directory, name + ".zip");
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
      String fileDirectory = name + "/testcases/";
      ZipEntry directoryEntry = new ZipEntry(fileDirectory);
      zipOutputStream.putNextEntry(directoryEntry);
      zipOutputStream.closeEntry();

      // Add a file with the name as testcaseName.txt and content as the literal string of
      // testcaseName
      String fileName = testcaseName;
      String filePath = fileDirectory + testcaseName + "/" + fileName;
      ZipEntry fileEntry = new ZipEntry(filePath);
      zipOutputStream.putNextEntry(fileEntry);

      // The content of the file is the same as the name of the testcase
      String content = testcaseName;
      zipOutputStream.write(content.getBytes(UTF_8));

      zipOutputStream.closeEntry();
    }
  }

  @Test
  public void onDriverStartingEvent_return() throws Exception {
    generateTestZipFile("android-mcts-networking", "networking.txt", "/tmp");
    generateTestZipFile("android-mcts-conscrypt", "conscrypt.txt", "/tmp");
    generateTestZipFile("android-mcts-configinfrastructure", "configinfrastructure.txt", "/tmp");
    generateTestZipFile("android-mcts-mainline-infra", "mainline-infra.txt", "/tmp");
    spyMctsDynamicDownloadPlugin.onDriverStarting(mockEvent);
    verifyDownloadAndUnzipFile();
  }

  private void verifyDownloadAndUnzipFile() throws Exception {

    File directory = new File("/tmp/android/xts/mcts/testcases");
    File[] files = directory.listFiles();

    Set<String> actualFileNames = new HashSet<>();
    if (files != null) {
      for (File file : files) {
        actualFileNames.add(file.getName());
      }
    }
    Set<String> expectedFileNames =
        new HashSet<>(
            Arrays.asList(
                "networking.txt",
                "conscrypt.txt",
                "configinfrastructure.txt",
                "mainline-infra.txt"));
    Truth.assertThat(actualFileNames).containsExactlyElementsIn(expectedFileNames);
  }

  private void mockDownloadPublicUrlFiles(String zipFile, String regexPatternUrl)
      throws MobileHarnessException {
    Mockito.doReturn(zipFile)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(argThat(arg -> Pattern.matches(regexPatternUrl, arg)), any());
    ;
  }
}
