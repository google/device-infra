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
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListModulesCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanLister;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultLister;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Command for "list" commands. */
@Command(
    name = "list",
    aliases = {"l"},
    sortOptions = false,
    description = "List invocations, devices, modules, etc.",
    subcommands = {
      // Add HelpCommand as a subcommand of "list" command so users can do "list help <subcommand>"
      // to get the usage help message for the <subcommand> in the "list" command.
      HelpCommand.class,
    })
public class ListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;
  private final ResultLister resultLister;
  private final PlanLister planLister;

  @Inject
  ListCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      ServerPreparer serverPreparer,
      AtsSessionStub atsSessionStub,
      ResultLister resultLister,
      PlanLister planLister) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.atsSessionStub = atsSessionStub;
    this.resultLister = resultLister;
    this.planLister = planLister;
  }

  @Override
  public Integer call() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  @Command(
      name = "commands",
      aliases = {"c"},
      description = "List all commands currently waiting to be executed")
  public int commands() {
    consoleUtil.printlnStderr("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "devices",
      aliases = {"d"},
      description =
          "List all detected or known devices. Use \"list devices all\" to list all devices"
              + " including placeholders.")
  public int devices(@Option(names = "all") boolean listAllDevices)
      throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    AtsSessionPluginOutput output =
        atsSessionStub.runShortSession(
            "list_devices_command",
            AtsSessionPluginConfig.newBuilder()
                .setListCommand(
                    SessionPluginProto.ListCommand.newBuilder()
                        .setListDevicesCommand(
                            ListDevicesCommand.newBuilder().setListAllDevices(listAllDevices)))
                .build());
    return PluginOutputPrinter.printOutput(output, consoleUtil);
  }

  @Command(
      name = "invocations",
      aliases = {"i"},
      description = "List all invocation threads")
  public int invocations() throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    ImmutableList<AtsSessionPluginOutput> sessionPluginOutputs =
        atsSessionStub.getAllSessions(
            RunCommand.RUN_COMMAND_SESSION_NAME,
            String.format(
                "%s|%s",
                SessionStatus.SESSION_SUBMITTED.name(), SessionStatus.SESSION_RUNNING.name()));
    String result = PluginOutputPrinter.listInvocations(sessionPluginOutputs);
    consoleUtil.printlnStdout(result);
    return ExitCode.OK;
  }

  @Command(
      name = "modules",
      aliases = {"m"},
      description = "List all modules available")
  public int modules() throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    AtsSessionPluginOutput output =
        atsSessionStub.runShortSession(
            "list_modules_command",
            AtsSessionPluginConfig.newBuilder()
                .setListCommand(
                    SessionPluginProto.ListCommand.newBuilder()
                        .setListModulesCommand(
                            ListModulesCommand.newBuilder()
                                .setXtsRootDir(consoleInfo.getXtsRootDirectory().orElse(""))))
                .build());
    return PluginOutputPrinter.printOutput(output, consoleUtil);
  }

  @Command(
      name = "plans",
      aliases = {"p", "configs"},
      description = "List all plans/configs available")
  public int plans() throws MobileHarnessException {
    consoleUtil.printlnStdout(planLister.listPlans());
    return ExitCode.OK;
  }

  @Command(
      name = "results",
      aliases = {"r"},
      description = "List all results")
  public int results() throws MobileHarnessException {
    consoleUtil.printlnStdout(resultLister.listResults());
    return ExitCode.OK;
  }
}
