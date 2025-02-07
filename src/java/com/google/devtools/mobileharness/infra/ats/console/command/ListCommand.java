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

import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.common.plan.PlanConfigUtil.PlanConfigInfo;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListModulesCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.AtsSessionPluginConfigOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultLister;
import com.google.devtools.mobileharness.infra.ats.console.util.subplan.SubPlanLister;
import com.google.devtools.mobileharness.platform.android.xts.suite.params.ModuleParameters;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command for "list" commands. */
@Command(
    name = "list",
    aliases = {"l"},
    sortOptions = false,
    description = "List invocations, devices, modules, etc.",
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
class ListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;
  private final ResultLister resultLister;
  private final PlanHelper planHelper;
  private final SubPlanLister subPlanLister;
  private final CommandHelper commandHelper;

  @Inject
  ListCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      ServerPreparer serverPreparer,
      AtsSessionStub atsSessionStub,
      ResultLister resultLister,
      PlanHelper planHelper,
      SubPlanLister subPlanLister,
      CommandHelper commandHelper) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.atsSessionStub = atsSessionStub;
    this.resultLister = resultLister;
    this.planHelper = planHelper;
    this.subPlanLister = subPlanLister;
    this.commandHelper = commandHelper;
  }

  @Override
  public Integer call() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  @Command(
      name = "commands",
      aliases = {"c"},
      description = "List all commands currently waiting to be executed")
  public int commands(@Option(names = "all") boolean listAllCommands)
      throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    ImmutableList<AtsSessionPluginConfigOutput> sessionPluginConfigOutputs =
        atsSessionStub.getAllUnfinishedSessions(
            RunCommand.RUN_COMMAND_SESSION_NAME, /* fromCurrentClient= */ !listAllCommands);
    String result = PluginOutputPrinter.listCommands(sessionPluginConfigOutputs);
    consoleUtil.printlnStdout(result);
    return ExitCode.OK;
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
  public int invocations(@Option(names = "all") boolean listAllInvocations)
      throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    ImmutableList<AtsSessionPluginConfigOutput> sessionPluginConfigOutputs =
        atsSessionStub.getAllUnfinishedSessions(
            RunCommand.RUN_COMMAND_SESSION_NAME, /* fromCurrentClient= */ !listAllInvocations);
    String result = PluginOutputPrinter.listInvocations(sessionPluginConfigOutputs);
    consoleUtil.printlnStdout(result);
    return ExitCode.OK;
  }

  @Command(
      name = "modules",
      aliases = {"m"},
      description =
          "List all modules available, or all modules matching the given module parameter.")
  public int modules(
      @Parameters(
              index = "0",
              arity = "0..1",
              paramLabel = "<module_parameter>",
              description = "Supported params: ${COMPLETION-CANDIDATES}")
          ModuleParameters moduleParameter)
      throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();

    ListModulesCommand.Builder listModulesCommandBuilder =
        ListModulesCommand.newBuilder()
            .setXtsRootDir(xtsRootDir.toString())
            .setXtsType(commandHelper.getXtsType());
    if (moduleParameter != null) {
      listModulesCommandBuilder.setModuleParameter(moduleParameter.name());
    }

    AtsSessionPluginOutput output =
        atsSessionStub.runShortSession(
            "list_modules_command",
            AtsSessionPluginConfig.newBuilder()
                .setListCommand(
                    SessionPluginProto.ListCommand.newBuilder()
                        .setListModulesCommand(listModulesCommandBuilder))
                .build());
    return PluginOutputPrinter.printOutput(output, consoleUtil);
  }

  @Command(
      name = "plans",
      aliases = {"p", "configs"},
      description = "List all plans/configs available")
  public int plans() throws MobileHarnessException {
    consoleUtil.printlnStdout(formatPlans(planHelper.listPlans()));
    return ExitCode.OK;
  }

  @Command(
      name = "results",
      aliases = {"r"},
      description = "List all results")
  public int results() throws MobileHarnessException {
    consoleUtil.printlnStdout(resultLister.listResults(commandHelper.getXtsType()));
    return ExitCode.OK;
  }

  @Command(
      name = "subplans",
      aliases = {"s"},
      description = "List all available subplans")
  public int subplans() throws MobileHarnessException {
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    ImmutableList<String> subPlanFileNames =
        subPlanLister.listSubPlans(xtsRootDir.toString(), commandHelper.getXtsType());

    if (subPlanFileNames.isEmpty()) {
      consoleUtil.printlnStdout("No subplans found");
      return ExitCode.OK;
    }

    subPlanFileNames.forEach(consoleUtil::printlnStdout);
    return ExitCode.OK;
  }

  private String formatPlans(ImmutableMap<String, PlanConfigInfo> planConfigInfosByConfigName) {
    return planConfigInfosByConfigName.entrySet().stream()
        .sorted(comparingByKey())
        .map(Entry::getValue)
        .map(
            planConfigInfo ->
                String.format(
                    "\n  %s: %s", planConfigInfo.configName(), planConfigInfo.description()))
        .collect(joining("", "Available plans include:", ""));
  }
}
