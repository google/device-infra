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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.AtsSessionPluginConfigOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsResponse;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;

/** Command for "invocation" commands. */
@Command(
    name = "invocation",
    aliases = {"i"},
    sortOptions = false,
    description = "Invocation information and operations.",
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
class InvocationCommand implements Callable<Integer> {

  @Parameters(
      index = "0",
      paramLabel = "<command_id>",
      hideParamSyntax = true,
      description = "Command ID.")
  private String commandId;

  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;

  @Inject
  InvocationCommand(
      ConsoleUtil consoleUtil, ServerPreparer serverPreparer, AtsSessionStub atsSessionStub) {
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.atsSessionStub = atsSessionStub;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    ImmutableList<AtsSessionPluginConfigOutput> sessionPluginConfigOutputs =
        atsSessionStub.getAllUnfinishedSessions(
            RunCommand.RUN_COMMAND_SESSION_NAME, /* fromCurrentClient= */ true);
    return PluginOutputPrinter.showCommandInvocations(
        sessionPluginConfigOutputs, commandId, consoleUtil);
  }

  @Command(name = "stop", description = "Stop the given invocation.")
  public Integer stop() throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    AbortSessionsResponse response = atsSessionStub.abortSessionByCommandId(commandId);
    if (response.getSessionIdList().isEmpty()) {
      consoleUtil.printlnStdout(
          String.format(
              "Could not stop invocation %s, try 'list invocation'"
                  + " or 'invocation %s' for more information.",
              commandId, commandId));
    } else {
      consoleUtil.printlnStdout(
          String.format(
              "Invocation %s has been requested to stop. It may take some times.", commandId));
    }
    return ExitCode.OK;
  }
}
