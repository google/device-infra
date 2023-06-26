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

package com.google.devtools.deviceaction.framework.actions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.util.List;
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
public final class PackageUpdateTrackerTest {

  private static final long VERSION_CODE = 33;
  private static final String APEX_PACKAGE = "fake.apex.package";
  private static final String APK_PACKAGE = "fake.apk.package";
  private static final String SPLIT_APK_PACKAGE = "fake.split-apk.package";
  private static final ModuleInfo APEX_MODULE_INFO =
      ModuleInfo.builder().setName("fake apex module").setPackageName(APEX_PACKAGE).build();
  private static final ModuleInfo APK_MODULE_INFO =
      ModuleInfo.builder().setName("fake apk module").setPackageName(APK_PACKAGE).build();
  private static final ModuleInfo SPLIT_APK_MODULE_INFO =
      ModuleInfo.builder()
          .setName("fake split apk module")
          .setPackageName(SPLIT_APK_PACKAGE)
          .build();
  private static final String SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX =
      "/system/apex/fake.apex.package.apex";
  private static final String DATA_APP_FAKE_APK_PACKAGE = "/data/app/fake.apk.package";

  private static final String DATA_APP_FAKE_SPLIT_APK_PACKAGE = "/data/app/fake.split-apk.package";
  private static final PackageInfo PRELOAD_APEX_INFO =
      PackageInfo.builder()
          .setIsApex(true)
          .setPackageName(APEX_PACKAGE)
          .setSourceDir(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)
          .setVersionCode(30)
          .build();

  private static final PackageInfo PUSHED_APEX_INFO =
      PackageInfo.builder()
          .setIsApex(true)
          .setPackageName(APEX_PACKAGE)
          .setSourceDir(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)
          .setVersionCode(VERSION_CODE)
          .build();
  private static final PackageInfo ACTIVATED_APEX_INFO =
      PackageInfo.builder()
          .setIsApex(true)
          .setPackageName(APEX_PACKAGE)
          .setSourceDir("/data/app/fake.apex.package.apex")
          .setVersionCode(VERSION_CODE)
          .build();

  private static final PackageInfo PRELOAD_APK_INFO =
      PackageInfo.builder()
          .setPackageName(APK_PACKAGE)
          .setSourceDir(DATA_APP_FAKE_APK_PACKAGE + "/base.apk")
          .setVersionCode(29)
          .build();
  private static final PackageInfo PUSHED_APK_INFO =
      PackageInfo.builder()
          .setPackageName(APK_PACKAGE)
          .setSourceDir(DATA_APP_FAKE_APK_PACKAGE + "/base.apex")
          .setVersionCode(VERSION_CODE)
          .build();
  private static final PackageInfo PRELOAD_SPLIT_APK_INFO =
      PackageInfo.builder()
          .setPackageName(SPLIT_APK_PACKAGE)
          .setSourceDir(DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/base.apk")
          .setVersionCode(VERSION_CODE)
          .build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private AaptUtil aaptUtil;
  @Mock private AndroidPhone mockDevice;
  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  private File inputDir;
  private File extractDir;
  private File apexFile;
  private File apkFile;
  private File apexApksFile;
  private File apexExtractFile;
  private File apkApksFile;
  private File apkExtractFile1;
  private File apkExtractFile2;
  private File splitBaseApkFile;
  private File splitAuxApkFile;

  private PackageUpdateTracker tracker;

  @Before
  public void setUp() throws Exception {
    inputDir = tmpFolder.newFolder("input");
    apexFile = tmpFolder.newFile("input/fake.apex.package.apex");
    apkFile = tmpFolder.newFile("input/fake.apk.package.apk");
    tmpFolder.newFolder("input/fake.split-apk.package");
    splitBaseApkFile = tmpFolder.newFile("input/fake.split-apk.package/base.apk");
    splitAuxApkFile = tmpFolder.newFile("input/fake.split-apk.package/aux.apk");
    tmpFolder.newFolder("input/apks");
    apexApksFile = tmpFolder.newFile("input/apks/fake.apex.package.apks");
    apkApksFile = tmpFolder.newFile("input/apks/fake.split-apk.package.apks");
    extractDir = tmpFolder.newFolder("extracted");
    tmpFolder.newFolder("extracted/fake.apex.package.apex-Splits");
    apexExtractFile = tmpFolder.newFile("extracted/fake.apex.package.apex-Splits/extract.apex");
    tmpFolder.newFolder("extracted/fake.split-apk.package-Splits");
    apkExtractFile1 = tmpFolder.newFile("extracted/fake.split-apk.package-Splits/extract-base.apk");
    apkExtractFile2 = tmpFolder.newFile("extracted/fake.split-apk.package-Splits/extract-aux.apk");
    when(mockDevice.listModules())
        .thenReturn(
            ImmutableSortedSet.of(APEX_MODULE_INFO, APK_MODULE_INFO, SPLIT_APK_MODULE_INFO));
    when(mockDevice.getInstalledPackageMap())
        .thenReturn(
            ImmutableMap.of(
                APEX_PACKAGE,
                PRELOAD_APEX_INFO,
                APK_PACKAGE,
                PRELOAD_APK_INFO,
                SPLIT_APK_PACKAGE,
                PRELOAD_SPLIT_APK_INFO));
    when(mockDevice.getAllInstalledPaths(APEX_PACKAGE))
        .thenReturn(ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX));
    when(mockDevice.getAllInstalledPaths(APK_PACKAGE))
        .thenReturn(ImmutableList.of(DATA_APP_FAKE_APK_PACKAGE + "/base.apk"));
    when(mockDevice.getAllInstalledPaths(SPLIT_APK_PACKAGE))
        .thenReturn(
            ImmutableList.of(
                DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/base.apk",
                DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/aux.apk"));
    when(mockDevice.extractFilesFromApks(apexApksFile))
        .thenReturn(ImmutableList.of(apexExtractFile));
    when(mockDevice.extractFilesFromApks(apkApksFile))
        .thenReturn(ImmutableList.of(apkExtractFile1, apkExtractFile2));
    setUpAapt(apexFile, APEX_PACKAGE);
    setUpAapt(apexExtractFile, APEX_PACKAGE);
    setUpAapt(apkFile, APK_PACKAGE);
    setUpAapt(apkExtractFile1, SPLIT_APK_PACKAGE);
    setUpAapt(apkExtractFile2, SPLIT_APK_PACKAGE);
    setUpAapt(splitBaseApkFile, SPLIT_APK_PACKAGE);
    setUpAapt(splitAuxApkFile, SPLIT_APK_PACKAGE);
    tracker = new PackageUpdateTracker(mockDevice, aaptUtil, localFileUtil);
  }

  @Test
  public void setUpInstall_emptySet_getEmpty() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
        tracker.getPackageUpdateMap(ImmutableSet.of());

    assertThat(toInstall).isEmpty();
  }

  @Test
  public void setUpInstall_plainModuleFiles_getResults() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
        tracker.getPackageUpdateMap(ImmutableSet.of(apexFile, apkFile));

    assertThat(toInstall)
        .containsExactly(
            buildAndroidPackageFromFile(apexFile, ImmutableList.of(apexFile)),
            buildAndroidPackageOnDevice(
                PRELOAD_APEX_INFO, ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)),
            buildAndroidPackageFromFile(apkFile, ImmutableList.of(apkFile)),
            buildAndroidPackageOnDevice(
                PRELOAD_APK_INFO, ImmutableList.of(DATA_APP_FAKE_APK_PACKAGE + "/base.apk")));
  }

  @Test
  public void setUpInstall_apksFiles_getExtractFiles() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
        tracker.getPackageUpdateMap(ImmutableSet.of(apkApksFile, apexApksFile));

    assertThat(toInstall).hasSize(2);
    assertThat(toInstall)
        .containsExactly(
            buildAndroidPackageFromFile(
                apexExtractFile, ImmutableList.of(apexExtractFile), apexApksFile),
            buildAndroidPackageOnDevice(
                PRELOAD_APEX_INFO, ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)),
            buildAndroidPackageFromFile(
                apkExtractFile1, ImmutableList.of(apkExtractFile1, apkExtractFile2), apkApksFile),
            buildAndroidPackageOnDevice(
                PRELOAD_SPLIT_APK_INFO,
                ImmutableList.of(
                    DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/base.apk",
                    DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/aux.apk")));
  }

  @Test
  public void setUpInstall_splitFiles_mergeSplitFilesToSinglePackage() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
        tracker.getPackageUpdateMap(ImmutableSet.of(apexFile, splitBaseApkFile, splitAuxApkFile));

    assertThat(toInstall).hasSize(2);
    assertThat(toInstall)
        .containsExactly(
            buildAndroidPackageFromFile(apexFile, ImmutableList.of(apexFile)),
            buildAndroidPackageOnDevice(
                PRELOAD_APEX_INFO, ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)),
            buildAndroidPackageFromFile(
                splitBaseApkFile, ImmutableList.of(splitBaseApkFile, splitAuxApkFile)),
            buildAndroidPackageOnDevice(
                PRELOAD_SPLIT_APK_INFO,
                ImmutableList.of(
                    DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/base.apk",
                    DATA_APP_FAKE_SPLIT_APK_PACKAGE + "/aux.apk")));
  }

  @Test
  public void setUpInstall_mixForm_throwException() throws Exception {
    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> tracker.getPackageUpdateMap(ImmutableSet.of(apexFile, apkApksFile)));

    assertThat(t).hasMessageThat().contains("INVALID_FILE_FORMAT");
  }

  @Test
  public void getAllFilesInDir_getExpectedResults() throws Exception {
    assertThat(tracker.getAllFilesInDir(inputDir, ImmutableList.of(APKS_SUFFIX)))
        .containsExactly(apexApksFile, apkApksFile);
    assertThat(tracker.getAllFilesInDir(inputDir, ImmutableList.of(APK_SUFFIX)))
        .containsExactly(apkFile, splitBaseApkFile, splitAuxApkFile);
    assertThat(tracker.getAllFilesInDir(inputDir, asList(APK_SUFFIX, APEX_SUFFIX)))
        .containsExactly(apkFile, splitBaseApkFile, splitAuxApkFile, apexFile);
    assertThat(tracker.getAllFilesInDir(extractDir, asList(APEX_SUFFIX, APK_SUFFIX)))
        .containsExactly(apexExtractFile, apkExtractFile1, apkExtractFile2);
    assertThat(tracker.getAllFilesInDir(inputDir, ImmutableList.of(".txt"))).isEmpty();
  }

  @Test
  public void checkVersionsUpdated_success() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> unused =
        tracker.getPackageUpdateMap(ImmutableSet.of(apkFile, apexFile));

    when(mockDevice.getInstalledPackageMap())
        .thenReturn(ImmutableMap.of(APEX_PACKAGE, PUSHED_APEX_INFO, APK_PACKAGE, PUSHED_APK_INFO));
    tracker.checkVersionsUpdated();
  }

  @Test
  public void checkVersionsUpdated_throwException() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> unused =
        tracker.getPackageUpdateMap(ImmutableSet.of(apkFile, apexFile));

    when(mockDevice.getInstalledPackageMap())
        .thenReturn(ImmutableMap.of(APEX_PACKAGE, PRELOAD_APEX_INFO, APK_PACKAGE, PUSHED_APK_INFO));
    DeviceActionException t =
        assertThrows(DeviceActionException.class, () -> tracker.checkVersionsUpdated());
    assertThat(t).hasMessageThat().contains("Module is not updated!");
  }

  @Test
  public void checkVersionsUpdatedAndActivated_success() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> unused =
        tracker.getPackageUpdateMap(ImmutableSet.of(apkFile, apexFile));

    when(mockDevice.getInstalledPackageMap())
        .thenReturn(
            ImmutableMap.of(APEX_PACKAGE, ACTIVATED_APEX_INFO, APK_PACKAGE, PUSHED_APK_INFO));
    tracker.checkVersionsUpdatedAndActivated();
  }

  @Test
  public void checkVersionsUpdatedAndActivated_throwException() throws Exception {
    ImmutableMap<AndroidPackage, AndroidPackage> unused =
        tracker.getPackageUpdateMap(ImmutableSet.of(apkFile, apexFile));

    when(mockDevice.getInstalledPackageMap())
        .thenReturn(ImmutableMap.of(APEX_PACKAGE, PUSHED_APEX_INFO, APK_PACKAGE, PUSHED_APK_INFO));
    DeviceActionException t =
        assertThrows(DeviceActionException.class, () -> tracker.checkVersionsUpdatedAndActivated());
    assertThat(t).hasMessageThat().contains("not activated!");
  }

  private void setUpAapt(File file, String packageName)
      throws DeviceActionException, InterruptedException {
    when(aaptUtil.getPackageInfo(file))
        .thenReturn(
            PackageInfo.builder()
                .setIsApex(file.getName().endsWith(APEX_SUFFIX))
                .setPackageName(packageName)
                .setSourceDir(file.getAbsolutePath())
                .setVersionCode(VERSION_CODE)
                .build());
  }

  private AndroidPackage buildAndroidPackageFromFile(File file, List<File> files) {
    boolean isApex = file.getName().endsWith(APEX_SUFFIX);
    String packageName = isApex ? APEX_PACKAGE : APK_PACKAGE;
    boolean isSplit = files.size() > 1;
    if (isSplit) {
      packageName = SPLIT_APK_PACKAGE;
    }
    return AndroidPackage.builder()
        .setInfo(
            PackageInfo.builder()
                .setIsApex(isApex)
                .setPackageName(packageName)
                .setSourceDir(file.getAbsolutePath())
                .setVersionCode(VERSION_CODE)
                .build())
        .setFiles(ImmutableList.copyOf(files))
        .setIsSplit(isSplit)
        .build();
  }

  private AndroidPackage buildAndroidPackageFromFile(File file, List<File> files, File apksFile) {
    return buildAndroidPackageFromFile(file, files).toBuilder().setApksFile(apksFile).build();
  }

  private AndroidPackage buildAndroidPackageOnDevice(PackageInfo packageInfo, List<String> files) {
    return AndroidPackage.builder()
        .setInfo(packageInfo)
        .setIsSplit(files.size() > 1)
        .addFiles(files.stream().map(File::new).collect(toImmutableList()))
        .build();
  }
}
