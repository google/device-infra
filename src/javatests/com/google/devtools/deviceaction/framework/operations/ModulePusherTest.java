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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.proto.ResourcePath;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

  private static final long FAKE_VERSION_CODE = 12345L;
  private static final String APEX_PACKAGE_NAME = "com.android.FAKE.APEX.PACKAGE.NAME";
  private static final String APK_PACKAGE_NAME = "com.android.FAKE.APK.PACKAGE.NAME";
  private static final String SPLIT_APK_PACKAGE_NAME = "com.android.FAKE.SPLIT.APK.PACKAGE.NAME";
  private static final String APEX_FILE_NAME = "com.android.FAKE.APEX.PACKAGE.NAME.apex";
  private static final String DATA_APP_SPLIT_APK_FOLDER =
      "/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME";
  private static final String SYSTEM_APEX_PATH =
      "/system/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex";
  private static final String SYSTEM_APK_PATH = "/system/app/com.android.FAKE.APK.PACKAGE.NAME.apk";
  private static final String DATA_APEX_PATH = "/data/apex/com.android.FAKE.APEX.PACKAGE.NAME.apex";
  private static final String DATA_APK_PATH = "/data/app/com.android.FAKE.APK.PACKAGE.NAME.apk";

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidPhone mockDevice;
  @Mock private ResourceHelper mockHelper;
  private File tmpDir;
  private File fakeApex;
  private File fakeApk;
  private File fakeSplitDir;
  private File fakeBaseApk;
  private File fakeHdpiApk;
  private ModulePusher modulePusher;
  private ImmutableMap<AndroidPackage, AndroidPackage> packageMap;

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
    File fakeApks = tmpFolder.newFile("bundletool/arm64.apks");
    when(mockHelper.getTmpFileDir()).thenReturn(tmpDir.toPath());
    when(mockDevice.isUserdebug()).thenReturn(true);
    when(mockDevice.moduleDirOnDevice()).thenReturn(ImmutableMap.of());
    modulePusher = new ModulePusher(mockDevice, new LocalFileUtil(), mockHelper);
    packageMap =
        ImmutableMap.of(
            getApex(fakeApex),
            getApex(new File(DATA_APEX_PATH)),
            getApk(fakeApk),
            getApk(new File(DATA_APK_PATH)),
            getSplit(fakeApks, fakeBaseApk, fakeHdpiApk),
            getSplit(
                new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
                new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base.apk"),
                new File("/data/app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk")));
  }

  @Test
  public void pushModules_reboot() throws Exception {
    modulePusher.pushModules(packageMap);

    verifyAllFilesExistAndPushedAndFolderOnDeviceRemoved();
    ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
    ArgumentCaptor<Path> target = ArgumentCaptor.forClass(Path.class);
    verify(mockDevice, times(3)).push(source.capture(), target.capture());
    List<Path> sources = source.getAllValues();
    List<Path> targets = target.getAllValues();
    assertIsFileAndEndWith(
        sources.get(0),
        "com.android.FAKE.APEX.PACKAGE.NAME/com.android.FAKE.APEX.PACKAGE.NAME.apex");
    assertThat(targets.get(0).toString()).isEqualTo(SYSTEM_APEX_PATH);
    assertIsFileAndEndWith(
        sources.get(1), "com.android.FAKE.APK.PACKAGE.NAME/com.android.FAKE.APK.PACKAGE.NAME.apk");
    assertThat(targets.get(1).toString()).isEqualTo(DATA_APK_PATH);
    assertIsDirectoryAndEndWith(
        sources.get(2),
        "com.android.FAKE.SPLIT.APK.PACKAGE.NAME/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
    assertThat(targets.get(2).toString()).isEqualTo(DATA_APP_SPLIT_APK_FOLDER);
    File[] splitFiles = sources.get(2).toFile().listFiles();
    assertThat(splitFiles)
        .asList()
        .comparingElementsUsing(
            Correspondence.<File, String>transforming(File::getName, "has a name of"))
        .containsExactly("base-arm64.apk", "hdpi-arm64.apk");
  }

  @Test
  public void pushModules_softReboot() throws Exception {
    modulePusher.pushModules(packageMap, /* softReboot= */ true);

    verify(mockDevice, times(2)).reboot();
    verify(mockDevice).softReboot();
  }

  @Test
  public void pushModules_enableTestHarness() throws Exception {
    when(mockDevice.reloadByFactoryReset()).thenReturn(true);

    modulePusher.pushModules(packageMap);

    verify(mockDevice, times(3)).push(any(), any());
    verify(mockDevice).removeFiles(DATA_APP_SPLIT_APK_FOLDER);
    verify(mockDevice, times(2)).reboot();
    verify(mockDevice).enableTestharness();
  }

  @Test
  public void pushModules_disablePackageCache() throws Exception {
    when(mockDevice.needDisablePackageCache()).thenReturn(true);

    modulePusher.pushModules(packageMap);

    verifyAllFilesExistAndPushedAndFolderOnDeviceRemoved();
    verify(mockDevice).removeFiles("/data/system/package_cache/");
    verify(mockDevice, times(3)).push(any(), any());
  }

  @Test
  public void pushModules_createTmpDirFail_throwException() throws Exception {
    when(mockHelper.getTmpFileDir())
        .thenThrow(new DeviceActionException("FAKE", ErrorType.CUSTOMER_ISSUE, "no message"));

    assertThrows(DeviceActionException.class, () -> modulePusher.pushModules(packageMap));
  }

  @Test
  public void pushModules_pushFail_throwException() throws Exception {
    doThrow(DeviceActionException.class).when(mockDevice).push(any(), any());

    assertThrows(DeviceActionException.class, () -> modulePusher.pushModules(packageMap));
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
    AndroidPackage targetPackage = getApex(new File(DATA_APEX_PATH));
    long modified = fakeApex.lastModified();

    PackageInfo info = modulePusher.pushModule(sourcePackage, targetPackage, tmpDir.toPath());

    assertTrue(fakeApex.exists());
    ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
    ArgumentCaptor<Path> target = ArgumentCaptor.forClass(Path.class);
    verify(mockDevice).push(source.capture(), target.capture());
    Path sourcePath = source.getValue();
    Path targetPath = target.getValue();
    assertThat(sourcePath.toFile().lastModified()).isGreaterThan(modified);
    assertIsFileAndEndWith(sourcePath, APEX_FILE_NAME);
    assertThat(targetPath.toString()).isEqualTo(SYSTEM_APEX_PATH);
    assertThat(info).isAtLeast(sourcePackage.info());
  }

  @Test
  public void pushModule_pushFail_throwException() throws Exception {
    doThrow(DeviceActionException.class).when(mockDevice).push(any(), any());
    AndroidPackage sourcePackage = getApex(fakeApex);
    AndroidPackage targetPackage = getApex(new File(DATA_APEX_PATH));

    assertThrows(
        DeviceActionException.class,
        () -> modulePusher.pushModule(sourcePackage, targetPackage, tmpDir.toPath()));
  }

  @Test
  public void getTargetOnDevice_apexUnderSystem_returnDirectly() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    AndroidPackage onDevice = getApex(new File(SYSTEM_APEX_PATH));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder().setPath(SYSTEM_APEX_PATH).setIsDirectory(false).build());
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexUnderData_returnApexUnderSystem() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    when(mockDevice.listFiles("/system/apex/")).thenReturn(listSystemApex(APEX_FILE_NAME));
    AndroidPackage onDevice =
        getApex(new File("/data/apex/active/com.android.FAKE.APEX.PACKAGE.NAME.apex"));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder().setPath(SYSTEM_APEX_PATH).setIsDirectory(false).build());
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexDecompressed_returnApexUnderSystem() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    SortedSet<String> apexPaths = listSystemApex(APEX_FILE_NAME);
    when(mockDevice.listFiles("/system/apex/")).thenReturn(apexPaths);
    AndroidPackage onDevice =
        getApex(
            new File(
                "/data/apex/decompressed/com.android.FAKE.APEX.PACKAGE.NAME@310000000.decompressed.apex"));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder().setPath(SYSTEM_APEX_PATH).setIsDirectory(false).build());
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apexOnQ_returnDefaultPath() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(29);
    AndroidPackage onDevice = getApex(new File(""));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder().setPath(SYSTEM_APEX_PATH).setIsDirectory(false).build());
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

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder()
                .setPath("/system/apex/com.android.FAKE.APEX.PACKAGE.NAME_trimmed_compressed.apex")
                .setIsDirectory(false)
                .build());
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_apkUnderSystem_returnDirectly() throws Exception {
    AndroidPackage onDevice = getApk(new File(SYSTEM_APK_PATH));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder().setPath(SYSTEM_APK_PATH).setIsDirectory(false).build());
    verify(mockDevice, never()).removeFiles(anyString());
  }

  @Test
  public void getTargetOnDevice_splitApks_returnDir() throws Exception {
    String baseApp = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base-master.apk";
    String hdpi = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk";
    AndroidPackage onDevice = getSplit(new File(baseApp), new File(baseApp), new File(hdpi));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(
            ResourcePath.newBuilder()
                .setPath("/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME")
                .setIsDirectory(true)
                .build());
    verify(mockDevice).removeFiles("/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME");
  }

  @Test
  public void getTargetOnDevice_splitApks_returnDirFromSpec() throws Exception {
    String baseApp = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/base-master.apk";
    String hdpi = "/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME/hdpi.apk";
    AndroidPackage onDevice = getSplit(new File(baseApp), new File(baseApp), new File(hdpi));
    String vendorPath = "/mnt/vendor/system/priv-app/com.android.FAKE.SPLIT.APK.PACKAGE.NAME";
    when(mockDevice.moduleDirOnDevice())
        .thenReturn(ImmutableMap.of(SPLIT_APK_PACKAGE_NAME, vendorPath));

    ResourcePath result = modulePusher.getTargetOnDevice(onDevice);

    assertThat(result)
        .isEqualTo(ResourcePath.newBuilder().setPath(vendorPath).setIsDirectory(true).build());
    verify(mockDevice).removeFiles(vendorPath);
  }

  @Test
  public void getTargetOnDevice_missFilePathForS_throwException() throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(31);
    AndroidPackage onDevice = getApex(new File(""));

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

  private static void assertIsDirectoryAndEndWith(Path filePath, String endWith) {
    assertTrue(filePath.toFile().isDirectory());
    assertThat(filePath.toString()).endsWith(endWith);
  }

  private static void assertIsFileAndEndWith(Path filePath, String endWith) {
    assertTrue(filePath.toFile().isFile());
    assertThat(filePath.toString()).endsWith(endWith);
  }

  private void verifyAllFilesExistAndPushedAndFolderOnDeviceRemoved()
      throws DeviceActionException, InterruptedException {
    assertTrue(fakeApex.exists());
    assertTrue(fakeApk.exists());
    assertTrue(fakeSplitDir.exists());
    assertTrue(fakeBaseApk.exists());
    assertTrue(fakeHdpiApk.exists());
    verify(mockDevice).removeFiles(DATA_APP_SPLIT_APK_FOLDER);
    verify(mockDevice, times(3)).reboot();
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
