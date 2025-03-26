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

package com.google.devtools.mobileharness.infra.controller.test.util.atsfileserveruploader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.UrlEscapers;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

/** Lab plugin to upload test gen files to ATS file server. */
@Plugin(type = Plugin.PluginType.LAB)
public class AtsFileServerUploaderPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final String atsFileServer;

  public AtsFileServerUploaderPlugin() {
    this(new LocalFileUtil(), Flags.instance().atsFileServer.getNonNull());
  }

  @VisibleForTesting
  AtsFileServerUploaderPlugin(LocalFileUtil localFileUtil, String atsFileServer) {
    this.localFileUtil = localFileUtil;
    this.atsFileServer = atsFileServer;
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event)
      throws MobileHarnessException, SkipTestException, InterruptedException {
    String genFileDir = event.getTest().getGenFileDir();
    List<String> genFiles = localFileUtil.listFilePaths(genFileDir, true);
    for (String genFile : genFiles) {
      if (genFile.contains(",")) {
        logger.atInfo().log("genFile contains comma: %s", genFile);
        localFileUtil.moveFileOrDir(genFile, genFile.replace(',', '_'));
        genFile = genFile.replace(',', '_');
      }
      updateGenFile(genFileDir, genFile, event.getTest().locator().getId());
    }
  }

  private void updateGenFile(String genFileDir, String genFile, String testId)
      throws InterruptedException {
    String relativePath = PathUtil.makeRelative(genFileDir, genFile);
    String destination =
        String.join(
            "/",
            atsFileServer,
            "file",
            "genfiles",
            testId,
            UrlEscapers.urlFragmentEscaper().escape(relativePath));

    try {
      destination = new URI(destination).normalize().toString();
      String output =
          createCommandExecutor()
              .run(
                  Command.of(
                          "curl",
                          "--request",
                          "POST",
                          "--form",
                          "file=@" + genFile,
                          "--fail",
                          "--location",
                          destination)
                      .timeout(Duration.ofMinutes(30)));
      logger.atInfo().log("Output for uploading file %s to %s: %s", genFile, destination, output);
    } catch (URISyntaxException e) {
      logger.atWarning().withCause(e).log("Invalid url address %s in file server.", destination);
    } catch (CommandException e) {
      if (e instanceof CommandFailureException) {
        CommandResult result = ((CommandFailureException) e).result();
        logger.atWarning().log(
            "Logs of failed ATS file uploader: STDOUT: %s\nSTDERR: %s",
            result.stdout(), result.stderr());
      }
      logger.atWarning().withCause(e).log(
          "Failed to upload file %s to %s in ATS file server.", genFile, atsFileServer);
    }
  }

  @VisibleForTesting
  CommandExecutor createCommandExecutor() {
    return new CommandExecutor();
  }
}
