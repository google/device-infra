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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.addCallback;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerLogPrinter;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.PluginOutputPrinter;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.MoblyResultInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlResultFormatter;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlResultUtil;
import com.google.devtools.mobileharness.infra.ats.console.testbed.config.YamlTestbedUpdater;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospPackageTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.FileNameUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command to run CTS tests. */
@Command(
    name = "run",
    aliases = {"r"},
    sortOptions = false,
    description = "Run CTS tests.",
    footer = {
      "%nAlternatively you can enter @|fg(yellow) <config>|@ right after \"run\" command which"
          + " will achieve same result.%n",
    },
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
final class RunCommand implements Callable<Integer> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final AtomicInteger RUNNING_COMMAND_COUNT = new AtomicInteger(0);

  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "<config>",
      hideParamSyntax = true,
      description = "CTS test config/plan.")
  private String config;

  @ArgGroup(exclusive = false, multiplicity = "0..*")
  private List<ModuleTestOptionsGroup> moduleTestOptionsGroups;

  static class ModuleTestOptionsGroup {
    @Option(
        names = {"-m", "--module"},
        required = true,
        paramLabel = "<test_module_name>",
        description = "Run the specified module.")
    String module;

    @Option(
        names = {"-t", "--test"},
        required = false,
        paramLabel = "<test_case_name>",
        description = "Run the specified test case.")
    String test;
  }

  @Option(
      names = {"-s", "--serial"},
      paramLabel = "<device_id>",
      description = "Run test on the specific device.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> serialOpt;

  @Option(
      names = {"--serials"},
      split = ",",
      paramLabel = "<device_id>",
      description =
          "A list serial numbers of Android devices used for multiple-device test(separated by"
              + " comma). For example, `--serials <device_a>,<device_b>`. No spaces around the"
              + " comma. Note: the order of pass-in device serial numbers will be kept when passing"
              + " them to the test infra.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> androidDeviceSerialList;

  @Option(
      names = {"--shard-count"},
      paramLabel = "<number_of_shards>",
      description =
          "Shard a CTS run into given number of independent chunks, to run on multiple devices in"
              + " parallel.")
  private int shardCount;

  @Option(
      names = {"--include-filter"},
      paramLabel = "\"<test_module_name> <test_name>\"",
      description =
          "Run with the specified modules, or test packages, classes, and cases. For example, run"
              + " cts --include-filter \"CtsCalendarcommon2TestCases"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" includes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> includeFilters;

  @Option(
      names = {"--exclude-filter"},
      paramLabel = "\"<test_module_name> <test_name>\"",
      description =
          "Exclude the specified modules, or test packages, classes, and cases, from the run. For"
              + " example, run cts --exclude-filter \"CtsCalendarcommon2Test"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" excludes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> excludeFilters;

  @Option(
      names = {"--exit-after-run"},
      paramLabel = "<exit_after_run>",
      description = "If true, exit the console after the command finishes. Default is false.")
  private boolean exitAfterRun;

  @Parameters(index = "1..*", hidden = true)
  private List<String> extraRunCmdArgs;

  @Spec private CommandSpec spec;

  static final String RUN_COMMAND_SESSION_NAME = "run_command";

  private static final String CTSV_CONFIG = "cts-v";
  private static final String DEFAULT_MOBLY_LOGPATH = "/tmp/logs/mobly";
  private static final String MOBLY_TEST_ZIP_DEFAULT_TEST = "suite_main.py";

  private static final String RUN_RETRY_SESSION_ID = "retry_session_id";
  private static final String RUN_RETRY_TYPE = "retry_type";

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final CommandHelper commandHelper;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final FileNameUtil fileNameUtil;
  private final CommandExecutor commandExecutor;
  private final YamlTestbedUpdater yamlTestbedUpdater;
  private final XmlResultFormatter xmlResultFormatter;
  private final XmlResultUtil xmlResultUtil;
  private final MoblyAospPackageTestSetupUtil moblyAospTestSetupUtil;

  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;
  private final ListeningExecutorService executorService;
  private final AtsSessionStub atsSessionStub;

  @Inject
  RunCommand(
      ConsoleInfo consoleInfo,
      CommandExecutor commandExecutor,
      ConsoleUtil consoleUtil,
      CommandHelper commandHelper,
      AndroidAdbInternalUtil androidAdbInternalUtil,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      FileNameUtil fileNameUtil,
      YamlTestbedUpdater yamlTestbedUpdater,
      XmlResultFormatter xmlResultFormatter,
      XmlResultUtil xmlResultUtil,
      MoblyAospPackageTestSetupUtil moblyAospTestSetupUtil,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter,
      ListeningExecutorService executorService,
      AtsSessionStub atsSessionStub) {
    this.consoleInfo = consoleInfo;
    this.commandExecutor = commandExecutor;
    this.consoleUtil = consoleUtil;
    this.commandHelper = commandHelper;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.fileNameUtil = fileNameUtil;
    this.yamlTestbedUpdater = yamlTestbedUpdater;
    this.xmlResultFormatter = xmlResultFormatter;
    this.xmlResultUtil = xmlResultUtil;
    this.moblyAospTestSetupUtil = moblyAospTestSetupUtil;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
    this.executorService = executorService;
    this.atsSessionStub = atsSessionStub;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException, IOException {
    try {
      if (Flags.instance().enableAtsConsoleOlcServer.getNonNull()) {
        validateCommandParameters();
        return runInM1(/* isRunRetry= */ false, /* extraRunRetryCmdArgs= */ ImmutableMap.of());
      } else {
        validateM0Config();
        return runInM0();
      }
    } finally {
      moduleTestOptionsGroups = null; // reset the group to clear the history
    }
  }

  @Command(
      name = "retry",
      description =
          "Retry all the tests that failed or weren't executed from the previous sessions.")
  public int execRunRetry(
      @Option(
              names = "--retry",
              required = true,
              description =
                  "Id for the retry session. Use @|bold list results|@ to get the session id.")
          int sessionId,
      @Option(names = "--retry-type", description = "Supported values: ${COMPLETION-CANDIDATES}")
          RetryType retryType)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap.Builder<String, String> extraRunRetryCmdArgs = ImmutableMap.builder();
    extraRunRetryCmdArgs.put(RUN_RETRY_SESSION_ID, String.valueOf(sessionId));
    if (retryType != null) {
      extraRunRetryCmdArgs.put(RUN_RETRY_TYPE, Ascii.toUpperCase(retryType.name()));
    }
    return runInM1(/* isRunRetry= */ true, extraRunRetryCmdArgs.build());
  }

  private void validateCommandParameters() {
    if (isNullOrEmpty(config)) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(
              "Param @|fg(yellow) <config>|@ right after 'run' command is required.\n"));
    }

    if (moduleTestOptionsGroups != null && !moduleTestOptionsGroups.isEmpty()) {
      ImmutableList<String> tests =
          moduleTestOptionsGroups.stream()
              .map(group -> group.test)
              .filter(test -> test != null)
              .collect(toImmutableList());
      if (tests.size() > 1) {
        throw new ParameterException(
            spec.commandLine(),
            Ansi.AUTO.string("Only at most one test case could be specified.\n"));
      }
      if (tests.size() == 1 && moduleTestOptionsGroups.size() > 1) {
        throw new ParameterException(
            spec.commandLine(),
            Ansi.AUTO.string("Multiple modules are unsupported if a test case is specified.\n"));
      }
    }
  }

  private int runInM0() throws MobileHarnessException, InterruptedException, IOException {
    if (Objects.equals(config, CTSV_CONFIG)) {
      if (!checkCtsvPreparation()) {
        return ExitCode.SOFTWARE;
      }
      ImmutableMap<String, ModuleTestOptionsGroup> moduleTestOptionsGroupMap =
          getModuleTestOptionsGroupMap();

      ImmutableList<MoblyTestRunEntry> moblyTestRunEntries = getMoblyTestRunEntries();

      // Filter Mobly test zips per command line options
      if (!moduleTestOptionsGroupMap.isEmpty()) {
        moblyTestRunEntries =
            moblyTestRunEntries.stream()
                .filter(
                    moblyTestRunEntry ->
                        moduleTestOptionsGroupMap.containsKey(moblyTestRunEntry.name()))
                .collect(toImmutableList());
      }

      if (moblyTestRunEntries.isEmpty()) {
        consoleUtil.printlnStderr(
            "Found no match Mobly test zip(s) under directory [%s], skip running.",
            consoleInfo.getMoblyTestCasesDir().orElse(null));
        return ExitCode.OK;
      }

      ImmutableList<String> serials = getDeviceSerialsBeforeTest();
      if (serials.isEmpty()) {
        logger.atWarning().log(
            "Found no matched and connected Android devices on the host, skip running.");
        return ExitCode.OK;
      }

      String moblyConfigFile =
          yamlTestbedUpdater.prepareMoblyConfig(
              serials, consoleInfo.getMoblyTestCasesDir().orElseThrow(), null);

      // Mobly log root directory contains all logs from each Mobly test zip run
      String moblyLogDir = prepareMoblyLogDir();
      ImmutableMap.Builder<String, String> moblyTestSummaryYamlFilesBuilder =
          ImmutableMap.builder();
      Instant startTime = Clock.systemUTC().instant();
      for (MoblyTestRunEntry moblyTestRunEntry : moblyTestRunEntries) {
        Optional<Path> moblyTestSummaryYaml =
            runSingleMoblyTestZip(moblyTestRunEntry, moblyConfigFile, moblyLogDir);
        moblyTestSummaryYaml.ifPresent(
            path ->
                moblyTestSummaryYamlFilesBuilder.put(moblyTestRunEntry.name(), path.toString()));
      }
      Instant endTime = Clock.systemUTC().instant();

      ImmutableMap<String, String> moblyTestSummaryYamlFiles =
          moblyTestSummaryYamlFilesBuilder.buildOrThrow();
      if (!moblyTestSummaryYamlFiles.isEmpty()) {
        xmlResultFormatter.writeMoblyResults(
            MoblyResultInfo.of(
                moblyTestSummaryYamlFiles,
                xmlResultUtil.prepareResultElementAttrs(startTime, endTime, serials),
                xmlResultUtil.prepareBuildElementAttrs(serials.get(0))),
            moblyLogDir);
        String resultZipDes = moblyLogDir + ".zip";
        logger.atInfo().log(
            "Zipping Mobly result directory \"%s\" to zip file \"%s\"...",
            moblyLogDir, resultZipDes);
        localFileUtil.zipDir(moblyLogDir, resultZipDes);
        logger.atInfo().log("Zipping Mobly result directory \"%s\" done", moblyLogDir);
      } else {
        logger.atWarning().log("Found no Mobly test summary yaml files after the test run.");
      }
    }
    return ExitCode.OK;
  }

  private int runInM1(boolean isRunRetry, ImmutableMap<String, String> extraRunRetryCmdArgs)
      throws InterruptedException, MobileHarnessException {
    serverPreparer.prepareOlcServer();

    ImmutableList<String> deviceSerials =
        serialOpt != null ? ImmutableList.copyOf(serialOpt) : ImmutableList.of();

    ImmutableList<String> modules =
        moduleTestOptionsGroups != null
            ? moduleTestOptionsGroups.stream().map(group -> group.module).collect(toImmutableList())
            : ImmutableList.of();

    String test =
        moduleTestOptionsGroups != null && moduleTestOptionsGroups.get(0).test != null
            ? moduleTestOptionsGroups.get(0).test
            : "";
    ImmutableList<String> includeFilters =
        this.includeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.includeFilters);
    ImmutableList<String> excludeFilters =
        this.excludeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.excludeFilters);

    ImmutableList<String> extraArgs =
        extraRunCmdArgs != null ? ImmutableList.copyOf(extraRunCmdArgs) : ImmutableList.of();

    String xtsRootDirectory = consoleInfo.getXtsRootDirectory().orElse("");
    XtsType xtsType = commandHelper.getXtsType(xtsRootDirectory);
    // Asynchronously runs the session.
    SessionPluginProto.RunCommand.Builder runCommand =
        SessionPluginProto.RunCommand.newBuilder()
            .setXtsRootDir(xtsRootDirectory)
            .setXtsType(xtsType)
            .addAllDeviceSerial(deviceSerials)
            .addAllIncludeFilter(includeFilters)
            .addAllExcludeFilter(excludeFilters);
    if (shardCount > 0) {
      runCommand.setShardCount(shardCount);
    }
    if (consoleInfo.getPythonPackageIndexUrl().isPresent()) {
      runCommand.setPythonPkgIndexUrl(consoleInfo.getPythonPackageIndexUrl().get());
    }

    if (isRunRetry) {
      runCommand.setTestPlan("retry");
      if (extraRunRetryCmdArgs.containsKey(RUN_RETRY_SESSION_ID)) {
        runCommand.setRetrySessionId(
            Integer.parseInt(extraRunRetryCmdArgs.get(RUN_RETRY_SESSION_ID)));
      }
      if (extraRunRetryCmdArgs.containsKey(RUN_RETRY_TYPE)) {
        runCommand.setRetryType(extraRunRetryCmdArgs.get(RUN_RETRY_TYPE));
      }
    } else {
      runCommand.setTestPlan(config).addAllModuleName(modules).addAllExtraArg(extraArgs);
      if (!test.isEmpty()) {
        runCommand.setTestName(test);
      }
    }

    serverLogPrinter.enable(true);
    ListenableFuture<AtsSessionPluginOutput> atsRunSessionFuture =
        atsSessionStub.runSession(
            RUN_COMMAND_SESSION_NAME,
            AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build());
    RUNNING_COMMAND_COUNT.incrementAndGet();
    addCallback(
        atsRunSessionFuture,
        new RunCommandFutureCallback(consoleUtil, serverLogPrinter, systemUtil, exitAfterRun),
        executorService);
    consoleUtil.printlnStdout("Command submitted.");
    return ExitCode.OK;
  }

  private void validateM0Config() {
    if (isNullOrEmpty(config)) {
      throw new ParameterException(
          spec.commandLine(), "Missing param <config> after command \"run\".");
    }

    if (!Objects.equals(config, CTSV_CONFIG)) {
      throw new ParameterException(
          spec.commandLine(), "Only supports config \"cts-v\" at this point.");
    }
  }

  private boolean checkCtsvPreparation() {
    if (consoleInfo.getMoblyTestCasesDir().isEmpty()) {
      consoleUtil.printlnStderr(
          "Mobly test cases dir is not set, please use 'set' command to set it first. All Mobly"
              + " test zips should be put in the Mobly test cases dir.%nCurrent Mobly test"
              + " cases dir: %s",
          consoleInfo.getMoblyTestCasesDir().orElse(null));
      return false;
    }
    if (consoleInfo.getMoblyTestZipSuiteMainFile().isEmpty()) {
      consoleUtil.printlnStderr(
          "\"suite_main.py\" file is required when running CTS-V test, please pass its local path"
              + " to JAVA system property \"MOBLY_TEST_ZIP_SUITE_MAIN_FILE\" and retry.");
      return false;
    } else if (!localFileUtil.isFileExist(getMoblyTestZipSuiteMainFilePath())) {
      consoleUtil.printlnStderr(
          "The given Moby suite main file \"%s\" doesn't exist, please check and retry.",
          getMoblyTestZipSuiteMainFilePath());
      return false;
    }

    return true;
  }

  private ImmutableList<MoblyTestRunEntry> getMoblyTestRunEntries() throws MobileHarnessException {
    String moblyTestCasesDir = consoleInfo.getMoblyTestCasesDir().orElse(null);
    if (moblyTestCasesDir == null || !localFileUtil.isDirExist(moblyTestCasesDir)) {
      logger.atWarning().log(
          "Directory [%s] containing Mobly zip tests doesn't exist, please ensure all being run"
              + " Mobly test zip files are stored under that directory.",
          moblyTestCasesDir);
      return ImmutableList.of();
    }
    ImmutableList.Builder<MoblyTestRunEntry> moblyTestRunEntries = ImmutableList.builder();
    localFileUtil.listFiles(moblyTestCasesDir, /* recursively= */ false).stream()
        .filter(fileNameUtil::isZipFile)
        .forEach(
            moblyTestZip -> {
              String name = fileNameUtil.removeExtension(moblyTestZip.getName());
              moblyTestRunEntries.add(
                  MoblyTestRunEntry.builder()
                      .setName(name)
                      .setMoblyTestZipPath(moblyTestZip.getPath())
                      .build());
            });
    return moblyTestRunEntries.build();
  }

  private ImmutableMap<String, ModuleTestOptionsGroup> getModuleTestOptionsGroupMap() {
    if (moduleTestOptionsGroups == null) {
      return ImmutableMap.of();
    }

    return moduleTestOptionsGroups.stream()
        .collect(
            toImmutableMap(
                moduleTestOptionsGroup -> moduleTestOptionsGroup.module, Function.identity()));
  }

  /**
   * Runs a single Mobly test zip and returns the file path to its generated test_summary.yaml which
   * contains info for the Mobly run.
   */
  private Optional<Path> runSingleMoblyTestZip(
      MoblyTestRunEntry moblyTestRunEntry, String moblyConfigFile, String moblyLogDir) {
    Optional<Path> curMoblyLogDir = prepareBeforeSingleMoblyTestRun(moblyTestRunEntry, moblyLogDir);
    if (curMoblyLogDir.isEmpty()) {
      return Optional.empty();
    }

    try {
      Path moblyTestZipPath = Paths.get(moblyTestRunEntry.moblyTestZipPath());

      Path testRunTmpDir = curMoblyLogDir.get().resolve("tmp");
      Path moblyUnzipDir = testRunTmpDir.resolve("mobly");
      localFileUtil.prepareDir(moblyUnzipDir);
      localFileUtil.copyFileOrDir(
          Paths.get(getMoblyTestZipSuiteMainFilePath()),
          moblyUnzipDir.resolve(MOBLY_TEST_ZIP_DEFAULT_TEST));

      String[] commandArray =
          moblyAospTestSetupUtil.setupEnvAndGenerateTestCommand(
              moblyTestZipPath,
              moblyUnzipDir,
              testRunTmpDir.resolve("venv"),
              Paths.get(moblyConfigFile),
              MOBLY_TEST_ZIP_DEFAULT_TEST,
              /* testCaseSelector= */ null,
              "python3",
              /* installMoblyTestDepsArgs= */ null);

      com.google.devtools.mobileharness.shared.util.command.Command cmd =
          com.google.devtools.mobileharness.shared.util.command.Command.of(commandArray)
              .workDir(moblyTestZipPath.getParent())
              .redirectStderr(true)
              .onStdout(LineCallback.does(consoleUtil::printlnStderr));

      commandExecutor.setBaseEnvironment(
          ImmutableMap.of("MOBLY_LOGPATH", curMoblyLogDir.get().toString()));
      consoleUtil.printlnStderr(
          "===== Running Mobly test zip [%s] =====", moblyTestRunEntry.name());
      consoleUtil.printlnStderr("Executing command \"%s\"", cmd);
      commandExecutor.exec(cmd);
      consoleUtil.printlnStderr(
          "===== Done running Mobly test zip [%s] =====", moblyTestRunEntry.name());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Error when executing Mobly test zip [%s]", moblyTestRunEntry.name());
    } catch (InterruptedException ie) {
      logger.atWarning().withCause(ie).log(
          "Caught interrupted exception when executing Mobly test zip [%s]",
          moblyTestRunEntry.name());
      Thread.currentThread().interrupt();
    }
    return findMoblyTestSummaryAfterSingleMoblyExec(curMoblyLogDir.get(), moblyTestRunEntry);
  }

  /**
   * Prepares before running the single Mobly test zip, and returns the new Mobly log directory for
   * the Mobly test zip only.
   */
  private Optional<Path> prepareBeforeSingleMoblyTestRun(
      MoblyTestRunEntry moblyTestRunEntry, String moblyLogDir) {
    try {
      Path curMoblyLogDir = Paths.get(moblyLogDir, moblyTestRunEntry.name());
      localFileUtil.prepareDir(curMoblyLogDir);
      localFileUtil.grantFileOrDirFullAccess(curMoblyLogDir);
      return Optional.of(curMoblyLogDir);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Error when preparing to run Mobly test zip [%s]", moblyTestRunEntry.name());
    }
    return Optional.empty();
  }

  private Optional<Path> findMoblyTestSummaryAfterSingleMoblyExec(
      Path moblyLogDir, MoblyTestRunEntry moblyTestRunEntry) {
    try {
      List<Path> testSummaryYaml =
          localFileUtil.listFilePaths(
              moblyLogDir, /* recursively= */ true, path -> path.endsWith("test_summary.yaml"));
      if (testSummaryYaml.isEmpty()) {
        logger.atWarning().log(
            "Failed to find Mobly test_summary.yaml file for the run of [%s]",
            moblyTestRunEntry.name());
        return Optional.empty();
      }
      // There should be only one test_summary.yaml after the Mobly run
      return testSummaryYaml.stream().findFirst();
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Error when finding Mobly test_summary.yaml file for the run of [%s]",
          moblyTestRunEntry.name());
    }
    return Optional.empty();
  }

  private String prepareMoblyLogDir() throws MobileHarnessException {
    Path logDir =
        Paths.get(
            consoleInfo.getResultsDirectory().orElse(DEFAULT_MOBLY_LOGPATH),
            String.format(
                "%s_%s",
                new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
                    .format(new Timestamp(Clock.systemUTC().millis())),
                "mobly"));
    localFileUtil.prepareDir(logDir);
    localFileUtil.grantFileOrDirFullAccess(logDir);
    return logDir.toString();
  }

  private ImmutableList<String> getDeviceSerialsBeforeTest() {
    try {
      Set<String> devices = androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE);
      logger.atInfo().log("Connected Android devices: %s", devices);
      if (devices.isEmpty()) {
        return ImmutableList.of();
      }

      if (serialOpt != null && !serialOpt.isEmpty() && devices.contains(serialOpt.get(0))) {
        return ImmutableList.copyOf(serialOpt);
      } else if (serialOpt != null && !serialOpt.isEmpty() && !devices.contains(serialOpt.get(0))) {
        return ImmutableList.of();
      }

      if (androidDeviceSerialList != null && !androidDeviceSerialList.isEmpty()) {
        androidDeviceSerialList =
            androidDeviceSerialList.stream()
                .map(String::trim)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .collect(toImmutableList());
        if (devices.containsAll(androidDeviceSerialList)) {
          return ImmutableList.copyOf(androidDeviceSerialList);
        } else {
          logger.atWarning().log(
              "Some given devices %s are not connected to the host", androidDeviceSerialList);
          return ImmutableList.of();
        }
      }

      Optional<String> result = devices.stream().findFirst();
      logger.atInfo().log(
          "No specific device serial provided, picks connected device [%s] to run the test",
          result.orElse(null));
      return ImmutableList.of(result.get());
    } catch (MobileHarnessException e) {
      // Ignored
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    return ImmutableList.of();
  }

  private String getMoblyTestZipSuiteMainFilePath() {
    return PathUtil.completeHomeDirectory(consoleInfo.getMoblyTestZipSuiteMainFile().orElseThrow());
  }

  @AutoValue
  abstract static class MoblyTestRunEntry {

    abstract String name();

    abstract String moblyTestZipPath();

    static Builder builder() {
      return new AutoValue_RunCommand_MoblyTestRunEntry.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setName(String name);

      abstract Builder setMoblyTestZipPath(String moblyTestZipPath);

      abstract MoblyTestRunEntry build();
    }
  }

  /** Future callback which prints {@code AtsSessionPluginOutput} to console. */
  private static class RunCommandFutureCallback implements FutureCallback<AtsSessionPluginOutput> {

    private final ConsoleUtil consoleUtil;
    private final ServerLogPrinter serverLogPrinter;
    private final SystemUtil systemUtil;
    private final boolean exitAfterRun;

    public RunCommandFutureCallback(
        ConsoleUtil consoleUtil,
        ServerLogPrinter serverLogPrinter,
        SystemUtil systemUtil,
        boolean exitAfterRun) {
      this.consoleUtil = consoleUtil;
      this.serverLogPrinter = serverLogPrinter;
      this.systemUtil = systemUtil;
      this.exitAfterRun = exitAfterRun;
    }

    @Override
    public void onSuccess(AtsSessionPluginOutput output) {
      PluginOutputPrinter.printOutput(output, consoleUtil);

      disableServerLogPrinterAndExitIfNecessary(output.hasSuccess() ? 0 : 1);
    }

    @Override
    public void onFailure(Throwable error) {
      logger.atWarning().withCause(error).log("Failed to execute command");

      disableServerLogPrinterAndExitIfNecessary(/* exitCode= */ 1);
    }

    private void disableServerLogPrinterAndExitIfNecessary(int exitCode) {
      if (RUNNING_COMMAND_COUNT.decrementAndGet() == 0) {
        try {
          serverLogPrinter.enable(false);
        } catch (MobileHarnessException | InterruptedException e) {
          logger.atWarning().withCause(e).log("Failed to disable server log printer");
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
        }

        if (exitAfterRun) {
          logger.atInfo().log("Exiting after the run command finishes");
          systemUtil.exit(exitCode);
        }
      }
    }
  }
}
