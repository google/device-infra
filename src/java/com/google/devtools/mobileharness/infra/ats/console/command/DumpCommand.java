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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.constant.AtsConsoleDirs;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpEnvVarCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpStackTraceCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpUptimeCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.log.LogDumper;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanHelper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command for "dump" commands. */
@Command(
    name = "dump",
    aliases = {"d"},
    sortOptions = false,
    description = "Dump logs, bugreport, config, etc.",
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
class DumpCommand implements Callable<Integer> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TRADEFED_CLASS_NAME = "CompatibilityConsole";

  @Spec private CommandSpec spec;

  private static final String DUMP_STACK_TRACE_SESSION_NAME = "dump_stack_trace_command";
  private static final SessionPluginProto.DumpCommand DUMP_STACK_TRACE_COMMAND =
      SessionPluginProto.DumpCommand.newBuilder()
          .setDumpStackTraceCommand(DumpStackTraceCommand.getDefaultInstance())
          .build();

  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;
  private final AtsSessionStub atsSessionStub;
  private final LocalFileUtil localFileUtil;
  private final PlanHelper planHelper;
  private final CommandExecutor cmdExecutor;

  @Inject
  DumpCommand(
      ConsoleUtil consoleUtil,
      ServerPreparer serverPreparer,
      AtsSessionStub atsSessionStub,
      LocalFileUtil localFileUtil,
      PlanHelper planHelper,
      CommandExecutor cmdExecutor) {
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
    this.atsSessionStub = atsSessionStub;
    this.localFileUtil = localFileUtil;
    this.planHelper = planHelper;
    this.cmdExecutor = cmdExecutor;
  }

  @Override
  public Integer call() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  @Command(
      name = "bugreport",
      aliases = {"b"},
      description = "Dump a bugreport for the running instance")
  public int bugreport() throws MobileHarnessException, InterruptedException {
    printLogDirs();

    // Copies files to bugreport dir.
    String baseDirPath = PathUtil.join(DirCommon.getTempDirRoot(), "ats_bugreport");
    String fileSuffix = Long.toString(Instant.now().toEpochMilli());
    String bugreportName = "ats_bugreport_" + fileSuffix;
    String bugreportDirPath = PathUtil.join(baseDirPath, bugreportName);
    consoleUtil.printlnStdout("Bugreport dir: %s", bugreportDirPath);
    localFileUtil.prepareDir(bugreportDirPath);

    localFileUtil.copyFileOrDir(
        AtsConsoleDirs.getLogDir(),
        PathUtil.join(bugreportDirPath, String.format("ats_console_logs_%s", fileSuffix)));
    localFileUtil.copyFileOrDir(
        OlcServerDirs.getLogDir(),
        PathUtil.join(bugreportDirPath, String.format("olc_server_logs_%s", fileSuffix)));
    localFileUtil.writeToFile(
        PathUtil.join(
            bugreportDirPath, String.format("ats_console_stack_trace_%s.txt", fileSuffix)),
        MoreThrowables.formatStackTraces());

    String serverStackTraceFilePath =
        PathUtil.join(bugreportDirPath, String.format("olc_server_stack_trace_%s.txt", fileSuffix));
    createServerStackTraceFile(serverStackTraceFilePath);

    String tradefedStackTraceFilePath =
        PathUtil.join(bugreportDirPath, String.format("tradefed_stack_trace_%s.txt", fileSuffix));
    createTradefedStackTraceFile(tradefedStackTraceFilePath);

    // Zips files.
    String bugreportFilePath = PathUtil.join(baseDirPath, String.format("%s.zip", bugreportName));
    localFileUtil.zipDir(bugreportDirPath, bugreportFilePath);
    consoleUtil.printlnStdout("Output bugreport zip in %s", bugreportFilePath);
    return ExitCode.OK;
  }

  @Command(
      name = "config",
      aliases = {"c"},
      description = "Dump the content of the specified config")
  public int config(
      @Parameters(
              index = "0",
              paramLabel = "<config>",
              hideParamSyntax = true,
              description = "Name of the config to dump.")
          String configName) {
    String config = planHelper.loadConfigContent(configName);
    consoleUtil.printlnStdout(config);
    return ExitCode.OK;
  }

  @Command(
      name = "env",
      aliases = {"e"},
      description = "Dump the environment variables available to test harness process")
  public int env() throws MobileHarnessException, InterruptedException {
    return runDumpCommandSessionAndPrint(
        "dump_env_var_command",
        SessionPluginProto.DumpCommand.newBuilder()
            .setDumpEnvVarCommand(DumpEnvVarCommand.getDefaultInstance())
            .build());
  }

  @Command(
      name = "logs",
      aliases = {"l"},
      description = "Dump the logs of all invocations to files")
  public int logs() {
    printLogDirs();
    return ExitCode.OK;
  }

  @Command(
      name = "stack",
      aliases = {"s"},
      description = "Dump the stack traces of a Java process")
  public int stack(
      @Parameters(
              index = "0",
              paramLabel = "<process>",
              defaultValue = "OLC",
              description =
                  "Process to dump stack traces for: [${COMPLETION-CANDIDATES}]. Default value:"
                      + " ${DEFAULT-VALUE}.",
              completionCandidates = ProcessCandidatesToDumpStackTrace.class)
          String processType)
      throws MobileHarnessException, InterruptedException {
    switch (Ascii.toLowerCase(processType)) {
      case "olc":
        return runDumpCommandSessionAndPrint(
            DUMP_STACK_TRACE_SESSION_NAME, DUMP_STACK_TRACE_COMMAND);
      case "tradefed":
        return dumpTradefedStackTrace();
      default:
        throw new ParameterException(
            spec.commandLine(), "Unsupported process type: " + processType);
    }
  }

  @Command(
      name = "uptime",
      aliases = {"u"},
      description = "Dump how long the process has been running")
  public int uptime() throws MobileHarnessException, InterruptedException {
    return runDumpCommandSessionAndPrint(
        "dump_uptime_command",
        SessionPluginProto.DumpCommand.newBuilder()
            .setDumpUptimeCommand(DumpUptimeCommand.getDefaultInstance())
            .build());
  }

  private void printLogDirs() {
    consoleUtil.printlnStdout(LogDumper.dumpLog());
  }

  private void createServerStackTraceFile(String filePath) {
    try {
      Optional<?> tryConnectResult = serverPreparer.tryConnectToOlcServer();
      if (tryConnectResult.isEmpty()) {
        logger.atInfo().log("OLC server isn't running, skip dumping server stack trace");
        return;
      }

      // Gets server stack trace.
      AtsSessionPluginOutput output =
          runDumpCommandSession(DUMP_STACK_TRACE_SESSION_NAME, DUMP_STACK_TRACE_COMMAND);
      String serverStackTrace;
      switch (output.getResultCase()) {
        case SUCCESS:
          serverStackTrace = output.getSuccess().getOutputMessage();
          break;
        case FAILURE:
          logger.atWarning().log(
              "Failed to get server stack trace, reason: %s",
              output.getFailure().getErrorMessage());
          return;
        default:
          logger.atWarning().log(
              "Failed to get server stack trace, plugin_output=[%s]", shortDebugString(output));
          return;
      }

      // Saves server stack trace to file.
      localFileUtil.writeToFile(filePath, serverStackTrace);
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      logger.atWarning().log(
          "Failed to create server stack trace file, error=[%s]", shortDebugString(e));
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void createTradefedStackTraceFile(String filePath) {
    try {
      String tradefedPid = getTradefedPid();

      if (tradefedPid.isEmpty()) {
        logger.atWarning().log("No Tradefed process found. Skip dumping Tradefed stack trace.");
        return;
      }

      String tradefedStackTrace = getTradefedStackTrace(tradefedPid);
      localFileUtil.writeToFile(filePath, tradefedStackTrace);
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      logger.atWarning().log(
          "Failed to create Tradefed stack trace file: %s.", shortDebugString(e));
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Runs an OLC server dump command session (to dump OLC server stack traces, uptime, etc.). */
  private int runDumpCommandSessionAndPrint(
      String sessionName, SessionPluginProto.DumpCommand dumpCommand)
      throws MobileHarnessException, InterruptedException {
    AtsSessionPluginOutput output = runDumpCommandSession(sessionName, dumpCommand);
    return PluginOutputPrinter.printOutput(output, consoleUtil);
  }

  private AtsSessionPluginOutput runDumpCommandSession(
      String sessionName, SessionPluginProto.DumpCommand dumpCommand)
      throws MobileHarnessException, InterruptedException {
    serverPreparer.prepareOlcServer();
    return atsSessionStub.runShortSession(
        sessionName, AtsSessionPluginConfig.newBuilder().setDumpCommand(dumpCommand).build());
  }

  private int dumpTradefedStackTrace() {
    try {
      String tradefedPid = getTradefedPid();

      if (tradefedPid.isEmpty()) {
        consoleUtil.printlnStdout("No Tradefed process found.");
        return ExitCode.SOFTWARE;
      }

      String tradefedStackTrace = getTradefedStackTrace(tradefedPid);
      consoleUtil.printlnStdout(tradefedStackTrace);
      return ExitCode.OK;
    } catch (CommandException | RuntimeException | InterruptedException | Error e) {
      consoleUtil.printlnStdout("Failed to dump Tradefed stack trace: " + e.getMessage());
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return ExitCode.SOFTWARE;
    }
  }

  private String getTradefedPid() throws CommandException, InterruptedException {
    return cmdExecutor.run(
        com.google.devtools.mobileharness.shared.util.command.Command.of(
            "/bin/sh",
            "-c",
            String.format("jps | grep %s | awk '{print $1}'", TRADEFED_CLASS_NAME)));
  }

  private String getTradefedStackTrace(String tradefedPid)
      throws CommandException, InterruptedException {
    return cmdExecutor.run(
        com.google.devtools.mobileharness.shared.util.command.Command.of(
            "/bin/sh", "-c", String.format("jstack %s", tradefedPid)));
  }

  private static class ProcessCandidatesToDumpStackTrace extends ArrayList<String> {
    ProcessCandidatesToDumpStackTrace() {
      super(ImmutableList.of("OLC", "Tradefed"));
    }
  }
}
