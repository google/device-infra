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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.bundletool.Bundletool;
import com.google.devtools.mobileharness.platform.android.lightning.bundletool.ExtractApksArgs;
import com.google.devtools.mobileharness.platform.android.lightning.bundletool.GetDeviceSpecArgs;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.nio.file.Path;
import java.time.Duration;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link AndroidInstallMainlineModulesDecorator}. */
@RunWith(JUnit4.class)
public final class AndroidInstallMainlineModulesDecoratorTest {

  public static final String TAG_BUNDLETOOL = "bundletool";
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String TEST_GEN_DIR = "/usr/local/test/gen/";
  private static final String PROVIDED_BUNDLETOOL_PATH = "/usr/local/bundletool.jar";
  private static final String DEVICE_ID = "device_id";
  private static final String MODULE_1 = "/usr/local/module1.apks";
  private static final String MODULE_2 = "/usr/local/module2.apks";
  private static final ImmutableList<String> APKS_FOR_MODULE_1 =
      ImmutableList.of(
          PathUtil.join(TEST_GEN_DIR, "module1.apks", "base1.apk"),
          PathUtil.join(TEST_GEN_DIR, "module1.apks", "base2.apk"));
  private static final ImmutableList<String> APKS_FOR_MODULE_2 =
      ImmutableList.of(PathUtil.join(TEST_GEN_DIR, "module2.apk", "base.apex"));
  private static final ImmutableSet<String> MODULES = ImmutableSet.of(MODULE_1, MODULE_2);
  private static final ImmutableList<String> ALL_APKS =
      new ImmutableList.Builder<String>()
          .addAll(APKS_FOR_MODULE_1)
          .addAll(APKS_FOR_MODULE_2)
          .build();

  private final Log log = new Log(new Timing());

  @Mock private Driver driver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidPackageManagerUtil androidPackageManagerUtil;
  @Mock private SystemStateManager systemStateManager;
  @Mock private Bundletool bundletool;

  private AndroidInstallMainlineModulesDecorator decorator;
  private Params params;

  @Before
  public void setUp() throws Exception {
    params = new Params(null);

    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir()).thenReturn(TEST_GEN_DIR);
    when(testInfo.log()).thenReturn(log);
    when(jobInfo.files()).thenReturn(files);
    when(jobInfo.params()).thenReturn(params);
    when(driver.getDevice()).thenReturn(device);
    when(device.getDeviceControlId()).thenReturn(DEVICE_ID);
    when(files.isTagNotEmpty(TAG_BUNDLETOOL)).thenReturn(true);
    when(files.getSingle(TAG_BUNDLETOOL)).thenReturn(PROVIDED_BUNDLETOOL_PATH);
    when(files.get("mainline_modules")).thenReturn(MODULES);
    when(localFileUtil.listFilePaths(PathUtil.join(TEST_GEN_DIR, "module1.apks"), true))
        .thenReturn(APKS_FOR_MODULE_1);
    when(localFileUtil.listFilePaths(PathUtil.join(TEST_GEN_DIR, "module2.apks"), true))
        .thenReturn(APKS_FOR_MODULE_2);
    when(bundletool.withCustomBundletoolJar(Path.of(PROVIDED_BUNDLETOOL_PATH)))
        .thenReturn(bundletool);

    decorator =
        new AndroidInstallMainlineModulesDecorator(
            driver,
            testInfo,
            localFileUtil,
            androidPackageManagerUtil,
            systemStateManager,
            bundletool);
  }

  @Test
  public void run_needReboot() throws Exception {
    decorator.run(testInfo);

    verify(bundletool)
        .getDeviceSpec(
            GetDeviceSpecArgs.builder()
                .setDeviceId(DEVICE_ID)
                .setOutput(Path.of(TEST_GEN_DIR, "device-spec.json"))
                .build());
    verify(bundletool)
        .extractApks(
            ExtractApksArgs.builder()
                .setApks(Path.of(MODULE_1))
                .setOutputDir(Path.of(TEST_GEN_DIR, "module1.apks"))
                .setDeviceSpec(Path.of(TEST_GEN_DIR, "device-spec.json"))
                .build());
    verify(bundletool)
        .extractApks(
            ExtractApksArgs.builder()
                .setApks(Path.of(MODULE_2))
                .setOutputDir(Path.of(TEST_GEN_DIR, "module2.apks"))
                .setDeviceSpec(Path.of(TEST_GEN_DIR, "device-spec.json"))
                .build());
    verify(androidPackageManagerUtil)
        .installMultiPackage(DEVICE_ID, ALL_APKS, Duration.ofSeconds(2), Duration.ofMinutes(6));
    verify(systemStateManager).reboot(device, log, null);
  }

  @Test
  public void run_noNeedReboot() throws Exception {
    params.add("reboot_after_installation", "false");

    decorator.run(testInfo);

    verify(systemStateManager, never()).reboot(device, log, null);
  }

  @Test
  public void run_verifyWaitForStagedSessionReadyTimeout() throws Exception {
    params.add("wait_for_staged_session_ready_ms", "3000");

    decorator.run(testInfo);

    verify(androidPackageManagerUtil)
        .installMultiPackage(DEVICE_ID, ALL_APKS, Duration.ofSeconds(3), Duration.ofMinutes(6));
  }

  @Test
  public void run_verifyInstallMultiPackageTimeout() throws Exception {
    params.add("install_multi_package_timeout_min", "10");

    decorator.run(testInfo);

    verify(androidPackageManagerUtil)
        .installMultiPackage(DEVICE_ID, ALL_APKS, Duration.ofSeconds(2), Duration.ofMinutes(10));
  }

  @Test
  public void run_usePackagedBundleToolIfBundletoolTagIsMissing() throws Exception {
    when(files.isTagNotEmpty(TAG_BUNDLETOOL)).thenReturn(false);

    decorator.run(testInfo);

    verify(bundletool, never()).withCustomBundletoolJar(any());
    verify(bundletool)
        .getDeviceSpec(
            GetDeviceSpecArgs.builder()
                .setOutput(Path.of(TEST_GEN_DIR, "device-spec.json"))
                .setDeviceId(DEVICE_ID)
                .build());
  }

  @Test
  public void run_installationVerification() throws Exception {
    params.add("verify_installation", "true");

    SortedSet<PackageInfo> packageInfoSet = new TreeSet<>();
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base1")
            .setVersionCode(123)
            .setSourceDir("/data/apex/decompressed/base1@123.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base2")
            .setVersionCode(124)
            .setSourceDir("/data/apex/decompressed/base2@124.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base")
            .setVersionCode(125)
            .setSourceDir("/data/apex/decompressed/base@125.decompressed.apex")
            .build());

    when(androidPackageManagerUtil.listApexPackageInfos(DEVICE_ID)).thenReturn(packageInfoSet);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn("base1");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn(123);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn("base2");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn(124);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn("base");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn(125);

    decorator.run(testInfo);

    verify(androidPackageManagerUtil).listApexPackageInfos(DEVICE_ID);
    verify(androidPackageManagerUtil)
        .getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk");
    verify(androidPackageManagerUtil)
        .getApkVersionCode("/usr/local/test/gen/module1.apks/base1.apk");
    verify(androidPackageManagerUtil)
        .getApkPackageName("/usr/local/test/gen/module1.apks/base2.apk");
    verify(androidPackageManagerUtil)
        .getApkVersionCode("/usr/local/test/gen/module1.apks/base2.apk");
    verify(androidPackageManagerUtil)
        .getApkPackageName("/usr/local/test/gen/module2.apk/base.apex");
    verify(androidPackageManagerUtil)
        .getApkVersionCode("/usr/local/test/gen/module2.apk/base.apex");
  }

  @Test
  public void run_installationVerification_packageNotInstalledInDataFolder() throws Exception {
    params.add("verify_installation", "true");

    SortedSet<PackageInfo> packageInfoSet = new TreeSet<>();
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base1")
            .setVersionCode(123)
            .setSourceDir("/data/apex/decompressed/base1@123.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base2")
            .setVersionCode(124)
            .setSourceDir("/data/apex/decompressed/base2@124.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base")
            .setVersionCode(125)
            .setSourceDir("/system/apex/decompressed/base@125.decompressed.apex")
            .build());

    when(androidPackageManagerUtil.listApexPackageInfos(DEVICE_ID)).thenReturn(packageInfoSet);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn("base1");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn(123);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn("base2");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn(124);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn("base");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn(125);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(exception.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_MODULE_INSTALLATION_FAILURE);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Failed to install mainline modules: [Package: base is not installed in /data folder."
                + " Installed in /system/apex/decompressed/base@125.decompressed.apex]");
  }

  @Test
  public void run_installationVerification_versionCodeNotMatch() throws Exception {
    params.add("verify_installation", "true");

    SortedSet<PackageInfo> packageInfoSet = new TreeSet<>();
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base1")
            .setVersionCode(123)
            .setSourceDir("/data/apex/decompressed/base1@123.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base2")
            .setVersionCode(124)
            .setSourceDir("/data/apex/decompressed/base2@124.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base")
            .setVersionCode(126)
            .setSourceDir("/system/apex/decompressed/base@125.decompressed.apex")
            .build());

    when(androidPackageManagerUtil.listApexPackageInfos(DEVICE_ID)).thenReturn(packageInfoSet);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn("base1");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn(123);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn("base2");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn(124);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn("base");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn(125);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(exception.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_MODULE_INSTALLATION_FAILURE);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Failed to install mainline modules: [Package: base version mismatch! Expected: 125,"
                + " but got: 126]");
  }

  @Test
  public void run_installationVerification_packageNotInstalled() throws Exception {
    params.add("verify_installation", "true");

    SortedSet<PackageInfo> packageInfoSet = new TreeSet<>();
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base1")
            .setVersionCode(123)
            .setSourceDir("/data/apex/decompressed/base1@123.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base2")
            .setVersionCode(124)
            .setSourceDir("/data/apex/decompressed/base2@124.decompressed.apk")
            .build());

    when(androidPackageManagerUtil.listApexPackageInfos(DEVICE_ID)).thenReturn(packageInfoSet);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn("base1");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenReturn(123);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn("base2");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module1.apks/base2.apk"))
        .thenReturn(124);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn("base");
    when(androidPackageManagerUtil.getApkVersionCode("/usr/local/test/gen/module2.apk/base.apex"))
        .thenReturn(125);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(exception.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_MODULE_INSTALLATION_FAILURE);
    assertThat(exception)
        .hasMessageThat()
        .contains("Failed to install mainline modules: [Package: base is not installed]");
  }

  @Test
  public void run_installationVerification_packageParsingError() throws Exception {
    params.add("verify_installation", "true");

    SortedSet<PackageInfo> packageInfoSet = new TreeSet<>();
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base1")
            .setVersionCode(123)
            .setSourceDir("/data/apex/decompressed/base1@123.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base2")
            .setVersionCode(124)
            .setSourceDir("/data/apex/decompressed/base2@124.decompressed.apk")
            .build());
    packageInfoSet.add(
        PackageInfo.builder()
            .setPackageName("base")
            .setVersionCode(126)
            .setSourceDir("/system/apex/decompressed/base@125.decompressed.apex")
            .build());

    when(androidPackageManagerUtil.listApexPackageInfos(DEVICE_ID)).thenReturn(packageInfoSet);

    when(androidPackageManagerUtil.getApkPackageName("/usr/local/test/gen/module1.apks/base1.apk"))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_AAPT_GET_APK_PACKAGE_NAME_ERROR, ""));

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(exception.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_PACKAGE_INFO_PARSING_ERROR);
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Failed to parse package info from apk file:"
                + " /usr/local/test/gen/module1.apks/base1.apk");
  }
}
