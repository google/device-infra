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

package com.google.wireless.qa.mobileharness.shared.api.step.android;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.event.util.AppInstallEventUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AppOperationMode;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.ApkInfo;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AppInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.Google3File;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.InstallApkStepSpec;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility methods of apk installation for drivers. */
public class InstallApkStep implements InstallApkStepConstants {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String APP_OP_MANAGE_EXTERNAL_STORAGE = "MANAGE_EXTERNAL_STORAGE";

  /** Interface to deal with app versions for AndroidAppVersionDecorator. */
  @FunctionalInterface
  public interface InstallSuccessHandler {
    void handle(String packageName, String buildApks)
        throws MobileHarnessException, InterruptedException;
  }

  /** {@code Aapt} for AAPT operations. */
  private final Aapt aapt;

  /** {@code LocalFileUtil} for reading apk size. */
  private final LocalFileUtil localFileUtil;

  /** {@code TestMessageUtil} for broadcasting events while starting/finishing installing an app. */
  private final TestMessageUtil testMessageUtil;

  private final ApkInstaller apkInstaller;

  private final SystemSettingManager systemSettingManager;

  private final AndroidUserUtil androidUserUtil;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final SystemStateManager systemStateManager;

  private final ChecksumUtil checksumUtil;

  @Inject
  InstallApkStep(
      Aapt aapt,
      LocalFileUtil localFileUtil,
      TestMessageUtil testMessageUtil,
      ApkInstaller apkInstaller,
      SystemSettingManager systemSettingManager,
      AndroidUserUtil androidUserUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      SystemStateManager systemStateManager,
      ChecksumUtil checksumUtil) {
    this.aapt = aapt;
    this.localFileUtil = localFileUtil;
    this.testMessageUtil = testMessageUtil;
    this.apkInstaller = apkInstaller;
    this.systemSettingManager = systemSettingManager;
    this.androidUserUtil = androidUserUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.systemStateManager = systemStateManager;
    this.checksumUtil = checksumUtil;
  }

  /**
   * Installs the build apks, which are the apps under test.
   *
   * @return the package names of the build apks
   * @throws MobileHarnessException if some error occurs
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public List<String> installBuildApks(Device device, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    return installBuildApks(
        device,
        testInfo,
        testInfo.jobInfo().files().get(TAG_BUILD_APK),
        /* installSuccessHandler= */ null,
        /* spec= */ null);
  }

  @CanIgnoreReturnValue
  public List<String> installBuildApks(Device device, TestInfo testInfo, InstallApkStepSpec spec)
      throws MobileHarnessException, InterruptedException {
    return installBuildApks(
        device,
        testInfo,
        getBuildApks(spec, testInfo.jobInfo()),
        /* installSuccessHandler= */ null,
        spec);
  }

  /**
   * Installs the build apks, which are the apps under test. And gets version and size info of each
   * package, including Gms core.
   *
   * @return the package names of the build apks
   * @throws MobileHarnessException if some error occurs
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public List<String> installBuildApks(
      Device device,
      TestInfo testInfo,
      ImmutableSet<String> jobBuildApks,
      @Nullable InstallSuccessHandler installSuccessHandler,
      @Nullable InstallApkStepSpec spec)
      throws MobileHarnessException, InterruptedException {
    if (spec == null) {
      spec = createInstallApkStepSpec(testInfo.jobInfo());
    }

    String deviceId = device.getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    Set<String> buildApks = new LinkedHashSet<>();

    // Makes sure the first apk in 'build_apk' will still be the first element of buildApks.
    addApks(buildApks, jobBuildApks, testInfo);
    addApks(buildApks, testInfo.files().get(TAG_BUILD_APK), testInfo);
    addApks(buildApks, testInfo.files().get(TAG_EXTRA_APK), testInfo);
    addApks(buildApks, getExtraApks(spec, jobInfo), testInfo);
    String extraFileTags = spec.getInstallApkExtraFileTags();
    for (String fileTag : Splitter.on(',').split(extraFileTags)) {
      testInfo.log().atInfo().alsoTo(logger).log("Get extra tag %s", fileTag);
      addApks(buildApks, testInfo.files().get(fileTag), testInfo);
      addApks(buildApks, jobInfo.files().get(fileTag), testInfo);
    }

    ImmutableSet<String> dexMetadataFiles = getDexMetadataFiles(spec, testInfo);
    if (!dexMetadataFiles.isEmpty()) {
      testInfo.log().atInfo().alsoTo(logger).log("Add dex metadata files %s", dexMetadataFiles);
    }
    ImmutableMap<String, String> dexMetadataFilesByNameMap =
        getDexMetadataFilesByName(dexMetadataFiles);
    Set<String> matchedDexMetadataFiles = new HashSet<>();

    Optional<Duration> sleepAfterInstallGms = Optional.empty();
    Optional<Duration> installTimeout = Optional.empty();
    if (spec.hasInstallApkTimeoutSec()) {
      long installTimeoutSec = spec.getInstallApkTimeoutSec();
      installTimeout = Optional.of(Duration.ofSeconds(installTimeoutSec));
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Parsed install APK timeout from job: %s sec, Device ID = %s",
              installTimeoutSec, deviceId);
    }

    boolean broadcastInstallMessage = spec.getBroadcastInstallMessage();
    boolean grantPermissionsOnInstall = spec.getGrantPermissionsOnInstall();
    boolean skipGmsDowngrade = spec.getSkipGmsDowngrade();
    boolean clearGmsAppData = spec.getClearGmsAppData();
    boolean forceInstallApks = spec.getForceInstallApks();
    boolean bypassLowTargetSdkBlock = spec.getBypassLowTargetSdkBlock();
    boolean skipApkDowngrade = spec.getSkipApkDowngrade();

    SetMultimap<String, String> allPackages = LinkedHashMultimap.create();
    Set<String> allPackageNames = new HashSet<>();
    for (String buildApk : buildApks) {
      // Gets the package name from the build apks.
      String buildPackageName = aapt.getApkPackageName(buildApk);
      if (forceInstallApks && !allPackageNames.contains(buildPackageName)) {
        apkInstaller.clearInstalledApkProperty(device, buildPackageName);
      }
      if (testInfo.properties().get(ApkInfo.MAIN_PACKAGE_NAME) == null) {
        testInfo.properties().add(ApkInfo.MAIN_PACKAGE_NAME, buildPackageName);
        testInfo.properties().add(AppInfo.AUT_ID, buildPackageName);
      }
      allPackages.put(buildPackageName, buildApk);
      allPackageNames.add(buildPackageName);
    }

    int deviceSdkVersion = systemSettingManager.getDeviceSdkVersion(device);
    // Install GMS if provided in build apks. GMS Core is installed first because it creates
    // new permissions. APKs that define permissions must precede the APKs that use them.
    // b/149046112 b/36941003
    if (allPackages.containsKey(PackageConstants.PACKAGE_NAME_GMS)) {
      Set<String> apkPaths = allPackages.get(PackageConstants.PACKAGE_NAME_GMS);
      if (apkPaths.size() > 1) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_APK_STEP_INSTALL_NOT_SUPPORTED,
            "GMS Core is not supported as a split APK.");
      }
      String buildApk = Iterables.getOnlyElement(apkPaths);
      Optional<String> dexMetadataFile =
          getMatchingDexMetadataFile(buildApk, dexMetadataFilesByNameMap);
      dexMetadataFile.ifPresent(matchedDexMetadataFiles::add);

      if (spec.hasSleepAfterInstallGmsSec()) {
        long sleepAfterInstallGmsSec = spec.getSleepAfterInstallGmsSec();
        sleepAfterInstallGms = Optional.of(Duration.ofSeconds(sleepAfterInstallGmsSec));
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Parsed sleep after install GMS from job: %s sec, Device ID = %s",
                sleepAfterInstallGmsSec, deviceId);
      }

      installSingleApk(
          device,
          deviceSdkVersion,
          PackageConstants.PACKAGE_NAME_GMS,
          buildApk,
          dexMetadataFile,
          testInfo,
          broadcastInstallMessage,
          skipGmsDowngrade,
          clearGmsAppData,
          grantPermissionsOnInstall,
          bypassLowTargetSdkBlock,
          installTimeout,
          sleepAfterInstallGms,
          skipApkDowngrade);

      if (installSuccessHandler != null) {
        installSuccessHandler.handle(PackageConstants.PACKAGE_NAME_GMS, buildApk);
      }
    } else if (testInfo
        .jobInfo()
        .params()
        .getBool(
            InstallApkStepConstants.PARAM_CHECK_INSTALLED_GMS_CORE_VERSION,
            /* defaultValue= */ true)) {
      // Gets Gms version if it is not in build apks.
      apkInstaller.checkInstalledAppVersion(
          testInfo, deviceId, PackageConstants.PACKAGE_NAME_GMS, null);
    }

    // Install non-GMS app bundles.
    SetMultimap<String, String> remainToInstall =
        Multimaps.filterKeys(
            allPackages, pkg -> !Objects.equals(pkg, PackageConstants.PACKAGE_NAME_GMS));
    SetMultimap<String, String> splitPackages =
        Multimaps.filterKeys(remainToInstall, pkg -> allPackages.get(pkg).size() > 1);
    if (deviceSdkVersion > AndroidVersion.PI.getEndSdkVersion() && !splitPackages.isEmpty()) {
      for (Map.Entry<String, Collection<String>> entry : splitPackages.asMap().entrySet()) {
        String packageName = entry.getKey();
        Collection<String> apkPaths = entry.getValue();
        installMultiPackages(
            device,
            packageName,
            apkPaths,
            deviceSdkVersion,
            testInfo,
            broadcastInstallMessage,
            grantPermissionsOnInstall,
            installTimeout);
      }
      remainToInstall =
          Multimaps.filterKeys(remainToInstall, pkg -> !splitPackages.containsKey(pkg));
    }

    // Install non-GMS apks individually.
    ImmutableMap<String, String> singlePackages;
    // Convert the multimap with single apk entries to a map.
    try {
      singlePackages =
          remainToInstall.entries().stream()
              .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException is thrown when there is duplicated key.
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTALL_APK_STEP_INSTALL_NOT_SUPPORTED,
          String.format(
              "The packages %s contain multiple apks with the same package name."
                  + "If it is your intention to install split apks, please make"
                  + " sure the device SDK version is >= 29.",
              remainToInstall),
          e);
    }
    if (!singlePackages.isEmpty()) {
      for (Map.Entry<String, String> entry : singlePackages.entrySet()) {
        // Gets the package name from the build apks.
        String buildPackageName = entry.getKey();
        String buildApk = entry.getValue();
        Optional<String> dexMetadataFile =
            getMatchingDexMetadataFile(buildApk, dexMetadataFilesByNameMap);
        dexMetadataFile.ifPresent(matchedDexMetadataFiles::add);

        installSingleApk(
            device,
            deviceSdkVersion,
            buildPackageName,
            buildApk,
            dexMetadataFile,
            testInfo,
            broadcastInstallMessage,
            skipGmsDowngrade,
            clearGmsAppData,
            grantPermissionsOnInstall,
            bypassLowTargetSdkBlock,
            installTimeout,
            sleepAfterInstallGms,
            skipApkDowngrade);

        if (installSuccessHandler != null) {
          installSuccessHandler.handle(buildPackageName, buildApk);
        }
      }
    }
    if (!matchedDexMetadataFiles.equals(dexMetadataFiles)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTALL_APK_STEP_DEX_METADATA_WITHOUT_APK,
          String.format(
              "Dex metadata files %s did not match any installed apks. Each dex metadata file must"
                  + " be installed with an apk with the same file name.",
              Sets.difference(dexMetadataFiles, matchedDexMetadataFiles)));
    }

    if (spec.getRebootAfterAllBuildApksInstallation()) {
      rebootDevice(testInfo, device);
    }

    return new ArrayList<>(allPackageNames);
  }

  private void installSingleApk(
      Device device,
      int deviceSdkVersion,
      String packageName,
      String apkPath,
      Optional<String> dexMetadataPath,
      TestInfo testInfo,
      boolean broadcastInstallMessage,
      boolean skipGmsDowngrade,
      boolean clearGmsAppData,
      boolean grantPermissionsOnInstall,
      boolean bypassLowTargetSdkBlock,
      Optional<Duration> installTimeout,
      Optional<Duration> sleepAfterInstallGms,
      boolean skipApkDowngrade)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      if (broadcastInstallMessage) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createStartMessage(device.getDimensions(), packageName));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .withCause(e)
              .alsoTo(logger)
              .log("Failed to broadcast message for starting installing app");
        }
      }
      boolean isGms = packageName.equals(PackageConstants.PACKAGE_NAME_GMS);

      // Installs APKs.
      ApkInstallArgs.Builder installArgsBuilder =
          ApkInstallArgs.builder()
              .setApkPath(apkPath)
              .setSkipDowngrade((isGms && skipGmsDowngrade) || (!isGms && skipApkDowngrade))
              .setClearAppData(isGms && clearGmsAppData)
              .setGrantPermissions(grantPermissionsOnInstall)
              .setBypassLowTargetSdkBlock(bypassLowTargetSdkBlock);
      if (isGms && shouldSkipGmsCompatibilityCheck(testInfo, apkPath)) {
        installArgsBuilder.setSkipGmsCompatCheck(true);
      }
      dexMetadataPath.ifPresent(installArgsBuilder::setDexMetadataPath);
      boolean forceQueryable =
          deviceSdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()
              && PackageConstants.ANDROIDX_SERVICES_APK_PACKAGE_NAMES.contains(packageName);
      if (forceQueryable) {
        installArgsBuilder.setForceQueryable(true);
      }
      installTimeout.ifPresent(installArgsBuilder::setInstallTimeout);
      sleepAfterInstallGms.ifPresent(installArgsBuilder::setSleepAfterInstallGms);
      apkInstaller.installApkIfNotExist(device, installArgsBuilder.build(), testInfo.log());
      // If currently not on system user 0, ensure apks are installed on system user too.
      // b/142827104
      if (androidUserUtil.getCurrentUser(deviceId, systemSettingManager.getDeviceSdkVersion(device))
          != 0) {
        apkInstaller.installApkIfNotExist(
            device, installArgsBuilder.setUserId("0").build(), testInfo.log());
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Installed package: %s, Device ID = %s", packageName, deviceId);

      if (packageName.equals(PackageConstants.PACKAGE_NAME_TEST_SERVICES_APK)
          && deviceSdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()) {
        // Enable MANAGE_EXTERNAL_STORAGE for test services apk to grant AndroidTestUtil permission
        // to access files (b/170517865)
        systemSettingUtil.setPackageOperationMode(
            deviceId, packageName, APP_OP_MANAGE_EXTERNAL_STORAGE, AppOperationMode.ALLOW);
      }

      apkInstaller.checkInstalledAppVersion(testInfo, deviceId, packageName, apkPath);
      checkSizeInfo(testInfo, packageName, ImmutableSet.of(apkPath));
    } finally {
      if (broadcastInstallMessage) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createFinishMessage(device.getDimensions(), packageName));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .withCause(e)
              .alsoTo(logger)
              .log("Failed to broadcast message for finishing installing app");
        }
      }
    }
  }

  private void installMultiPackages(
      Device device,
      String buildPackageName,
      Collection<String> apkPaths,
      int deviceSdkVersion,
      TestInfo testInfo,
      boolean broadcastInstallMessage,
      boolean grantPermissionsOnInstall,
      Optional<Duration> installTimeout)
      throws InterruptedException, MobileHarnessException {
    String deviceId = device.getDeviceId();
    // Uninstall APKs.
    if (apkInstaller
        .checkInstalledAppVersion(testInfo, deviceId, buildPackageName, null)
        .isPresent()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Uninstall the installed package %s before installation.", buildPackageName);
      apkInstaller.uninstallApk(device, buildPackageName, true, testInfo.log());
    }
    try {
      if (broadcastInstallMessage) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createStartMessage(device.getDimensions(), buildPackageName));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .withCause(e)
              .alsoTo(logger)
              .log("Failed to broadcast message for starting installing app");
        }
      }
      // Installs APKs.
      apkInstaller.installMultiNonGmsPackages(
          deviceId,
          /* userId= */ null,
          deviceSdkVersion,
          Multimaps.index(apkPaths, v -> buildPackageName),
          grantPermissionsOnInstall,
          /* forceNoStreaming= */ false,
          installTimeout.orElse(null),
          testInfo.log());
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Installed package: %s, Device ID = %s", buildPackageName, deviceId);

      apkInstaller.checkInstalledAppVersion(testInfo, deviceId, buildPackageName, null);
      checkSizeInfo(testInfo, buildPackageName, apkPaths);
    } finally {
      if (broadcastInstallMessage) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createFinishMessage(device.getDimensions(), buildPackageName));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .withCause(e)
              .alsoTo(logger)
              .log("Failed to broadcast message for finishing installing app");
        }
      }
    }
  }

  private static void addApks(Set<String> buildApks, Set<String> apksToAdd, TestInfo testInfo) {
    if (!apksToAdd.isEmpty()) {
      testInfo.log().atInfo().alsoTo(logger).log("Add apks %s to install.", apksToAdd);
      buildApks.addAll(apksToAdd);
    }
  }

  /**
   * Checks whether the exception has certain ErrorCode/ErrorId. If so, and warnings and mark test
   * as FAIL.
   *
   * @return whether the exception comes from certain Installation Error (error from user's fault).
   */
  @CanIgnoreReturnValue
  public boolean isInstallFailure(MobileHarnessException e, TestInfo testInfo) {
    boolean isInstallationFailure = false;
    ErrorId errorId = e.getErrorId();
    if (errorId == AndroidErrorId.ANDROID_APK_INSTALLER_GMS_INCOMPATIBLE
        || errorId == AndroidErrorId.ANDROID_APK_INSTALLER_INVALID_GMS_VERSION
        || errorId == AndroidErrorId.ANDROID_APK_INSTALLER_DEVICE_SDK_TOO_LOW
        || errorId == AndroidErrorId.ANDROID_APK_INSTALLER_APPLY_MULTI_PACKAGE_INSTALL_TO_GMS
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ABI_INCOMPATIBLE
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_MISSING_SHARED_LIBRARY
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_UPDATE_INCOMPATIBLE
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_SDK_VERSION_NOT_SUPPORT
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PARTIAL_INSTALL_NOT_ALLOWED_ERROR
        || errorId == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_VERSION_DOWNGRADE) {
      isInstallationFailure = true;
    }

    if (isInstallationFailure) {
      testInfo
          .resultWithCause()
          .setNonPassing(TestResult.FAIL, ErrorModelConverter.toExceptionDetail(e));
    }
    return isInstallationFailure;
  }

  private void checkSizeInfo(TestInfo testInfo, String packageName, Collection<String> apks) {
    String propertyName =
        Ascii.toLowerCase(ApkInfo.APK_SIZE_.name()) + packageName.replace('.', '_');
    if (testInfo.properties().get(propertyName) != null) {
      return;
    }
    long totalSize = 0L;
    try {
      for (String apk : apks) {
        totalSize += localFileUtil.getFileSize(apk);
      }
      testInfo.properties().add(propertyName, totalSize + " B");
    } catch (MobileHarnessException e) {
      testInfo.warnings().addAndLog(e, logger);
      testInfo.log().append("\n");
    }
  }

  private static InstallApkStepSpec createInstallApkStepSpec(JobInfo jobInfo)
      throws MobileHarnessException {
    InstallApkStepSpec.Builder spec =
        InstallApkStepSpec.newBuilder()
            .setInstallApkExtraFileTags(
                jobInfo.params().get(PARAM_INSTALL_APK_EXTRA_FILE_TAGS, /* defaultValue= */ ""))
            .setSkipGmsDowngrade(jobInfo.params().getBool(PARAM_SKIP_GMS_DOWNGRADE, true))
            .setGrantPermissionsOnInstall(
                jobInfo.params().getBool(PARAM_GRANT_PERMISSIONS_ON_INSTALL, true))
            .setBroadcastInstallMessage(jobInfo.params().isTrue(PARAM_BROADCAST_INSTALL_MESSAGE))
            .setClearGmsAppData(jobInfo.params().isTrue(PARAM_CLEAR_GMS_APP_DATA))
            .setForceInstallApks(jobInfo.params().getBool(PARAM_FORCE_INSTALL_APKS, false))
            .setRebootAfterAllBuildApksInstallation(
                jobInfo.params().getBool(PARAM_REBOOT_AFTER_ALL_BUILD_APKS_INSTALLATION, false))
            .setBypassLowTargetSdkBlock(
                jobInfo.params().getBool(PARAM_BYPASS_LOW_TARGET_SDK_BLOCK, false))
            .setSkipApkDowngrade(jobInfo.params().getBool(PARAM_SKIP_APK_DOWNGRADE, false));
    if (jobInfo.params().has(PARAM_INSTALL_APK_TIMEOUT_SEC)) {
      spec.setInstallApkTimeoutSec(jobInfo.params().getLong(PARAM_INSTALL_APK_TIMEOUT_SEC));
    }
    if (jobInfo.params().has(PARAM_SLEEP_AFTER_INSTALL_GMS_SEC)) {
      spec.setSleepAfterInstallGmsSec(jobInfo.params().getLong(PARAM_SLEEP_AFTER_INSTALL_GMS_SEC));
    }
    return spec.build();
  }

  private static ImmutableSet<String> getBuildApks(InstallApkStepSpec spec, JobInfo jobInfo) {
    return new ImmutableSet.Builder<String>()
        .addAll(expandG3Files(spec.getBuildApkList()))
        .addAll(jobInfo.files().get(TAG_BUILD_APK))
        .build();
  }

  private static ImmutableSet<String> getExtraApks(InstallApkStepSpec spec, JobInfo jobInfo) {
    return new ImmutableSet.Builder<String>()
        .addAll(expandG3Files(spec.getExtraApkList()))
        .addAll(jobInfo.files().get(TAG_EXTRA_APK))
        .build();
  }

  private static ImmutableSet<String> getDexMetadataFiles(
      InstallApkStepSpec spec, TestInfo testInfo) {
    return new ImmutableSet.Builder<String>()
        .addAll(expandG3Files(spec.getDexMetadataList()))
        .addAll(testInfo.files().get(TAG_DEX_METADATA))
        .addAll(testInfo.jobInfo().files().get(TAG_DEX_METADATA))
        .build();
  }

  private static ImmutableSet<String> expandG3Files(List<Google3File> g3Files) {
    ImmutableSet.Builder<String> outputPaths = ImmutableSet.builder();
    for (Google3File g3File : g3Files) {
      outputPaths.addAll(g3File.getOutputList());
    }
    return outputPaths.build();
  }

  private static String getFileNameForDexMetadataMatching(String filePath) {
    return Files.getNameWithoutExtension(filePath);
  }

  private static ImmutableMap<String, String> getDexMetadataFilesByName(Set<String> filePaths)
      throws MobileHarnessException {
    try {
      return filePaths.stream()
          .collect(toImmutableMap(InstallApkStep::getFileNameForDexMetadataMatching, f -> f));
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTALL_APK_STEP_DEX_METADATA_CONFLICT,
          String.format("Multiple dex metadata files with the same name: %s", filePaths),
          e);
    }
  }

  private static Optional<String> getMatchingDexMetadataFile(
      String apkPath, Map<String, String> dexMetadataFilesByName) {
    return Optional.ofNullable(
        dexMetadataFilesByName.get(getFileNameForDexMetadataMatching(apkPath)));
  }

  /** Reboots devices, and blocked current thread until it is finished. */
  @VisibleForTesting
  void rebootDevice(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    testInfo.log().atInfo().alsoTo(logger).log("Waiting for device %s rebooting... ", deviceId);
    try {
      systemStateManager.reboot(device, testInfo.log(), null);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTALL_APK_STEP_REBOOT_ERROR,
          "Exception during reboot: " + e.getMessage(),
          e);
    }
    testInfo.log().atInfo().alsoTo(logger).log("device %s is rebooted.", deviceId);
  }

  private boolean shouldSkipGmsCompatibilityCheck(TestInfo testInfo, String gmscoreApkPath) {
    Optional<String> gmscoreApksFromLspace =
        testInfo.properties().getOptional(ApkInfo.GMSCORE_APKS_SKIP_COMPATIBILITY_CHECK);
    if (gmscoreApksFromLspace.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("no apk provided in properties %s", ApkInfo.GMSCORE_APKS_SKIP_COMPATIBILITY_CHECK);
      return false;
    }
    Map<String, String> gmscoreApkNameToFingerprint = StrUtil.toMap(gmscoreApksFromLspace.get());
    String checkGmscoreApkName = new File(gmscoreApkPath).getName();
    try {
      if (gmscoreApkNameToFingerprint.containsKey(checkGmscoreApkName)
          && checksumUtil
              .fingerprint(gmscoreApkPath)
              .equals(gmscoreApkNameToFingerprint.get(checkGmscoreApkName))) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Will skip compatibility check for %s", gmscoreApkPath);
        return true;
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to calculate fingerprint for %s.", gmscoreApkPath);
      return false;
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("no gmscore provided in properties match the given %s", gmscoreApkPath);
    return false;
  }

  public void uninstallPackages(Device device, TestInfo testInfo, List<String> packages)
      throws InterruptedException {
    for (String packageName : packages) {
      apkInstaller.uninstallApk(device, packageName, /* logFailures= */ true, testInfo.log());
    }
  }
}
