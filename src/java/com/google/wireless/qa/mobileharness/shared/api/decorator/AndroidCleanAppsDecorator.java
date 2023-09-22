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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonApkInfoProvider;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.android.WifiUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidCleanAppsSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;

/**
 * Driver decorator for uninstalling all the third party apks from the Android device before test.
 * If the "remove_system_apps" param is specified, this will remove the given system app(s). Note
 * you should use this decorator "outside" of the other decorators need to install and use some
 * apks.
 */
@DecoratorAnnotation(
    help =
        "For uninstalling all the third party apks from the Android device, "
            + "and cleaning system packages under tests before running test. Note you should use "
            + "this decorator \"outside\" of the other decorators need to install and use some "
            + "apks.")
public class AndroidCleanAppsDecorator extends BaseDecorator implements AndroidCleanAppsSpec {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout for waiting for reboot. */
  // TODO: Replace this with a global constant for reboot timeouts.
  private static final Duration WAIT_FOR_REBOOT_TIMEOUT = Duration.ofMinutes(3L);

  private static final Splitter PACKAGE_NAME_SPLITTER = Splitter.on(',');

  private final Aapt aapt;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ApkInstaller apkInstaller;
  private final DeviceDaemonApkInfoProvider deviceDaemonApkInfoProvider;
  private final SystemStateManager systemStateManager;
  private final WifiUtil wifiUtil;

  @Inject
  AndroidCleanAppsDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Aapt aapt,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ApkInstaller apkInstaller,
      DeviceDaemonApkInfoProvider deviceDaemonApkInfoProvider,
      SystemStateManager systemStateManager,
      WifiUtil wifiUtil) {
    super(decoratedDriver, testInfo);
    this.aapt = aapt;
    this.androidFileUtil = androidFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.apkInstaller = apkInstaller;
    this.deviceDaemonApkInfoProvider = deviceDaemonApkInfoProvider;
    this.systemStateManager = systemStateManager;
    this.wifiUtil = wifiUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Log testLog = testInfo.log();
    JobInfo jobInfo = testInfo.jobInfo();
    Params params = jobInfo.params();
    List<String> packagesToUninstall =
        params.getList(AndroidCleanAppsSpec.PARAM_1P_PKGS_TO_UNINSTALL, ImmutableList.of());
    if (!DeviceUtil.inSharedLab()) {
      // Gets white list.
      List<String> packagesToKeep =
          new ArrayList<>(
              params.getList(AndroidCleanAppsSpec.PARAM_PKGS_TO_KEEP, ImmutableList.of()));

      // For some phone (e.g. NBU), GMS Core is installed as third-party package. Always keep the
      // gms core since cleaning the gms core may cause an error dialog saying "Unfortunately,
      // Google Play services has stopped.". And the error dialog will block the test so that the
      // test will definitely fail. Rerun/retry does not help.
      //
      // <p>The correct order to dismiss the dialog may be kill-after-clear: $ adb shell pm clear
      // com.google.android.gms $ adb shell am force-stop com.google.android.gms
      packagesToKeep.add(PackageConstants.PACKAGE_NAME_GMS);

      // Remove WifiUtil from device (Android P) would lead Wi-Fi disconnection. See b/78900682 for
      // detail information.
      int deviceSdkVersion =
          androidSystemSettingUtil.getDeviceSdkVersion(getDevice().getDeviceId());
      if (deviceSdkVersion >= AndroidVersion.PI.getStartSdkVersion()) {
        packagesToKeep.add(wifiUtil.getWifiUtilApkPackageName());
      }

      // Always keep Mobile Harness daemon alive on device unless it is killed before test started.
      packagesToKeep.add(
          deviceDaemonApkInfoProvider
              .getDeviceDaemonApkInfoInstance(deviceSdkVersion)
              .getPackageName());

      // Other upstream decorators or plug-ins may install packages to keep, that the user doesn't
      // necessarily know about to protect.
      testInfo
          .jobInfo()
          .properties()
          .getOptional(AndroidCleanAppsSpec.PROPERTY_EXTRA_PKGS_TO_KEEP)
          .ifPresent(
              extraPackagesToKeep ->
                  packagesToKeep.addAll(PACKAGE_NAME_SPLITTER.splitToList(extraPackagesToKeep)));

      removeThirdPartyApps(packagesToKeep, testInfo);

      // Cleans system apps under test.
      cleanApps(packagesToKeep, testInfo);
    } else {
      testLog.atInfo().alsoTo(logger).log("No need to clear apps in M&M lab.");
    }
    if (!packagesToUninstall.isEmpty()) {
      removeFirstPartyApps(packagesToUninstall, testInfo);
    }
    if (!DeviceUtil.inSharedLab()) {
      if (testInfo
              .jobInfo()
              .params()
              .getBool(AndroidCleanAppsSpec.PARAM_REBOOT_AFTER_UNINSTALLATION, false)
          || testInfo
              .properties()
              .getBoolean(PropertyName.Test.RETRY_AFTER_NO_VALID_UID_ASSIGNED)
              .orElse(false)) {
        cacheDeviceStateAndReboot(testInfo.log());
      }
    }
    getDecorated().run(testInfo);
  }

  /** Cleans data of system apps that are being tested. */
  private void cleanApps(List<String> packagesToKeep, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    String deviceId = getDevice().getDeviceId();
    Log testLog = testInfo.log();
    Set<String> buildApks = new HashSet<>();
    buildApks.addAll(jobInfo.files().get(AndroidCleanAppsSpec.TAG_BUILD_APK));
    buildApks.addAll(testInfo.files().get(AndroidCleanAppsSpec.TAG_BUILD_APK));
    if (!buildApks.isEmpty()) {
      // Gets the packages of the build_apks.
      Set<String> buildPackages = new HashSet<>();
      for (String buildApk : buildApks) {
        buildPackages.add(aapt.getApkPackageName(buildApk));
      }

      // Gets the system packages.
      Set<String> systemPackages =
          androidPackageManagerUtil.listPackages(deviceId, PackageType.SYSTEM);

      // Keeps system packages that match PARAM_PKGS_TO_KEEP_REGEX value
      packagesToKeep.addAll(filterPackagesByRegex(systemPackages, testInfo));

      if (!packagesToKeep.isEmpty()) {
        packagesToKeep.forEach(systemPackages::remove);
      }

      // Gets the build packages which are also system packages.
      buildPackages.retainAll(systemPackages);
      for (String buildPackage : buildPackages) {
        // Clears the build package which is also a system package.
        testLog.atInfo().alsoTo(logger).log("Clear data of system package: %s", buildPackage);
        androidPackageManagerUtil.clearPackage(deviceId, buildPackage);
      }
    }
  }

  /**
   * Uninstall third-party apps. Accepts a whitelist of packages to keep and also keeps the Mobile
   * Harness Android device daemon.
   *
   * @param packagesToKeep list of packages to not uninstall.
   */
  private void removeThirdPartyApps(List<String> packagesToKeep, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    // Gets third_party apps.
    Set<String> thirdPartyPackages =
        androidPackageManagerUtil.listPackages(deviceId, PackageType.THIRD_PARTY);

    Log testLog = testInfo.log();
    if (thirdPartyPackages.isEmpty()) {
      testLog.atInfo().alsoTo(logger).log("No third party package installed");
    } else {
      testLog
          .atInfo()
          .alsoTo(logger)
          .log(
              "Third party packages installed:\n - %s",
              Joiner.on("\n - ").join(thirdPartyPackages));
    }

    // Keeps third party packages that match PARAM_PKGS_TO_KEEP_REGEX value
    packagesToKeep.addAll(filterPackagesByRegex(thirdPartyPackages, testInfo));

    if (!packagesToKeep.isEmpty()) {
      testLog
          .atInfo()
          .alsoTo(logger)
          .log("Packages that are not being cleaned: %s", packagesToKeep);
      packagesToKeep.forEach(thirdPartyPackages::remove);
    }

    for (String packageName : thirdPartyPackages) {
      apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ true, testLog);
    }
  }

  private Set<String> filterPackagesByRegex(Set<String> packageNames, TestInfo testInfo)
      throws MobileHarnessException {
    Set<String> filteredPackageNames = ImmutableSet.of();
    String regexValue;
    if ((regexValue =
            testInfo.jobInfo().params().get(AndroidCleanAppsSpec.PARAM_PKGS_TO_KEEP_REGEX, null))
        != null) {
      filteredPackageNames = filterPackagesByRegex(packageNames, regexValue);
    }
    return filteredPackageNames;
  }

  /**
   * Return a set of package names in {@code packageNames} that match regex expression {@code
   * filteredPackageNameRegex}.
   */
  @VisibleForTesting
  Set<String> filterPackagesByRegex(Set<String> packageNames, String filteredPackageNameRegex)
      throws MobileHarnessException {
    try {
      final Pattern pattern = Pattern.compile(filteredPackageNameRegex);
      return packageNames.stream()
          .filter(packageName -> pattern.matcher(packageName).find())
          .collect(ImmutableSet.toImmutableSet());
    } catch (PatternSyntaxException e) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_CLEAN_APPS_DECORATOR_INVALID_PARAM_VALUE,
          String.format(
              "Failed to compile the regex %s%n%s", filteredPackageNameRegex, e.getMessage()),
          e);
    }
  }

  /**
   * Remove first-party apps. System apps that cannot be removed with a simple uninstallation
   * command will be removed by deleting the system apk if the device is rooted.
   *
   * @param packagesToUninstall list of package names to remove.
   */
  private void removeFirstPartyApps(List<String> packagesToUninstall, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (!((AndroidDevice) getDevice()).isRooted()) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_CLEAN_APPS_DECORATOR_CANNOT_REMOVE_1P_APP_IN_NONROOT_DEVICE,
          "Cannot remove system apps from an unrooted device. Consider adding \"rooted\": \"true\""
              + " to the device dimensions.");
    } else {
      // Uninstall updates to system apps, if any were installed. Errors are ignored+logged.
      Log testLog = testInfo.log();
      String deviceId = getDevice().getDeviceId();
      // Only removes the ones installed under current device.
      Set<String> systemPackagesToRemove =
          androidPackageManagerUtil.listPackages(deviceId, PackageType.SYSTEM);
      systemPackagesToRemove.retainAll(packagesToUninstall);
      for (String packageName : systemPackagesToRemove) {
        apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ true, testLog);
      }
      systemPackagesToRemove = androidPackageManagerUtil.listPackages(deviceId, PackageType.SYSTEM);
      systemPackagesToRemove.retainAll(packagesToUninstall);

      if (!systemPackagesToRemove.isEmpty()) {
        testLog
            .atInfo()
            .alsoTo(logger)
            .log(
                "System packages requested to be removed:\n - %s",
                Joiner.on("\n - ").join(systemPackagesToRemove));

        // Disable verity.  This was added in Lollipop 5.1 (22).
        if (androidSystemSettingUtil.getDeviceSdkVersion(deviceId) >= 22
            && androidSystemSettingUtil
                .setDmVerityChecking(deviceId, false)
                .equals(PostSetDmVerityDeviceOp.REBOOT)) {
          testLog.atInfo().alsoTo(logger).log("Disabling verity and rebooting");
          cacheDeviceStateAndReboot(testLog);

          // Check if system package is actually present after disabling verity b/64258732
          systemPackagesToRemove =
              androidPackageManagerUtil.listPackages(deviceId, PackageType.SYSTEM);
          systemPackagesToRemove.retainAll(packagesToUninstall);
        }

        if (!systemPackagesToRemove.isEmpty()) {
          androidFileUtil.remount(deviceId);
          for (String packageName : systemPackagesToRemove) {
            String apkPath = androidPackageManagerUtil.getInstalledPath(deviceId, packageName);
            testLog.atInfo().alsoTo(logger).log("Removing system apk: %s", apkPath);
            androidFileUtil.removeFiles(deviceId, apkPath);
          }
          cacheDeviceStateAndReboot(testLog);
        } else {
          testLog
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Device did not list package names for system packages to be removed after "
                      + "disabling verity and reboot.");
        }
      } else if (!packagesToUninstall.isEmpty()) {
        testLog.atInfo().alsoTo(logger).log("Not removing any system packages.");
      }
    }
  }

  private void cacheDeviceStateAndReboot(Log log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    log.atInfo().alsoTo(logger).log("Waiting for device %s rebooting... ", deviceId);

    systemStateManager.reboot(getDevice(), log, WAIT_FOR_REBOOT_TIMEOUT);
  }
}
