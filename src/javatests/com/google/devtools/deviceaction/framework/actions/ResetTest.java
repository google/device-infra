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
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ImageZipFlasher;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.operations.OtaSideloader;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.action.ResetOption;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.File;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public final class ResetTest {
  private static final String APEX_PACKAGE = "fake.apex.package";
  private static final String APK_PACKAGE = "fake.apk.package";
  private static final int VERSION_CODE = 33;

  private static final String SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX =
      "/system/apex/fake.apex.package.apex";
  private static final String DATA_APP_FAKE_APK_PACKAGE = "/data/app/fake.apk.package";
  private static final PackageInfo PRELOAD_APEX_INFO =
      PackageInfo.builder()
          .setIsApex(true)
          .setPackageName(APEX_PACKAGE)
          .setSourceDir(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)
          .setVersionCode(30)
          .build();

  private static final PackageInfo PRELOAD_APK_INFO =
      PackageInfo.builder()
          .setPackageName(APK_PACKAGE)
          .setSourceDir(DATA_APP_FAKE_APK_PACKAGE + "/base.apk")
          .setVersionCode(29)
          .build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private PackageUpdateTracker mockTracker;
  @Mock private ModulePusher mockPusher;
  @Mock private ImageZipFlasher mockFlasher;
  @Mock private OtaSideloader mockSideloader;
  @Mock private AndroidPhone mockDevice;

  private Reset reset;

  private File moduleDir;
  private File apexFile;
  private File apkFile;
  private File otaFile;
  private File imageZipFile;

  @Before
  public void setUp() throws Exception {
    moduleDir = tmpFolder.newFolder("modules");
    apexFile = tmpFolder.newFile("modules/fake.apex.package.apex");
    apkFile = tmpFolder.newFile("modules/fake.apk.package.apk");
    otaFile = tmpFolder.newFile("ota.zip");
    imageZipFile = tmpFolder.newFile("image.zip");
    when(mockDevice.getSdkVersion()).thenReturn(VERSION_CODE);
    when(mockTracker.getAllFilesInDir(moduleDir, asList(APK_SUFFIX, APEX_SUFFIX)))
        .thenReturn(ImmutableSet.of(apexFile, apkFile));
    when(mockTracker.getPackageUpdateMap(ImmutableSet.of(apexFile, apkFile)))
        .thenReturn(
            ImmutableMap.of(
                buildAndroidPackageFromFile(apexFile, ImmutableList.of(apexFile)),
                buildAndroidPackageOnDevice(
                    PRELOAD_APEX_INFO, ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)),
                buildAndroidPackageFromFile(apkFile, ImmutableList.of(apkFile)),
                buildAndroidPackageOnDevice(
                    PRELOAD_APK_INFO,
                    ImmutableList.of(DATA_APP_FAKE_APK_PACKAGE + "/fake.apk.package.apk"))));
  }

  @Test
  public void perform_testharness_needRecovery(
      @TestParameter({"TEST_HARNESS", "OTA_SIDELOAD", "FASTBOOT_WITH_PARTITION_IMAGES"})
          ResetOption option,
      @TestParameter boolean needPreloadModulesRecovery)
      throws Exception {
    setUpAction(option, needPreloadModulesRecovery);

    reset.perform();
    switch (option) {
      case TEST_HARNESS:
        verify(mockDevice, times(2)).enableTestharness();
        break;
      case OTA_SIDELOAD:
        verify(mockSideloader).sideload(eq(otaFile), any(Duration.class), anyBoolean());
        verify(mockDevice).enableTestharness();
        break;
      case FASTBOOT_WITH_PARTITION_IMAGES:
        verify(mockFlasher).flashDevice(eq(imageZipFile), eq("flash-all.sh"), any(Duration.class));
        verify(mockDevice).enableTestharness();
        break;
      default:
        break;
    }
    if (needPreloadModulesRecovery) {
      verify(mockPusher)
          .pushModules(
              ImmutableMap.of(
                  buildAndroidPackageFromFile(apexFile, ImmutableList.of(apexFile)),
                  buildAndroidPackageOnDevice(
                      PRELOAD_APEX_INFO, ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX)),
                  buildAndroidPackageFromFile(apkFile, ImmutableList.of(apkFile)),
                  buildAndroidPackageOnDevice(
                      PRELOAD_APK_INFO,
                      ImmutableList.of(DATA_APP_FAKE_APK_PACKAGE + "/fake.apk.package.apk"))));
    } else {
      verify(mockPusher, never()).pushModules(anyMap());
    }
  }

  private void setUpAction(ResetOption resetOption, boolean needPreloadModulesRecovery) {
    ResetSpec resetSpec =
        ResetSpec.newBuilder()
            .setResetOption(resetOption)
            .setNeedPreloadModulesRecovery(needPreloadModulesRecovery)
            .addFiles(
                FileSpec.newBuilder()
                    .setTag("recovery_modules")
                    .setLocalPath(moduleDir.getAbsolutePath()))
            .addFiles(
                FileSpec.newBuilder().setTag("ota_package").setLocalPath(otaFile.getAbsolutePath()))
            .addFiles(
                FileSpec.newBuilder()
                    .setTag("image_zip")
                    .setLocalPath(imageZipFile.getAbsolutePath()))
            .build();
    reset =
        new Reset(
            mockTracker,
            mockPusher,
            mockSideloader,
            mockFlasher,
            resetSpec,
            mockDevice,
            ImmutableMultimap.of(
                "recovery_modules", moduleDir, "ota_package", otaFile, "image_zip", imageZipFile));
  }

  private static AndroidPackage buildAndroidPackageFromFile(File file, List<File> files) {
    boolean isApex = file.getName().endsWith(APEX_SUFFIX);
    String packageName = isApex ? APEX_PACKAGE : APK_PACKAGE;
    boolean isSplit = files.size() > 1;
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

  private static AndroidPackage buildAndroidPackageOnDevice(
      PackageInfo packageInfo, List<String> files) {
    return AndroidPackage.builder()
        .setInfo(packageInfo)
        .setIsSplit(files.size() > 1)
        .addFiles(files.stream().map(File::new).collect(toImmutableList()))
        .build();
  }
}
