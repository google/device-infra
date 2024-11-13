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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidAdbShellSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAdbShellDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Driver decorator for running some ADB shell commands before/after the test is run. It only
 * accepts "adb shell" commands.
 */
@DecoratorAnnotation(
    help =
        "For running some ADB shell commands before/after the test is run. "
            + "Commands that end with & are executed asynchronously."
            + "Commands that start with CACHE_DEVICE are executed without "
            + "interrupting the test due to potential device disconnection "
            + "caused by the command.")
public class AndroidAdbShellDecorator extends BaseDecorator
    implements SpecConfigable<AndroidAdbShellDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Adb adb;

  private final AndroidSystemStateUtil systemStateUtil;

  private final SystemStateManager systemStateManager;

  private static final String CACHE_DEVICE_PREFIX = "CACHE_DEVICE:";

  /** Timeout of probing device/emulator readiness. */
  private static final Duration DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);

  @Inject
  @VisibleForTesting
  AndroidAdbShellDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Adb adb,
      AndroidSystemStateUtil systemStateUtil,
      SystemStateManager systemStateManager) {
    super(decoratedDriver, testInfo);
    this.adb = adb;
    this.systemStateUtil = systemStateUtil;
    this.systemStateManager = systemStateManager;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    AndroidAdbShellDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    boolean ignoreError = spec.getAdbShellIgnoreError();

    List<CommandProcess> asyncCommands = new ArrayList<>();
    Duration syncCommandTimeout = Duration.ofSeconds(spec.getAdbShellSyncCommandTimeoutSec());

    Duration remainingTime = testInfo.timer().remainingTimeJava();
    if (syncCommandTimeout.compareTo(remainingTime) > 0) {
      logger.atInfo().log(
          "Adjusting the timeout %s to test remaining time %s", syncCommandTimeout, remainingTime);
      syncCommandTimeout = remainingTime;
    }

    try {
      // Before test.
      Iterable<String> commandsBeforeTest =
          AndroidAdbShellSpec.parseCommands(spec.getAdbShellBeforeTest());
      asyncCommands.addAll(
          runCommands(deviceId, commandsBeforeTest, testInfo, ignoreError, syncCommandTimeout));

      try {
        // Runs the actual test.
        getDecorated().run(testInfo);
      } finally {
        // After test.
        Iterable<String> commandsAfterTest =
            AndroidAdbShellSpec.parseCommands(spec.getAdbShellAfterTest());
        try {
          asyncCommands.addAll(
              runCommands(deviceId, commandsAfterTest, testInfo, ignoreError, syncCommandTimeout));
        } catch (MobileHarnessException e) {
          testInfo.warnings().addAndLog(e, logger);
        }
      }

    } finally {
      // After test completes stop all async commands.
      for (CommandProcess commandProcess : asyncCommands) {
        commandProcess.kill();
      }
    }
  }

  private ImmutableList<CommandProcess> runCommands(
      String serial,
      Iterable<String> commands,
      TestInfo testInfo,
      boolean ignoreError,
      Duration syncCommandTimeout)
      throws MobileHarnessException, InterruptedException {
    if (Iterables.isEmpty(commands)) {
      return ImmutableList.of();
    }
    List<CommandProcess> asyncCommands = new ArrayList<>();
    for (String command : commands) {
      String output = null;
      try {
        // If the command ends with & then execute it asynchronously.
        if (command.endsWith("&")) {
          // Remove the & suffix and execute the command in a separate thread.
          command = command.substring(0, command.length() - 1);
          logger.atInfo().log("Running adb shell command in background: %s", command);
          asyncCommands.add(
              adb.runShellAsync(serial, command, testInfo.timer().remainingTimeJava()));
        } else if (command.startsWith(CACHE_DEVICE_PREFIX)) {
          // Remove the prefix
          command = command.substring(CACHE_DEVICE_PREFIX.length());
          output =
              runShellCommandWithCaching(
                  serial, command, getDevice(), syncCommandTimeout, testInfo, ignoreError);
        } else {
          logger.atInfo().log("Running adb shell command: %s", command);
          output = adb.runShell(serial, command, syncCommandTimeout);
        }
      } catch (MobileHarnessException e) {
        if (ignoreError) {
          testInfo.warnings().addAndLog(e, logger);
        } else {
          // This causes the test to fail.
          // Stop any running async commands before rethrowing exception.
          for (CommandProcess commandProcess : asyncCommands) {
            commandProcess.kill();
          }

          throw e;
        }
      }
      if (!Strings.isNullOrEmpty(output)) {
        testInfo.log().atInfo().alsoTo(logger).log("%s", output);
      }
    }
    return ImmutableList.copyOf(asyncCommands);
  }

  private String runShellCommandWithCaching(
      String serial,
      String command,
      Device device,
      Duration syncCommandTimeout,
      TestInfo testInfo,
      boolean ignoreError)
      throws MobileHarnessException, InterruptedException {
    String output = null;

    // Caching duration is twice the DEVICE_READY_TIMEOUT as after disconnection of device
    // waitForState and waitUntilReady each has timeout set to DEVICE_READY_TIMEOUT
    Duration cacheDuration = DEVICE_READY_TIMEOUT.multipliedBy(2);
    DeviceCache.getInstance().cache(serial, device.getClass().getSimpleName(), cacheDuration);
    try {
      logger.atInfo().log("Running adb shell command: %s", command);
      output = adb.runShell(serial, command, syncCommandTimeout);
      waitForDeviceReady(serial, testInfo, ignoreError);
    } catch (MobileHarnessException e) {
      if (ignoreError) {
        testInfo.warnings().addAndLog(e, logger);
        // Added waitForDeviceReady here, as sometimes command runs successfully but due
        // to non-zero exit codes from the command it throws an error. To avoid this we
        // can make ignoreError=true.
        waitForDeviceReady(serial, testInfo, ignoreError);
      } else {
        // This causes the test to fail.
        throw e;
      }
    } finally {
      DeviceCache.getInstance().invalidateCache(serial);
    }
    systemStateManager.becomeRoot(device);
    return output;
  }

  private void waitForDeviceReady(String serial, TestInfo testInfo, boolean ignoreError)
      throws MobileHarnessException, InterruptedException {
    try {
      // `adb wait-for-state` exits with error code when using a proxied device. Therefore, we wait
      // for the device ready by using `systemStateUtil.waitUntilReady` instead.
      if (!DeviceUtil.isOverTcpDevice(serial)) {
        systemStateUtil.waitForState(serial, DeviceConnectionState.DEVICE, DEVICE_READY_TIMEOUT);
      }
      systemStateUtil.waitUntilReady(serial, DEVICE_READY_TIMEOUT);

    } catch (MobileHarnessException e) {
      if (ignoreError) {
        testInfo.warnings().addAndLog(e, logger);
      } else {
        // This causes the test to fail.
        throw e;
      }
    }
  }
}
