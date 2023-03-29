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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.atsconsole.ConsoleInfo;
import com.google.devtools.atsconsole.ConsoleUtil;
import com.google.devtools.atsconsole.result.xml.MoblyResultInfo;
import com.google.devtools.atsconsole.result.xml.XmlResultFormatter;
import com.google.devtools.atsconsole.result.xml.XmlResultUtil;
import com.google.devtools.atsconsole.testbed.config.YamlTestbedUpdater;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
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
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import org.apache.commons.io.FilenameUtils;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command to run CTS and CTS-V tests. */
@Command(
    name = "run",
    sortOptions = false,
    mixinStandardHelpOptions = true,
    descriptionHeading = "%n",
    description = "Run CTS and CTS-V tests.",
    synopsisHeading = "Usage:%n ",
    footer = {
      "%nAlternatively you can enter @|fg(yellow) <config>|@ right after \"run\" command which"
          + " will achieve same result.%n",
    })
final class RunCommand implements Callable<Integer> {

  @Parameters(
      index = "0",
      arity = "1",
      paramLabel = "<config>",
      description = "CTS and CTS-V test config/plan.")
  private String config;

  @ArgGroup(exclusive = false, multiplicity = "0..*")
  private List<ModuleTestOptionsGroup> moduleTestOptionsGroups;

  static class ModuleTestOptionsGroup {
    @Option(
        names = {"-m", "--module"},
        required = true,
        description = "Run the specified module.")
    String module;
  }

  @Option(
      names = {"-s", "--serial"},
      paramLabel = "deviceID",
      description = "Run test on the specific device.")
  private String serialOpt;

  @Option(
      names = {"--serials"},
      split = ",",
      paramLabel = "device_serial",
      description =
          "A list serial numbers of Android devices used for multiple-device test(separated by"
              + " comma). For example, `--serials <device_a>,<device_b>`. No spaces around the"
              + " comma. Note: the order of pass-in device serial numbers will be kept when passing"
              + " them to the test infra.")
  private List<String> androidDeviceSerialList;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CTSV_CONFIG = "cts-v";
  private static final String DEFAULT_MOBLY_LOGPATH = "/tmp/logs/mobly";
  private static final String MOBLY_TEST_ZIP_DEFAULT_TEST = "suite_main.py";

  @Spec private CommandSpec spec;

  private final ConsoleInfo consoleInfo;
  private final ConsoleUtil consoleUtil;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  private final LocalFileUtil localFileUtil;
  private final CommandExecutor commandExecutor;
  private final YamlTestbedUpdater yamlTestbedUpdater;
  private final XmlResultFormatter xmlResultFormatter;
  private final XmlResultUtil xmlResultUtil;
  private final MoblyAospTestSetupUtil moblyAospTestSetupUtil;

  @Inject
  RunCommand(
      ConsoleInfo consoleInfo,
      CommandExecutor commandExecutor,
      ConsoleUtil consoleUtil,
      AndroidAdbInternalUtil androidAdbInternalUtil,
      LocalFileUtil localFileUtil,
      YamlTestbedUpdater yamlTestbedUpdater,
      XmlResultFormatter xmlResultFormatter,
      XmlResultUtil xmlResultUtil,
      MoblyAospTestSetupUtil moblyAospTestSetupUtil) {
    this.consoleInfo = consoleInfo;
    this.commandExecutor = commandExecutor;
    this.consoleUtil = consoleUtil;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.localFileUtil = localFileUtil;
    this.yamlTestbedUpdater = yamlTestbedUpdater;
    this.xmlResultFormatter = xmlResultFormatter;
    this.xmlResultUtil = xmlResultUtil;
    this.moblyAospTestSetupUtil = moblyAospTestSetupUtil;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException, IOException {
    try {
      validateConfig();

      if (Objects.equals(config, CTSV_CONFIG)) {
        if (Flags.instance().enableAtsConsoleOlcServer.getNonNull()) {
          return runCtsvInM1();
        } else {
          return runCtsvInM0();
        }
      }

      return 0;
    } finally {
      moduleTestOptionsGroups = null; // reset the group to clear the history
    }
  }

  private int runCtsvInM0() throws MobileHarnessException, InterruptedException, IOException {
    if (!checkCtsvPreparation()) {
      return 1;
    }
    ImmutableMap<String, ModuleTestOptionsGroup> moduleTestOptionsGroupMap =
        getModuleTestOptionsGroupMap();

    ImmutableList<MoblyTestRunEntry> moblyTestRunEntries = getMoblyTestRunEntries();

    // Filter Mobly test zips per commandline options
    if (!moduleTestOptionsGroupMap.isEmpty()) {
      moblyTestRunEntries =
          moblyTestRunEntries.stream()
              .filter(
                  moblyTestRunEntry ->
                      moduleTestOptionsGroupMap.containsKey(moblyTestRunEntry.name()))
              .collect(toImmutableList());
    }

    if (moblyTestRunEntries.isEmpty()) {
      consoleUtil.printLine(
          String.format(
              "Found no match Mobly test zip(s) under directory [%s], skip running.",
              consoleInfo.getMoblyTestCasesDir().orElse(null)));
      return 0;
    }

    ImmutableList<String> serials = getDeviceSerialsBeforeTest();
    if (serials.isEmpty()) {
      logger.atWarning().log(
          "Found no matched and connected Android devices on the host, skip running.");
      return 0;
    }

    String moblyConfigFile =
        yamlTestbedUpdater.prepareMoblyConfig(
            serials, consoleInfo.getMoblyTestCasesDir().orElseThrow(), null);

    // Mobly log root directory contains all logs from each Mobly test zip run
    String moblyLogDir = prepareMoblyLogDir();
    ImmutableMap.Builder<String, String> moblyTestSummaryYamlFilesBuilder = ImmutableMap.builder();
    Instant startTime = Clock.systemUTC().instant();
    for (MoblyTestRunEntry moblyTestRunEntry : moblyTestRunEntries) {
      Optional<Path> moblyTestSummaryYaml =
          runSingleMoblyTestZip(moblyTestRunEntry, moblyConfigFile, moblyLogDir);
      moblyTestSummaryYaml.ifPresent(
          path -> moblyTestSummaryYamlFilesBuilder.put(moblyTestRunEntry.name(), path.toString()));
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
          "Zipping Mobly result directory \"%s\" to zip file \"%s\"...", moblyLogDir, resultZipDes);
      localFileUtil.zipDir(moblyLogDir, resultZipDes);
      logger.atInfo().log("Zipping Mobly result directory \"%s\" done", moblyLogDir);
    } else {
      logger.atWarning().log("Found no Mobly test summary yaml files after the test run.");
    }
    return 0;
  }

  private int runCtsvInM1() {
    throw new UnsupportedOperationException();
  }

  private void validateConfig() {
    if (isNullOrEmpty(config)) {
      throw new ParameterException(
          spec.commandLine(), "Missing param <config> after command \"run\".");
    }

    if (!CTSV_CONFIG.equals(config)) {
      throw new ParameterException(
          spec.commandLine(), "Only supports config \"cts-v\" at this point.");
    }
  }

  private boolean checkCtsvPreparation() {
    if (consoleInfo.getMoblyTestCasesDir().isEmpty()) {
      consoleUtil.printLine(
          String.format(
              "Mobly test cases dir is not set, please use 'set' command to set it first. All Mobly"
                  + " test zips should be put in the Mobly test cases dir.%nCurrent Mobly test"
                  + " cases dir: %s",
              consoleInfo.getMoblyTestCasesDir().orElse(null)));
      return false;
    }
    if (consoleInfo.getMoblyTestZipSuiteMainFile().isEmpty()) {
      consoleUtil.printLine(
          "\"suite_main.py\" file is required when running CTS-V test, please pass its local path"
              + " to JAVA system property \"MOBLY_TEST_ZIP_SUITE_MAIN_FILE\" and retry.");
      return false;
    } else if (!localFileUtil.isFileExist(getMoblyTestZipSuiteMainFilePath())) {
      consoleUtil.printLine(
          String.format(
              "The given Moby suite main file \"%s\" doesn't exist, please check and retry.",
              getMoblyTestZipSuiteMainFilePath()));
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
        .filter(consoleUtil::isZipFile)
        .forEach(
            moblyTestZip -> {
              String name = FilenameUtils.removeExtension(moblyTestZip.getName());
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
              "python3");

      com.google.devtools.mobileharness.shared.util.command.Command cmd =
          com.google.devtools.mobileharness.shared.util.command.Command.of(commandArray)
              .workDir(moblyTestZipPath.getParent())
              .redirectStderr(true)
              .onStdout(LineCallback.does(consoleUtil::printLine));

      commandExecutor.setBaseEnvironment(
          ImmutableMap.of("MOBLY_LOGPATH", curMoblyLogDir.get().toString()));
      consoleUtil.printLine(
          String.format("===== Running Mobly test zip [%s] =====", moblyTestRunEntry.name()));
      consoleUtil.printLine(String.format("Executing command \"%s\"", cmd));
      commandExecutor.exec(cmd);
      consoleUtil.printLine(
          String.format("===== Done running Mobly test zip [%s] =====", moblyTestRunEntry.name()));
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

      if (serialOpt != null && devices.contains(serialOpt)) {
        return ImmutableList.of(serialOpt);
      } else if (serialOpt != null && !devices.contains(serialOpt)) {
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
    return consoleUtil.completeHomeDirectory(
        consoleInfo.getMoblyTestZipSuiteMainFile().orElseThrow());
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
}
