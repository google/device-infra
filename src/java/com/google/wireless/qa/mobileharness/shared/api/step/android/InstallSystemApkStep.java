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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.List;

/** Instantiable class for system apk installation on real devices. */
public class InstallSystemApkStep {

  private final AndroidSystemStateUtil systemStateUtil;

  /** Class to configure system setting on Android device. */
  private final AndroidSystemSettingUtil systemSettingUtil;

  /** Class to get system spec on Android device. */
  private final AndroidSystemSpecUtil systemSpecUtil;

  /** File operation to android device. */
  private final AndroidFileUtil fileUtil;

  /** Class to manage package on Android device. */
  private final AndroidPackageManagerUtil packageManagerUtil;

  private final AndroidProcessUtil processUtil;

  /** Logger for this driver. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** {@code Aapt} for AAPT operations. */
  private final Aapt aapt;

  /** Timeout for waiting for reboot. */
  private static final Duration WAIT_FOR_REBOOT_TIMEOUT = Duration.ofMinutes(10);

  /** Milliseconds of waiting for reboot. */
  static final long WAIT_FOR_REBOOT_MS = Duration.ofMinutes(3).toMillis();

  /** Device path used for pushing the permissions XML */
  public static final String PERMISSIONS_FILE_PATH = "/etc/permissions";

  /** Device path used for pushing the system APKs (sdk >= 19) */
  public static final String PRIVILEGED_APP_PATH = "/system/priv-app";

  /** Device path used for pushing the system APKs (sdk < 19) */
  public static final String SYSTEM_APP_PATH = "/system";

  /** Device path used for pushing the system APKs */
  public static final String DATA_PATH = "/data";

  /** Enum for device reboot type* */
  public enum DeviceRebootType {
    SOFT_REBOOT,
    HARD_REBOOT,
    NONE,
  }

  public InstallSystemApkStep(
      AndroidSystemStateUtil systemStateUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidSystemSpecUtil systemSpectUtil,
      AndroidFileUtil fileUtil,
      AndroidPackageManagerUtil packageManagerUtil,
      AndroidProcessUtil processUtil,
      Aapt aapt) {
    this.systemStateUtil = systemStateUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.systemSpecUtil = systemSpectUtil;
    this.fileUtil = fileUtil;
    this.packageManagerUtil = packageManagerUtil;
    this.processUtil = processUtil;
    this.aapt = aapt;
  }

  /**
   * Installs Android system app.
   *
   * @param deviceId Mobile harness device id.
   * @param deviceClassName Mobile harness device class name.
   * @param systemApks List of system apks to be installed.
   * @param replaceApkPaths If it is not null, then this will be the system image apks to be
   *     replaced by systemApks.
   * @param permissionFiles If it is not null, then this will be a list of permission files to push
   *     to system.
   * @param permissionFilesInDevice the destination path of {@code permissionFiles}; use {@link
   *     #PERMISSIONS_FILE_PATH} if it is empty; otherwise, it must have the same size as {@code
   *     permissionFiles}
   * @param forceInstallation Whether to force install systemApks.
   */
  public void installSystemApp(
      String deviceId,
      String deviceClassName,
      List<String> systemApks,
      List<String> replaceApkPaths,
      List<String> permissionFiles,
      List<String> permissionFilesInDevice,
      boolean forceInstallation,
      boolean allowReplaceOfNotInstalled,
      DeviceRebootType deviceRebootType)
      throws MobileHarnessException, InterruptedException {
    // Prepare device system volume.
    disableCheckAndRemount(deviceId, deviceClassName);
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);

    if (replaceApkPaths != null && !replaceApkPaths.isEmpty()) {
      if (systemApks.size() != replaceApkPaths.size()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_SYSTEM_APK_PATHS_NUM_NOT_THE_SAME,
            String.format(
                "Installing system apps systemApks lengths (%d) are not the same with"
                    + " replaceApkPaths (%d).",
                systemApks.size(), replaceApkPaths.size()));
      }
      // Remove original files and push new files.
      for (int i = 0; i < systemApks.size(); ++i) {
        replaceSystemPackage(
            deviceId, systemApks.get(i), replaceApkPaths.get(i), allowReplaceOfNotInstalled);
      }
      rebootDevice(deviceId, deviceClassName, deviceRebootType);
      systemStateUtil.waitUntilReady(deviceId);
    } else {
      // Check the SDK version to determine where to push the system apps.
      String pathOnDevice = SYSTEM_APP_PATH;
      if (sdkVersion >= 19) {
        pathOnDevice = PRIVILEGED_APP_PATH;
      }

      // Push the files.
      for (String systemApk : systemApks) {
        // See cl/465328918 for emulator /system partition limit workaround.
        if (systemSpecUtil.isEmulator(deviceId) && !systemSpecUtil.isCuttlefishEmulator(deviceId)) {
          String systemApkFile = systemApk.substring(systemApk.lastIndexOf("/") + 1);
          String srcPath = String.format("%s/%s", DATA_PATH, systemApkFile);
          String desPath = String.format("%s/%s", pathOnDevice, systemApkFile);

          logger.atInfo().log("Pushing %s to %s.", systemApk, DATA_PATH);
          fileUtil.push(deviceId, sdkVersion, systemApk, DATA_PATH, null);

          // Handle test_attempts > 1
          logger.atInfo().log("rm symlink %s if exist.", desPath);
          fileUtil.removeFiles(deviceId, desPath);

          logger.atInfo().log("Symlink %s to %s.", srcPath, desPath);
          fileUtil.createSymlink(deviceId, srcPath, desPath);
        } else {
          logger.atInfo().log("Pushing %s to %s.", systemApk, pathOnDevice);
          fileUtil.push(deviceId, sdkVersion, systemApk, pathOnDevice, null);
        }
      }
    }

    if (permissionFilesInDevice != null && !permissionFilesInDevice.isEmpty()) {
      if (permissionFiles.size() != permissionFilesInDevice.size()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTALL_SYSTEM_APK_PERMISSION_FILES_NUM_NOT_THE_SAME,
            String.format(
                "Permissions files number (%d) is not the same as permission files in device (%d).",
                permissionFiles.size(), permissionFilesInDevice.size()));
      }
      // push permissions files to corresponding paths.
      for (int i = 0; i < permissionFiles.size(); ++i) {
        String permissionFile = permissionFiles.get(i);
        String permissionFileInDevice = permissionFilesInDevice.get(i);
        logger.atInfo().log("Pushing %s to %s.", permissionFile, permissionFileInDevice);
        fileUtil.push(deviceId, sdkVersion, permissionFile, permissionFileInDevice, null);
      }
    } else if (permissionFiles != null) {
      // Push the permission files to default path.
      for (String permissionFile : permissionFiles) {
        logger.atInfo().log("Pushing %s to %s.", permissionFile, PERMISSIONS_FILE_PATH);
        fileUtil.push(deviceId, sdkVersion, permissionFile, PERMISSIONS_FILE_PATH, null);
      }
    }

    if (forceInstallation) {
      // Reinstall apps.
      for (String systemApk : systemApks) {
        logger.atInfo().log("Reinstalling %s.", systemApk);
        packageManagerUtil.installApk(deviceId, sdkVersion, systemApk);
      }
    }
    rebootDevice(deviceId, deviceClassName, deviceRebootType);
    if (sdkVersion == 23
        && systemSpecUtil.isEmulator(deviceId)
        && !systemSpecUtil.isCuttlefishEmulator(deviceId)) {
      systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(20));
    } else {
      systemStateUtil.waitUntilReady(deviceId);
    }
  }

  /**
   * Reboots Android device.
   *
   * @param deviceId Mobile harness device id.
   * @param deviceClassName Mobile harness device class name.
   */
  @VisibleForTesting
  void rebootDevice(String deviceId, String deviceClassName, DeviceRebootType deviceRebootType)
      throws MobileHarnessException, InterruptedException {
    try {
      // The only supported device for this decorator is {@link AndroidRealDevice}.
      // Disable autoDelete to avoid device detector remove cached device before reboot
      switch (deviceRebootType) {
        case SOFT_REBOOT:
          if (!DeviceUtil.inSharedLab()) {
            logger.atInfo().log("Device %s soft-reboot started.", deviceId);
            systemStateUtil.softReboot(deviceId);
            logger.atInfo().log("Device %s soft-reboot complete.", deviceId);
          } else {
            logger.atSevere().log(
                "Ignoring attempt to adb shell stop/start on device %s while not managing devices.",
                deviceId);
          }
          break;
        case HARD_REBOOT:
          logger.atInfo().log("Device %s hard-reboot started.", deviceId);
          DeviceCache.getInstance().cache(deviceId, deviceClassName, WAIT_FOR_REBOOT_TIMEOUT);
          systemStateUtil.reboot(deviceId);
          // Wait for adb connect to device.
          systemStateUtil.waitForDevice(deviceId, Duration.ofMillis(WAIT_FOR_REBOOT_MS));
          // Wait for device is online.
          systemStateUtil.waitUntilReady(deviceId);
          systemStateUtil.becomeRoot(deviceId);
          systemStateUtil.waitUntilReady(deviceId);
          logger.atInfo().log("Device %s hard-reboot complete.", deviceId);
          break;
        case NONE:
          logger.atInfo().log("Device %s not rebooted.", deviceId);
          break;
      }

    } finally {
      DeviceCache.getInstance().invalidateCache(deviceId);
    }
  }

  /**
   * Replaces Android system apk with sourceApk.
   *
   * @param deviceId Mobile harness device id.
   * @param sourceApk Apk that you want to replace.
   * @param destApkPath Apk path on Android to push to.
   */
  @VisibleForTesting
  void replaceSystemPackage(
      String deviceId, String sourceApk, String destApkPath, boolean allowNotInstalled)
      throws MobileHarnessException, InterruptedException {
    String packageName = aapt.getApkPackageName(sourceApk);

    logger.atInfo().log("Stopping application %s.", packageName);
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    processUtil.stopApplication(
        UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion).build(),
        packageName,
        /* kill= */ true);
    logger.atInfo().log("Uninstalling package %s.", packageName);
    packageManagerUtil.uninstallApkWithoutCheckingOutput(deviceId, packageName);

    try {
      String installedPath = packageManagerUtil.getInstalledPath(deviceId, packageName);
      fileUtil.removeFiles(deviceId, installedPath);
    } catch (MobileHarnessException e) {
      if (!allowNotInstalled
          || e.getErrorCode()
              != AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND.code()) {
        throw e;
      }
    }

    fileUtil.removeFiles(deviceId, destApkPath);
    logger.atInfo().log("Pushing %s to %s.", sourceApk, destApkPath);
    fileUtil.push(deviceId, sdkVersion, sourceApk, destApkPath, null);
  }

  /**
   * Disables Android dm verity check, and remount device.
   *
   * @param deviceId Mobile harness device id.
   * @param deviceClassName Mobile harness device class name.
   */
  @VisibleForTesting
  void disableCheckAndRemount(String deviceId, String deviceClassName)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Disabling dm-verity checking.");
    // Disable verity.  This was added in Lollipop 5.1 (22).
    // No need to disable Goldfish emulator verity as they come with verity disabled(b/123670720)
    boolean isGoldfishEmulator =
        systemSpecUtil.isEmulator(deviceId) && !systemSpecUtil.isCuttlefishEmulator(deviceId);
    if (!isGoldfishEmulator
        && systemSettingUtil.getDeviceSdkVersion(deviceId) >= 22
        && systemSettingUtil.setDmVerityChecking(deviceId, /* enabled= */ false)
            == PostSetDmVerityDeviceOp.REBOOT) {
      logger.atInfo().log("Rebooting for dm-verity setting to take effect.");
      rebootDevice(deviceId, deviceClassName, DeviceRebootType.HARD_REBOOT);
    }

    logger.atInfo().log("Remounting in order to push system applications.");
    fileUtil.remount(deviceId);
  }
}
