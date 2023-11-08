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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;

/** Implementation for {@link AdbInitializeTemplate}. */
public class AdbInitializeTemplateImpl extends AdbInitializeTemplate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEFAULT_STOCK_ADB_PATH = "adb";

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;

  public AdbInitializeTemplateImpl() {
    this(new LocalFileUtil(), new SystemUtil());
  }

  @VisibleForTesting
  AdbInitializeTemplateImpl(LocalFileUtil localFileUtil, SystemUtil systemUtil) {
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
  }

  @Override
  protected void prepareHomeDirAdbKeyFiles() {
    try {
      for (String keyName : AdbInitializeTemplate.ADB_KEY_NAMES) {
        // The adb tools will read the keys from $HOME/.android/adbkey
        String adbKeyDesPath = PathUtil.join(systemUtil.getUserHome(), "/.android/", keyName);
        if (!localFileUtil.isFileExist(adbKeyDesPath)) {
          logger.atWarning().log(
              "The adb key file [%s] does not exist, please follow"
                  + " https://developer.android.com/studio/command-line/adb to set up the ADB"
                  + " command line tool environment.",
              adbKeyDesPath);
        } else {
          logger.atInfo().log("The adb key has already existed: %s", adbKeyDesPath);
        }
      }
    } catch (MobileHarnessException e) {
      logger.atInfo().withCause(e).log(
          "Failed to check adb key files existence: %s", e.getMessage());
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log("Interrupted");
      Thread.currentThread().interrupt();
    }
  }

  @Override
  protected String getStockAdbPath() {
    String adbPath;
    String adbPathFromUser = getAdbPathFromUser();
    if (!adbPathFromUser.isEmpty()) {
      adbPath = adbPathFromUser;
    } else {
      logger.atInfo().log(
          "Flag --adb=</path/to/adb> not specified, use \"%s\" as ADB path",
          DEFAULT_STOCK_ADB_PATH);
      adbPath = DEFAULT_STOCK_ADB_PATH;
    }
    if (!localFileUtil.isFileExistInPath(adbPath)) {
      logger.atWarning().log(
          "Invalid ADB path [%s] (file doesn't exist or isn't in PATH dirs)", adbPath);
    }
    return adbPath;
  }

  @Override
  protected boolean ifKillAdbServer() {
    return getAdbForceKillServer() || !getAdbDontKillServer();
  }

  @Override
  protected void manageAdbServer(String adbPath, String stockAdbPath) {
    CommandExecutor commandExecutor = new CommandExecutor();
    // Sets ADB path for adb.waterfall fallback.
    commandExecutor.updateBaseEnvironment("ANDROID_ADB", stockAdbPath);
    AdbInitializerHelper.manageAdbServer(
        adbPath, commandExecutor, localFileUtil, systemUtil, ifKillAdbServer());
  }

  @Override
  protected ImmutableMap<String, String> getAdbCommandEnvVars(
      String stockAdbPath,
      String adbKeyPath,
      boolean enableAdbLibusb,
      int adbServerPort,
      String adbServerHost) {
    ImmutableMap.Builder<String, String> commandEnvVars =
        ImmutableMap.<String, String>builder()
            .putAll(
                super.getAdbCommandEnvVars(
                    stockAdbPath, adbKeyPath, enableAdbLibusb, adbServerPort, adbServerHost));

    // Always set ADB server port to make sure expected port got used.
    commandEnvVars.put("ANDROID_ADB_SERVER_PORT", String.valueOf(adbServerPort));

    return commandEnvVars.buildOrThrow();
  }
}
