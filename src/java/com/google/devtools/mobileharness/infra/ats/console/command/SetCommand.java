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
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.SetLogLevelRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;

/** Command to set console configurations. */
@Command(
    name = "set",
    aliases = {"s"},
    sortOptions = false,
    description = "Set console configurations.",
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
class SetCommand {

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final ControlStub controlStub;

  @Inject
  SetCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      ServerPreparer serverPreparer,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.controlStub = controlStub;
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
  public int setPythonPackageIndexUrl(String pythonPackageIndexUrl) {
    if (!StrUtil.isEmptyOrWhitespace(pythonPackageIndexUrl)) {
      consoleInfo.setPythonPackageIndexUrl(pythonPackageIndexUrl.trim());
      consoleUtil.printlnStdout(
          "Base URL of Python Package Index now set to '%s'.", pythonPackageIndexUrl.trim());
    }

    return ExitCode.OK;
  }
}
