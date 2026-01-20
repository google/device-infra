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
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Base64;
import java.util.Optional;
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
  @Mock private AndroidAdbInternalUtil mockAdbInternalUtil;
  @Mock private AndroidPackageManagerUtil mockAndroidPackageManagerUtil;
  @Mock private Properties testProperties;
  @Mock private Properties jobProperties;
  @Mock private DeviceLocator mockDeviceLocator;
  @Mock private Allocation mockAllocation;

  private MctsDynamicDownloadPlugin spyMctsDynamicDownloadPlugin;
  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  @SuppressWarnings("DirectInvocationOnMock")
  @Before
  public void setUp() throws MobileHarnessException, InterruptedException {
    when(mockEvent.getTest()).thenReturn(mockTestInfo);
    when(mockEvent.getAllocation()).thenReturn(mockAllocation);
    when(mockAllocation.getAllDeviceLocators()).thenReturn(ImmutableList.of(mockDeviceLocator));
    when(mockDeviceLocator.getSerial()).thenReturn("device_id");
    when(mockAdbInternalUtil.getDeviceSerialsByState(any(), any()))
        .thenReturn(ImmutableSet.of("device_id"));
    when(mockTestInfo.jobInfo()).thenReturn(mockJobInfo);
    when(mockTestInfo.getTmpFileDir()).thenReturn("/tmp");
    when(mockTestInfo.properties()).thenReturn(testProperties);
    when(mockTestInfo.jobInfo().properties()).thenReturn(jobProperties);
    when(testProperties.getOptional(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY))
        .thenReturn(Optional.of("30"));
    when(testProperties.getOptional(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY))
        .thenReturn(Optional.of("351030004"));
    when(testProperties.getOptional(XtsConstants.DEVICE_ABI_PROPERTY_KEY))
        .thenReturn(Optional.of("arm64-v8a"));
    ListMultimap<String, String> mctsModulesInfo = ArrayListMultimap.create();
    mctsModulesInfo.put("preloaded", "android-mcts-networking:2024-10");
    mctsModulesInfo.put("preloaded", "android-mcts-conscrypt:AOSP");
    mctsModulesInfo.put("preloaded", "android-mcts-documentsui:AOSP");
    mctsModulesInfo.put("preloaded", "android-mcts-configinfrastructure:AOSP");
    mctsModulesInfo.put("non-preloaded", "android-mcts-media");
    mctsModulesInfo.put("non-preloaded", "android-mcts-dnsresolver");
    mctsModulesInfo.put("non-preloaded", "android-mcts-adbd");
    mctsModulesInfo.put("non-preloaded", "android-mcts-cellbroadcast");
    mctsModulesInfo.put("non-preloaded", "android-mcts-extservices");
    mctsModulesInfo.put("non-preloaded", "android-mcts-ipsec");
    mctsModulesInfo.put("non-preloaded", "android-mcts-mediaprovider");
    mctsModulesInfo.put("non-preloaded", "android-mcts-neuralnetworks");
    mctsModulesInfo.put("non-preloaded", "android-mcts-statsd");
    mctsModulesInfo.put("non-preloaded", "android-mcts-permission");
    mctsModulesInfo.put("non-preloaded", "android-mcts-sdkextensions");
    mctsModulesInfo.put("non-preloaded", "android-mcts-tethering");
    mctsModulesInfo.put("non-preloaded", "android-mcts-wifi");
    mctsModulesInfo.put("non-preloaded", "android-mcts-tzdata");
    mctsModulesInfo.put("non-preloaded", "android-mcts-art");
    mctsModulesInfo.put("non-preloaded", "android-mcts-scheduling");
    mctsModulesInfo.put("non-preloaded", "android-mcts-adservices");
    mctsModulesInfo.put("non-preloaded", "android-mcts-appsearch");
    mctsModulesInfo.put("non-preloaded", "android-mcts-ondevicepersonalization");
    mctsModulesInfo.put("non-preloaded", "android-mcts-uwb");
    mctsModulesInfo.put("non-preloaded", "android-mcts-healthfitness");
    mctsModulesInfo.put("non-preloaded", "android-mcts-rkpd");
    when(testProperties.getOptional(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY))
        .thenReturn(
            Optional.of(
                Base64.getEncoder().encodeToString(serialize((Serializable) mctsModulesInfo))));
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
        .thenReturn(341080004);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.documentsui")))
        .thenReturn(351089999);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(any(), eq("com.google.android.appsearch")))
        .thenReturn(361040000);
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.extservices")))
        .thenReturn(370130004);
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
        new MctsDynamicDownloadPlugin(
            mockAdbUtil, mockAndroidPackageManagerUtil, mockAdbInternalUtil);
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
  public void onTestStarting_dynamicMctsJob_success() throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.DYNAMIC_MCTS_JOB_NAME));
    generateTestZipFilesForDynamicJob();

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verifyDownloadAndUnzipFile();
    verify(testProperties).add(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY, "30");
    verify(testProperties, times(2)).add(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY, "351030004");
    verify(testProperties).add(XtsConstants.DEVICE_ABI_PROPERTY_KEY, "arm64-v8a");
    verify(testProperties).add(eq(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY), anyString());
  }

  @Test
  public void onTestStarting_staticJob_success() throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).add(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY, "30");
    verify(testProperties).add(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY, "351030004");
    verify(testProperties).add(XtsConstants.DEVICE_ABI_PROPERTY_KEY, "arm64-v8a");
    verify(testProperties).add(eq(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY), anyString());
  }

  @Test
  public void onTestStarting_fetchDeviceAospVersionThrowsException_fallbackToProperties()
      throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.SDK_VERSION)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR, "error"));

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).getOptional(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY);
  }

  @Test
  public void onTestStarting_fetchDeviceTvpVersionThrowsException_fallbackToProperties()
      throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));
    when(mockAndroidPackageManagerUtil.getAppVersionCode(
            any(), eq("com.google.android.modulemetadata")))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR, "error"));

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).getOptional(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY);
  }

  @Test
  public void onTestStarting_fetchDeviceAbiVersionThrowsException_fallbackToProperties()
      throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.ABI)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR, "error"));

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).getOptional(XtsConstants.DEVICE_ABI_PROPERTY_KEY);
  }

  @Test
  public void onTestStarting_fetchDeviceMctsModulesInfoThrowsException_fallbackToProperties()
      throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));
    when(mockAndroidPackageManagerUtil.getAppVersionCode(any(), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR, "error"));

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).getOptional(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY);
  }

  @Test
  public void onTestStarting_sdk36_usesSdkFullVersion() throws Exception {
    when(jobProperties.getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME))
        .thenReturn(Optional.of(XtsConstants.STATIC_XTS_JOB_NAME));
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.SDK_VERSION))).thenReturn("36");
    when(mockAdbUtil.getProperty(any(), eq(AndroidProperty.SDK_FULL_VERSION))).thenReturn("36-ext");

    spyMctsDynamicDownloadPlugin.onTestStarting(mockEvent);

    verify(testProperties).add(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY, "36-ext");
  }

  private void generateTestZipFilesForDynamicJob() throws IOException {
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
    generateTestZipFile(
        "android-mcts-ondevicepersonalization", "ondevicepersonalization.txt", "/tmp");
    generateTestZipFile("android-mcts-uwb", "uwb.txt", "/tmp");
    generateTestZipFile("android-mcts-healthfitness", "healthfitness.txt", "/tmp");
    generateTestZipFile("android-mcts-rkpd", "rkpd.txt", "/tmp");
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

  @Test
  public void processModuleVersion_calculateVersions_v2() throws Exception {
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "340100000", "some_module", "default", "34"))
        .isEqualTo("default"); // Predates Android V.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "351100004", "some_module", "default", "35"))
        .isEqualTo("2024-11");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "351300000", "some_module", "default", "35"))
        .isEqualTo("2025-01");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "360100000", "some_module", "default", "36.0"))
        .isEqualTo("default"); // Matches initial release versioncode map.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "361200000", "some_module", "default", "36.0"))
        .isEqualTo("2025-12");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "361300000", "some_module", "default", "36.0"))
        .isEqualTo("2026-01");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "351180000", "some_module", "default", "35"))
        .isEqualTo("default"); // Beta version (5th digit >= 8).
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "361040000", "some_module", "default", "36.0"))
        .isEqualTo("default"); // Platform beta version.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "360000000", "some_module", "default", "36.0"))
        .isEqualTo("default"); // AOSP version.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "359900000", "some_module", "default", "35"))
        .isEqualTo("default"); // Daily train version.
  }

  @Test
  public void processModuleVersion_calculateVersions_v3() throws Exception {
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "370500000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Initial release version.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "370500000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Matches initial release versioncode map.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "371000000", "some_module", "default", "37.0"))
        .isEqualTo("2026-06");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "372000000", "some_module", "default", "37.0"))
        .isEqualTo("2026-08");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "374000000", "some_module", "default", "37.0"))
        .isEqualTo("2026-12");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "374500000", "some_module", "default", "37.0"))
        .isEqualTo("2027-01");
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "373200000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Mainline beta mission.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "373400000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Platform beta mission.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "371100000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Other non-release mission.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "370000000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // AOSP version.
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "379900000", "some_module", "default", "37.0"))
        .isEqualTo("default"); // Daily train version.
  }

  @Test
  public void processModuleVersion_calculateVersions_aosp() throws Exception {
    assertThat(
            spyMctsDynamicDownloadPlugin.processModuleVersion(
                "352090000", "some_module", "default", "35"))
        .isEqualTo("default");
  }

  @Test
  public void processModuleVersion_unsupportedSdkException() throws Exception {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                spyMctsDynamicDownloadPlugin.processModuleVersion(
                    "990500000", "some_module", "default", ""));
    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.XTS_DYNAMIC_DOWNLOADER_DEVICE_SDK_VERSION_NOT_SUPPORT);
  }
}
