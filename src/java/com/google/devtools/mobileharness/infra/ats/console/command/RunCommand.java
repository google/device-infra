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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
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
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ParseCommandOnly;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.RunCommandParsingResultFuture;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor.MapPreprocessor;
import com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor.MultimapPreprocessor;
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
import com.google.devtools.mobileharness.infra.ats.console.util.subplan.SubPlanLister;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.ModuleArg;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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

  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "<config>",
      hideParamSyntax = true,
      description = "xTS test config/plan.")
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
      names = {"--exclude-serial"},
      paramLabel = "<exclude_device_id>",
      description = "Run test on any device except those with this serial number(s).")
  private List<String> excludeSerialOpt;

  @Option(
      names = {"--product-type"},
      paramLabel = "<device_product_type>",
      description =
          "Run test on device with this product type(s). May also filter by variant using"
              + " product:variant.")
  private List<String> productTypes;

  @Option(
      names = {"--property"},
      paramLabel = "<device_property>",
      preprocessor = MapPreprocessor.class,
      description =
          "Run test on device with this property value. Expected format --property <propertyname>"
              + " <propertyvalue>.")
  private Map<String, String> devicePropertiesMap;

  @Option(
      names = {"--min-battery"},
      paramLabel = "<min_battery_level>",
      description =
          "only run this test on a device whose battery level is at least the given"
              + " amount. Scale: 0-100")
  private Integer minBattery = null;

  @Option(
      names = {"--max-battery"},
      paramLabel = "<max_battery_level>",
      description =
          "only run this test on a device whose battery level is strictly less than the "
              + "given amount. Scale: 0-100")
  private Integer maxBattery = null;

  @Option(
      names = {"--require-battery-check"},
      arity = "1",
      paramLabel = "<require_battery_check>",
      description =
          "If --min-battery and/or --max-battery is specified, enforce the check. If"
              + " require-battery-check=false, then no battery check will occur. This is TRUE by"
              + " default.")
  private boolean requireBatteryCheck = true;

  @Option(
      names = {"--max-battery-temperature"},
      paramLabel = "<max_battery_temperature>",
      description =
          "only run this test on a device whose battery temperature is strictly "
              + "less than the given amount. Scale: Degrees celsius")
  private Integer maxBatteryTemperature = null;

  @Option(
      names = {"--require-battery-temp-check"},
      arity = "1",
      paramLabel = "<require_battery_temp_check>",
      description =
          "If --max-battery-temperature is specified, enforce the battery checking. If"
              + " require-battery-temp-check=false, then no temperature check will occur. This is"
              + " TRUE by default.")
  private boolean requireBatteryTemperatureCheck = true;

  @Option(
      names = {"--min-sdk-level"},
      paramLabel = "<min_sdk_level>",
      description = "Only run this test on devices that support this Android SDK/API level")
  private Integer minSdk = null;

  @Option(
      names = {"--max-sdk-level"},
      paramLabel = "<max_sdk_level>",
      description = "Only run this test on devices that support this Android SDK/API level")
  private Integer maxSdk = null;

  @Option(
      names = {"--shard-count"},
      paramLabel = "<number_of_shards>",
      description =
          "Shard a run into given number of independent chunks, to run on multiple devices in"
              + " parallel.")
  private int shardCount;

  @Option(
      names = {"--include-filter", "--compatibility:include-filter"},
      paramLabel = "\"[abi] <module_name> <test_name>\"",
      description =
          "Run with the specified modules, or test packages, classes, and cases. For example, run"
              + " cts --include-filter \"CtsCalendarcommon2TestCases"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" includes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> includeFilters;

  @Option(
      names = {"--exclude-filter", "--compatibility:exclude-filter"},
      paramLabel = "\"[abi] <module_name> <test_name>\"",
      description =
          "Exclude the specified modules, or test packages, classes, and cases, from the run. For"
              + " example, run cts --exclude-filter \"CtsCalendarcommon2Test"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" excludes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  private List<String> excludeFilters;

  @Option(
      names = {
        "--module-metadata-include-filter",
        "--compatibility:module-metadata-include-filter"
      },
      paramLabel = "<key> <value>",
      preprocessor = MultimapPreprocessor.class,
      description =
          "Run modules with specific metadata in key-value pairs. For example, run"
              + " cts --module-metadata-include-filter component gts-root"
              + " includes modules with metadata key=component value=gts-root.")
  @SuppressWarnings("PreferredInterfaceType")
  private Multimap<String, String> moduleMetadataIncludeFilters;

  @Option(
      names = {
        "--module-metadata-exclude-filter",
        "--compatibility:module-metadata-exclude-filter"
      },
      arity = "2",
      paramLabel = "<key> <value>",
      preprocessor = MultimapPreprocessor.class,
      description =
          "Exclude modules with specific metadata in key-value pairs. For example, run"
              + " cts --module-metadata-exclude-filter component gts-root"
              + " excludes modules with metadata key=component value=gts-root.")
  @SuppressWarnings("PreferredInterfaceType")
  private Multimap<String, String> moduleMetadataExcludeFilters;

  @Option(
      names = {"--html-in-zip"},
      arity = "0..1",
      paramLabel = "<html_in_zip>",
      description = "Whether to include html reports in the result zip file. Default is false.")
  private boolean htmlInZip;

  @Option(
      names = {"--report-system-checkers"},
      arity = "0..1",
      paramLabel = "<report_system_checkers>",
      description = "Whether reporting system checkers as test or not. Default is false.")
  private boolean reportSystemCheckers = false;

  @Option(
      names = {"-d", "--skip-device-info"},
      arity = "0..1",
      paramLabel = "<skip_device_info>",
      description = "Whether device info collection should be skipped. Default is false.")
  private Boolean skipDeviceInfo = null;

  @Option(
      names = {"--subplan"},
      paramLabel = "<subplan_name>",
      description = "Run the specified subplan.")
  private String subPlanName;

  @Option(
      names = {"--help"},
      paramLabel = "<help>",
      description = "Show the help message.")
  private boolean showHelp;

  @Option(
      names = {"--help-all"},
      paramLabel = "<help_all>",
      description = "Show the help all message.")
  private boolean showHelpAll;

  @Option(
      names = {"--module-arg", "--compatibility:module-arg"},
      paramLabel = "\"<module_name>:<arg_name>:[<arg_key>:=]<arg_value>\"",
      description = "Arguments to pass to a module.")
  private List<String> moduleCmdArgs;

  @Parameters(index = "1..*", hidden = true)
  private List<String> extraRunCmdArgs;

  // Command options for "run retry" command
  @Option(
      names = "--retry",
      paramLabel = "<retry_session_id>",
      description =
          "Index for the retry session. Use @|bold list results|@ to get the session index."
              + " Either this or <retry_session_result_dir_name> must be set when calling 'run"
              + " retry' command")
  private Integer retrySessionIndex; // Use Integer instead of int to check if it's set

  @Option(
      names = "--retry-result-dir",
      paramLabel = "<retry_session_result_dir_name>",
      description =
          "Result directory name for the retry session. Use @|bold list results|@ to get the result"
              + " directory name. Either this or <retry_session_id> must be set when calling 'run"
              + " retry' command. Use this option instead of <retry_session_id> to retry specific"
              + " sessions parallelly if needed")
  private String retrySessionResultDirName;

  @Option(
      names = "--retry-type",
      description =
          "Test retry type for 'run retry' command. Supported values: ${COMPLETION-CANDIDATES}")
  private RetryType retryType;

  @Option(names = "--exclude-runner", description = "Exclude tests by test runners.")
  private List<String> excludeRunnerOpt;

  @Option(
      names = {"--enable-default-logs"},
      arity = "0..1",
      paramLabel = "<enable_default_logs>",
      description = "Whether to enable default logs. Default is false.")
  private Boolean enableDefaultLogs = null;

  @Option(
      names = {"--enable-token-sharding"},
      arity = "0..1",
      paramLabel = "<enable_token_sharding>",
      description = "Automatically matches the test that requires respective SIM type")
  private boolean enableTokenSharding = false;

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  private DeviceTypeOptionsGroup deviceTypeOptionsGroup;

  static class DeviceTypeOptionsGroup {
    @Option(
        names = {"-e", "--emulator"},
        required = false,
        paramLabel = "<run_test_on_emulator>",
        description = "If true, force this test to run on emulator. Default is false.")
    boolean runTestOnEmulator;

    @Option(
        names = {"-rd", "--device"},
        required = false,
        paramLabel = "<run_test_on_real_device>",
        description =
            "If true, force this test to run on a physical device, not an emulator. Default is"
                + " false.")
    boolean runTestOnRealDevice;
  }

  @Spec private CommandSpec spec;

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
  private final SubPlanLister subPlanLister;
  private final ResultListerHelper resultListerHelper;

  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;
  private final ListeningExecutorService executorService;
  private final AtsSessionStub atsSessionStub;
  private final Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture;
  private final boolean parseCommandOnly;
  private final CommandExecutor commandExecutor;
  private final XtsCommandUtil xtsCommandUtil;

  @Inject
  RunCommand(
      ConsoleInfo consoleInfo,
      ConsoleUtil consoleUtil,
      CommandHelper commandHelper,
      SubPlanLister subPlanLister,
      ResultListerHelper resultListerHelper,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter,
      ListeningExecutorService executorService,
      AtsSessionStub atsSessionStub,
      CommandExecutor commandExecutor,
      XtsCommandUtil xtsCommandUtil,
      @RunCommandParsingResultFuture
          Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture,
      @ParseCommandOnly boolean parseCommandOnly) {
    this.consoleInfo = consoleInfo;
    this.consoleUtil = consoleUtil;
    this.commandHelper = commandHelper;
    this.subPlanLister = subPlanLister;
    this.resultListerHelper = resultListerHelper;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
    this.executorService = executorService;
    this.atsSessionStub = atsSessionStub;
    this.commandExecutor = commandExecutor;
    this.xtsCommandUtil = xtsCommandUtil;
    this.resultFuture = resultFuture;
    this.parseCommandOnly = parseCommandOnly;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException {
    ImmutableList<String> command = consoleInfo.getLastCommand();
    try {
      if (parseCommandOnly) {
        try {
          // TODO: Add additional command line options from ats console.
          resultFuture.accept(immediateFuture(createParseResult()));
          return ExitCode.OK;
        } catch (RuntimeException | Error e) {
          resultFuture.accept(immediateFailedFuture(e));
          return ExitCode.SOFTWARE;
        }
      }
      checkState(Flags.instance().enableAtsConsoleOlcServer.getNonNull());
      if (showHelp || showHelpAll) {
        return showHelpMessage(
            commandHelper.getXtsType(), consoleInfo.getXtsRootDirectoryNonEmpty());
      } else {
        validateCommandParameters();
        return runWithOlcServerPrepared(command);
      }
    } finally {
      moduleTestOptionsGroups = null; // Resets the group to clear the history
    }
  }

  /** Returns the number of currently running RunCommand instances. */
  public static int getRunningRunCommandCount() {
    return RUNNING_COMMAND_COUNT.get();
  }

  private SessionRequestInfo.Builder createParseResult() throws MobileHarnessException {
    SessionRequestInfo.Builder sessionRequestBuilder = SessionRequestInfo.builder();
    validateCommandParameters();
    sessionRequestBuilder
        .setTestPlan(config)
        .setModuleNames(getModules())
        .setIncludeFilters(
            this.includeFilters == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(this.includeFilters))
        .setExcludeFilters(
            this.excludeFilters == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(this.excludeFilters))
        .setModuleMetadataIncludeFilters(
            this.moduleMetadataIncludeFilters == null
                ? ImmutableMultimap.of()
                : ImmutableMultimap.copyOf(this.moduleMetadataIncludeFilters))
        .setModuleMetadataExcludeFilters(
            this.moduleMetadataExcludeFilters == null
                ? ImmutableMultimap.of()
                : ImmutableMultimap.copyOf(this.moduleMetadataExcludeFilters))
        .setHtmlInZip(htmlInZip);
    if (this.shardCount > 0) {
      sessionRequestBuilder.setShardCount(this.shardCount);
    }
    if (!this.getTest().isEmpty()) {
      sessionRequestBuilder.setTestName(this.getTest());
    }
    ImmutableList<String> moduleArgs =
        moduleCmdArgs != null ? ImmutableList.copyOf(moduleCmdArgs) : ImmutableList.of();
    ImmutableList<String> extraArgs =
        extraRunCmdArgs != null ? ImmutableList.copyOf(extraRunCmdArgs) : ImmutableList.of();
    ImmutableSet<String> excludeRunners =
        excludeRunnerOpt != null ? ImmutableSet.copyOf(excludeRunnerOpt) : ImmutableSet.of();
    if (this.retryType != null) {
      sessionRequestBuilder.setRetryType(
          RetryType.valueOf(Ascii.toUpperCase(this.retryType.name())));
    }
    if (isSkipDeviceInfo().isPresent()) {
      sessionRequestBuilder.setSkipDeviceInfo(isSkipDeviceInfo().get());
    }
    if (enableDefaultLogs != null) {
      sessionRequestBuilder.setEnableDefaultLogs(enableDefaultLogs);
    }
    sessionRequestBuilder.setEnableTokenSharding(enableTokenSharding);

    return sessionRequestBuilder
        .setModuleArgs(moduleArgs)
        .setExtraArgs(extraArgs)
        .setExcludeRunners(excludeRunners);
  }

  @VisibleForTesting
  void validateCommandParameters() throws MobileHarnessException {
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
              .filter(Objects::nonNull)
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
    if (includeFilters != null
        && !includeFilters.isEmpty()
        && moduleTestOptionsGroups != null
        && !moduleTestOptionsGroups.isEmpty()) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(
              "Don't use '--include-filter' and '--module/-m' options at the same time.\n"));
    }
    if (config.equals("retry")) {
      validateRunRetryCommandParameters();
    }
    if (!isNullOrEmpty(subPlanName) && !isSubPlanExist(subPlanName)) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(String.format("Subplan [%s] doesn't exist.\n", subPlanName)));
    }
    if (moduleCmdArgs != null && !moduleCmdArgs.isEmpty()) {
      for (String moduleArg : moduleCmdArgs) {
        if (!ModuleArg.isValid(moduleArg)) {
          throw new ParameterException(
              spec.commandLine(),
              Ansi.AUTO.string(
                  String.format(
                      "Invalid module arguments provided. Unprocessed arguments: %s\n"
                          + "Expected format: <module_name>:<arg_name>:[<arg_key>:=]<arg_value>.\n",
                      moduleArg)));
        }
      }
    }
    validateRunCommandExtraArgs();
  }

  private void validateRunRetryCommandParameters() {
    if (retrySessionIndex == null && isNullOrEmpty(retrySessionResultDirName)) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(
              "Must provide option '--retry <retry_session_id>' or '--retry-result-dir"
                  + " <retry_session_result_dir_name>' for retry command.\n"));
    }
    if (retrySessionIndex != null && !isNullOrEmpty(retrySessionResultDirName)) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(
              "Option '--retry <retry_session_id>' and '--retry-result-dir"
                  + " <retry_session_result_dir_name>' are mutually exclusive.\n"));
    }
    if (!isNullOrEmpty(subPlanName)) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string(
              "Option '--subplan <subplan_name>' is not supported in retry command.\n"));
    }
    if (includeFilters != null && !includeFilters.isEmpty()) {
      throw new ParameterException(
          spec.commandLine(),
          Ansi.AUTO.string("Option '--include-filter' is not supported in retry command.\n"));
    }
  }

  private void validateRunCommandExtraArgs() {
    if (extraRunCmdArgs != null && !extraRunCmdArgs.isEmpty()) {
      // The extra args are passed to TF behind if need to run tests via TF. Ideally we should add
      // corresponding parameter or option in this Command class explicitly, but at the moment we
      // may not cover all TF supported options, so we do some basic validations here.
      if (!extraRunCmdArgs.get(0).startsWith("-")) {
        throw new ParameterException(
            spec.commandLine(),
            Ansi.AUTO.string(
                String.format(
                    "Invalid arguments provided. Unprocessed arguments: %s\n"
                        + "Double check if the input is valid, for example, quoting the arg value"
                        + " if it contains space.\n",
                    extraRunCmdArgs)));
      }
    }
  }

  private boolean isSubPlanExist(String subPlanName) throws MobileHarnessException {
    Path xtsRootDir = consoleInfo.getXtsRootDirectoryNonEmpty();
    return subPlanLister
        .listSubPlans(xtsRootDir.toString(), commandHelper.getXtsType())
        .contains(subPlanName);
  }

  private ImmutableList<String> getModules() {
    return moduleTestOptionsGroups != null
        ? moduleTestOptionsGroups.stream().map(group -> group.module).collect(toImmutableList())
        : ImmutableList.of();
  }

  private String getTest() {
    return moduleTestOptionsGroups != null && moduleTestOptionsGroups.get(0).test != null
        ? moduleTestOptionsGroups.get(0).test
        : "";
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
                            "run", "commandAndExit", config, showHelp ? "--help" : "--help-all")))
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
      } else if (line.startsWith("'" + config + "' configuration:")) {
        output.append(line).append("\n");
        printing = true;
      } else if (line.startsWith("Failed to run command:")) {
        output.append(line).append("\n");
        break;
      }
    }
    // Remove the last newline character because printLnStdout will add a new line.
    consoleUtil.printlnStdout(
        output.length() > 0 ? output.deleteCharAt(output.length() - 1).toString() : "");

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
        serialOpt != null ? ImmutableList.copyOf(serialOpt) : ImmutableList.of();
    ImmutableList<String> excludeSerials =
        excludeSerialOpt != null ? ImmutableList.copyOf(excludeSerialOpt) : ImmutableList.of();
    ImmutableList<String> productTypes =
        this.productTypes == null ? ImmutableList.of() : ImmutableList.copyOf(this.productTypes);
    ImmutableMap<String, String> devicePropertiesMap =
        this.devicePropertiesMap == null
            ? ImmutableMap.of()
            : ImmutableMap.copyOf(this.devicePropertiesMap);

    ImmutableList<String> modules = getModules();

    String test = getTest();
    ImmutableList<String> includeFilters =
        this.includeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.includeFilters);
    ImmutableList<String> excludeFilters =
        this.excludeFilters == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(this.excludeFilters);

    ImmutableMultimap<String, String> moduleMetadataIncludeFilters =
        this.moduleMetadataIncludeFilters == null
            ? ImmutableMultimap.of()
            : ImmutableMultimap.copyOf(this.moduleMetadataIncludeFilters);
    ImmutableMultimap<String, String> moduleMetadataExcludeFilters =
        this.moduleMetadataExcludeFilters == null
            ? ImmutableMultimap.of()
            : ImmutableMultimap.copyOf(this.moduleMetadataExcludeFilters);

    ImmutableList<String> moduleArgs =
        moduleCmdArgs != null ? ImmutableList.copyOf(moduleCmdArgs) : ImmutableList.of();
    ImmutableList<String> extraArgs =
        extraRunCmdArgs != null ? ImmutableList.copyOf(extraRunCmdArgs) : ImmutableList.of();
    ImmutableSet<String> excludeRunners =
        excludeRunnerOpt != null ? ImmutableSet.copyOf(excludeRunnerOpt) : ImmutableSet.of();

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
            .setHtmlInZip(htmlInZip)
            .setReportSystemCheckers(reportSystemCheckers)
            .putAllXtsSuiteInfo(
                new CertificationSuiteInfoFactory()
                    .generateSuiteInfoMap(xtsRootDirectory.toString(), xtsType, config));
    moduleMetadataIncludeFilters.forEach(
        (key, value) ->
            runCommand.addModuleMetadataIncludeFilter(
                ModuleMetadataFilterEntry.newBuilder().setKey(key).setValue(value)));
    moduleMetadataExcludeFilters.forEach(
        (key, value) ->
            runCommand.addModuleMetadataExcludeFilter(
                ModuleMetadataFilterEntry.newBuilder().setKey(key).setValue(value)));
    isSkipDeviceInfo().ifPresent(runCommand::setSkipDeviceInfo);
    if (shardCount > 0) {
      runCommand.setShardCount(shardCount);
    }
    if (consoleInfo.getPythonPackageIndexUrl().isPresent()) {
      runCommand.setPythonPkgIndexUrl(consoleInfo.getPythonPackageIndexUrl().get());
    }
    runCommand.setEnableTokenSharding(enableTokenSharding);

    runCommand
        .setTestPlan(config)
        .addAllModuleName(modules)
        .addAllModuleArg(moduleArgs)
        .addAllExtraArg(extraArgs)
        .addAllExcludeRunner(excludeRunners);
    if (!test.isEmpty()) {
      runCommand.setTestName(test);
    }
    if (!isNullOrEmpty(subPlanName)) {
      runCommand.setSubPlanName(subPlanName);
    }
    if (retrySessionIndex != null) {
      // As the command parameters validation ensures only one of retrySessionIndex or
      // retrySessionResultDirName is set, so at this point retrySessionResultDirName is empty. Now
      // we get the retrySessionResultDirName by the retrySessionIndex and will use
      // retrySessionResultDirName to locate the previous result later.
      retrySessionResultDirName = getRetrySessionResultDirName(retrySessionIndex);
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Retry session index [%s] mapped to retry session result dir name [%s]",
              retrySessionIndex, retrySessionResultDirName);
    }
    if (!isNullOrEmpty(retrySessionResultDirName)) {
      runCommand.setRetrySessionResultDirName(retrySessionResultDirName.trim());
    }
    if (retryType != null) {
      runCommand.setRetryType(Ascii.toUpperCase(retryType.name()));
    }
    if (Flags.instance().enableXtsDynamicDownloader.getNonNull()) {
      runCommand.setEnableXtsDynamicDownload(true);
    }
    if (Flags.instance().enableMoblyResultstoreUpload.getNonNull()) {
      runCommand.setEnableMoblyResultstoreUpload(true);
    }
    if (deviceTypeOptionsGroup != null) {
      if (deviceTypeOptionsGroup.runTestOnEmulator) {
        runCommand.setDeviceType(DeviceType.EMULATOR);
      }
      if (deviceTypeOptionsGroup.runTestOnRealDevice) {
        runCommand.setDeviceType(DeviceType.REAL_DEVICE);
      }
    }
    if (requireBatteryCheck) {
      if (maxBattery != null) {
        runCommand.setMaxBatteryLevel(maxBattery);
      }
      if (minBattery != null) {
        runCommand.setMinBatteryLevel(minBattery);
      }
    }
    if (requireBatteryTemperatureCheck) {
      if (maxBatteryTemperature != null) {
        runCommand.setMaxBatteryTemperature(maxBatteryTemperature);
      }
    }
    if (minSdk != null) {
      runCommand.setMinSdkLevel(minSdk);
    }
    if (maxSdk != null) {
      runCommand.setMaxSdkLevel(maxSdk);
    }
    if (Flags.instance().enableCtsVerifierResultReporter.getNonNull()) {
      runCommand.setEnableCtsVerifierResultReporter(true);
    }
    if (consoleInfo.isFromCommandFile()) {
      runCommand.setJobStartTimeout(toProtoDuration(CMDFILE_JOBS_START_TIMEOUT));
    }
    runCommand.setEnableDefaultLogs(Boolean.TRUE.equals(enableDefaultLogs));

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
    return this.productTypes;
  }

  @VisibleForTesting
  Map<String, String> getDevicePropertiesMap() {
    return this.devicePropertiesMap;
  }

  @VisibleForTesting
  List<String> getSerials() {
    return this.serialOpt;
  }

  @VisibleForTesting
  List<String> getExtraRunCmdArgs() {
    return this.extraRunCmdArgs;
  }

  @VisibleForTesting
  Multimap<String, String> getModuleMetadataIncludeFilters() {
    return this.moduleMetadataIncludeFilters;
  }

  @VisibleForTesting
  Multimap<String, String> getModuleMetadataExcludeFilters() {
    return this.moduleMetadataExcludeFilters;
  }

  private Optional<Boolean> isSkipDeviceInfo() {
    // Currently skip collecting device info for plan cts-dev or set explicitly, and ideally it
    // should parse given plan and its child plans to see if the option "skip-device-info" is set to
    // true explicitly.
    if (skipDeviceInfo == null && Objects.equals(config, "cts-dev")) {
      return Optional.of(true);
    }
    // TODO Temporary solution to unblock app compat test post processing.
    if (skipDeviceInfo == null && Objects.equals(config, "csuite-app-crawl")) {
      return Optional.of(true);
    }
    return Optional.ofNullable(skipDeviceInfo);
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
