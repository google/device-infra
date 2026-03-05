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
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Duration;

/** Periodically scan devices in ROM recovery mode, and trigger cl_rom_recovery to recover them. */
public class PixelRomRecoveryWatcher implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

  private final LocalDeviceManager deviceManager;
  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final LocalFileUtil localFileUtil;

  public PixelRomRecoveryWatcher(LocalDeviceManager deviceManager) {
    this(deviceManager, new CommandExecutor(), Sleeper.defaultSleeper(), new LocalFileUtil());
  }

  @VisibleForTesting
  PixelRomRecoveryWatcher(
      LocalDeviceManager deviceManager,
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      LocalFileUtil localFileUtil) {
    this.deviceManager = deviceManager;
    this.commandExecutor = commandExecutor;
    this.sleeper = sleeper;
    this.localFileUtil = localFileUtil;
  }

  private boolean checkDependencies() {
    // TODO: check if the ROM recovery dependencies are met.
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
      } catch (RuntimeException e) {
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
    logger.atInfo().log("Checking P24 devices in ROM recovery mode.");
  }

  private void recoverP25P26Devices() {
    // TODO: via fastboot to check P25+ devices.
    logger.atInfo().log("Checking P25+ devices in ROM recovery mode.");
  }
}
