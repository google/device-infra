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

package com.google.devtools.mobileharness.infra.ats.console.command;

import com.google.common.base.Ascii;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/** Command to set console configurations. */
@Command(
    name = "set",
    aliases = {"s"},
    sortOptions = false,
    description = "Set console configurations.",
    subcommands = {
      // Add HelpCommand as a subcommand of "set" command so users can do "set help <subcommand>"
      // to get the usage help message for the <subcommand> in the "set" command.
      HelpCommand.class,
    })
public class SetCommand implements Callable<Integer> {

  @Option(
      names = "--mobly_testcases_dir",
      description = "Directory contains all being run Mobly testcases in zip file format.")
  private String moblyTestCasesDir;

  @Option(names = "--results_dir", description = "Directory in which the test results are saved.")
  private String resultsDir;

  private final ConsoleInfo consoleInfo;
  private final LocalFileUtil localFileUtil;
  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final ControlStub controlStub;

  @Inject
  SetCommand(
      ConsoleInfo consoleInfo,
      LocalFileUtil localFileUtil,
      ConsoleUtil consoleUtil,
      ServerPreparer serverPreparer,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub) {
    this.consoleInfo = consoleInfo;
    this.localFileUtil = localFileUtil;
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.controlStub = controlStub;
  }

  @Override
  public Integer call() {
    @SuppressWarnings("ShortCircuitBoolean")
    boolean allSuccess = setMoblyTestCasesDir() & setResultsDir();
    return allSuccess ? ExitCode.OK : ExitCode.SOFTWARE;
  }

  @Command(name = "log-level-display", description = "Sets the global display log level to <level>")
  public int setLogLevelDisplay(String level)
      throws MobileHarnessException, GrpcExceptionWithErrorId, InterruptedException {
    serverPreparer.prepareOlcServer();
    controlStub.setLogLevel(SetLogLevelRequest.newBuilder().setLevel(level).build());
    consoleUtil.printlnStdout("Log level now set to '%s'.", Ascii.toUpperCase(level));
    return ExitCode.OK;
  }

  @Command(
      name = "python-package-index-url",
      description =
          "Sets the global base URL of python package index to <python-package-index-url>")
  public int setPythonPackageIndexUrl(String pythonPackageIndexUrl)
      throws MobileHarnessException, InterruptedException {
    if (!StrUtil.isEmptyOrWhitespace(pythonPackageIndexUrl)) {
      consoleInfo.setPythonPackageIndexUrl(pythonPackageIndexUrl.trim());
      consoleUtil.printlnStdout(
          "Base URL of Python Package Index now set to '%s'.", pythonPackageIndexUrl.trim());
    }

    return ExitCode.OK;
  }

  private boolean setMoblyTestCasesDir() {
    if (moblyTestCasesDir != null) {
      moblyTestCasesDir = consoleUtil.completeHomeDirectory(moblyTestCasesDir);
      if (!moblyTestCasesDir.isEmpty() && localFileUtil.isDirExist(moblyTestCasesDir)) {
        consoleInfo.setMoblyTestCasesDir(moblyTestCasesDir);
      } else {
        consoleUtil.printlnStderr(
            "Directory '%s' doesn't exist, please confirm and retry.", moblyTestCasesDir);
        return false;
      }
    }
    return true;
  }

  private boolean setResultsDir() {
    if (resultsDir != null) {
      resultsDir = consoleUtil.completeHomeDirectory(resultsDir);
      if (!resultsDir.isEmpty() && localFileUtil.isDirExist(resultsDir)) {
        consoleInfo.setResultsDirectory(resultsDir);
      } else {
        consoleUtil.printlnStderr(
            "Directory '%s' doesn't exist, please confirm and retry.", resultsDir);
        return false;
      }
    }
    return true;
  }
}
