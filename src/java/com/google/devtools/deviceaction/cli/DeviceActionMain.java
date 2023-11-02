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

package com.google.devtools.deviceaction.cli;

import static com.google.devtools.deviceaction.common.utils.Constants.CURRENT_VERSION;
import static com.google.devtools.deviceaction.common.utils.Constants.HELP_COMMAND;
import static com.google.devtools.deviceaction.common.utils.Constants.VERSION_COMMAND;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.utils.FlagBasedResourceHelper;
import com.google.devtools.deviceaction.common.utils.FlagParser;
import com.google.devtools.deviceaction.common.utils.HelpUtil;
import com.google.devtools.deviceaction.framework.ActionConfigurer;
import com.google.devtools.deviceaction.framework.DeviceActionModule;
import com.google.devtools.deviceaction.framework.actions.Actions;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Objects;

/** Main entry point of the cli. */
public final class DeviceActionMain {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private DeviceActionMain() {}

  public static void main(String[] args) throws DeviceActionException, InterruptedException {
    if (args.length > 0 && Objects.equals(args[0], VERSION_COMMAND)) {
      System.out.println(CURRENT_VERSION);
      return;
    }

    if (args.length > 0 && Objects.equals(args[0], HELP_COMMAND)) {
      HelpUtil helpUtil = new HelpUtil(System.out);
      if (args.length > 1) {
        helpUtil.help(args[1]);
      } else {
        helpUtil.help();
      }
      return;
    }

    ActionOptions actionOptions;
    try {
      actionOptions = FlagParser.parse(args);
      logger.atInfo().log("Get action options:\n%s", actionOptions);
    } catch (DeviceActionException e) {
      Runtime.getRuntime().exit(1);
      throw e;
    }

    // TODO:b/308534552 - Support using TF quota.
    Injector injector =
        Guice.createInjector(
            new DeviceActionModule(
                FlagBasedResourceHelper.getInstance(), QuotaManager.getInstance()));

    ActionConfig actionConfig =
        injector.getInstance(ActionConfigurer.class).createActionConfigure(actionOptions);
    logger.atInfo().log("Get action config:\n%s", actionConfig);

    Actions actions = injector.getInstance(Actions.class);
    run(actions, actionConfig);
  }

  private static void run(Actions actions, ActionConfig actionConfig)
      throws DeviceActionException, InterruptedException {
    actions.preAction();
    actions.create(actionConfig).perform();
  }
}
