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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;
import java.util.Set;

/** Periodically scan devices in ROM recovery mode, and trigger cl_rom_recovery to recover them. */
public class PixelRomRecoveryWatcher implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

  private static final String SERVICE_ACCOUNT_JSON_PRIVATE_KEY_PATH =
      "/usr/local/google/mobileharness/android_tradefed_test_key.json";
  private static final String CL_ROM_RECOVERY_PATH =
      "/usr/local/google/mobileharness/ate/cl_rom_recovery";
  private static final String ADB_PATH = "/usr/local/google/mobileharness/ate/adb";

  private static final Duration RECOVERY_COMMAND_TIMEOUT = Duration.ofMinutes(2);

  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final LocalFileUtil localFileUtil;
  private final Fastboot fastboot;

  public PixelRomRecoveryWatcher() {
    this(new CommandExecutor(), Sleeper.defaultSleeper(), new LocalFileUtil(), new Fastboot());
  }

  @VisibleForTesting
  PixelRomRecoveryWatcher(
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      LocalFileUtil localFileUtil,
      Fastboot fastboot) {
    this.commandExecutor = commandExecutor;
    this.sleeper = sleeper;
    this.localFileUtil = localFileUtil;
    this.fastboot = fastboot;
  }

  private boolean checkDependencies() {
    if (!localFileUtil.isFileExist(SERVICE_ACCOUNT_JSON_PRIVATE_KEY_PATH)) {
      logger.atWarning().log(
          "Missing dependency: Service account key file not found at %s",
          SERVICE_ACCOUNT_JSON_PRIVATE_KEY_PATH);
      return false;
    }
    if (!localFileUtil.isFileExist(CL_ROM_RECOVERY_PATH)) {
      logger.atWarning().log(
          "Missing dependency: cl_rom_recovery not found at %s", CL_ROM_RECOVERY_PATH);
      return false;
    }
    if (!localFileUtil.isFileExist(ADB_PATH)) {
      logger.atWarning().log("Missing dependency: adb not found at %s", ADB_PATH);
      return false;
    }
    return true;
  }

  @Override
  public void run() {
    if (!checkDependencies()) {
      logger.atInfo().log("Dependencies not met, PixelROMRecoveryWatcher will not run.");
      return;
    }
    while (!Thread.currentThread().isInterrupted()) {
      try {
        logger.atInfo().log("Checking devices in ROM recovery mode.");
        recoverP24Devices();
        recoverP25P26Devices();
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted");
        Thread.currentThread().interrupt();
        break;
      } catch (RuntimeException | MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to run one round of ROM recovery");
      }

      try {
        sleeper.sleep(CHECK_INTERVAL);
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted");
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void recoverP24Devices() {
    // TODO: via lsusb to check P24- devices.
    logger.atInfo().log("[NOT IMPLEMENTED] Checking P24 devices in ROM recovery mode.");
  }

  private void recoverP25P26Devices() throws InterruptedException, MobileHarnessException {
    logger.atInfo().log("Checking P25+ devices in ROM recovery mode.");

    Set<String> fastbootDeviceSerials = fastboot.getDeviceSerials();
    logger.atInfo().log("fastbootDeviceSerials: %s", fastbootDeviceSerials);
    if (fastbootDeviceSerials.isEmpty()) {
      logger.atInfo().log("No P25+ devices in ROM recovery mode found.");
      return;
    }

    for (String serial : fastbootDeviceSerials) {
      String productInfo;
      try {
        productInfo = fastboot.getVar(serial, FastbootProperty.PRODUCT).trim();
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "getvar product failed for %s, skipping device", serial);
        continue;
      }

      switch (productInfo) {
        case "lga" -> {
          logger.atInfo().log("P25 ROM recovery device detected: %s", serial);
          recoverDevice(serial, "laguna");
        }
        case "mbu" -> {
          logger.atInfo().log("P26 ROM recovery device detected: %s", serial);
          recoverDevice(serial, "malibu");
        }
        default -> logger.atInfo().log("Skipping device %s (Not ROM Recovery device)", serial);
      }
    }
  }

  private void recoverDevice(String serial, String config) throws InterruptedException {
    try {
      CommandResult commandResult =
          commandExecutor.exec(
              Command.of(
                      CL_ROM_RECOVERY_PATH,
                      "--noautoupdate",
                      "--private_key_file",
                      SERVICE_ACCOUNT_JSON_PRIVATE_KEY_PATH,
                      "--config",
                      config,
                      "--serial",
                      serial,
                      "--adbpath",
                      ADB_PATH)
                  .timeout(RECOVERY_COMMAND_TIMEOUT));
      logger.atInfo().log("Command result: %s", commandResult);
      logger.atInfo().log("Recovery attempt finished for %s", serial);
    } catch (CommandTimeoutException e) {
      logger.atWarning().withCause(e).log("Recovery timed out for %s", serial);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Recovery failed for %s", serial);
    }
  }
}
