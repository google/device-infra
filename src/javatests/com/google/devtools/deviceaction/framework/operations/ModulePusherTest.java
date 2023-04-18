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

package com.google.devtools.deviceaction.framework.operations;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Pair;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ModulePusherTest {
  private static final String APEX_PACKAGE_NAME = "com.android.FAKE.APEX.PACKAGE.NAME";
  private static final String APK_PACKAGE_NAME = "com.android.FAKE.APK.PACKAGE.NAME";
  private static final String SPLIT_APK_PACKAGE_NAME = "com.android.FAKE.SPLIT.APK.PACKAGE.NAME";
  private static final long FAKE_VERSION_CODE = 12345L;

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidPhone mockDevice;
  @Mock private ResourceHelper mockHelper;

  private File tmpDir;
  private File fakeApex;
  private File fakeApk;
  private File fakeSplitDir;
  private File fakeBaseApk;
  private File fakeHdpiApk;
  private File fakeApks;

  private ModulePusher modulePusher;

  public ModulePusherTest() {}

  @Before
  public void setUp() throws Exception {
    tmpDir = tmpFolder.newFolder();
    tmpFolder.newFolder("bundletool");
    fakeApex = tmpFolder.newFile("bundletool/arm64.apex");
    tmpFolder.newFolder("bundletool/single");
    fakeApk = tmpFolder.newFile("bundletool/single/arm64.apk");
    fakeSplitDir = tmpFolder.newFolder("bundletool/split");
    fakeBaseApk = tmpFolder.newFile("bundletool/split/base-arm64.apk");
    fakeHdpiApk = tmpFolder.newFile("bundletool/split/hdpi-arm64.apk");
    fakeApks = tmpFolder.newFile("bundletool/arm64.apks");
    when(mockHelper.getTmpFileDir()).thenReturn(tmpDir.toPath());
    modulePusher = new ModulePusher(mockDevice, new LocalFileUtil(), mockHelper);
  }

  @Test
  public void pushModules_reboot() throws Exception {
    Map<String, AndroidPackage> sourceMap =
        getPackageMap(fakeApex, fakeApk, fakeApks, fakeBaseApk, fakeHdpiApk);
    Map<String, AndroidPackage> onDeviceMap =
        getPackageMap(
            new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"),
            new File("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk"));

    ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
    ArgumentCaptor<Path> target = ArgumentCaptor.forClass(Path.class);

    modulePusher.pushModules(sourceMap, onDeviceMap);

    assertTrue(fakeApex.exists());
    assertTrue(fakeApk.exists());
    assertTrue(fakeSplitDir.exists());
    assertTrue(fakeBaseApk.exists());
    assertTrue(fakeHdpiApk.exists());
    verify(mockDevice, times(3)).push(source.capture(), target.capture());
    List<Path> sources = source.getAllValues();
    List<Path> targets = target.getAllValues();
    assertTrue(sources.get(0).toFile().isFile());
    assertTrue(
        sources
            .get(0)
            .toString()
            .endsWith(
                "com.android.FAKE.APEX.PACKAGE.NAME/com.android.FAKE.APEX.PACKAGE.NAME.apex"));
    assertThat(targets.get(0).toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertTrue(sources.get(1).toFile().isFile());
    assertTrue(
        sources
            .get(1)
            .toString()
            .endsWith("com.android.FAKE.APK.PACKAGE.NAME/com.android.FAKE.APK.PACKAGE.NAME.apk"));
    assertThat(targets.get(1).toString())
        .isEqualTo("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk");
    assertTrue(sources.get(2).toFile().isDirectory());
    assertTrue(
        sources
            .get(2)
            .toString()
            .endsWith(
                "com.android.FAKE.SPLIT.APK.PACKAGE.NAME/com.android.FAKE.SPLIT.APK.PACKAGE.NAME"));
    assertThat(targets.get(2).toString())
        .isEqualTo("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    File[] splitFiles = sources.get(2).toFile().listFiles();
    assertThat(splitFiles).hasLength(2);
    assertThat(splitFiles[0].getName()).isEqualTo("base-arm64.apk");
    assertThat(splitFiles[1].getName()).isEqualTo("hdpi-arm64.apk");
    verify(mockDevice).removeFiles("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    verify(mockDevice, times(3)).reboot();
  }

  @Test
  public void pushModules_enableTestHarness() throws Exception {
    when(mockDevice.reloadByFactoryReset()).thenReturn(true);
    Map<String, AndroidPackage> sourceMap =
        getPackageMap(fakeApex, fakeApk, fakeApks, fakeBaseApk, fakeHdpiApk);
    Map<String, AndroidPackage> onDeviceMap =
        getPackageMap(
            new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"),
            new File("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk"));

    modulePusher.pushModules(sourceMap, onDeviceMap);

    verify(mockDevice, times(3)).push(any(), any());
    verify(mockDevice).removeFiles("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    verify(mockDevice, times(2)).reboot();
    verify(mockDevice, times(1)).enableTestharness();
  }

  @Test
  public void pushModules_disablePackageCache() throws Exception {
    when(mockDevice.needDisablePackageCache()).thenReturn(true);
    Map<String, AndroidPackage> sourceMap =
        getPackageMap(fakeApex, fakeApk, fakeApks, fakeBaseApk, fakeHdpiApk);
    Map<String, AndroidPackage> onDeviceMap =
        getPackageMap(
            new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"),
            new File("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk"));

    modulePusher.pushModules(sourceMap, onDeviceMap);

    assertTrue(fakeApex.exists());
    assertTrue(fakeApk.exists());
    assertTrue(fakeSplitDir.exists());
    assertTrue(fakeBaseApk.exists());
    assertTrue(fakeHdpiApk.exists());
    verify(mockDevice, times(3)).push(any(), any());
    verify(mockDevice).removeFiles("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    verify(mockDevice, times(3)).reboot();
    verify(mockDevice, times(1)).removeFiles("/data/system/package_cache/");
  }

  @Test
  public void pushModules_createTmpDirFail_throwException() throws Exception {
    when(mockHelper.getTmpFileDir())
        .thenThrow(new DeviceActionException("FAKE", ErrorType.CUSTOMER_ISSUE, "no message"));
    Map<String, AndroidPackage> sourceMap =
        getPackageMap(fakeApex, fakeApk, fakeApks, fakeBaseApk, fakeHdpiApk);
    Map<String, AndroidPackage> onDeviceMap =
        getPackageMap(
            new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"),
            new File("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk"));

    assertThrows(
        DeviceActionException.class, () -> modulePusher.pushModules(sourceMap, onDeviceMap));
  }

  @Test
  public void pushModules_pushFail_throwException() throws Exception {
    doThrow(DeviceActionException.class).when(mockDevice).push(any(), any());
    Map<String, AndroidPackage> sourceMap =
        getPackageMap(fakeApex, fakeApk, fakeApks, fakeBaseApk, fakeHdpiApk);
    Map<String, AndroidPackage> onDeviceMap =
        getPackageMap(
            new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"),
            new File("/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
            new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk"));

    assertThrows(
        DeviceActionException.class, () -> modulePusher.pushModules(sourceMap, onDeviceMap));
  }

  @Test
  public void setupDevice_getExpectedBehavior() throws Exception {
    modulePusher.setupDevice();

    InOrder setupOrder = inOrder(mockDevice);
    setupOrder.verify(mockDevice).becomeRoot();
    setupOrder.verify(mockDevice).disableVerity();
    setupOrder.verify(mockDevice).reboot();
    setupOrder.verify(mockDevice).remount();
    setupOrder.verify(mockDevice).reboot();
    setupOrder.verify(mockDevice).remount();
  }

  @Test
  public void pushModule_success() throws Exception {
    AndroidPackage sourcePackage = getApex(fakeApex);
    AndroidPackage targetPackage =
        getApex(new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"));
    ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
    ArgumentCaptor<Path> target = ArgumentCaptor.forClass(Path.class);

    PackageInfo info = modulePusher.pushModule(sourcePackage, targetPackage, tmpDir.toPath());

    assertTrue(fakeApex.exists());
    verify(mockDevice, times(1)).push(source.capture(), target.capture());
    Path sourcePath = source.getValue();
    Path targetPath = target.getValue();
    assertTrue(sourcePath.toFile().isFile());
    assertTrue(sourcePath.toString().endsWith("com.android.FAKE.APEX.PACKAGE.NAME.apex"));
    assertThat(targetPath.toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(info).isAtLeast(sourcePackage.info());
  }

  @Test
  public void pushModule_pushFail_throwException() throws Exception {
    doThrow(DeviceActionException.class).when(mockDevice).push(any(), any());
    AndroidPackage sourcePackage = getApex(fakeApex);
    AndroidPackage targetPackage =
        getApex(new File("/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"));

    assertThrows(
        DeviceActionException.class,
        () -> modulePusher.pushModule(sourcePackage, targetPackage, tmpDir.toPath()));
  }

  @Test
  public void getTargetOnDevice_apexUnderSystem_returnDirectly() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    AndroidPackage onDevice =
        getApex(new File("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex"));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexUnderData_returnApexUnderSystem() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    when(mockDevice.listFiles("/system/apex/"))
        .thenReturn(listSystemApex("com.android.FAKE.APEX.PACKAGE.NAME.apex"));
    AndroidPackage onDevice =
        getApex(new File("/data/apex/active/com.android.FAKE.APEX.PACKAGE.NAME.apex"));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexDecompressed_returnApexUnderSystem() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    SortedSet<String> apexPaths = listSystemApex("com.android.FAKE.APEX.PACKAGE.NAME.apex");
    when(mockDevice.listFiles("/system/apex/")).thenReturn(apexPaths);
    AndroidPackage onDevice =
        getApex(
            new File(
                "/data/apex/decompressed/com.android.FAKE.APEX.PACKAGE.NAME@310000000.decompressed.apex"));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexOnQ_returnDefaultPath() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(29);
    AndroidPackage onDevice =
        AndroidPackage.builder()
            .setInfo(
                PackageInfo.builder()
                    .setPackageName(APEX_PACKAGE_NAME)
                    .setIsApex(true)
                    .setVersionCode(310000000L)
                    .setSourceDir("")
                    .build())
            .build();

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexDecompressed_returnApexWithDashUnderSystem() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    when(mockDevice.listFiles("/system/apex/"))
        .thenReturn(listSystemApex("com.android.FAKE.APEX.PACKAGE.NAME_trimmed_compressed.apex"));
    AndroidPackage onDevice =
        getApex(
            new File(
                "/data/apex/decompressed/com.android.FAKE.APEX.PACKAGE.NAME@310000000.decompressed.apex"));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME_trimmed_compressed.apex");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apkUnderSystem_returnDirectly() throws Exception {
    AndroidPackage onDevice = getApk(new File("/system/app/com.android.FAKE.APK.PACKAGE.NAME.apk"));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/app/com.android.FAKE.APK.PACKAGE.NAME.apk");
    assertThat(result.getSecond()).isFalse();
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_splitApks_returnDir() throws Exception {
    String baseApp = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base-master.apk";
    String hdpi = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk";
    AndroidPackage onDevice = getSplit(new File(baseApp), new File(baseApp), new File(hdpi));

    Pair<Path, Boolean> result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result.getFirst().toString())
        .isEqualTo("/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    assertThat(result.getSecond()).isTrue();
    verify(mockDevice).removeFiles("/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
  }

  @Test
  public void getTargetOnDevice_missFilePathForS_throwException() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    AndroidPackage onDevice =
        AndroidPackage.builder()
            .setInfo(
                PackageInfo.builder()
                    .setPackageName(APEX_PACKAGE_NAME)
                    .setIsApex(true)
                    .setVersionCode(310000000L)
                    .setSourceDir("")
                    .build())
            .build();

    assertThrows(DeviceActionException.class, () -> modulePusher.getTargetOnDevice(onDevice));
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void prepareTargetOnHost_renameFile() throws Exception {
    Path tempDir = tmpDir.toPath();
    tmpFolder.newFolder("external");
    Path source = tmpFolder.newFile("external/file1").toPath();

    Path target = modulePusher.prepareTargetOnHost(source, tempDir, Paths.get("file2"));

    assertThat(target.toString()).isEqualTo(Paths.get(tempDir.toString(), "file2").toString());
  }

  @Test
  public void prepareTargetOnHost_noRename() throws Exception {
    Path tempDir = tmpDir.toPath();
    tmpFolder.newFolder("external");
    Path source = tmpFolder.newFile("external/file1").toPath();

    Path target = modulePusher.prepareTargetOnHost(source, tempDir, Paths.get("file1"));

    assertThat(target.toString()).isEqualTo(Paths.get(tempDir.toString(), "file1").toString());
  }

  private Map<String, AndroidPackage> getPackageMap(
      File apexFile, File apkFile, File apksFile, File baseFile, File splitFile) {
    return ImmutableMap.of(
        APEX_PACKAGE_NAME,
        getApex(apexFile),
        APK_PACKAGE_NAME,
        getApk(apkFile),
        SPLIT_APK_PACKAGE_NAME,
        getSplit(apksFile, baseFile, splitFile));
  }

  private static AndroidPackage getApex(File apexFile) {
    return AndroidPackage.builder()
        .setInfo(
            PackageInfo.builder()
                .setPackageName(APEX_PACKAGE_NAME)
                .setIsApex(true)
                .setVersionCode(FAKE_VERSION_CODE)
                .setSourceDir(apexFile.getAbsolutePath())
                .build())
        .addFiles(apexFile)
        .build();
  }

  private AndroidPackage getApk(File apkFile) {
    return AndroidPackage.builder()
        .setInfo(
            PackageInfo.builder()
                .setPackageName(APK_PACKAGE_NAME)
                .setIsApex(false)
                .setVersionCode(FAKE_VERSION_CODE)
                .setSourceDir(apkFile.getAbsolutePath())
                .build())
        .addFiles(apkFile)
        .build();
  }

  private AndroidPackage getSplit(File apksFile, File baseFile, File splitFile) {
    return AndroidPackage.builder()
        .setInfo(
            PackageInfo.builder()
                .setPackageName(SPLIT_APK_PACKAGE_NAME)
                .setIsApex(false)
                .setVersionCode(FAKE_VERSION_CODE)
                .setSourceDir(apksFile.getAbsolutePath())
                .build())
        .addFiles(baseFile, splitFile)
        .setIsSplit(true)
        .build();
  }

  private SortedSet<String> listSystemApex(String apex) {
    SortedSet<String> apexPaths = new TreeSet<>();
    apexPaths.add("com.android.i18n.apex");
    apexPaths.add(apex);
    apexPaths.add("com.google.android.media.apex");
    apexPaths.add("com.google.android.media.swcodec.apex");
    apexPaths.add("com.google.android.mediaprovider.apex");
    return apexPaths;
  }
}
