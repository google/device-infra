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

package com.google.devtools.atsconsole.command;

import com.google.devtools.atsconsole.ConsoleUtil;
import com.google.devtools.atsconsole.controller.olcserver.AtsSessionStub;
import com.google.devtools.atsconsole.controller.olcserver.ServerPreparer;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.atsconsole.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Command for "list" commands. */
@Command(
    name = "list",
    aliases = {"l"},
    sortOptions = false,
    description = "List invocations, devices, modules, etc.")
public class ListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;

  @Inject
  ListCommand(
      ConsoleUtil consoleUtil, ServerPreparer serverPreparer, AtsSessionStub atsSessionStub) {
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.atsSessionStub = atsSessionStub;
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

  @Command(name = "configs", description = "List all known configurations")
  public int configs() {
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
  public int invocations() {
    consoleUtil.printlnStderr("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "modules",
      aliases = {"m"},
      description = "List all modules available")
  public int modules() {
    consoleUtil.printlnStderr("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "plans",
      aliases = {"p"},
      description = "List all plans available")
  public int plans() {
    consoleUtil.printlnStderr("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "results",
      aliases = {"r"},
      description = "List all results")
  public int results() {
    consoleUtil.printlnStderr("Unimplemented");
    return ExitCode.SOFTWARE;
  }
}
