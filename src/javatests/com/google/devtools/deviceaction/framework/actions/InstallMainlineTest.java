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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.truth.Correspondence;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ModuleCleaner;
import com.google.devtools.deviceaction.framework.operations.ModuleInstaller;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public final class InstallMainlineTest {

  private static final String APEX_PACKAGE = "fake.apex.package";
  private static final String APK_PACKAGE = "fake.apk.package";
  private static final String APKS_DIR = "apks-dir";
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

  private static final ModuleInfo APEX_MODULE_INFO =
      ModuleInfo.builder().setName("fake apex module").setPackageName(APEX_PACKAGE).build();
  private static final ModuleInfo APK_MODULE_INFO =
      ModuleInfo.builder().setName("fake apk module").setPackageName(APK_PACKAGE).build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Captor public ArgumentCaptor<List<File>> listArgumentCaptor;
  @Captor public ArgumentCaptor<Collection<AndroidPackage>> collectionArgumentCaptor;
  @Captor public ArgumentCaptor<ImmutableMap<String, AndroidPackage>> sourceMapCaptor;
  @Captor public ArgumentCaptor<ImmutableMap<String, AndroidPackage>> targetMapCaptor;
  @Mock private AaptUtil aaptUtil;
  @Mock private ModuleCleaner mockCleaner;
  @Mock private ModuleInstaller mockInstaller;
  @Mock private ModulePusher mockPusher;
  @Mock private AndroidPhone mockDevice;
  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  private File apexFile;
  private File apkFile;
  private File apksFolder;
  private File apexApksFile;
  private File apexExtractFile;
  private File apkApksFile;
  private File splitFolder;
  private File apkExtractFile1;
  private File apkExtractFile2;
  private File trainZip1;
  private File trainZip2;

  private InstallMainline installMainline;

  enum ModuleType {
    EMPTY,
    ZIP,
    APKS,
    PACKAGE_FILE,
    SPLIT_FOLDER
  }

  @Before
  public void setUp() throws Exception {
    apexFile = tmpFolder.newFile(APEX_PACKAGE + ".apex");
    apkFile = tmpFolder.newFile("base.apk");
    apksFolder = tmpFolder.newFolder(APKS_DIR);
    tmpFolder.newFolder(APKS_DIR, APEX_PACKAGE);
    apexApksFile =
        tmpFolder.newFile(
            Path.of(APKS_DIR, APEX_PACKAGE).resolve(APEX_PACKAGE + ".apks").toString());
    splitFolder = tmpFolder.newFolder("Splits");
    tmpFolder.newFolder("Splits/" + APEX_PACKAGE + "-Splits");
    apexExtractFile = tmpFolder.newFile("Splits/" + APEX_PACKAGE + "-Splits/extract.apex");
    tmpFolder.newFolder(APKS_DIR, APK_PACKAGE);
    apkApksFile =
        tmpFolder.newFile(Path.of(APKS_DIR, APK_PACKAGE).resolve(APK_PACKAGE + ".apks").toString());
    tmpFolder.newFolder("Splits/" + APK_PACKAGE + "-Splits/");
    apkExtractFile1 = tmpFolder.newFile("Splits/" + APK_PACKAGE + "-Splits/extract-base.apk");
    apkExtractFile2 = tmpFolder.newFile("Splits/" + APK_PACKAGE + "-Splits/extract-aux.apk");
    tmpFolder.newFolder("train-dir");
    trainZip1 = tmpFolder.newFile("train-dir/train1.zip");
    trainZip2 = tmpFolder.newFile("train-dir/train2.zip");
    when(mockDevice.getSdkVersion()).thenReturn(33);
    when(mockDevice.devKeySigned()).thenReturn(false);
    when(mockDevice.listModules())
        .thenReturn(ImmutableSortedSet.of(APEX_MODULE_INFO, APK_MODULE_INFO));
    when(mockDevice.getAllInstalledPaths(APEX_PACKAGE))
        .thenReturn(ImmutableList.of(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX));
    when(mockDevice.getAllInstalledPaths(APK_PACKAGE))
        .thenReturn(
            ImmutableList.of(
                DATA_APP_FAKE_APK_PACKAGE + "/base.apk", DATA_APP_FAKE_APK_PACKAGE + "/aux.apk"));
    when(mockDevice.extractFilesFromApks(apexApksFile))
        .thenReturn(ImmutableList.of(apexExtractFile));
    when(mockDevice.extractFilesFromApks(apkApksFile))
        .thenReturn(ImmutableList.of(apkExtractFile1, apkExtractFile2));
    setUpAapt(apexFile);
    setUpAapt(apexExtractFile);
    setUpAapt(apkFile);
    setUpAapt(apkExtractFile1);
    setUpAapt(apkExtractFile2);
  }

  @Test
  public void perform_skipIfSdkUnderQ_skip(
      @TestParameter({"ZIP", "APKS", "PACKAGE_FILE"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    when(mockDevice.getSdkVersion()).thenReturn(28);
    setUpTestConfig(type, devKey, cleanup, enableRollback);

    installMainline.perform();

    verify(mockCleaner, never()).cleanUpSessions();
    verify(mockInstaller, never()).sideloadTrains(anyList(), anyBoolean());
    verify(mockInstaller, never()).installModules(anyCollection(), anyBoolean());
    verify(mockPusher, never()).pushModules(anyMap(), anyMap());
  }

  @Test
  public void perform_emptyList_skip(
      @TestParameter({"EMPTY"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, devKey, cleanup, enableRollback);

    installMainline.perform();

    verifyCleanupBehavoir(cleanup);
    verify(mockInstaller, never()).sideloadTrains(anyList(), anyBoolean());
    verify(mockInstaller, never()).installModules(anyCollection(), anyBoolean());
    verify(mockPusher, never()).pushModules(anyMap(), anyMap());
  }

  @Test
  public void perform_installZip_success(
      @TestParameter({"ZIP"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, devKey, cleanup, enableRollback);

    installMainline.perform();

    verifyCleanupBehavoir(cleanup);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(mockInstaller)
        .sideloadTrains(listArgumentCaptor.capture(), booleanArgumentCaptor.capture());
    assertThat(listArgumentCaptor.getValue()).containsExactly(trainZip1, trainZip2);
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(enableRollback);
    verify(mockInstaller, never()).installModules(anyCollection(), anyBoolean());
    verify(mockPusher, never()).pushModules(anyMap(), anyMap());
  }

  @Test
  public void perform_installZip_throwException(
      @TestParameter({"ZIP"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, devKey, cleanup, enableRollback);
    doThrow(DeviceActionException.class)
        .when(mockInstaller)
        .sideloadTrains(anyList(), anyBoolean());

    assertThrows(DeviceActionException.class, () -> installMainline.perform());
  }

  @Test
  public void perform_installPackages_success(
      @TestParameter({"APKS", "PACKAGE_FILE", "SPLIT_FOLDER"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, devKey, cleanup, enableRollback);
    // If devKey, need to push and list packages three times.
    if (devKey) {
      when(mockDevice.listPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APK_INFO),
              ImmutableSortedSet.of(PUSHED_APK_INFO),
              ImmutableSortedSet.of(PUSHED_APK_INFO));
      when(mockDevice.listApexPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APEX_INFO),
              ImmutableSortedSet.of(PUSHED_APEX_INFO),
              ImmutableSortedSet.of(ACTIVATED_APEX_INFO));
    } else {
      when(mockDevice.listPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APK_INFO), ImmutableSortedSet.of(PUSHED_APK_INFO));
      when(mockDevice.listApexPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APEX_INFO), ImmutableSortedSet.of(ACTIVATED_APEX_INFO));
    }

    installMainline.perform();

    verifyCleanupBehavoir(cleanup);
    verify(mockInstaller, never()).sideloadTrains(anyList(), anyBoolean());
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(mockInstaller)
        .installModules(collectionArgumentCaptor.capture(), booleanArgumentCaptor.capture());
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(enableRollback);
    List<AndroidPackage> androidPackages = new ArrayList<>(collectionArgumentCaptor.getValue());
    assertThat(androidPackages).hasSize(2);
    switch (type) {
      case PACKAGE_FILE:
        assertThat(androidPackages.stream().flatMap(ap -> ap.files().stream()))
            .containsExactly(apexFile, apkFile);
        break;
      case SPLIT_FOLDER:
        assertThat(androidPackages.stream().flatMap(ap -> ap.files().stream()))
            .containsExactly(apexExtractFile, apkExtractFile1, apkExtractFile2);
        break;
      default:
        assertThat(androidPackages)
            .comparingElementsUsing(
                Correspondence.<AndroidPackage, File>transforming(
                    ap -> ap.apksFile().orElseThrow(), "has apks file"))
            .containsExactly(apexApksFile, apkApksFile);
        break;
    }
    if (devKey) {
      verify(mockPusher).pushModules(sourceMapCaptor.capture(), targetMapCaptor.capture());
      ImmutableMap<String, AndroidPackage> sourceMap = sourceMapCaptor.getValue();
      ImmutableMap<String, AndroidPackage> targetMap = targetMapCaptor.getValue();
      if (type == ModuleType.PACKAGE_FILE) {
        assertThat(sourceMap.get(APEX_PACKAGE).files()).containsExactly(apexFile);
        assertThat(sourceMap.get(APK_PACKAGE).files()).containsExactly(apkFile);
      } else {
        assertThat(sourceMap.get(APEX_PACKAGE).files()).containsExactly(apexExtractFile);
        assertThat(sourceMap.get(APK_PACKAGE).files())
            .containsExactly(apkExtractFile1, apkExtractFile2);
      }
      assertThat(targetMap.get(APEX_PACKAGE).files())
          .containsExactly(new File(SYSTEM_APEX_FAKE_APEX_PACKAGE_APEX));
      assertThat(targetMap.get(APK_PACKAGE).files())
          .containsExactly(
              new File(DATA_APP_FAKE_APK_PACKAGE + "/base.apk"),
              new File(DATA_APP_FAKE_APK_PACKAGE + "/aux.apk"));
    } else {
      verify(mockPusher, never()).pushModules(anyMap(), anyMap());
    }
  }

  @Test
  public void perform_installPackages_fail(
      @TestParameter({"APKS", "PACKAGE_FILE", "SPLIT_FOLDER"}) ModuleType type,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, /* devKey= */ true, cleanup, enableRollback);
    when(mockDevice.listPackages())
        .thenReturn(
            ImmutableSortedSet.of(PRELOAD_APK_INFO),
            ImmutableSortedSet.of(PUSHED_APK_INFO),
            ImmutableSortedSet.of(PUSHED_APK_INFO));
    when(mockDevice.listApexPackages())
        .thenReturn(
            ImmutableSortedSet.of(PRELOAD_APEX_INFO),
            ImmutableSortedSet.of(PUSHED_APEX_INFO),
            ImmutableSortedSet.of(PUSHED_APEX_INFO)); // The last data not activated.

    assertThrows(DeviceActionException.class, () -> installMainline.perform());
  }

  @Test
  public void perform_installPackages_throwException(
      @TestParameter({"APKS", "PACKAGE_FILE", "SPLIT_FOLDER"}) ModuleType type,
      @TestParameter boolean devKey,
      @TestParameter boolean cleanup,
      @TestParameter boolean enableRollback)
      throws Exception {
    setUpTestConfig(type, devKey, cleanup, enableRollback);
    // If devKey, need to push and list packages three times.
    if (devKey) {
      when(mockDevice.listPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APK_INFO), ImmutableSortedSet.of(PUSHED_APK_INFO));
      when(mockDevice.listApexPackages())
          .thenReturn(
              ImmutableSortedSet.of(PRELOAD_APEX_INFO), ImmutableSortedSet.of(PUSHED_APEX_INFO));
    } else {
      when(mockDevice.listPackages()).thenReturn(ImmutableSortedSet.of(PRELOAD_APK_INFO));
      when(mockDevice.listApexPackages()).thenReturn(ImmutableSortedSet.of(PRELOAD_APEX_INFO));
    }
    doThrow(DeviceActionException.class)
        .when(mockInstaller)
        .installModules(anyCollection(), anyBoolean());

    assertThrows(DeviceActionException.class, () -> installMainline.perform());
  }

  @Test
  @TestParameters("{deviceSign: true, packageSign: true, expectValue: false}")
  @TestParameters("{deviceSign: true, packageSign: false, expectValue: true}")
  @TestParameters("{deviceSign: false, packageSign: true, expectValue: true}")
  @TestParameters("{deviceSign: false, packageSign: false, expectValue: false}")
  public void needPush_checkAllValues(boolean deviceSign, boolean packageSign, boolean expectValue)
      throws Exception {
    when(mockDevice.devKeySigned()).thenReturn(deviceSign);
    setUpAction(
        InstallMainlineSpec.newBuilder().setDevKeySigned(packageSign).build(),
        ImmutableMultimap.of());

    assertThat(installMainline.needPush()).isEqualTo(expectValue);
  }

  private void setUpAapt(File file) throws DeviceActionException, InterruptedException {
    boolean isApex = file.getName().endsWith(APEX_SUFFIX);
    when(aaptUtil.getPackageInfo(file))
        .thenReturn(
            PackageInfo.builder()
                .setIsApex(isApex)
                .setPackageName(isApex ? APEX_PACKAGE : APK_PACKAGE)
                .setSourceDir(file.getAbsolutePath())
                .setVersionCode(VERSION_CODE)
                .build());
  }

  private void setUpTestConfig(
      ModuleType moduleType, boolean devKey, boolean cleanUp, boolean enableRollback) {
    InstallMainlineSpec spec =
        InstallMainlineSpec.newBuilder()
            .setDevKeySigned(devKey)
            .setCleanUpSessions(cleanUp)
            .setEnableRollback(enableRollback)
            .build();
    ImmutableMultimap.Builder<String, File> filesBuilder = ImmutableMultimap.builder();
    switch (moduleType) {
      case ZIP:
        filesBuilder.putAll("apks_zips", trainZip1, trainZip2);
        break;
      case APKS:
        filesBuilder.put("train_folder", apksFolder);
        break;
      case PACKAGE_FILE:
        filesBuilder.putAll("mainline_modules", apexFile, apkFile);
        break;
      case SPLIT_FOLDER:
        filesBuilder.putAll("train_folder", splitFolder);
        break;
      default:
        break;
    }
    setUpAction(spec, filesBuilder.build());
  }

  private void setUpAction(InstallMainlineSpec spec, ImmutableMultimap<String, File> files) {
    installMainline =
        new InstallMainline(
            mockCleaner,
            mockInstaller,
            mockPusher,
            spec,
            mockDevice,
            aaptUtil,
            localFileUtil,
            files);
  }

  private void verifyCleanupBehavoir(boolean cleanup)
      throws DeviceActionException, InterruptedException {
    if (cleanup) {
      verify(mockCleaner).cleanUpSessions();
    } else {
      verify(mockCleaner, never()).cleanUpSessions();
    }
  }
}
