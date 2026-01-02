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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyPythonVenvUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import java.nio.file.Path;
import java.time.Duration;

/** Lab plugin to upload Mobly test results to Resultstore. */
@Plugin(type = PluginType.LAB)
public final class MoblyResultstoreUploadPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(20);

  private static final String MOBLY_ANDROID_PARTNER_TOOLS_PACKAGE = "mobly-android-partner-tools";
  private static final String RESULTS_UPLOADER_BIN = "results_uploader";

  private final MoblyPythonVenvUtil moblyPythonVenvUtil;
  private final CommandExecutor commandExecutor;
  private final LocalFileUtil localFileUtil;

  public MoblyResultstoreUploadPlugin() {
    this(new MoblyPythonVenvUtil(), new CommandExecutor(), new LocalFileUtil());
  }

  @VisibleForTesting
  MoblyResultstoreUploadPlugin(
      MoblyPythonVenvUtil moblyPythonVenvUtil,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil) {
    this.moblyPythonVenvUtil = moblyPythonVenvUtil;
    this.commandExecutor = commandExecutor;
    this.localFileUtil = localFileUtil;
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event) throws InterruptedException {
    logger.atInfo().log("Starting Mobly result upload to Resultstore.");
    Path genFileDir;
    Path tmpFileDir;
    try {
      genFileDir = Path.of(event.getTest().getGenFileDir());
      tmpFileDir = Path.of(event.getTest().getTmpFileDir());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get test gen or tmp file dir.");
      return;
    }

    Path venvPath = tmpFileDir.resolve("venv");
    try {
      localFileUtil.prepareDir(venvPath);
      Path sysPythonBin = moblyPythonVenvUtil.getPythonPath(null);
      Path venvPythonBin = moblyPythonVenvUtil.createVenv(sysPythonBin, venvPath);
      moblyPythonVenvUtil.installPackageFromPypi(
          venvPythonBin, MOBLY_ANDROID_PARTNER_TOOLS_PACKAGE);

      Path resultsUploaderBin = moblyPythonVenvUtil.resolveVenvBin(venvPath, RESULTS_UPLOADER_BIN);
      Command uploadCmd =
          Command.of(
                  resultsUploaderBin.toString(),
                  genFileDir.toString(),
                  "--abort_if_no_creds",
                  "--label",
                  event.getTest().locator().getId())
              .timeout(UPLOAD_TIMEOUT);
      if (event.getTest().jobInfo().properties().has("xts-module-name")) {
        uploadCmd =
            uploadCmd.argsAppended(
                "--label", event.getTest().jobInfo().properties().get("xts-module-name"));
      }
      try {
        commandExecutor.run(uploadCmd);
        logger.atInfo().log("Successfully uploaded results to Resultstore.");
      } catch (CommandException e) {
        logger.atWarning().withCause(e).log("Failed to run results_uploader command.");
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to set up Python venv and install packages for result uploading.");
    }
  }
}
