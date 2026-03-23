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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Utility methods of apk installation for drivers. */
public class InstallApkStep implements InstallApkStepConstants {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String APP_OP_MANAGE_EXTERNAL_STORAGE = "MANAGE_EXTERNAL_STORAGE";

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
        device, testInfo, testInfo.jobInfo().files().get(TAG_BUILD_APK), /* spec= */ null);
  }

  @CanIgnoreReturnValue
  public List<String> installBuildApks(Device device, TestInfo testInfo, InstallApkStepSpec spec)
      throws MobileHarnessException, InterruptedException {
    return installBuildApks(device, testInfo, getBuildApks(spec, testInfo.jobInfo()), spec);
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
      @Nullable InstallApkStepSpec spec)
      throws MobileHarnessException, InterruptedException {
    if (spec == null) {
      spec = createInstallApkStepSpec(testInfo.jobInfo());
    }

    String deviceId = device.getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    InstallCoordinator coordinator = new InstallCoordinator(testInfo, aapt);

    // Makes sure the first apk in 'build_apk' will still be the first element inserted.
    coordinator.addApks(jobBuildApks);
    coordinator.addApks(testInfo.files().get(TAG_BUILD_APK));
    coordinator.addApks(testInfo.files().get(TAG_EXTRA_APK));
    coordinator.addApks(getExtraApks(spec, jobInfo));

    String extraFileTags = spec.getInstallApkExtraFileTags();
    for (String fileTag : Splitter.on(',').split(extraFileTags)) {
      testInfo.log().atInfo().alsoTo(logger).log("Get extra tag %s", fileTag);
      coordinator.addApks(testInfo.files().get(fileTag));
      coordinator.addApks(jobInfo.files().get(fileTag));
    }

    coordinator.addDexMetadataFiles(getDexMetadataFiles(spec, testInfo));

    coordinator
        .getFirstPackageName()
        .ifPresent(
            packageName -> {
              if (testInfo.properties().get(ApkInfo.MAIN_PACKAGE_NAME) == null) {
                testInfo.properties().add(ApkInfo.MAIN_PACKAGE_NAME, packageName);
                testInfo.properties().add(AppInfo.AUT_ID, packageName);
              }
            });

    // Install GMS if provided in inputs. GMS Core is installed first because it creates
    // new permissions. APKs that define permissions must precede the APKs that use them.
    // b/149046112 b/36941003
    if (!coordinator.orderToFront(PackageConstants.PACKAGE_NAME_GMS)) {
      if (testInfo
          .jobInfo()
          .params()
          .getBool(
              InstallApkStepConstants.PARAM_CHECK_INSTALLED_GMS_CORE_VERSION,
              /* defaultValue= */ true)) {
        apkInstaller.checkInstalledAppVersion(
            testInfo, deviceId, PackageConstants.PACKAGE_NAME_GMS);
      }
    }

    Optional<Duration> sleepAfterInstallGms =
        spec.hasSleepAfterInstallGmsSec()
            ? Optional.of(Duration.ofSeconds(spec.getSleepAfterInstallGmsSec()))
            : Optional.empty();
    Optional<Duration> installTimeout =
        spec.hasInstallApkTimeoutSec()
            ? Optional.of(Duration.ofSeconds(spec.getInstallApkTimeoutSec()))
            : Optional.empty();
    int deviceSdkVersion = systemSettingManager.getDeviceSdkVersion(device);

    if (deviceSdkVersion < AndroidVersion.LOLLIPOP.getStartSdkVersion()) {
      ImmutableList<String> splits =
          coordinator.getAllPackages().stream()
              .filter(PackageToInstall::isSplitApk)
              .map(pkg -> pkg.packageName)
              .collect(toImmutableList());
      if (!splits.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_APK_STEP_INSTALL_NOT_SUPPORTED,
            String.format(
                "The packages %s contain multiple apks with the same package name."
                    + "If it is your intention to install split apks, please make"
                    + " sure the device SDK version is >= 21.",
                splits));
      }
    }

    for (PackageToInstall pkg : coordinator.getAllPackages()) {
      installApk(
          device,
          deviceSdkVersion,
          pkg,
          testInfo,
          spec,
          installTimeout,
          pkg.packageName.equals(PackageConstants.PACKAGE_NAME_GMS)
              ? sleepAfterInstallGms
              : Optional.empty());
    }

    if (spec.getRebootAfterAllBuildApksInstallation()) {
      rebootDevice(testInfo, device);
    }

    return coordinator.getAllPackages().stream()
        .map(pkg -> pkg.packageName)
        .collect(toImmutableList());
  }

  private void installApk(
      Device device,
      int deviceSdkVersion,
      PackageToInstall pkg,
      TestInfo testInfo,
      InstallApkStepSpec spec,
      Optional<Duration> installTimeout,
      Optional<Duration> sleepAfterInstall)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    if (spec.getForceInstallApks()) {
      apkInstaller.clearInstalledApkProperty(device, pkg.packageName);
    }
    try {
      if (spec.getBroadcastInstallMessage()) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createStartMessage(device.getDimensions(), pkg.packageName));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .withCause(e)
              .alsoTo(logger)
              .log("Failed to broadcast message for starting installing app");
        }
      }
      boolean isGms = pkg.packageName.equals(PackageConstants.PACKAGE_NAME_GMS);

      // Installs APKs.
      ApkInstallArgs.Builder installArgsBuilder =
          ApkInstallArgs.builder()
              .addAllApkPaths(pkg.apkPaths)
              .addAllDexMetadataPaths(pkg.dexMetadataPaths)
              .setSkipDowngrade(isGms && spec.getSkipGmsDowngrade())
              .setClearAppData(isGms && spec.getClearGmsAppData())
              .setGrantPermissions(spec.getGrantPermissionsOnInstall())
              .setBypassLowTargetSdkBlock(spec.getBypassLowTargetSdkBlock());
      if (isGms && shouldSkipGmsCompatibilityCheck(testInfo, pkg.apkPaths.get(0))) {
        installArgsBuilder.setSkipGmsCompatCheck(true);
      }
      if (PackageConstants.ANDROIDX_SERVICES_APK_PACKAGE_NAMES.contains(pkg.packageName)) {
        installArgsBuilder.setForceQueryable(true);
      }
      installTimeout.ifPresent(installArgsBuilder::setInstallTimeout);
      sleepAfterInstall.ifPresent(installArgsBuilder::setSleepAfterInstall);
      apkInstaller.installApkIfNotExist(device, installArgsBuilder.build(), testInfo.log());
      // If currently not on system user 0, ensure apks are installed on system user too.
      // b/142827104
      if (androidUserUtil.getCurrentUser(deviceId, deviceSdkVersion) != 0) {
        apkInstaller.installApkIfNotExist(
            device, installArgsBuilder.setUserId("0").build(), testInfo.log());
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Installed package: %s, Device ID = %s", pkg.packageName, deviceId);

      if (pkg.packageName.equals(PackageConstants.PACKAGE_NAME_TEST_SERVICES_APK)
          && deviceSdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()) {
        // Enable MANAGE_EXTERNAL_STORAGE for test services apk to grant AndroidTestUtil permission
        // to access files (b/170517865)
        systemSettingUtil.setPackageOperationMode(
            deviceId, pkg.packageName, APP_OP_MANAGE_EXTERNAL_STORAGE, AppOperationMode.ALLOW);
      }

      apkInstaller.checkInstalledAppVersion(testInfo, deviceId, pkg.packageName);
      checkSizeInfo(testInfo, pkg.packageName, pkg.apkPaths);
    } finally {
      if (spec.getBroadcastInstallMessage()) {
        try {
          testMessageUtil.sendMessageToTest(
              testInfo,
              AppInstallEventUtil.createFinishMessage(device.getDimensions(), pkg.packageName));
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
                jobInfo.params().getBool(PARAM_BYPASS_LOW_TARGET_SDK_BLOCK, false));
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

  private static class PackageToInstall {

    private final String packageName;
    private final ArrayList<String> apkPaths = new ArrayList<>();
    private final ArrayList<String> dexMetadataPaths = new ArrayList<>();

    PackageToInstall(String packageName) {
      this.packageName = packageName;
    }

    boolean isSplitApk() {
      return apkPaths.size() > 1;
    }
  }

  /**
   * Consolidates a collection of APK and dex metadata files to install, into a list of {@link
   * PackageToInstall}s.
   */
  private static class InstallCoordinator {

    private final List<PackageToInstall> allPackages = new ArrayList<>();
    private final Map<String, PackageToInstall> packageNameToPackage = new HashMap<>();
    private final Map<String, PackageToInstall> dexMetadataKeyToPackage = new HashMap<>();
    private final Set<String> seenApkPaths = new HashSet<>();
    private final Map<String, String> seenDexMetadataKeys = new HashMap<>();
    private final TestInfo testInfo;
    private final Aapt aapt;

    InstallCoordinator(TestInfo testInfo, Aapt aapt) {
      this.testInfo = testInfo;
      this.aapt = aapt;
    }

    void addApks(Collection<String> apkPaths) throws MobileHarnessException, InterruptedException {
      for (String apkPath : apkPaths) {
        addApk(apkPath);
      }
      if (!apkPaths.isEmpty()) {
        testInfo.log().atInfo().alsoTo(logger).log("Add apks for install: %s.", apkPaths);
      }
    }

    private void addApk(String apkPath) throws MobileHarnessException, InterruptedException {
      if (!seenApkPaths.add(apkPath)) {
        // Duplicates are allowed to keep backward compatibility.
        return;
      }

      String packageName = aapt.getApkPackageName(apkPath);

      PackageToInstall pkg = packageNameToPackage.get(packageName);
      if (pkg == null) {
        pkg = new PackageToInstall(packageName);
        allPackages.add(pkg);
        packageNameToPackage.put(packageName, pkg);
      }

      pkg.apkPaths.add(apkPath);
      dexMetadataKeyToPackage.put(getDexMetadataKeyFromPath(apkPath), pkg);
    }

    /**
     * Adds dex metadata files to existing packages.
     *
     * <p>Should be called after {@link #addApks}, as each dex metadata file must match an existing
     * apk.
     */
    void addDexMetadataFiles(Collection<String> dexMetadataPaths)
        throws MobileHarnessException, InterruptedException {
      for (String dexMetadataPath : dexMetadataPaths) {
        addDexMetadataFile(dexMetadataPath);
      }
      if (!dexMetadataPaths.isEmpty()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Add dex metadata files for install: %s.", dexMetadataPaths);
      }
    }

    private void addDexMetadataFile(String dexMetadataPath) throws MobileHarnessException {
      String key = getDexMetadataKeyFromPath(dexMetadataPath);
      PackageToInstall pkg = dexMetadataKeyToPackage.get(key);
      if (pkg == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_APK_STEP_DEX_METADATA_WITHOUT_APK,
            String.format(
                "Dex metadata file %s did not match any installed apks. Each dex metadata file must"
                    + " be installed with an apk with the same file name.",
                dexMetadataPath));
      }
      if (seenDexMetadataKeys.containsKey(key)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_APK_STEP_DEX_METADATA_CONFLICT,
            String.format(
                "Multiple dex metadata files with the same name: %s is conflicting with %s.",
                dexMetadataPath, seenDexMetadataKeys.get(key)));
      }
      seenDexMetadataKeys.put(key, dexMetadataPath);
      pkg.dexMetadataPaths.add(dexMetadataPath);
    }

    private static String getDexMetadataKeyFromPath(String filePath) {
      return Files.getNameWithoutExtension(filePath);
    }

    /**
     * Orders the package with the given package name to the front of the packages list.
     *
     * @return true if the package was in the packages list
     */
    @CanIgnoreReturnValue
    boolean orderToFront(String packageName) {
      PackageToInstall packageToInstall = packageNameToPackage.get(packageName);
      if (packageToInstall == null) {
        return false;
      }
      allPackages.remove(packageToInstall);
      allPackages.add(0, packageToInstall);
      return true;
    }

    Optional<String> getFirstPackageName() {
      return allPackages.isEmpty() ? Optional.empty() : Optional.of(allPackages.get(0).packageName);
    }

    List<PackageToInstall> getAllPackages() {
      return allPackages;
    }
  }
}
