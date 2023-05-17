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
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.DumpStackCommand;
import com.google.devtools.atsconsole.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Command for "dump" commands. */
@Command(
    name = "dump",
    aliases = {"d"},
    sortOptions = false,
    description = "Dump logs, bugreport, config, etc.")
public class DumpCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;

  @Inject
  DumpCommand(
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
      name = "bugreport",
      aliases = {"b"},
      description = "Dump a bugreport for the running instance")
  public int bugreport() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(name = "commandQueue", description = "Dump the contents of the commmand execution queue")
  public int commandQueue() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "commands",
      description = "Dump all the config XML for the commands waiting to be executed")
  public int commands() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "config",
      aliases = {"c"},
      description = "Dump the content of the specified config")
  public int config() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "env",
      aliases = {"e"},
      description = "Dump the environment variables available to test harness process")
  public int env() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "logs",
      aliases = {"l"},
      description = "Dump the logs of all invocations to files")
  public int logs() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "stack",
      aliases = {"s"},
      description = "Dump the stack traces of all threads")
  public int stack() throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    AtsSessionPluginOutput output =
        atsSessionStub.runShortSession(
            "dump_stack_command",
            AtsSessionPluginConfig.newBuilder()
                .setDumpCommand(
                    SessionPluginProto.DumpCommand.newBuilder()
                        .setDumpStackCommand(DumpStackCommand.getDefaultInstance()))
                .build());
    return PluginOutputPrinter.printOutput(output, consoleUtil);
  }

  @Command(
      name = "uptime",
      aliases = {"u"},
      description = "Dump how long the process has been running")
  public int uptime() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }
}
