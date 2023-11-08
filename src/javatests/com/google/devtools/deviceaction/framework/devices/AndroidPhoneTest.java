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

package com.google.devtools.deviceaction.framework.devices;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.devtools.deviceaction.framework.devices.AndroidPhone.DEFAULT_AWAIT_FOR_DISCONNECT;
import static com.google.devtools.deviceaction.framework.devices.AndroidPhone.DEFAULT_DEVICE_READY_TIMEOUT;
import static com.google.devtools.deviceaction.framework.devices.AndroidPhone.DEFAULT_REBOOT_TIMEOUT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.protobuf.util.Durations;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public class AndroidPhoneTest {
  private static final long VERSION_CODE = 123L;
  private static final String PACKAGE_NAME = "fake.package";
  private static final String APEX_PACKAGE_NAME = "fake.apex.package";
  private static final String SOURCE_DIR = "/data/app/fake";
  private static final String FAKE_MODULE = "fake module";
  private static final String SDK_VERSION = "31";
  private static final String UUID = "serial";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Captor public ArgumentCaptor<Multimap<String, String>> multimapArgumentCaptor;

  @Mock private AndroidAdbUtil mockAdbUtil;
  @Mock private AndroidFileUtil mockFileUtil;
  @Mock private AndroidPackageManagerUtil mockPackageManagerUtil;
  @Mock private AndroidSystemSettingUtil mockSystemSettingUtil;
  @Mock private AndroidSystemStateUtil mockSystemStateUtil;
  @Mock private BundletoolUtil mockBundletoolUtil;

  enum AndroidPhoneSpecTestCase {
    DEFAULT(AndroidPhoneSpec.newBuilder().setBrand("google").build()),
    STAGE(
        AndroidPhoneSpec.newBuilder()
            .setBrand("google")
            .setStagedReadyTimeout(Durations.fromMinutes(2))
            .setExtraWaitForStaging(Durations.fromMinutes(1))
            .build()),
    REBOOT(
        AndroidPhoneSpec.newBuilder()
            .setBrand("Google")
            .setRebootAwait(Durations.fromSeconds(10))
            .setRebootTimeout(Durations.fromMinutes(3))
            .build()),
    TESTHARNESS(
        AndroidPhoneSpec.newBuilder()
            .setBrand("GOOGLE")
            .setTestharnessBootAwait(Durations.fromSeconds(10))
            .setTestharnessBootTimeout(Durations.fromMinutes(3))
            .build());

    final AndroidPhoneSpec androidPhoneSpec;

    AndroidPhoneSpecTestCase(AndroidPhoneSpec androidPhoneSpec) {
      this.androidPhoneSpec = androidPhoneSpec;
    }
  }

  @TestParameter({"DEFAULT", "STAGE", "REBOOT", "TESTHARNESS"})
  private AndroidPhoneSpecTestCase specTestCase;

  private AndroidPhoneSpec spec;

  private AndroidPhone device;
  private final Sleeper sleeper = Sleeper.noOpSleeper();
  private final PackageInfo packageInfo =
      PackageInfo.builder()
          .setPackageName(PACKAGE_NAME)
          .setSourceDir(SOURCE_DIR)
          .setVersionCode(VERSION_CODE)
          .build();

  @Before
  public void setup() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SDK_VERSION)).thenReturn(SDK_VERSION);
    spec = specTestCase.androidPhoneSpec;
    device =
        new AndroidPhone(
            mockAdbUtil,
            mockFileUtil,
            mockPackageManagerUtil,
            mockSystemSettingUtil,
            mockSystemStateUtil,
            mockBundletoolUtil,
            sleeper,
            UUID,
            spec);
  }

  @Test
  public void getUuid_getExpected() {
    assertThat(device.getUuid()).isEqualTo(UUID);
  }

  @Test
  public void getDeviceType_getExpected() {
    assertThat(device.getDeviceType()).isEqualTo(DeviceType.ANDROID_PHONE);
  }

  @Test
  public void listPackages_getExpected() throws Exception {
    SortedSet<PackageInfo> expected = new TreeSet<>();
    expected.add(packageInfo);
    when(mockPackageManagerUtil.listPackageInfos(UUID)).thenReturn(expected);

    assertThat(device.listPackages()).isEqualTo(expected);
  }

  @Test
  public void listPackages_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listPackageInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listPackages());
  }

  @Test
  public void listApexPackages_getExpected() throws Exception {
    SortedSet<PackageInfo> expected = new TreeSet<>();
    expected.add(packageInfo);
    when(mockPackageManagerUtil.listApexPackageInfos(UUID)).thenReturn(expected);

    assertThat(device.listApexPackages()).isEqualTo(expected);
  }

  @Test
  public void listApexPackages_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listApexPackageInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listApexPackages());
  }

  @Test
  public void listModules_getExpected() throws Exception {
    SortedSet<ModuleInfo> expected = new TreeSet<>();
    expected.add(ModuleInfo.builder().setPackageName(PACKAGE_NAME).setName(FAKE_MODULE).build());
    when(mockPackageManagerUtil.listModuleInfos(UUID)).thenReturn(expected);

    assertThat(device.listModules()).isEqualTo(expected);
  }

  @Test
  public void listModules_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.listModuleInfos(UUID)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listModules());
  }

  @Test
  public void getDeviceSpecFilePath_cacheResults_getExpected() throws Exception {
    String expect = "/file.json";
    when(mockBundletoolUtil.generateDeviceSpecFile(UUID)).thenReturn(Paths.get(expect));

    assertThat(device.getDeviceSpecFilePath().toString()).isEqualTo(expect);
    assertThat(device.getDeviceSpecFilePath().toString()).isEqualTo(expect);
    verify(mockBundletoolUtil).generateDeviceSpecFile(UUID);
  }

  @Test
  public void getDeviceSpecFilePath_throwExceptionFromBundletoolUtil() throws Exception {
    when(mockBundletoolUtil.generateDeviceSpecFile(UUID)).thenThrow(fakeDeviceActionException());

    assertThrows(DeviceActionException.class, () -> device.getDeviceSpecFilePath());
  }

  @Test
  public void getSdkVersion_cacheResults_getExpected() throws Exception {
    int sdkVersion = device.getSdkVersion();
    int sdkVersion1 = device.getSdkVersion();

    assertThat(sdkVersion).isEqualTo(31);
    assertThat(sdkVersion1).isEqualTo(31);
    verify(mockAdbUtil).getProperty(anyString(), any(AndroidProperty.class));
  }

  @Test
  public void getSdkVersion_catchMobileHarnessException_throwException() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SDK_VERSION))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.getSdkVersion());
  }

  @Test
  public void isUserdebug_returnFalse() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.BUILD_TYPE)).thenReturn("user");

    assertFalse(device.isUserdebug());
  }

  @Test
  public void isUserdebug_returnTrue() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.BUILD_TYPE)).thenReturn("userdebug");

    assertTrue(device.isUserdebug());
  }

  @Test
  public void isUserdebug_catchMobileHarnessException_throwException() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.BUILD_TYPE))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.isUserdebug());
  }

  @Test
  public void removeFiles_success() throws Exception {
    device.removeFiles("/data/system/package_cache/");

    verify(mockFileUtil).removeFiles(UUID, "/data/system/package_cache/");
  }

  @Test
  public void removeFiles_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockFileUtil).removeFiles(anyString(), anyString());

    assertThrows(
        DeviceActionException.class, () -> device.removeFiles("/data/system/package_cache/"));
  }

  @Test
  public void listFiles_getExpected() throws Exception {
    String filePath = "/path/to/directory";
    String file1 = "/path/to/directory/file1";
    String file2 = "/path/to/directory/file2";
    SortedSet<String> expect = new TreeSet<>();
    expect.add(file1);
    expect.add(file2);
    when(mockFileUtil.listFilesInOrder(UUID, filePath)).thenReturn(expect);

    SortedSet<String> files = device.listFiles(filePath);

    assertThat(files).containsExactly(file1, file2);
    verify(mockFileUtil).listFilesInOrder(UUID, filePath);
  }

  @Test
  public void listFiles_catchMobileHarnessException_throwException() throws Exception {
    String filePath = "/path/to/directory";
    when(mockFileUtil.listFilesInOrder(UUID, filePath)).thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.listFiles(filePath));
  }

  @Test
  public void getAllInstalledPaths_getExpected() throws Exception {
    ImmutableList<String> paths =
        ImmutableList.of("/data/app/com.example.app/file1", "/data/app/com.example.app/file2");
    when(mockPackageManagerUtil.getAllInstalledPaths(any(), eq(PACKAGE_NAME))).thenReturn(paths);
    ArgumentCaptor<UtilArgs> utilArgsCaptor = ArgumentCaptor.forClass(UtilArgs.class);

    ImmutableList<String> allInstalledPath = device.getAllInstalledPaths(PACKAGE_NAME);
    assertThat(allInstalledPath)
        .containsExactly("/data/app/com.example.app/file1", "/data/app/com.example.app/file2");
    verify(mockPackageManagerUtil).getAllInstalledPaths(utilArgsCaptor.capture(), eq(PACKAGE_NAME));
    assertThat(utilArgsCaptor.getValue().serial()).isEqualTo(UUID);
  }

  @Test
  public void getAllInstalledPaths_catchMobileHarnessException_throwException() throws Exception {
    when(mockPackageManagerUtil.getAllInstalledPaths(any(), eq(PACKAGE_NAME)))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.getAllInstalledPaths(PACKAGE_NAME));
  }

  @Test
  @TestParameters("{containApex: false, expectedMapSize: 2}")
  @TestParameters("{containApex: true, expectedMapSize: 3}")
  public void installPackages_installPackages_succeeds(boolean containApex, int expectedMapSize)
      throws Exception {
    ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
    File file1 = new File("base.apk");
    File file2 = new File("aux.apk");
    builder.putAll(PACKAGE_NAME, file1, file2);
    File file3 = new File("fake.apex");
    if (containApex) {
      builder.put(APEX_PACKAGE_NAME, file3);
    }
    ImmutableMultimap<String, File> packageFiles = builder.build();

    boolean reboot = device.installPackages(packageFiles, "--enable-rollback");

    assertThat(reboot).isEqualTo(containApex);
    ArgumentCaptor<UtilArgs> utilArgsCaptor = ArgumentCaptor.forClass(UtilArgs.class);
    ArgumentCaptor<InstallCmdArgs> installCmdArgsCaptor =
        ArgumentCaptor.forClass(InstallCmdArgs.class);
    ArgumentCaptor<Duration> durationArgumentCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mockPackageManagerUtil)
        .installMultiPackage(
            utilArgsCaptor.capture(),
            installCmdArgsCaptor.capture(),
            multimapArgumentCaptor.capture(),
            durationArgumentCaptor.capture(),
            isNull());
    UtilArgs utilArgs = utilArgsCaptor.getValue();
    InstallCmdArgs installCmdArgs = installCmdArgsCaptor.getValue();
    assertThat(utilArgs.sdkVersion()).hasValue(31);
    assertThat(utilArgs.serial()).isEqualTo(UUID);
    assertThat(installCmdArgs.extraArgs()).contains("--enable-rollback");
    if (spec.hasStagedReadyTimeout()) {
      assertThat(installCmdArgs.extraArgs()).containsAtLeast("--staged-ready-timeout", "120000");
    }
    if (containApex) {
      assertThat(installCmdArgs.extraArgs()).contains("--staged");
    }
    Multimap<String, String> multimap = multimapArgumentCaptor.getValue();
    assertThat(multimap).hasSize(expectedMapSize);
    assertThat(multimap)
        .valuesForKey(PACKAGE_NAME)
        .containsExactly(file1.getAbsolutePath(), file2.getAbsolutePath());
    if (containApex) {
      assertThat(multimap).valuesForKey(APEX_PACKAGE_NAME).containsExactly(file3.getAbsolutePath());
    }
    if (spec.hasExtraWaitForStaging()) {
      assertThat(durationArgumentCaptor.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getExtraWaitForStaging().getSeconds()));
    } else {
      assertNull(durationArgumentCaptor.getValue());
    }
  }

  @Test
  public void installPackages_catchMobileHarnessException_throwsException() throws Exception {
    ImmutableMultimap<String, File> packageFiles =
        ImmutableMultimap.of(
            PACKAGE_NAME,
            new File("base.apk"),
            PACKAGE_NAME,
            new File("aux.apk"),
            APEX_PACKAGE_NAME,
            new File("fake.apex"));
    doThrow(fakeMobileHarnessException())
        .when(mockPackageManagerUtil)
        .installMultiPackage(any(), any(), multimapArgumentCaptor.capture(), any(), any());

    assertThrows(
        DeviceActionException.class,
        () -> device.installPackages(packageFiles, "--enable-rollback"));
  }

  @Test
  public void installBundledPackages_successAndReboot() throws Exception {
    ImmutableList<File> packages =
        ImmutableList.of(new File("package1.apks"), new File("package2.apks"));
    String cmdOutput =
        "The APKs have been extracted in the directory: /tmp/16784512761210886430\n"
            + "INFO: Output:\n"
            + "Created parent session ID 463525912.\n"
            + "Created parent session ID 485684688.\n"
            + "Created child session ID 1348219772.\n"
            + "Success. Reboot device to apply staged session\n"
            + "Apr 11, 2023 11:07:34 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand execute\n"
            + "INFO: Please reboot device to complete installation.";
    when(mockBundletoolUtil.installMultiApks(eq(UUID), eq(packages), any())).thenReturn(cmdOutput);

    assertTrue(device.installBundledPackages(packages, "--enable-rollback"));

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockBundletoolUtil).installMultiApks(eq(UUID), eq(packages), stringCaptor.capture());
    assertThat(stringCaptor.getAllValues()).containsAtLeast("--enable-rollback", "--staged");
    if (spec.hasStagedReadyTimeout()) {
      assertThat(stringCaptor.getAllValues()).contains("--timeout-millis=120000");
    }
  }

  @Test
  public void installBundledPackages_successAndNoReboot() throws Exception {
    ImmutableList<File> packages =
        ImmutableList.of(new File("package1.apks"), new File("package2.apks"));
    String cmdOutput =
        "The APKs have been extracted in the directory: /tmp/5955812264849426382\n"
            + "INFO: Output:\n"
            + "Created parent session ID 388181659.\n"
            + "Created parent session ID 485684688.\n"
            + "Created child session ID 581317030.\n"
            + "Success\n"
            + "Apr 11, 2023 11:15:27 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand execute";
    when(mockBundletoolUtil.installMultiApks(eq(UUID), eq(packages), any())).thenReturn(cmdOutput);

    assertFalse(device.installBundledPackages(packages, "--enable-rollback"));

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockBundletoolUtil).installMultiApks(eq(UUID), eq(packages), stringCaptor.capture());
    assertThat(stringCaptor.getAllValues()).containsAtLeast("--enable-rollback", "--staged");
    if (spec.hasStagedReadyTimeout()) {
      assertThat(stringCaptor.getAllValues()).contains("--timeout-millis=120000");
    }
  }

  @Test
  public void installBundledPackages_installFail_throwsException() throws Exception {
    ImmutableList<File> packages =
        ImmutableList.of(new File("package1.apks"), new File("package2.apks"));
    String cmdOutput =
        "The APKs have been extracted in the directory: /tmp/17269205391452023453\n"
            + "11:17:23 E/SplitApkInstallerBase: Failed to commit install session 2017987441 with"
            + " command cmd package install-commit 2017987441. Error:"
            + " INSTALL_FAILED_DUPLICATE_PACKAGE: Scanning Failed.: com.google.android.art is an"
            + " APEX package and can't be installed as an APK.\n"
            + "[BT:1.8.0] Error: Installation of the app failed.\n"
            + "com.android.tools.build.bundletool.model.exceptions.CommandExecutionException:"
            + " Installation of the app failed.\n"
            + "\tat com.android.tools.build.bundletool.model.exceptions.InternalExceptionBuilder.build(InternalExceptionBuilder.java:57)\n"
            + "\t... 6 more";
    when(mockBundletoolUtil.installMultiApks(eq(UUID), eq(packages), any())).thenReturn(cmdOutput);

    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> device.installBundledPackages(packages, "--enable-rollback"));

    assertThat(t).hasMessageThat().contains(cmdOutput);
  }

  @Test
  public void installZippedTrain_successAndReboot() throws Exception {
    File train = new File("mainline_s.zip");
    String cmdOutput =
        "The APKs have been extracted in the directory: /tmp/6059426715083540522\n"
            + "The APKs have been extracted in the directory: /tmp/10747964627635931626\n"
            + "Apr 12, 2023 8:15:09 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand"
            + " extractApkListFromApks\n"
            + "INFO: Extracting package 'com.google.android.os.statsd'\n"
            + "The APKs have been extracted in the directory:"
            + " /tmp/14831784731443690324/com.google.android.os.statsd\n"
            + "Apr 12, 2023 8:15:09 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand"
            + " extractApkListFromApks\n"
            + "INFO: Extracting package 'com.google.mainline.telemetry'\n"
            + "The APKs have been extracted in the directory:"
            + " /tmp/14831784731443690324/com.google.mainline.telemetry\n"
            + "Apr 12, 2023 8:15:09 PM"
            + " com.android.tools.build.bundletool.androidtools.AdbCommand$DefaultAdbCommand"
            + " installMultiPackage\n"
            + "INFO: Executing:"
            + " /usr/local/google/mobileharness/mh_res_files/devtools/mobileharness/platform/android/sdktool/binary/platform-tools/adb"
            + " install-multi-package --enable-rollback"
            + " /tmp/14831784731443690324/com.google.android.os.statsd/standalone-armeabi_v7a.arm64_v8a.apex"
            + " /tmp/14831784731443690324/com.google.mainline.telemetry/base-master.apk\n"
            + "Apr 12, 2023 8:15:10 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand execute\n"
            + "INFO: Output:\n"
            + "Created parent session ID 60961047.\n"
            + "Created child session ID 1796838565.\n"
            + "Created child session ID 1077735626.\n"
            + "Success. Reboot device to apply staged session\n"
            + "Apr 12, 2023 8:15:10 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand execute\n"
            + "INFO: Please reboot device to complete installation.";
    when(mockBundletoolUtil.installApksZip(eq(UUID), eq(train), any())).thenReturn(cmdOutput);

    assertTrue(device.installZippedTrain(train, "--enable-rollback"));

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockBundletoolUtil).installApksZip(eq(UUID), eq(train), stringCaptor.capture());
    assertThat(stringCaptor.getAllValues()).containsAtLeast("--enable-rollback", "--staged");
    if (spec.hasStagedReadyTimeout()) {
      assertThat(stringCaptor.getAllValues()).contains("--timeout-millis=120000");
    }
  }

  @Test
  public void installZippedTrain_installFail_throwsException() throws Exception {
    File train = new File("mainline_s.zip");
    String cmdOutput =
        "The APKs have been extracted in the directory: /tmp/10525201165219864343\n"
            + "Apr 04, 2023 3:37:35 PM"
            + " com.android.tools.build.bundletool.commands.InstallMultiApksCommand"
            + " extractApkListFromApks\n"
            + "INFO: Extracting package 'com.google.android.media'\n"
            + "The APKs have been extracted in the directory:"
            + " /tmp/3874078813899838452/com.google.android.media\n"
            + "Apr 04, 2023 3:37:35 PM"
            + " com.android.tools.build.bundletool.androidtools.AdbCommand$DefaultAdbCommand"
            + " installMultiPackage\n"
            + "INFO: Executing:"
            + " /usr/local/google/mobileharness/mh_res_files/devtools/mobileharness/platform/android/sdktool/binary/platform-tools/adb"
            + " install-multi-package --staged"
            + " /tmp/3874078813899838452/com.google.android.media/standalone-armeabi_v7a.arm64_v8a.apex\n"
            + "adb: failed to finalize session\n"
            + "Error [-22] [apexd verification failed : Cannot verify ApexVerity of compressed"
            + " APEX]\n"
            + "Attempting to abandon session ID ...\n"
            + "[BT:1.8.0] Error: Command"
            + " '[/usr/local/google/mobileharness/mh_res_files/devtools/mobileharness/platform/android/sdktool/binary/platform-tools/adb,"
            + " install-multi-package, --staged,"
            + " /tmp/3874078813899838452/com.google.android.media/standalone-armeabi_v7a.arm64_v8a.apex]'"
            + " didn't terminate successfully (exit code: 1). Check the logs.\n"
            + "com.android.tools.build.bundletool.model.exceptions.CommandExecutionException:"
            + " Command"
            + " '[/usr/local/google/mobileharness/mh_res_files/devtools/mobileharness/platform/android/sdktool/binary/platform-tools/adb,"
            + " install-multi-package, --staged,"
            + " /tmp/3874078813899838452/com.google.android.media/standalone-armeabi_v7a.arm64_v8a.apex]'"
            + " didn't terminate successfully (exit code: 1). Check the logs.\n"
            + "\tat com.android.tools.build.bundletool.model.exceptions.InternalExceptionBuilder.build(InternalExceptionBuilder.java:57)\n"
            + "\t...";
    when(mockBundletoolUtil.installApksZip(eq(UUID), eq(train), any())).thenReturn(cmdOutput);

    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> device.installZippedTrain(train, "--enable-rollback"));

    assertThat(t).hasMessageThat().contains(cmdOutput);
  }

  @Test
  public void reboot_rebootAndWait() throws Exception {
    device.reboot();

    ArgumentCaptor<Duration> durationArgumentCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mockSystemStateUtil).reboot(UUID, RebootMode.SYSTEM_IMAGE);
    verify(mockSystemStateUtil).waitUntilReady(eq(UUID), durationArgumentCaptor.capture());
    if (spec.hasRebootTimeout()) {
      assertThat(durationArgumentCaptor.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getRebootTimeout().getSeconds()));
    } else {
      assertThat(durationArgumentCaptor.getValue()).isEqualTo(DEFAULT_DEVICE_READY_TIMEOUT);
    }
  }

  @Test
  public void reboot_catchMobileHarnessException_throwsException() throws Exception {
    doThrow(fakeMobileHarnessException())
        .when(mockSystemStateUtil)
        .reboot(UUID, RebootMode.SYSTEM_IMAGE);

    assertThrows(DeviceActionException.class, () -> device.reboot());
  }

  @Test
  public void reboot_successInDifferentModes(@TestParameter RebootMode mode) throws Exception {
    device.reboot(mode);

    ArgumentCaptor<Duration> disconnectTimeCaptor = ArgumentCaptor.forClass(Duration.class);
    ArgumentCaptor<Duration> waitTimeCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mockSystemStateUtil).reboot(UUID, mode);
    verify(mockSystemStateUtil)
        .waitForState(
            eq(UUID), eq(DeviceConnectionState.DISCONNECT), disconnectTimeCaptor.capture());
    if (spec.hasRebootAwait()) {
      assertThat(disconnectTimeCaptor.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getRebootAwait().getSeconds()));
    } else {
      assertThat(disconnectTimeCaptor.getValue()).isEqualTo(DEFAULT_AWAIT_FOR_DISCONNECT);
    }
    if (!mode.equals(RebootMode.BOOTLOADER)) {
      verify(mockSystemStateUtil)
          .waitForState(eq(UUID), eq(mode.getTargetState()), waitTimeCaptor.capture());
      if (spec.hasRebootTimeout()) {
        assertThat(waitTimeCaptor.getValue())
            .isEqualTo(Duration.ofSeconds(spec.getRebootTimeout().getSeconds()));
      } else {
        assertThat(waitTimeCaptor.getValue()).isEqualTo(DEFAULT_REBOOT_TIMEOUT);
      }
    }
    if (mode.equals(RebootMode.SYSTEM_IMAGE)) {
      verify(mockSystemStateUtil).waitUntilReady(eq(UUID), waitTimeCaptor.capture());
    }
  }

  @Test
  public void reboot_inDifferentModes_throwsException(@TestParameter RebootMode mode)
      throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockSystemStateUtil).reboot(UUID, mode);

    assertThrows(DeviceActionException.class, () -> device.reboot(mode));
  }

  @Test
  public void enableTestharness_factoryResetAndWait() throws Exception {
    device.enableTestharness();

    ArgumentCaptor<Duration> captor1 = ArgumentCaptor.forClass(Duration.class);
    ArgumentCaptor<Duration> captor2 = ArgumentCaptor.forClass(Duration.class);
    verify(mockSystemStateUtil).factoryResetViaTestHarness(eq(UUID), captor1.capture());
    if (spec.hasTestharnessBootAwait()) {
      assertThat(captor1.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getTestharnessBootAwait().getSeconds()));
    } else {
      assertNull(captor1.getValue());
    }
    verify(mockSystemStateUtil).waitUntilReady(eq(UUID), captor2.capture());
    if (spec.hasTestharnessBootTimeout()) {
      assertThat(captor2.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getTestharnessBootTimeout().getSeconds()));
    } else {
      assertThat(captor2.getValue()).isEqualTo(DEFAULT_DEVICE_READY_TIMEOUT);
    }
  }

  @Test
  public void enableTestharness_catchMobileHarnessException_throwsException() throws Exception {
    doThrow(fakeMobileHarnessException())
        .when(mockSystemStateUtil)
        .factoryResetViaTestHarness(eq(UUID), any());

    assertThrows(DeviceActionException.class, () -> device.enableTestharness());
  }

  @Test
  public void softReboot_rebootAndWait() throws Exception {
    device.softReboot();

    ArgumentCaptor<Duration> durationArgumentCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mockSystemStateUtil).softReboot(UUID);
    verify(mockSystemStateUtil).waitUntilReady(eq(UUID), durationArgumentCaptor.capture());
    if (spec.hasRebootTimeout()) {
      assertThat(durationArgumentCaptor.getValue())
          .isEqualTo(Duration.ofSeconds(spec.getRebootTimeout().getSeconds()));
    } else {
      assertThat(durationArgumentCaptor.getValue()).isEqualTo(DEFAULT_DEVICE_READY_TIMEOUT);
    }
  }

  @Test
  public void softReboot_catchMobileHarnessException_throwsException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockSystemStateUtil).softReboot(anyString());

    assertThrows(DeviceActionException.class, () -> device.softReboot());
  }

  @Test
  public void sideload_success() throws Exception {
    File otaPackage = new File("fake.zip");

    device.sideload(otaPackage, Duration.ofSeconds(10), Duration.ofSeconds(1));

    verify(mockSystemStateUtil)
        .sideload(UUID, otaPackage, Duration.ofSeconds(10), Duration.ofSeconds(1));
  }

  @Test
  public void sideload_catchMobileHarnessException_throwException() throws Exception {
    File otaPackage = new File("fake.zip");
    doThrow(fakeMobileHarnessException())
        .when(mockSystemStateUtil)
        .sideload(UUID, otaPackage, Duration.ofSeconds(10), Duration.ofSeconds(1));

    assertThrows(
        DeviceActionException.class,
        () -> device.sideload(otaPackage, Duration.ofSeconds(10), Duration.ofSeconds(1)));
  }

  @Test
  public void waitUntilReady(@TestParameter RebootMode mode) throws Exception {
    Duration timeout = Duration.ofSeconds(10);
    device.waitUntilReady(mode, timeout);

    if (!mode.equals(RebootMode.BOOTLOADER)) {
      verify(mockSystemStateUtil).waitForState(UUID, mode.getTargetState(), timeout);
    }
    if (mode.equals(RebootMode.SYSTEM_IMAGE)) {
      verify(mockSystemStateUtil).waitUntilReady(UUID, timeout);
    }
  }

  @Test
  public void becomeRoot_callSystemStateUtil() throws Exception {
    device.becomeRoot();

    verify(mockSystemStateUtil).becomeRoot(UUID);
  }

  @Test
  public void becomeRoot_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockSystemStateUtil).becomeRoot(UUID);

    assertThrows(DeviceActionException.class, () -> device.becomeRoot());
  }

  @Test
  public void push_success() throws Exception {
    Path srcOnHost = Paths.get("src/main/java/com/example/app/MyClass.java");
    Path desOnDevice = Path.of("/data/local/tmp/MyClass.java");

    device.push(srcOnHost, desOnDevice);

    verify(mockFileUtil).push(UUID, 31, srcOnHost.toString(), desOnDevice.toString());
  }

  @Test
  public void push_catchMobileHarnessException_throwException() throws Exception {
    Path srcOnHost = Paths.get("src/main/java/com/example/app/MyClass.java");
    Path desOnDevice = Path.of("/data/local/tmp/MyClass.java");
    doThrow(fakeMobileHarnessException())
        .when(mockFileUtil)
        .push(eq(UUID), eq(31), anyString(), anyString());

    assertThrows(DeviceActionException.class, () -> device.push(srcOnHost, desOnDevice));
  }

  @Test
  public void remount_success() throws Exception {
    device.remount();

    verify(mockSystemStateUtil).becomeRoot(UUID);
    verify(mockFileUtil).remount(UUID, true);
  }

  @Test
  public void remount_catchMobileHarnessException_throwException() throws Exception {
    doThrow(fakeMobileHarnessException()).when(mockFileUtil).remount(UUID, true);

    assertThrows(DeviceActionException.class, () -> device.remount());
  }

  @Test
  public void disableVerity_successAndReboot() throws Exception {
    when(mockSystemSettingUtil.setDmVerityChecking(UUID, false))
        .thenReturn(PostSetDmVerityDeviceOp.REBOOT);

    device.disableVerity();

    verify(mockSystemSettingUtil).setDmVerityChecking(UUID, false);
    verify(mockSystemStateUtil).reboot(UUID, RebootMode.SYSTEM_IMAGE);
  }

  @Test
  public void disableVerity_catchMobileHarnessException_throwsException() throws Exception {
    when(mockSystemSettingUtil.setDmVerityChecking(UUID, false))
        .thenThrow(fakeMobileHarnessException());

    assertThrows(DeviceActionException.class, () -> device.disableVerity());
  }

  @Test
  @TestParameters("{sign: dev-keys, expectedReturn: true}")
  @TestParameters("{sign: release-keys, expectedReturn: false}")
  public void devKeySigned_getExpectedValue(String sign, boolean expectedReturn) throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.SIGN)).thenReturn(sign);

    boolean actural = device.devKeySigned();

    assertThat(actural).isEqualTo(expectedReturn);
  }

  private static MobileHarnessException fakeMobileHarnessException() {
    return new MobileHarnessException(BasicErrorId.SYSTEM_INVALID_PROCESS_ID, "message");
  }

  private static DeviceActionException fakeDeviceActionException() {
    return new DeviceActionException("FAKE", ErrorType.CUSTOMER_ISSUE, "message");
  }
}
