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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ModuleInstallerTest {

  private static final int VERSION_CODE = 33;
  private static final File train1 = new File("train1.zip");
  private static final File train2 = new File("train2.zip");
  private static final PackageInfo apexPackageInfo =
      PackageInfo.builder()
          .setPackageName("module1")
          .setVersionCode(VERSION_CODE)
          .setSourceDir("system/apex/module1.apex")
          .setIsApex(true)
          .build();
  private static final PackageInfo apkPackageInfo =
      PackageInfo.builder()
          .setPackageName("module2")
          .setVersionCode(VERSION_CODE)
          .setSourceDir("data/app/module2/base.apk")
          .setIsApex(false)
          .build();
  private static final AndroidPackage apexPackage =
      AndroidPackage.builder()
          .setInfo(apexPackageInfo)
          .addFiles(new File("gen-files/module1.apex"))
          .build();
  private static final AndroidPackage apkPackage =
      AndroidPackage.builder()
          .setInfo(apkPackageInfo)
          .setIsSplit(true)
          .addFiles(new File("gen-files/base.apk"), new File("gen-files/split2.apk"))
          .build();
  private static final AndroidPackage apexApksPackage =
      AndroidPackage.builder()
          .setInfo(apexPackageInfo)
          .addFiles(new File("gen-files/module1.apex"))
          .setApksFile(new File("gen-files/module1.apks"))
          .build();
  private static final AndroidPackage apkApksPackage =
      AndroidPackage.builder()
          .setInfo(apkPackageInfo)
          .setIsSplit(true)
          .addFiles(new File("gen-files/base.apk"), new File("gen-files/split2.apk"))
          .setApksFile(new File("gen-files/modules2.apks"))
          .build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidPhone mockDevice;

  @Captor public ArgumentCaptor<List<File>> listArgumentCaptorCaptor;

  @Captor public ArgumentCaptor<Multimap<String, File>> multimapArgumentCaptor;
  private final Sleeper sleeper = Sleeper.noOpSleeper();

  private ModuleInstaller moduleInstaller;

  @Before
  public void setUp() {
    moduleInstaller = new ModuleInstaller(mockDevice, sleeper);
  }

  @Test
  public void installModules_installApks_reboots() throws Exception {
    when(mockDevice.installBundledPackages(anyList(), any())).thenReturn(true);

    moduleInstaller.installModules(
        ImmutableList.of(apexApksPackage, apkApksPackage), /* enableRollback= */ true);

    verify(mockDevice)
        .installBundledPackages(listArgumentCaptorCaptor.capture(), eq("--enable-rollback"));
    assertThat(listArgumentCaptorCaptor.getValue())
        .containsExactly(new File("gen-files/module1.apks"), new File("gen-files/modules2.apks"));
    verify(mockDevice).reboot();
  }

  @Test
  public void installModules_installApkAndApex_reboots() throws Exception {
    when(mockDevice.installPackages(any(), any())).thenReturn(true);

    moduleInstaller.installModules(
        ImmutableList.of(apexPackage, apkPackage), /* enableRollback= */ true);

    verify(mockDevice).installPackages(multimapArgumentCaptor.capture(), eq("--enable-rollback"));
    Multimap<String, File> multimap = multimapArgumentCaptor.getValue();
    assertThat(multimap)
        .valuesForKey("module1")
        .containsExactly(new File("gen-files/module1.apex"));
    assertThat(multimap)
        .valuesForKey("module2")
        .containsExactly(new File("gen-files/base.apk"), new File("gen-files/split2.apk"));
    verify(mockDevice).reboot();
  }

  @Test
  public void installModules_installApksFail_throwsException() throws Exception {
    when(mockDevice.installBundledPackages(anyList(), any()))
        .thenThrow(DeviceActionException.class);

    assertThrows(
        DeviceActionException.class,
        () ->
            moduleInstaller.installModules(
                ImmutableList.of(apexApksPackage, apkApksPackage), /* enableRollback= */ true));
  }

  @Test
  public void sideloadTrains_installTwoTrains_rebootsTwice() throws Exception {
    when(mockDevice.installZippedTrain(any(File.class), any())).thenReturn(true);

    moduleInstaller.sideloadTrains(ImmutableList.of(train1, train2), /* enableRollback= */ true);

    verify(mockDevice).installZippedTrain(eq(train1), eq("--enable-rollback"));
    verify(mockDevice).installZippedTrain(eq(train2), eq("--enable-rollback"));
    verify(mockDevice, times(2)).reboot();
  }

  @Test
  public void sideloadTrains_throwsException() throws Exception {
    when(mockDevice.installZippedTrain(eq(train1), any())).thenThrow(DeviceActionException.class);

    assertThrows(
        DeviceActionException.class,
        () ->
            moduleInstaller.sideloadTrains(
                ImmutableList.of(train1, train2), /* enableRollback= */ true));
  }
}
