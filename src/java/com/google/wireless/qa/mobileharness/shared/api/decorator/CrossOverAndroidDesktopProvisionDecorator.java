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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import javax.inject.Inject;

/** Decorator for CrossOver provisioning on Android Desktop. */
@DecoratorAnnotation(help = "CrossOver provisioning decorator for Android Desktop.")
public class CrossOverAndroidDesktopProvisionDecorator extends CrosBaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor commandExecutor;

  @Inject
  CrossOverAndroidDesktopProvisionDecorator(
      Driver driver, TestInfo testInfo, CommandExecutor commandExecutor) {
    super(driver, testInfo);
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void prepare(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.crossoverUseCommandLineFlasher.getNonNull()) {
      testInfo.log().atInfo().alsoTo(logger).log("Running command line based flash...");
      try {
        commandExecutor.run(Command.of("true"));
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.COMMAND_EXEC_FAIL, "Failed to run command line flash.", e);
      }
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "CrossOverAndroidDesktopProvisionDecorator is not ready yet and will be implemented"
                  + " as part of b/487343637. It will use foil-provision CIPD from CTP.");
    }
  }

  @Override
  protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // Do nothing for now.
  }
}
