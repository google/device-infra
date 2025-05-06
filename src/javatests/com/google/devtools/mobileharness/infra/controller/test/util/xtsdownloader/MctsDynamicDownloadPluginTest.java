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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
  @Mock private LocalTestStartingEvent mockEvent;
  @Mock private TestInfo mockTestInfo;
  @Mock private JobInfo mockJobInfo;
  @Mock private AndroidAdbUtil mockAdbUtil;
  @Mock private AndroidPackageManagerUtil mockAndroidPackageManagerUtil;
  @Mock private Properties testProperties;
  @Mock private Properties jobProperties;
  @Mock private DeviceLocator mockDeviceLocator;

  private MctsDynamicDownloadPlugin spyMctsDynamicDownloadPlugin;
  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  @SuppressWarnings("DirectInvocationOnMock")
  @Before
  public void setUp() throws MobileHarnessException, InterruptedException {
    when(mockEvent.getTest()).thenReturn(mockTestInfo);
    when(mockEvent.getDeviceLocator()).thenReturn(mockDeviceLocator);
    when(mockDeviceLocator.getSerial()).thenReturn("device_id");
    when(mockTestInfo.jobInfo()).thenReturn(mockJobInfo);
    when(mockTestInfo.getTmpFileDir()).thenReturn("/tmp");
    when(mockTestInfo.properties()).thenReturn(testProperties);
    when(mockTestInfo.jobInfo().properties()).thenReturn(jobProperties);
    when(jobProperties.get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(XtsConstants.DYNAMIC_MCTS_JOB_NAME);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.modulemetadata")))
        .thenReturn(351030004);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.captiveportallogin")))
        .thenReturn(351030004);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(any(), eq("com.google.android.conscrypt")))
        .thenReturn(350050004);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.go.configinfrastructure")))
        .thenReturn(341050004);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.documentsui")))
        .thenReturn(351059999);
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.ABI))).thenReturn("arm64-v8a");
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.SDK_VERSION))).thenReturn("30");
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.CODENAME))).thenReturn("fake_codename");
    when(mockAndroidPackageManagerUtil.listModuleInfos(any()))
        .thenReturn(
            ImmutableSortedSet.of(
                ModuleInfo.builder()
                    .setPackageName("com.google.android.modulemetadata")
                    .setName("modulemetadata")
                    .build(),
                ModuleInfo.builder()
                    .setPackageName("com.google.android.captiveportallogin")
                    .setName("networking")
                    .build(),
                ModuleInfo.builder()
                    .setPackageName("com.google.android.conscrypt")
                    .setName("conscrypt")
                    .build(),
                ModuleInfo.builder()
                    .setPackageName("com.google.android.documentsui")
                    .setName("documentsui")
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
        "https://dl\\.google\\.com/dl/android/xts/mcts/2024-10/arm64/android-mcts-networking\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-conscrypt.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-conscrypt\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-configinfrastructure.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-configinfrastructure\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-documentsui.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-documentsui\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-media.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-media\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-dnsresolver.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-dnsresolver\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-adbd.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-adbd\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-cellbroadcast.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-cellbroadcast\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-extservices.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-extservices\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-ipsec.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-ipsec\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-mediaprovider.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-mediaprovider\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-neuralnetworks.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-neuralnetworks\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-statsd.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-statsd\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-permission.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-permission\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-sdkextensions.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-sdkextensions\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-tethering.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-tethering\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-wifi.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-wifi\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-tzdata.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-tzdata\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-art.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-art\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-scheduling.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-scheduling\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-adservices.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-adservices\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-appsearch.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-appsearch\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-bluetooth.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-bluetooth\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-ondevicepersonalization.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-ondevicepersonalization\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-uwb.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-uwb\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-healthfitness.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-healthfitness\\.zip");
    mockDownloadPublicUrlFiles(
        "/tmp/android-mcts-rkpd.zip",
        "https://dl\\.google\\.com/dl/android/xts/mcts/30/arm64/android-mcts-rkpd\\.zip");
    Mockito.doReturn(null)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(
            "https://dl.google.com/dl/android/xts/mcts/tool/jdk.zip",
            "/android/xts/mcts/tool/jdk.zip");
    Mockito.doReturn(null)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(
            "https://dl.google.com/dl/android/xts/mcts/tool/mcts_exclude/30/2024-10/mcts-exclude.txt",
            "/android/xts/mcts/tool/mcts_exclude/30/2024-10/mcts-exclude.txt");
    Mockito.doReturn(null)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(
            "https://dl.google.com/dl/android/xts/mcts/2024-10/arm64/mcts_test_list.txt",
            "/android/xts/mcts/2024-10/arm64/mcts_test_list.txt");
    Mockito.doReturn(null)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(
            "https://dl.google.com/dl/android/xts/mcts/tool/35/jdk.zip",
            "/android/xts/mcts/tool/jdk.zip");
  }

  @Test
  public void onDriverStartingEvent_return() throws Exception {
    generateTestZipFile("android-mcts-networking", "networking.txt", "/tmp");
    generateTestZipFile("android-mcts-conscrypt", "conscrypt.txt", "/tmp");
    generateTestZipFile("android-mcts-configinfrastructure", "configinfrastructure.txt", "/tmp");
    generateTestZipFile("android-mcts-documentsui", "documentsui.txt", "/tmp");
    generateTestZipFile("android-mcts-media", "media.txt", "/tmp");
    generateTestZipFile("android-mcts-dnsresolver", "dnsresolver.txt", "/tmp");
    generateTestZipFile("android-mcts-adbd", "adbd.txt", "/tmp");
    generateTestZipFile("android-mcts-cellbroadcast", "cellbroadcast.txt", "/tmp");
    generateTestZipFile("android-mcts-extservices", "extservices.txt", "/tmp");
    generateTestZipFile("android-mcts-ipsec", "ipsec.txt", "/tmp");
    generateTestZipFile("android-mcts-mediaprovider", "mediaprovider.txt", "/tmp");
    generateTestZipFile("android-mcts-neuralnetworks", "neuralnetworks.txt", "/tmp");
    generateTestZipFile("android-mcts-statsd", "statsd.txt", "/tmp");
    generateTestZipFile("android-mcts-permission", "permission.txt", "/tmp");
    generateTestZipFile("android-mcts-sdkextensions", "sdkextensions.txt", "/tmp");
    generateTestZipFile("android-mcts-tethering", "tethering.txt", "/tmp");
    generateTestZipFile("android-mcts-wifi", "wifi.txt", "/tmp");
    generateTestZipFile("android-mcts-tzdata", "tzdata.txt", "/tmp");
    generateTestZipFile("android-mcts-art", "art.txt", "/tmp");
    generateTestZipFile("android-mcts-scheduling", "scheduling.txt", "/tmp");
    generateTestZipFile("android-mcts-adservices", "adservices.txt", "/tmp");
    generateTestZipFile("android-mcts-appsearch", "appsearch.txt", "/tmp");
    generateTestZipFile("android-mcts-bluetooth", "bluetooth.txt", "/tmp");
    generateTestZipFile(
        "android-mcts-ondevicepersonalization", "ondevicepersonalization.txt", "/tmp");
    generateTestZipFile("android-mcts-uwb", "uwb.txt", "/tmp");
    generateTestZipFile("android-mcts-healthfitness", "healthfitness.txt", "/tmp");
    generateTestZipFile("android-mcts-rkpd", "rkpd.txt", "/tmp");

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verifyDownloadAndUnzipFile();
  }

  private void verifyDownloadAndUnzipFile() throws Exception {

    File directory = new File("/tmp/android/xts/mcts/testcases");
    File[] files = directory.listFiles();

    ImmutableSet<String> actualFileNames =
        files == null
            ? ImmutableSet.of()
            : stream(files).map(File::getName).collect(toImmutableSet());
    assertThat(actualFileNames)
        .containsExactly(
            "networking.txt",
            "conscrypt.txt",
            "configinfrastructure.txt",
            "documentsui.txt",
            "media.txt",
            "dnsresolver.txt",
            "adbd.txt",
            "cellbroadcast.txt",
            "extservices.txt",
            "ipsec.txt",
            "mediaprovider.txt",
            "neuralnetworks.txt",
            "statsd.txt",
            "permission.txt",
            "sdkextensions.txt",
            "tethering.txt",
            "wifi.txt",
            "tzdata.txt",
            "art.txt",
            "scheduling.txt",
            "adservices.txt",
            "appsearch.txt",
            "bluetooth.txt",
            "ondevicepersonalization.txt",
            "uwb.txt",
            "healthfitness.txt",
            "rkpd.txt");
    localFileUtil.removeFileOrDir(directory.getAbsolutePath());
  }

  private void mockDownloadPublicUrlFiles(String zipFile, String regexPatternUrl)
      throws MobileHarnessException, InterruptedException {
    Mockito.doReturn(zipFile)
        .when(spyMctsDynamicDownloadPlugin)
        .downloadPublicUrlFiles(argThat(arg -> Pattern.matches(regexPatternUrl, arg)), any());
    ;
  }

  /**
   * Generates a mock test zip file. The file structure is
   * {ZIP_NAME}/testcases/{TESTCASE}/{TESTCASE}.txt The content of {TESTCASE}.txt is the literal
   * string of the testcase name.
   */
  private void generateTestZipFile(String name, String testcaseName, String directory)
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

      // The content of the file is the same as the name of the testcase.
      String content = testcaseName;
      zipOutputStream.write(content.getBytes(UTF_8));

      zipOutputStream.closeEntry();
    }
  }
}
