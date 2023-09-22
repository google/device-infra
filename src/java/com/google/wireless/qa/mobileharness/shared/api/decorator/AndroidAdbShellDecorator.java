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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidAdbShellSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAdbShellDecoratorSpec;
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
            + "Commands that end with & are executed asynchronously.")
public class AndroidAdbShellDecorator extends BaseDecorator
    implements SpecConfigable<AndroidAdbShellDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Adb adb;

  @Inject
  AndroidAdbShellDecorator(Driver decoratedDriver, TestInfo testInfo, Adb adb) {
    super(decoratedDriver, testInfo);
    this.adb = adb;
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
      } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
        throw ErrorModelConverter.upgradeMobileHarnessException(e);
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
}
