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

package com.google.devtools.mobileharness.platform.android.lightning.fileoperator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedPropertyUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * File operator for managing files on Android device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class FileOperator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Prefix of the device property names of the pushed files/folders. */
  @VisibleForTesting
  static final String DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR = "pushed_file_or_dir:";

  /** Tmp directories on device that skip caching pushed dir/file. */
  private static final ImmutableSet<String> TMP_DIRS_ON_DEVICE = ImmutableSet.of("/data/local/tmp");

  /** Util class for generating MD5 of a file or directory. */
  private final ChecksumUtil md5Util;

  private final LocalFileUtil localFileUtil;

  private final AndroidFileUtil androidFileUtil;

  private final AndroidSystemSettingUtil systemSettingUtil;

  public FileOperator() {
    this(
        new ChecksumUtil(Hashing.md5()),
        new LocalFileUtil(),
        new AndroidFileUtil(),
        new AndroidSystemSettingUtil());
  }

  @VisibleForTesting
  FileOperator(
      ChecksumUtil md5Util,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil systemSettingUtil) {
    this.md5Util = md5Util;
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.systemSettingUtil = systemSettingUtil;
  }

  /**
   * Clears the stored information of pushed file or dir associated with a device. This is
   * especially useful for emulators, because emulators share device objects, but we will start a
   * new emulator on the fly. The information about the installed apks has to be cleared for the
   * newly started emulator.
   */
  public void clearPushedFileOrDirProperties(Device device) {
    SharedPropertyUtil.clearPropertiesWithPrefix(device, DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR);
  }

  /**
   * Pulls the file or directory from device to lab server host machine.
   *
   * @param serial serial number of the device
   * @param pullArgs argument wrapper for pulling file/dir from device to host
   * @param log log collector of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if logFailuresOnly is {@code false} and it failed to run the
   *     command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  @CanIgnoreReturnValue
  public boolean pull(String serial, FilePullArgs pullArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String srcPathOnDevice = pullArgs.srcPathOnDevice();
    String desPathOnHost = pullArgs.desPathOnHost();
    boolean prepareDesPathOnHostAsDir = pullArgs.prepareDesPathOnHostAsDir().orElse(false);
    boolean grantDesPathOnHostFullAccess = pullArgs.grantDesPathOnHostFullAccess().orElse(false);
    boolean logFailuresOnly =
        pullArgs.logFailuresOnly().orElse(true); // Log failures only instead of throwing it out
    boolean isFileOrDirExist = false;
    try {
      isFileOrDirExist = androidFileUtil.isFileOrDirExisted(serial, srcPathOnDevice);
      if (!isFileOrDirExist) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "Skip pulling file/dir %s from device %s as it doesn't exist",
            srcPathOnDevice,
            serial);
        return false;
      }
      if (prepareDesPathOnHostAsDir) {
        localFileUtil.prepareDir(desPathOnHost);
      }
      String output = androidFileUtil.pull(serial, srcPathOnDevice, desPathOnHost);
      SharedLogUtil.logMsg(
          logger, log, "Pulled file/dir %s from device %s:%n%s", srcPathOnDevice, serial, output);
      if (grantDesPathOnHostFullAccess) {
        localFileUtil.grantFileOrDirFullAccess(desPathOnHost);
      }
      return true;
    } catch (MobileHarnessException e) {
      if (logFailuresOnly) {
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            e,
            isFileOrDirExist
                ? "Failed to pull file/dir %s from device %s"
                : "Failed to check existence for file/dir %s from device %s",
            srcPathOnDevice,
            serial);
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_OPERATOR_PULL_FILE_ERROR, e.getMessage(), e);
      }
    }
    return false;
  }

  /**
   * Pushes the file or directory from host machine to device with caching files capability.
   *
   * <p>NOTICE: If the destination is in a temp dir like /data/local/tmp, it'll skip caching.
   * Because contents within this directory may be deleted by the OS, and it won't notify us.
   *
   * @param device the device that the file/dir being pushed to
   * @param pushArgs argument wrapper for pushing file/dir to device
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if failed to run the command, or source file/dir doesn't exist,
   *     or destination path is empty, or failed to get cache property key for pushed file/dir.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public void pushFileOrDir(Device device, FilePushArgs pushArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String srcPathOnHost = pushArgs.srcPathOnHost();
    String desPathOnDevice = pushArgs.desPathOnDevice();
    Duration pushTimeout = pushArgs.pushTimeout().orElse(null);
    boolean prepareDesDirWhenSrcIsFile = pushArgs.prepareDesDirWhenSrcIsFile().orElse(false);

    if (!localFileUtil.isFileOrDirExist(srcPathOnHost) || desPathOnDevice.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_OPERATOR_FILE_NOT_FOUND, "Source/Destination file not found");
    }

    String deviceId = device.getDeviceId();
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    String info =
        String.format(
            "- source path on host: %s%n- destination path on device: %s",
            srcPathOnHost, desPathOnDevice);
    String md5 = null;
    String propertyKey = "";
    boolean cacheFiles = isCacheFilesEnabled(desPathOnDevice);
    if (cacheFiles) {
      // TODO: Create a new class to provide APIs to handle caching pushed dirs/files
      propertyKey =
          getPushedTargetCachedPropertyKey(deviceId, sdkVersion, srcPathOnHost, desPathOnDevice);
      if (propertyKey.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_OPERATOR_ILLEGAL_ARGUMENT,
            String.format(
                "Cannot get pushed target cached property key for device %s%n%s", deviceId, info));
      }
      // Checks whether the file/dir has been pushed.
      md5 = md5Util.fingerprint(srcPathOnHost);
      if (md5.equals(device.getProperty(propertyKey))) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "Skip pushing file/dir on device %s, which has been pushed before:%n%s",
            deviceId,
            info);
        return;
      }
    }
    if (prepareDesDirWhenSrcIsFile && localFileUtil.isFileExist(srcPathOnHost)) {
      prepareDir(device, desPathOnDevice, log);
    }
    // Actual push.
    SharedLogUtil.logMsg(logger, log, "Start pushing file/dir to device %s:%n%s", deviceId, info);
    try {
      String output =
          androidFileUtil.push(
              device.getDeviceId(), sdkVersion, srcPathOnHost, desPathOnDevice, pushTimeout);
      if (cacheFiles) {
        device.setProperty(propertyKey, md5);
      }
      SharedLogUtil.logMsg(
          logger,
          log,
          "Successfully pushed to device %s:%n%s%n%s",
          deviceId,
          output,
          cacheFiles ? String.format("Cached property key/value:%s|%s", propertyKey, md5) : "");
    } catch (MobileHarnessException e) {
      device.info().properties().remove(propertyKey);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_OPERATOR_PUSH_FILE_OR_DIR_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Removes file/dir from device.
   *
   * <p>NOTICE: It doesn't support globbing, i.e., needs to pass explicit path of file/dir.
   *
   * @param device the device on which the file/dir being removed
   * @param fileOrDirPath path of file/dir being removed on device
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public void removeFileOrDir(Device device, String fileOrDirPath, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    fileOrDirPath = PathUtil.removeExtraneousSlashes(fileOrDirPath);
    try {
      if (!androidFileUtil.isFileOrDirExisted(deviceId, fileOrDirPath)) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "File/Dir %s doesn't exist on device %s, skipped removing",
            fileOrDirPath,
            deviceId);
      } else {
        androidFileUtil.removeFiles(deviceId, fileOrDirPath);
        SharedLogUtil.logMsg(
            logger, log, "Removed file/dir [%s] in device %s", fileOrDirPath, deviceId);
      }
    } catch (MobileHarnessException e) {
      if (AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_INVALID_ARGUMENT.equals(e.getErrorId())) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_OPERATOR_REMOVE_FILE_INVALID_ARGUMENT, e.getMessage(), e);
      }
      throw e;
    } finally {
      // TODO: Create a new class to provide APIs to handle caching pushed dirs/files
      if (isCacheFilesEnabled(fileOrDirPath)) {
        String fileCachedPropertyKey = DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + fileOrDirPath;
        ImmutableSet<String> propNamesForAllAncestorDirs =
            getAllAncestorDirs(fileOrDirPath).stream()
                .map(path -> DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + path)
                .collect(ImmutableSet.toImmutableSet());
        for (Entry<String, String> property : device.info().properties().getAll().entrySet()) {
          String propertyName = property.getKey();
          // There are 3 cases it needs to clear properties:
          // 1. Property is cache for the being removed file/dir;
          // 2. Properties are cache for children, grandchildren, or grand grandchildren, etc of the
          // being removed file/dir;
          // 3. Properties are cache for ancestors of the being removed file/dir. This ensures it
          // can push ancestor dir later no matter if the being removed file/dir is part of ancestor
          // dir content.
          if (propertyName.equals(fileCachedPropertyKey)
              || propertyName.startsWith(fileCachedPropertyKey + "/")
              || propNamesForAllAncestorDirs.contains(propertyName)) {
            if (property.getValue() != null) {
              device.info().properties().remove(propertyName);
            }
          }
        }
      }
    }
  }

  /**
   * Prepares {@code fileOrDirPath}'s directory.
   *
   * <p>NOTICE: It doesn't support globbing, i.e., needs to pass explicit path of file/dir.
   *
   * @param device the device on which the dir being created
   * @param fileOrDirPath path of file/dir whose parent directory needs to be prepared
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  private void prepareDir(Device device, String fileOrDirPath, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String dirPath = fileOrDirPath.endsWith("/") ? fileOrDirPath : PathUtil.dirname(fileOrDirPath);
    String deviceId = device.getDeviceId();
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    if (androidFileUtil.isDirExist(deviceId, sdkVersion, dirPath)) {
      SharedLogUtil.logMsg(
          logger, log, "Destination dir %s already existed in device %s", dirPath, deviceId);
      return;
    }
    try {
      androidFileUtil.makeDirectory(deviceId, dirPath);
      SharedLogUtil.logMsg(
          logger,
          log,
          "Creates dir %s for destination %s in device %s",
          dirPath,
          fileOrDirPath,
          deviceId);
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Failed to create dir %s for destination %s in device %s",
          dirPath,
          fileOrDirPath,
          deviceId);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_OPERATOR_PREPARE_DES_DIR_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Returns set of ancestor directories of {@code fileOrDirPath}, including root "/". Only supports
   * absolute path.
   */
  @VisibleForTesting
  ImmutableSet<String> getAllAncestorDirs(String fileOrDirPath) {
    if (!PathUtil.isAbsolute(fileOrDirPath)) {
      return ImmutableSet.of();
    }
    Set<String> parentDirs = new HashSet<>();

    String parentDir = PathUtil.dirname(fileOrDirPath);
    parentDirs.add(parentDir);
    while (!"/".equals(parentDir)) {
      parentDir = PathUtil.dirname(parentDir);
      parentDirs.add(parentDir);
    }
    return ImmutableSet.copyOf(parentDirs);
  }

  /**
   * Gets property key of the file/dir being pushed to device.
   *
   * <p>It caches md5 of pushed file/dir in device properties with pushed file/dir's final path on
   * device as the key.
   *
   * @return {@code String} for propertyKey, or empty string if failed to get pushed file/dir's
   *     destination path on device
   */
  private String getPushedTargetCachedPropertyKey(
      String serial, int sdkVersion, String srcPathOnHost, String desPathOnDevice)
      throws MobileHarnessException, InterruptedException {
    String desFinalPathOnDevice =
        androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            serial, sdkVersion, srcPathOnHost, desPathOnDevice);
    if (desFinalPathOnDevice.isEmpty()) {
      logger.atWarning().log(
          "Cannot get pushed target (src:%s|des:%s) cached property key for device %s.",
          srcPathOnHost, desPathOnDevice, serial);
      return "";
    }

    // Uses the actual result path of adb push as the property key
    return DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + desFinalPathOnDevice;
  }

  private static boolean isCacheFilesEnabled(String desPathOnDevice) {
    return !isDesPathOnDeviceUnderTmpDirs(desPathOnDevice);
  }

  private static boolean isDesPathOnDeviceUnderTmpDirs(String desPathOnDevice) {
    if (TMP_DIRS_ON_DEVICE.contains(desPathOnDevice)) {
      return true;
    }
    return TMP_DIRS_ON_DEVICE.stream()
        .map(path -> path + "/")
        .anyMatch(desPathOnDevice::startsWith);
  }
}
