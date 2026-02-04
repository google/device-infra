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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerLogPrinter;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DeviceType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ModuleMetadataFilterEntry;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;

/** Command to run xTS tests. */
@Command(
    name = "run",
    aliases = {"r"},
    sortOptions = false,
    description = "Run xTS tests.",
    footer = {
      "%nAlternatively you can enter @|fg(yellow) <config>|@ right after \"run\" command which"
          + " will achieve same result.%n",
    },
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
public final class RunCommand implements Callable<Integer> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting @Mixin RunCommandOptions options;

  static final String RUN_COMMAND_SESSION_NAME = "run_command";

  private static final Pattern LINE_DATETIME_START_PATTERN =
      Pattern.compile("^\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d");

  // Set the job start timeout to 36500 days for jobs created by cmdfile.
  // To promise it will never expire because of the start timeout for these jobs.
  private static final Duration CMDFILE_JOBS_START_TIMEOUT = Duration.ofDays(36500L);

  private static final AtomicInteger RUNNING_COMMAND_COUNT = new AtomicInteger(0);

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final CommandHelper commandHelper;
  private final ResultListerHelper resultListerHelper;

  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;
  private final ListeningExecutorService executorService;
  private final AtsSessionStub atsSessionStub;
  private final CommandExecutor commandExecutor;
  private final XtsCommandUtil xtsCommandUtil;

  @Inject
  RunCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      CommandHelper commandHelper,
      ResultListerHelper resultListerHelper,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter,
      ListeningExecutorService executorService,
      AtsSessionStub atsSessionStub,
      CommandExecutor commandExecutor,
      XtsCommandUtil xtsCommandUtil) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.commandHelper = commandHelper;
    this.resultListerHelper = resultListerHelper;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
    this.executorService = executorService;
    this.atsSessionStub = atsSessionStub;
    this.commandExecutor = commandExecutor;
    this.xtsCommandUtil = xtsCommandUtil;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException {
    try {
      checkState(Flags.instance().enableAtsConsoleOlcServer.getNonNull());
      if (options.showHelp || options.showHelpAll) {
        return showHelpMessage(
            commandHelper.getXtsType(), consoleInfo.getXtsRootDirectoryNonEmpty());
      } else {
        options.validateCommandParameters();
        ImmutableList<String> command = consoleInfo.getLastCommand();
        return runWithOlcServerPrepared(command);
      }
    } finally {
      options.moduleTestOptionsGroups = null; // Resets the group to clear the history
    }
  }

  /** Returns the number of currently running RunCommand instances. */
  public static int getRunningRunCommandCount() {
    return RUNNING_COMMAND_COUNT.get();
  }

  @VisibleForTesting
  int showHelpMessage(String xtsType, Path xtsRootDir)
      throws CommandException, InterruptedException {
    Path xtsToolsDir = XtsDirUtil.getXtsToolsDir(xtsRootDir, xtsType);
    String result =
        commandExecutor.run(
            com.google.devtools.mobileharness.shared.util.command.Command.of(
                    getXtsJavaCommand(
                        xtsType,
                        xtsRootDir,
                        ImmutableList.of(),
                        xtsToolsDir.resolve("tradefed.jar")
                            + ":"
                            + xtsToolsDir.resolve(xtsType + "-tradefed.jar"),
                        ImmutableList.of(
                            "run",
                            "commandAndExit",
                            options.config,
                            options.showHelp ? "--help" : "--help-all")))
                .timeout(Timeout.fixed(Duration.ofSeconds(60))));
    Iterable<String> lines = Splitters.LINE_SPLITTER.split(result);

    // A successful output of the help should start with the line like "'cts'
    // configuration:"(include) and stop with the line like "Received shutdown request."(exclude).
    // So only print the lines between these two lines.
    // If a line starts with date time like "02-23 11:11:11", it means this line is the
    // log of tradefed command and not part of the help message. So need to remove it.
    // A failed output of the help should only include a line starting with "Failed to run
    // command:".
    boolean printing = false;
    StringBuilder output = new StringBuilder();
    for (String line : lines) {
      if (LINE_DATETIME_START_PATTERN.matcher(line).find()) {
        continue;
      }
      if (printing) {
        if (line.endsWith("Received shutdown request.")) {
          break;
        }
        output.append(line).append("\n");
      } else if (line.startsWith("'" + options.config + "' configuration:")) {
        output.append(line).append("\n");
        printing = true;
      } else if (line.startsWith("Failed to run command:")) {
        output.append(line).append("\n");
        break;
      }
    }
    // Remove the last newline character because printLnStdout will add a new line.
    consoleUtil.printlnStdout(
        !output.isEmpty() ? output.deleteCharAt(output.length() - 1).toString() : "");

    return ExitCode.OK;
  }

  private int runWithOlcServerPrepared(ImmutableList<String> command)
      throws InterruptedException, MobileHarnessException {
    serverPreparer.prepareOlcServer();
    return runWithCommand(command);
  }

  @VisibleForTesting
  int runWithCommand(ImmutableList<String> command)
      throws InterruptedException, MobileHarnessException {
    ImmutableList<String> deviceSerials =
        options.serialOpt != null ? ImmutableList.copyOf(options.serialOpt) : ImmutableList.of();
    ImmutableList<String> excludeSerials =
        options.excludeSerialOpt != null
            ? ImmutableList.copyOf(options.excludeSerialOpt)
            : ImmutableList.of();
    ImmutableList<String> productTypes =
        this.options.productTypes == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.options.productTypes);
    ImmutableMap<String, String> devicePropertiesMap =
        this.options.devicePropertiesMap == null
            ? ImmutableMap.of()
            : ImmutableMap.copyOf(this.options.devicePropertiesMap);

    ImmutableList<String> modules = options.getModules();

    String test = options.getTest();
    ImmutableList<String> includeFilters =
        this.options.includeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.options.includeFilters);
    ImmutableList<String> excludeFilters =
        this.options.excludeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.options.excludeFilters);
    ImmutableList<String> strictIncludeFilters =
        this.options.strictIncludeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.options.strictIncludeFilters);

    ImmutableMultimap<String, String> moduleMetadataIncludeFilters =
        this.options.moduleMetadataIncludeFilters == null
            ? ImmutableMultimap.of()
            : ImmutableMultimap.copyOf(this.options.moduleMetadataIncludeFilters);
    ImmutableMultimap<String, String> moduleMetadataExcludeFilters =
        this.options.moduleMetadataExcludeFilters == null
            ? ImmutableMultimap.of()
            : ImmutableMultimap.copyOf(this.options.moduleMetadataExcludeFilters);

    ImmutableList<String> moduleArgs =
        options.moduleCmdArgs != null
            ? ImmutableList.copyOf(options.moduleCmdArgs)
            : ImmutableList.of();
    ImmutableList<String> extraArgs =
        options.extraRunCmdArgs != null
            ? ImmutableList.copyOf(options.extraRunCmdArgs)
            : ImmutableList.of();
    ImmutableSet<String> excludeRunners =
        options.excludeRunnerOpt != null
            ? ImmutableSet.copyOf(options.excludeRunnerOpt)
            : ImmutableSet.of();

    Path xtsRootDirectory = consoleInfo.getXtsRootDirectoryNonEmpty();
    String xtsType = commandHelper.getXtsType();
    // Asynchronously runs the session.
    SessionPluginProto.RunCommand.Builder runCommand =
        SessionPluginProto.RunCommand.newBuilder()
            .setXtsRootDir(xtsRootDirectory.toString())
            .setXtsType(xtsType)
            .addAllDeviceSerial(deviceSerials)
            .addAllExcludeDeviceSerial(excludeSerials)
            .addAllProductType(productTypes)
            .putAllDeviceProperty(devicePropertiesMap)
            .addAllIncludeFilter(includeFilters)
            .addAllExcludeFilter(excludeFilters)
            .addAllStrictIncludeFilter(strictIncludeFilters)
            .setHtmlInZip(options.htmlInZip)
            .setReportSystemCheckers(options.reportSystemCheckers)
            .putAllXtsSuiteInfo(
                new CertificationSuiteInfoFactory()
                    .generateSuiteInfoMap(xtsRootDirectory.toString(), xtsType, options.config));
    moduleMetadataIncludeFilters.forEach(
        (key, value) ->
            runCommand.addModuleMetadataIncludeFilter(
                ModuleMetadataFilterEntry.newBuilder().setKey(key).setValue(value)));
    moduleMetadataExcludeFilters.forEach(
        (key, value) ->
            runCommand.addModuleMetadataExcludeFilter(
                ModuleMetadataFilterEntry.newBuilder().setKey(key).setValue(value)));
    options.isSkipDeviceInfo().ifPresent(runCommand::setSkipDeviceInfo);
    if (options.shardCount > 0) {
      runCommand.setShardCount(options.shardCount);
    }
    if (consoleInfo.getPythonPackageIndexUrl().isPresent()) {
      runCommand.setPythonPkgIndexUrl(consoleInfo.getPythonPackageIndexUrl().get());
    }
    runCommand.setEnableTokenSharding(options.enableTokenSharding);

    runCommand
        .setTestPlan(options.config)
        .addAllModuleName(modules)
        .addAllModuleArg(moduleArgs)
        .addAllExtraArg(extraArgs)
        .addAllExcludeRunner(excludeRunners);
    if (!test.isEmpty()) {
      runCommand.setTestName(test);
    }
    if (!isNullOrEmpty(options.subPlanName)) {
      runCommand.setSubPlanName(options.subPlanName);
    }
    if (options.retrySessionIndex != null) {
      // As the command parameters validation ensures only one of retrySessionIndex or
      // retrySessionResultDirName is set, so at this point retrySessionResultDirName is empty. Now
      // we get the retrySessionResultDirName by the retrySessionIndex and will use
      // retrySessionResultDirName to locate the previous result later.
      options.retrySessionResultDirName = getRetrySessionResultDirName(options.retrySessionIndex);
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Retry session index [%s] mapped to retry session result dir name [%s]",
              options.retrySessionIndex, options.retrySessionResultDirName);
    }
    if (!isNullOrEmpty(options.retrySessionResultDirName)) {
      runCommand.setRetrySessionResultDirName(options.retrySessionResultDirName.trim());
    }
    if (options.retryType != null) {
      runCommand.setRetryType(Ascii.toUpperCase(options.retryType.name()));
    }
    if (Flags.instance().enableXtsDynamicDownloader.getNonNull()) {
      runCommand.setEnableXtsDynamicDownload(true);
    }
    if (Flags.instance().enableMoblyResultstoreUpload.getNonNull()) {
      runCommand.setEnableMoblyResultstoreUpload(true);
    }
    if (options.deviceTypeOptionsGroup != null) {
      if (options.deviceTypeOptionsGroup.runTestOnEmulator) {
        runCommand.setDeviceType(DeviceType.EMULATOR);
      }
      if (options.deviceTypeOptionsGroup.runTestOnRealDevice) {
        runCommand.setDeviceType(DeviceType.REAL_DEVICE);
      }
    }
    if (options.requireBatteryCheck) {
      if (options.maxBattery != null) {
        runCommand.setMaxBatteryLevel(options.maxBattery);
      }
      if (options.minBattery != null) {
        runCommand.setMinBatteryLevel(options.minBattery);
      }
    }
    if (options.requireBatteryTemperatureCheck) {
      if (options.maxBatteryTemperature != null) {
        runCommand.setMaxBatteryTemperature(options.maxBatteryTemperature);
      }
    }
    if (options.minSdk != null) {
      runCommand.setMinSdkLevel(options.minSdk);
    }
    if (options.maxSdk != null) {
      runCommand.setMaxSdkLevel(options.maxSdk);
    }
    if (Flags.instance().enableCtsVerifierResultReporter.getNonNull()) {
      runCommand.setEnableCtsVerifierResultReporter(true);
    }
    if (consoleInfo.isFromCommandFile()) {
      runCommand.setJobStartTimeout(toProtoDuration(CMDFILE_JOBS_START_TIMEOUT));
    }
    runCommand.setEnableDefaultLogs(Objects.equals(options.enableDefaultLogs, true));

    ImmutableList<String> commandLineArgs = command.stream().skip(1L).collect(toImmutableList());
    runCommand.setInitialState(
        RunCommandState.newBuilder()
            .setCommandLineArgs(String.join(" ", commandLineArgs))
            .addAllSeparatedCommandLineArgs(commandLineArgs));

    if (!Flags.instance().enableAtsConsoleOlcServerLog.getNonNull()) {
      serverLogPrinter.enable(true);
    }
    ListenableFuture<AtsSessionPluginOutput> atsRunSessionFuture =
        atsSessionStub.runSession(
            RUN_COMMAND_SESSION_NAME,
            AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build());
    RUNNING_COMMAND_COUNT.incrementAndGet();
    addCallback(
        atsRunSessionFuture,
        new RunCommandFutureCallback(consoleUtil, serverLogPrinter),
        executorService);
    consoleUtil.printlnStdout("Command submitted.");
    return ExitCode.OK;
  }

  private ImmutableList<String> getXtsJavaCommand(
      String xtsType,
      Path xtsRootDir,
      ImmutableList<String> jvmFlags,
      String concatenatedJarPath,
      ImmutableList<String> xtsRunCommandArgs) {
    Path javaBinary = xtsCommandUtil.getJavaBinary(xtsType, xtsRootDir);
    return xtsCommandUtil.getTradefedJavaCommand(
        javaBinary.toString(),
        jvmFlags,
        concatenatedJarPath,
        ImmutableList.of(XtsCommandUtil.getXtsRootJavaProperty(xtsType, xtsRootDir)),
        XtsCommandUtil.getConsoleClassName(),
        xtsRunCommandArgs);
  }

  /** Future callback which prints {@code AtsSessionPluginOutput} to console. */
  private static class RunCommandFutureCallback implements FutureCallback<AtsSessionPluginOutput> {

    private final ConsoleUtil consoleUtil;
    private final ServerLogPrinter serverLogPrinter;

    public RunCommandFutureCallback(ConsoleUtil consoleUtil, ServerLogPrinter serverLogPrinter) {
      this.consoleUtil = consoleUtil;
      this.serverLogPrinter = serverLogPrinter;
    }

    @Override
    public void onSuccess(AtsSessionPluginOutput output) {
      try {
        PluginOutputPrinter.printOutput(output, consoleUtil);
      } finally {
        disableServerLogPrinterIfNecessary();
      }
    }

    @Override
    public void onFailure(Throwable error) {
      try {
        logger
            .atWarning()
            .with(IMPORTANCE, IMPORTANT)
            .withCause(error)
            .log("Failed to execute command");
      } finally {
        disableServerLogPrinterIfNecessary();
      }
    }

    private void disableServerLogPrinterIfNecessary() {
      int runningCommandCount = RUNNING_COMMAND_COUNT.decrementAndGet();
      if (!Flags.instance().enableAtsConsoleOlcServerLog.getNonNull() && runningCommandCount == 0) {
        try {
          serverLogPrinter.enable(false);
        } catch (MobileHarnessException | InterruptedException e) {
          logger
              .atWarning()
              .with(IMPORTANCE, IMPORTANT)
              .withCause(e)
              .log("Failed to disable server log printer");
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
  }

  @VisibleForTesting
  List<String> getProductTypes() {
    return this.options.productTypes;
  }

  @VisibleForTesting
  Map<String, String> getDevicePropertiesMap() {
    return this.options.devicePropertiesMap;
  }

  @VisibleForTesting
  List<String> getSerials() {
    return this.options.serialOpt;
  }

  @VisibleForTesting
  List<String> getExtraRunCmdArgs() {
    return this.options.extraRunCmdArgs;
  }

  @VisibleForTesting
  Multimap<String, String> getModuleMetadataIncludeFilters() {
    return this.options.moduleMetadataIncludeFilters;
  }

  @VisibleForTesting
  Multimap<String, String> getModuleMetadataExcludeFilters() {
    return this.options.moduleMetadataExcludeFilters;
  }

  /**
   * Gets the retry session result dir name for the given retry session index.
   *
   * @throws IllegalArgumentException if the retry session index is invalid.
   * @throws MobileHarnessException if fails to find all result directories.
   */
  @VisibleForTesting
  String getRetrySessionResultDirName(int retrySessionIndex) throws MobileHarnessException {
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    String resultsDir =
        XtsDirUtil.getXtsResultsDir(xtsRootDir, commandHelper.getXtsType()).toString();
    ImmutableList<File> allResultDirs = resultListerHelper.listResultDirsInOrder(resultsDir);
    if (retrySessionIndex < 0 || retrySessionIndex >= allResultDirs.size()) {
      throw new IllegalArgumentException(
          String.format(
              "The given retry session index %s is out of index. The session index range is [%d,"
                  + " %d)",
              retrySessionIndex, 0, allResultDirs.size()));
    }
    return allResultDirs.get(retrySessionIndex).getName();
  }
}
