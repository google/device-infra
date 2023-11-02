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

package com.google.devtools.deviceaction.lib;

import static com.google.devtools.deviceaction.common.utils.Constants.CURRENT_VERSION;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.utils.ProtoHelper;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.common.utils.StaticResourceHelper;
import com.google.devtools.deviceaction.framework.ActionConfigurer;
import com.google.devtools.deviceaction.framework.DeviceActionModule;
import com.google.devtools.deviceaction.framework.actions.Actions;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.nio.file.Path;

/**
 * A utility class to use device action in device infra.
 *
 * <p>Before calling the action method, make sure initializing the class and do device cache.
 */
public class DeviceActionUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final StaticResourceHelper.Factory factory;

  private ActionConfigurer actionConfigurer;

  private Actions actions;

  DeviceActionUtil(StaticResourceHelper.Factory factory) {
    this.factory = factory;
  }

  public DeviceActionUtil(Adb adb, Aapt aapt, Path javaBin) {
    this(new StaticResourceHelper.Factory(adb, aapt, javaBin));
  }

  /**
   * Initializes the {@link DeviceActionUtil} to get all classes for a device action.
   *
   * <p>It uses a Guice module to bind all classes.
   */
  public void initialize(Path tmpFileDir, Path genFileDir, Path bundletoolJar, Path credFile)
      throws MobileHarnessException {
    try {
      ResourceHelper resourceHelper =
          factory.create(tmpFileDir, genFileDir, bundletoolJar, credFile);
      Injector injector =
          Guice.createInjector(new DeviceActionModule(resourceHelper, QuotaManager.getInstance()));
      actionConfigurer = injector.getInstance(ActionConfigurer.class);
      actions = injector.getInstance(Actions.class);
    } catch (DeviceActionException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ACTION_RESOURCE_CREATE_ERROR,
          "Failed to bind the device action module.",
          e);
    }
    logger.atInfo().log("The current version is %s", CURRENT_VERSION);
  }

  /** Installs mainline modules as specified by {@code spec} to the device {@code uuid}. */
  public void installMainline(String uuid, InstallMainlineSpec spec)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessExceptions.check(
        !Strings.isNullOrEmpty(uuid),
        AndroidErrorId.DEVICE_ACTION_VALIDATION_FAILURE,
        () -> "Uuid null or empty");
    runAction(getInstallMainlineConfig(uuid, spec));
  }

  /** Resets the device {@code uuid} as specified by {@code spec}. */
  public void reset(String uuid, ResetSpec spec)
      throws InterruptedException, MobileHarnessException {
    MobileHarnessExceptions.check(
        !Strings.isNullOrEmpty(uuid),
        AndroidErrorId.DEVICE_ACTION_VALIDATION_FAILURE,
        () -> "Uuid null or empty");
    runAction(getResetConfig(uuid, spec));
  }

  private ActionConfig getInstallMainlineConfig(String uuid, InstallMainlineSpec spec)
      throws MobileHarnessException, InterruptedException {
    ActionSpec initialSpec = ProtoHelper.getActionSpecForInstallMainline(spec, uuid);
    try {
      ActionConfig actionConfig =
          actionConfigurer.createActionConfigure(Command.INSTALL_MAINLINE, initialSpec);
      logger.atInfo().log("Get action config:\n%s", actionConfig);
      return actionConfig;
    } catch (DeviceActionException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ACTION_CONFIG_CREATE_ERROR,
          "Failed to get the action config for spec " + initialSpec,
          e);
    }
  }

  private ActionConfig getResetConfig(String uuid, ResetSpec spec)
      throws MobileHarnessException, InterruptedException {
    ActionSpec initialSpec = ProtoHelper.getActionSpecForReset(spec, uuid);
    try {
      ActionConfig actionConfig =
          actionConfigurer.createActionConfigure(Command.RESET, initialSpec);
      logger.atInfo().log("Get action config:\n%s", actionConfig);
      return actionConfig;
    } catch (DeviceActionException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ACTION_CONFIG_CREATE_ERROR,
          "Failed to get the action config for spec " + initialSpec,
          e);
    }
  }

  private void runAction(ActionConfig actionConfig)
      throws MobileHarnessException, InterruptedException {
    try {
      actions.create(actionConfig).perform();
    } catch (DeviceActionException e) {
      throw new MobileHarnessException(
          AndroidErrorId.DEVICE_ACTION_EXECUTION_ERROR,
          "Failed to execute the action for " + actionConfig,
          e);
    }
  }
}
