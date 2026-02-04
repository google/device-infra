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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor.MapPreprocessor;
import com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor.MultimapPreprocessor;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.subplan.SubPlanLister;
import com.google.devtools.mobileharness.platform.android.xts.suite.ModuleArg;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Options of {@link RunCommand}. */
class RunCommandOptions {

  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "<config>",
      hideParamSyntax = true,
      description = "xTS test config/plan.")
  String config;

  @ArgGroup(exclusive = false, multiplicity = "0..*")
  List<ModuleTestOptionsGroup> moduleTestOptionsGroups;

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
  List<String> serialOpt;

  @Option(
      names = {"--exclude-serial"},
      paramLabel = "<exclude_device_id>",
      description = "Run test on any device except those with this serial number(s).")
  List<String> excludeSerialOpt;

  @Option(
      names = {"--product-type"},
      paramLabel = "<device_product_type>",
      description =
          "Run test on device with this product type(s). May also filter by variant using"
              + " product:variant.")
  List<String> productTypes;

  @Option(
      names = {"--property"},
      paramLabel = "<device_property>",
      preprocessor = MapPreprocessor.class,
      description =
          "Run test on device with this property value. Expected format --property <propertyname>"
              + " <propertyvalue>.")
  Map<String, String> devicePropertiesMap;

  @Option(
      names = {"--min-battery"},
      paramLabel = "<min_battery_level>",
      description =
          "only run this test on a device whose battery level is at least the given"
              + " amount. Scale: 0-100")
  Integer minBattery = null;

  @Option(
      names = {"--max-battery"},
      paramLabel = "<max_battery_level>",
      description =
          "only run this test on a device whose battery level is strictly less than the "
              + "given amount. Scale: 0-100")
  Integer maxBattery = null;

  @Option(
      names = {"--require-battery-check"},
      arity = "1",
      paramLabel = "<require_battery_check>",
      description =
          "If --min-battery and/or --max-battery is specified, enforce the check. If"
              + " require-battery-check=false, then no battery check will occur. This is TRUE by"
              + " default.")
  boolean requireBatteryCheck = true;

  @Option(
      names = {"--max-battery-temperature"},
      paramLabel = "<max_battery_temperature>",
      description =
          "only run this test on a device whose battery temperature is strictly "
              + "less than the given amount. Scale: Degrees celsius")
  Integer maxBatteryTemperature = null;

  @Option(
      names = {"--require-battery-temp-check"},
      arity = "1",
      paramLabel = "<require_battery_temp_check>",
      description =
          "If --max-battery-temperature is specified, enforce the battery checking. If"
              + " require-battery-temp-check=false, then no temperature check will occur. This is"
              + " TRUE by default.")
  boolean requireBatteryTemperatureCheck = true;

  @Option(
      names = {"--min-sdk-level"},
      paramLabel = "<min_sdk_level>",
      description = "Only run this test on devices that support this Android SDK/API level")
  Integer minSdk = null;

  @Option(
      names = {"--max-sdk-level"},
      paramLabel = "<max_sdk_level>",
      description = "Only run this test on devices that support this Android SDK/API level")
  Integer maxSdk = null;

  @Option(
      names = {"--shard-count"},
      paramLabel = "<number_of_shards>",
      description =
          "Shard a run into given number of independent chunks, to run on multiple devices in"
              + " parallel.")
  int shardCount;

  @Option(
      names = {"--include-filter", "--compatibility:include-filter"},
      paramLabel = "\"[abi] <module_name> <test_name>\"",
      description =
          "Run with the specified modules, or test packages, classes, and cases. For example, run"
              + " cts --include-filter \"CtsCalendarcommon2TestCases"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" includes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  List<String> includeFilters;

  @Option(
      names = {"--exclude-filter", "--compatibility:exclude-filter"},
      paramLabel = "\"[abi] <module_name> <test_name>\"",
      description =
          "Exclude the specified modules, or test packages, classes, and cases, from the run. For"
              + " example, run cts --exclude-filter \"CtsCalendarcommon2Test"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" excludes the"
              + " specified module.")
  @SuppressWarnings("PreferredInterfaceType")
  List<String> excludeFilters;

  @Option(
      names = {"--strict-include-filter"},
      paramLabel = "\"[abi] <module_name> <test_name>\"",
      description =
          "Run with the specified modules, or test packages, classes, and cases. For example, run"
              + " cts --strict-include-filter \"CtsCalendarcommon2Test"
              + " android.calendarcommon2.cts.Calendarcommon2Test#testStaticLinking\" includes the"
              + " specified module. All other filters will be ignored to strictly run this set.")
  @SuppressWarnings("PreferredInterfaceType")
  List<String> strictIncludeFilters;

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
  Multimap<String, String> moduleMetadataIncludeFilters;

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
  Multimap<String, String> moduleMetadataExcludeFilters;

  @Option(
      names = {"--html-in-zip"},
      arity = "0..1",
      paramLabel = "<html_in_zip>",
      description = "Whether to include html reports in the result zip file. Default is false.")
  boolean htmlInZip;

  @Option(
      names = {"--report-system-checkers"},
      arity = "0..1",
      paramLabel = "<report_system_checkers>",
      description = "Whether reporting system checkers as test or not. Default is false.")
  boolean reportSystemCheckers = false;

  @Option(
      names = {"-d", "--skip-device-info"},
      arity = "0..1",
      paramLabel = "<skip_device_info>",
      description = "Whether device info collection should be skipped. Default is false.")
  Boolean skipDeviceInfo = null;

  @Option(
      names = {"--subplan"},
      paramLabel = "<subplan_name>",
      description = "Run the specified subplan.")
  String subPlanName;

  @Option(
      names = {"--help"},
      paramLabel = "<help>",
      description = "Show the help message.")
  boolean showHelp;

  @Option(
      names = {"--help-all"},
      paramLabel = "<help_all>",
      description = "Show the help all message.")
  boolean showHelpAll;

  @Option(
      names = {"--module-arg", "--compatibility:module-arg"},
      paramLabel = "\"<module_name>:<arg_name>:[<arg_key>:=]<arg_value>\"",
      description = "Arguments to pass to a module.")
  List<String> moduleCmdArgs;

  @Parameters(index = "1..*", hidden = true)
  List<String> extraRunCmdArgs;

  // Command options for "run retry" command
  @Option(
      names = "--retry",
      paramLabel = "<retry_session_id>",
      description =
          "Index for the retry session. Use @|bold list results|@ to get the session index."
              + " Either this or <retry_session_result_dir_name> must be set when calling 'run"
              + " retry' command")
  Integer retrySessionIndex; // Use Integer instead of int to check if it's set

  @Option(
      names = "--retry-result-dir",
      paramLabel = "<retry_session_result_dir_name>",
      description =
          "Result directory name for the retry session. Use @|bold list results|@ to get the result"
              + " directory name. Either this or <retry_session_id> must be set when calling 'run"
              + " retry' command. Use this option instead of <retry_session_id> to retry specific"
              + " sessions parallelly if needed")
  String retrySessionResultDirName;

  @Option(
      names = "--retry-type",
      description =
          "Test retry type for 'run retry' command. Supported values: ${COMPLETION-CANDIDATES}")
  RetryType retryType;

  @Option(names = "--exclude-runner", description = "Exclude tests by test runners.")
  List<String> excludeRunnerOpt;

  @Option(
      names = {"--enable-default-logs"},
      arity = "0..1",
      paramLabel = "<enable_default_logs>",
      description = "Whether to enable default logs. Default is false.")
  Boolean enableDefaultLogs = null;

  @Option(
      names = {"--enable-token-sharding"},
      arity = "0..1",
      paramLabel = "<enable_token_sharding>",
      description = "Automatically matches the test that requires respective SIM type")
  boolean enableTokenSharding = false;

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  DeviceTypeOptionsGroup deviceTypeOptionsGroup;

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

  @Spec CommandSpec spec;

  private final ConsoleInfo consoleInfo;
  private final CommandHelper commandHelper;
  private final SubPlanLister subPlanLister;

  @Inject
  RunCommandOptions(
      ConsoleInfo consoleInfo, CommandHelper commandHelper, SubPlanLister subPlanLister) {
    this.consoleInfo = consoleInfo;
    this.commandHelper = commandHelper;
    this.subPlanLister = subPlanLister;
  }

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
                      """
                      Invalid module arguments provided. Unprocessed arguments: %s
                      Expected format: <module_name>:<arg_name>:[<arg_key>:=]<arg_value>.
                      """,
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

  ImmutableList<String> getModules() {
    return moduleTestOptionsGroups != null
        ? moduleTestOptionsGroups.stream().map(group -> group.module).collect(toImmutableList())
        : ImmutableList.of();
  }

  String getTest() {
    return moduleTestOptionsGroups != null && moduleTestOptionsGroups.get(0).test != null
        ? moduleTestOptionsGroups.get(0).test
        : "";
  }

  Optional<Boolean> isSkipDeviceInfo() {
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
}
