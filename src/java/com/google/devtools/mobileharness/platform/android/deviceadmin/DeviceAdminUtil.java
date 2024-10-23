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

package com.google.devtools.mobileharness.platform.android.deviceadmin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.util.UUID;

/** Utility class for controlling Android device admin. */
public class DeviceAdminUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor commandExecutor;
  private final String deviceAdminCliPath;
  private final String kmsKeyName;
  private final String credPath;
  private final String adminAppPath;
  private final SystemUtil systemUtil;

  private static final String ACTION_INSTALL = "INSTALL";
  private static final String ACTION_ENABLE = "ENABLE";
  private static final String ACTION_LOCK = "LOCK";
  private static final String ACTION_UNLOCK = "UNLOCK";

  public DeviceAdminUtil() {
    this(
        new CommandExecutor(),
        new SystemUtil(),
        Flags.instance().deviceAdminCliPath.getNonNull(),
        Flags.instance().deviceAdminKmsKey.getNonNull(),
        Flags.instance().deviceAdminKmsKeyCred.getNonNull(),
        Flags.instance().deviceAdminApkPath.getNonNull());
  }

  @VisibleForTesting
  DeviceAdminUtil(
      CommandExecutor commandExecutor,
      SystemUtil systemUtil,
      String deviceAdminCliPath,
      String kmsKeyName,
      String credPath,
      String adminAppPath) {
    this.commandExecutor = commandExecutor;
    this.systemUtil = systemUtil;
    this.deviceAdminCliPath = deviceAdminCliPath;
    this.kmsKeyName = kmsKeyName;
    this.credPath = credPath;
    this.adminAppPath = adminAppPath;
  }

  /**
   * Installs the device admin app on the device.
   *
   * @param deviceId the serial number of the device
   */
  public void install(String deviceId) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Installing device admin app on %s", deviceId);
    try {
      exec(deviceId, ACTION_INSTALL);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ADMIN_UTIL_INSTALL_ERROR,
          "Fail to install the device admin app",
          e);
    }
  }

  /**
   * Enables the device admin on the device.
   *
   * @param deviceId the serial number of the device
   */
  public void enable(String deviceId) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Enabling device admin on %s", deviceId);
    try {
      exec(deviceId, ACTION_ENABLE);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ADMIN_UTIL_ENABLE_ERROR, "Fail to enable the device admin app", e);
    }
  }

  /**
   * Locks the device with device admin to enable user restrictions and hide specific apps.
   *
   * @param deviceId the serial number of the device
   */
  public void lock(String deviceId) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Locking device %s with device admin", deviceId);
    try {
      exec(deviceId, ACTION_LOCK);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ADMIN_UTIL_LOCK_ERROR,
          "Fail to lock the device with device admin",
          e);
    }
  }

  /**
   * Unlocks the device to remove user restrictions and allows the device to be reset.
   *
   * @param deviceId the serial number of the device
   */
  public void unlock(String deviceId) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Unlocking device %s with device admin", deviceId);
    try {
      exec(deviceId, ACTION_UNLOCK);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ADMIN_UTIL_UNLOCK_ERROR,
          "Fail to unlock the device with device admin",
          e);
    }
  }

  /**
   * Sets up and locks the device with device admin.
   *
   * <p>This action will install the device admin app and lock the device.
   *
   * @param deviceId the serial number of the device
   */
  public void setupAndLock(String deviceId) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Setup and lock device %s", deviceId);
    install(deviceId);
    enable(deviceId);
    lock(deviceId);
  }

  private void exec(String deviceId, String action)
      throws MobileHarnessException, InterruptedException {
    Command command =
        Command.of(
            systemUtil.getJavaBin(),
            "-jar",
            deviceAdminCliPath,
            "--action=" + action,
            "--serial=" + deviceId,
            "--kms_key_name=" + kmsKeyName,
            "--credentials_path=" + credPath);

    if (action.equals(ACTION_INSTALL)) {
      command = command.argsAppended("--admin_app_path=" + adminAppPath);
    }
    String commandId = UUID.randomUUID().toString();
    logger.atInfo().log(
        "Device admin util is running command [CID=%s] on %s: %s", commandId, deviceId, command);
    String output = commandExecutor.run(command);
    logger.atInfo().log(
        "Device admin command output [CID=%s, DEVICE=%s]: %s", commandId, deviceId, output);
  }
}
