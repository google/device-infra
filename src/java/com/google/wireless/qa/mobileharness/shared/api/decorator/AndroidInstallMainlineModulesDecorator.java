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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
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
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstallMainlineModulesDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.inject.Inject;

/**
 * An Android decorator that installs mainline modules Only works for Android devices with SDK
 * version >= 29.
 */
@DecoratorAnnotation(help = "Decorator that installs Android Mainline modules.")
public class AndroidInstallMainlineModulesDecorator extends LifecycleDecorator
    implements AndroidInstallMainlineModulesDecoratorSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String APKS_FILE_EXTENSION = ".apks";

  private final String deviceId;
  private final LocalFileUtil localFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final SystemStateManager systemStateManager;

  private Bundletool bundletool;

  @Inject
  AndroidInstallMainlineModulesDecorator(
      Driver decoratorDriver,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      SystemStateManager systemStateManager,
      Bundletool bundletool) {
    super(decoratorDriver, testInfo);
    deviceId = decoratorDriver.getDevice().getDeviceControlId();
    this.localFileUtil = localFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.systemStateManager = systemStateManager;
    this.bundletool = bundletool;
  }

  @Override
  protected void setUp(SetupContext context) throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (testInfo.jobInfo().files().isTagNotEmpty(TAG_BUNDLETOOL_FILE)) {
      bundletool =
          bundletool.withCustomBundletoolJar(
              Path.of(testInfo.jobInfo().files().getSingle(TAG_BUNDLETOOL_FILE)));
    }
    installModules(testInfo);
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {}

  private void installModules(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String genFileDirRoot = testInfo.getGenFileDir();

    ImmutableSet<String> modules = testInfo.jobInfo().files().get(TAG_MODULE_FILES);
    testInfo.log().atInfo().alsoTo(logger).log("Going to install mainline modules %s", modules);

    // Gets device spec.
    Path deviceSpecOutput = Path.of(genFileDirRoot, "device-spec.json");
    if (needExtractApks(modules)) {
      logger.atInfo().log("Getting device spec to %s", deviceSpecOutput);
      bundletool.getDeviceSpec(
          GetDeviceSpecArgs.builder().setOutput(deviceSpecOutput).setDeviceId(deviceId).build());
    }

    List<String> allApks = new ArrayList<>();
    for (String module : modules) {
      String extractOutputDir = PathUtil.join(genFileDirRoot, PathUtil.basename(module));
      localFileUtil.prepareDir(extractOutputDir);

      if (!module.endsWith(APKS_FILE_EXTENSION)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Module %s is not an .apks file, no need to extract it", module);
        localFileUtil.copyFileOrDir(module, extractOutputDir);
        allApks.add(PathUtil.join(extractOutputDir, PathUtil.basename(module)));
        continue;
      }

      // Extracts apks.
      testInfo.log().atInfo().alsoTo(logger).log("Extract apks from module %s", module);
      bundletool.extractApks(
          ExtractApksArgs.builder()
              .setApks(Path.of(module))
              .setOutputDir(Path.of(extractOutputDir))
              .setDeviceSpec(deviceSpecOutput)
              .build());

      List<String> apks = localFileUtil.listFilePaths(extractOutputDir, /* recursively= */ true);
      testInfo.log().atInfo().alsoTo(logger).log("Extracted %s from module %s", apks, module);
      allApks.addAll(apks);
    }

    // Installs apks.
    testInfo.log().atInfo().alsoTo(logger).log("Installing apks %s", allApks);
    androidPackageManagerUtil.installMultiPackage(
        deviceId,
        allApks,
        Duration.ofMillis(
            testInfo
                .jobInfo()
                .params()
                .getLong(
                    WAIT_FOR_STAGED_SESSION_READY_MS,
                    DEFAULT_WAIT_FOR_STAGED_SESSION_READY.toMillis())),
        Duration.ofMinutes(
            testInfo
                .jobInfo()
                .params()
                .getLong(
                    INSTALL_MULTI_PACKAGE_TIMEOUT_MIN,
                    DEFAULT_INSTALL_MULTI_PACKAGE_TIMEOUT.toMinutes())));

    // TODO: Auto detect whether the reboot is needed.
    if (testInfo
        .jobInfo()
        .params()
        .getBool(REBOOT_AFTER_INSTALLATION, DEFAULT_REBOOT_AFTER_INSTALLATION)) {
      systemStateManager.reboot(getDevice(), testInfo.log(), /* deviceReadyTimeout= */ null);
    }

    if (testInfo.jobInfo().params().getBool(VERIFY_INSTALLATION, DEFAULT_VERIFY_INSTALLATION)) {
      verifyInstallation(testInfo, allApks);
    }
  }

  private boolean needExtractApks(Collection<String> modules) {
    return modules.stream().anyMatch(module -> module.endsWith(APKS_FILE_EXTENSION));
  }

  private void verifyInstallation(TestInfo testInfo, List<String> allApks)
      throws MobileHarnessException, InterruptedException {
    // 1. Get all installed modules version on the device.
    ImmutableMap<String, Entry<String, Long>> moduleInfo =
        androidPackageManagerUtil.listApexPackageInfos(deviceId).stream()
            .collect(
                toImmutableMap(
                    PackageInfo::packageName,
                    p -> new SimpleEntry<>(p.sourceDir(), p.versionCode()),
                    (entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? entry1 : entry2));

    List<String> installationFailureModuleDetails = new ArrayList<>();
    for (String apkFile : allApks) {
      String packageName;
      long versionCode;

      try {
        // 2. Get module name and version code from apk/apex file.
        packageName = androidPackageManagerUtil.getApkPackageName(apkFile);
        versionCode = androidPackageManagerUtil.getApkVersionCode(apkFile);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Package name: %s, version code: %d, in file: %s",
                packageName, versionCode, apkFile);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_PACKAGE_INFO_PARSING_ERROR,
            String.format(
                "Failed to parse package info from apk file: %s. Error: %s",
                apkFile, e.getMessage()));
      }

      Entry<String, Long> installedModuleInfo = moduleInfo.get(packageName);
      if (installedModuleInfo == null) {
        installationFailureModuleDetails.add(
            String.format("Package: %s is not installed", packageName));
        continue;
      }

      // Check if the installed module version code matches the expected version.
      Long installedVersionCode = installedModuleInfo.getValue();
      if (!Objects.equals(installedVersionCode, versionCode)) {
        installationFailureModuleDetails.add(
            String.format(
                "Package: %s version mismatch! Expected: %d, but got: %d",
                packageName, versionCode, installedVersionCode));
        continue;
      }

      // Check if the installed module is in /data folder.
      String installationPath = installedModuleInfo.getKey();
      if (!installationPath.startsWith("/data")) {
        installationFailureModuleDetails.add(
            String.format(
                "Package: %s is not installed in /data folder. Installed in %s",
                packageName, installationPath));
      }
    }

    if (!installationFailureModuleDetails.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTALL_MAINLINE_MODULES_DECORATOR_MODULE_INSTALLATION_FAILURE,
          String.format(
              "Failed to install mainline modules: %s", installationFailureModuleDetails));
    }
  }
}
