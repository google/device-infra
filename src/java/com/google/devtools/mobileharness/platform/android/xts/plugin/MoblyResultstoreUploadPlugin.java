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
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyPythonVenvUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Lab plugin to upload Mobly test results to Resultstore. */
@Plugin(type = PluginType.LAB)
public final class MoblyResultstoreUploadPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(20);

  private static final String MOBLY_ANDROID_PARTNER_TOOLS_PACKAGE = "mobly-android-partner-tools";
  private static final String RESULTS_UPLOADER_BIN = "results_uploader";

  private static final Pattern RESULT_STORE_LINK_PATTERN =
      Pattern.compile("(https?://btx\\.cloud\\.google\\.com/\\S+)");

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
  public void onTestStarting(TestStartingEvent event)
      throws SkipTestException, InterruptedException {
    TestInfo testInfo = event.getTest();
    testInfo.log().atInfo().alsoTo(logger).log("Verifying Resultstore upload prerequisites.");

    try {
      commandExecutor.run(Command.of("which", "gcloud"));
    } catch (CommandException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("gcloud is not installed on the host.");
      throw SkipTestException.create(
          "gcloud is not installed on the host. Follow"
              + " https://github.com/android/mobly-android-partner-tools/blob/main/README.md#first-time-setup"
              + " to install gcloud and set up authentication",
          DesiredTestResult.ERROR,
          ExtErrorId.MOBLY_RESULTSTORE_UPLOAD_PRECHECK_FAILED);
    }

    try {
      commandExecutor.run(
          Command.of("gcloud", "auth", "application-default", "print-access-token"));
    } catch (CommandException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to get gcloud auth token.");
      throw SkipTestException.create(
          "Failed to get gcloud auth token. Follow"
              + " https://github.com/android/mobly-android-partner-tools/blob/main/README.md#first-time-setup"
              + " to set up gcloud auth",
          DesiredTestResult.ERROR,
          ExtErrorId.MOBLY_RESULTSTORE_UPLOAD_PRECHECK_FAILED);
    }
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event) throws InterruptedException {
    TestInfo testInfo = event.getTest();
    testInfo.log().atInfo().alsoTo(logger).log("Starting Mobly result upload to Resultstore.");
    Path genFileDir;
    Path tmpFileDir;
    try {
      genFileDir = Path.of(event.getTest().getGenFileDir());
      tmpFileDir = Path.of(event.getTest().getTmpFileDir());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to get test gen or tmp file dir.");
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
      String moduleName = event.getTest().jobInfo().properties().get("xts_module_name");
      if (moduleName != null) {
        uploadCmd = uploadCmd.argsAppended("--label", moduleName);
      }
      try {
        String stdout = commandExecutor.run(uploadCmd);
        logger.atInfo().log("Resultstore upload stdout: %s", stdout);
        String link = findResultStoreLink(stdout);
        if (link != null) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Successfully uploaded results to Resultstore. %s", link);
          if (moduleName != null) {
            saveLinkToJson(
                link,
                moduleName,
                genFileDir.resolve(MoblyConstant.TestGenOutput.MOBLY_LOG_DIR),
                testInfo);
          }
        }
      } catch (CommandException e) {
        testInfo
            .log()
            .atWarning()
            .withCause(e)
            .alsoTo(logger)
            .log("Failed to run results_uploader command.");
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to set up Python venv and install packages for result uploading.");
    }
  }

  @Nullable
  private String findResultStoreLink(String output) {
    Matcher matcher = RESULT_STORE_LINK_PATTERN.matcher(output);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private void saveLinkToJson(String link, String moduleName, Path moblyLogDir, TestInfo testInfo) {
    Path reportLogDir = moblyLogDir.resolve("report-log-files");
    try {
      localFileUtil.prepareDir(reportLogDir);
      Path reportLogFile = reportLogDir.resolve(moduleName + ".reportlog.json");

      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("resultstore_link", link);
      String jsonContent = new Gson().toJson(jsonObject);

      localFileUtil.writeToFile(reportLogFile.toString(), jsonContent);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("Failed to save ResultStore link to JSON file.");
    }
  }
}
