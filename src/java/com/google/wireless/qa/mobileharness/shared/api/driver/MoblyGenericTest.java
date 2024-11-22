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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyConfigCreator;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.LineCallbackException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.CompositeDeviceUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.scanner.ScannerException;

/** Driver for running Mobly tests using third_party/py/mobly. */
@DriverAnnotation(help = "For running Mobly tests.")
public class MoblyGenericTest extends BaseDriver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FileAnnotation(required = true, help = "The .par file of your Mobly testcases.")
  public static final String FILE_TEST_LIB_PAR = "test_lib_par";

  @FileAnnotation(required = false, help = "A custom Mobly YAML config file")
  public static final String FILE_MOBLY_CONFIG = "mobly_config";

  private static final String RAW_MOBLY_LOG_DIR = "raw_mobly_logs";

  /** Name of file that catches stdout & stderr output of Mobly process. */
  private static final String RAW_MOBLY_LOG_ALL_IN_ONE = "mobly_command_output.log";

  private static final String MOBLY_LOG_DIR = "mobly_logs";

  public static final String TEST_SELECTOR_ALL = "all";

  @ParamAnnotation(
      required = false,
      help =
          "By default, all testcases in a Mobly test class are executed. To only execute a subset"
              + " of tests, supply the test names in this param as: \"test_a test_b ...\"")
  public static final String TEST_SELECTOR_KEY = "test_case_selector";

  private static final String MOBLY_SIDE_ERROR_MESSAGE =
      "\n\n"
          + "     ============================================================\n"
          + "     ||                                                        ||\n"
          + "     ||            Mobly did not execute correctly             ||\n"
          + "     ||                                                        ||\n"
          + "     ||         Please check mobly_command_output.log          ||\n"
          + "     ||                                                        ||\n"
          + "     ============================================================\n\n";

  private static final String ROOT_TEST_ID = "test_id";

  /**
   * This test parameter is a comma separated list of other test parameters which are intended to be
   * used only by MobileHarness or MobileHarness plugins and must not be passed to Mobly. It can be
   * used to pass some sensitive information, which is not required by Mobly, to MobileHarness
   * decorators or plugins. For example, this is used to pass account passwords to MH plugins
   * designed to securely handle them
   */
  @VisibleForTesting static final String PARAM_PRIVATE_PARAMS = "mobly_mh_only_params";

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final MoblyYamlParser parser;
  private final MoblyTestInfoMapHelper mapper;
  private final CommandExecutor executor;

  @Nullable protected String testbedName;

  @Inject
  MoblyGenericTest(
      Device device,
      TestInfo testInfo,
      MoblyYamlParser parser,
      MoblyTestInfoMapHelper mapper,
      CommandExecutor executor) {
    super(device, testInfo);
    this.localFileUtil = new LocalFileUtil();
    this.systemUtil = new SystemUtil();
    this.parser = parser;
    this.mapper = mapper;
    this.executor = executor;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    File configFile = prepareMoblyConfig(testInfo);
    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    boolean passed;
    try {
      passed = runMoblyCommand(testInfo, configFile);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Finished running Mobly test. Success: %s", passed);
    } finally {
      CompositeDeviceUtil.uncacheTestbed(getDevice());
    }

    if (!passed && TestResult.TIMEOUT.equals(testInfo.resultWithCause().get().type())) {
      // If we timed out there is a chance that the "latest" dir will not have been created so
      // handleOutput will throw a NoSuchFileException. Return early so users don't have to deal
      // with noise from infra issues. All test outputs will still be uploaded but there may be
      // duplicates.
      return;
    }

    // Don't use the Mobly Python Sponge converter to at this point
    processTestOutput(testInfo, passed);
  }

  /**
   * Processes Mobly's output artifacts.
   *
   * <p>This function does the following:
   *
   * <ul>
   *   <li>Sets the overall test result.
   *   <li>Sets the individual test results for each test case.
   *   <li>Sets the TestDiagnostics field in {@link TestInfo}.
   * </ul>
   *
   * @param testInfo the testInfo for this particular test
   * @param passed whether or not the test passed
   * @throws MobileHarnessException if test output processing failed
   * @throws InterruptedException if the thread was interrupted while processing the output
   */
  protected void processTestOutput(TestInfo testInfo, boolean passed)
      throws MobileHarnessException, InterruptedException {
    try {
      handleOutput(testInfo);
      parseResults(testInfo);
      setTestResult(testInfo, passed);
    } catch (MobileHarnessException | IOException e) {
      String moblyCommandOutput;
      try {
        moblyCommandOutput =
            localFileUtil.readFile(
                Path.of(testInfo.getGenFileDir()).resolve(RAW_MOBLY_LOG_ALL_IN_ONE));
      } catch (MobileHarnessException e2) {
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_FAILED_TO_READ_COMMAND_OUTPUT, "Failed to read command output", e2);
      }
      if (moblyCommandOutput.isEmpty()) {
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_COMMAND_OUTPUT_EMPTY, "Mobly command did not produce any logs.", e);
      }
      // When this happens, it is usually a syntax error in the python code. The error will be
      // logged in the command output, so attach it to the error message.
      if (moblyCommandOutput.length() < 2000) {
        testInfo
            .properties()
            .add(MoblyConstant.TestProperty.MOBLY_STACK_TRACE_KEY, moblyCommandOutput);
      }
      testInfo
          .properties()
          .add(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY, MOBLY_SIDE_ERROR_MESSAGE);
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new MobileHarnessException(
                  ExtErrorId.MOBLY_TEST_SCRIPT_ERROR, MOBLY_SIDE_ERROR_MESSAGE, e));
    }
  }

  protected File prepareMoblyConfig(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    JSONObject moblyJson = generateMoblyConfig(testInfo, getDevice());
    testbedName = getTestbedName(moblyJson);
    return prepareMoblyConfig(testInfo, moblyJson);
  }

  public File prepareMoblyConfig(TestInfo testInfo, JSONObject moblyJson)
      throws MobileHarnessException {
    File configFile = new File(testInfo.getGenFileDir(), MoblyConstant.TestGenOutput.CONFIG_FILE);
    LoaderOptions loaderConfig = new LoaderOptions();
    loaderConfig.setCodePointLimit(32 * 1024 * 1024);
    Yaml yaml =
        new Yaml(
            new SafeConstructor(new LoaderOptions()),
            new Representer(new DumperOptions()),
            new DumperOptions(),
            loaderConfig,
            new Resolver());
    StringWriter configWriter = new StringWriter();
    // The replace is used here because snakeyaml only supports YAML 1.1 which is not wholly
    // compatible with json. Specifically, in a JSON string a forward slash can be optionally
    // escaped (and is by org.json.JSONobject#toString).
    // TODO: Remove the hack when no longer relying on org.json.
    Object configObject = yaml.load(moblyJson.toString().replace("\\/", "/"));
    yaml.dump(configObject, configWriter);
    String header =
        "# Mobly config automatically generated by MoblyGenericTest for test "
            + testInfo.locator().getName()
            + "\n";
    try {
      localFileUtil.writeToFile(configFile.getPath(), header + configWriter);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_CONFIG_GENERATION_ERROR,
          "Unable to write the Mobly config to a file.",
          e);
    }
    return configFile;
  }

  public static String getTestbedName(JSONObject moblyJson) throws MobileHarnessException {
    try {
      JSONArray testbedArray = (JSONArray) moblyJson.get(MoblyConstant.ConfigKey.TESTBEDS);
      JSONObject testbed = (JSONObject) testbedArray.get(0);
      return testbed.getString(MoblyConstant.ConfigKey.TESTBED_NAME);
    } catch (JSONException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_TESTBED_CONFIG_PARSING_ERROR, "The given Mobly Config is invalid.", e);
    }
  }

  public JSONObject generateMoblyConfig(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException {
    try {
      return generateMoblyConfigHelper(testInfo, device);
    } catch (JSONException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_CONFIG_GENERATION_ERROR, "Failed to create Mobly config", e);
    }
  }

  private JSONObject generateMoblyConfigHelper(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException, JSONException {
    JSONObject config = new JSONObject();
    // Set Framework params (logdir).
    File logDir = getLogDir(testInfo);
    JSONObject moblyParams = new JSONObject();
    moblyParams.put("LogPath", logDir);
    config.put(MoblyConstant.ConfigKey.MOBLY_PARAMS, moblyParams);
    JSONObject testbedConfig = MoblyConfigCreator.getLocalMoblyConfig(device);

    // Overwrite the testbed config with the user-provided custom Mobly config, if it exists.
    if (testInfo.jobInfo().files().isTagNotEmpty(FILE_MOBLY_CONFIG)) {
      JSONObject customMoblyConfig =
          MoblyConfigCreator.getMoblyConfigFromYaml(
              localFileUtil.readFile(testInfo.jobInfo().files().getSingle(FILE_MOBLY_CONFIG)));
      MoblyConfigCreator.concatMoblyConfig(
          testbedConfig, customMoblyConfig, /* overwriteOriginal= */ true);
      logger.atInfo().log("Config after loading custom Mobly YAML: %s", config);
    }

    JSONObject testParams;
    if (testbedConfig.isNull(MoblyConstant.ConfigKey.TEST_PARAMS)) {
      testParams = new JSONObject();
      testbedConfig.put(MoblyConstant.ConfigKey.TEST_PARAMS, testParams);
    } else {
      testParams = testbedConfig.getJSONObject(MoblyConstant.ConfigKey.TEST_PARAMS);
    }

    // Merge MH job params into Mobly's local TestParams. In the event of merge conflict, local
    // testbed parameter will take precedence.
    for (Map.Entry<String, String> entry : testInfo.jobInfo().params().getAll().entrySet()) {
      if (testParams.isNull(entry.getKey())) {
        testParams.put(entry.getKey(), entry.getValue());
      }
    }
    // Dump all files from MH into a key called 'mh_files' in userparams.
    // The test and job file sets are different. jobInfo.files() comes from the build tuple and
    // contains user-added files. testInfo.files() contains only files added specifically to that
    // test by a plugin.
    ImmutableMultimap<String, String> mhFiles =
        new ImmutableMultimap.Builder<String, String>()
            .putAll(testInfo.jobInfo().files().getAll())
            .putAll(testInfo.files().getAll())
            .build();
    if (!mhFiles.isEmpty()) {
      testParams.put(MoblyConstant.ConfigKey.TEST_PARAM_MH_FILES, mhFiles.asMap());
    }

    testParams.put(
        MoblyConstant.ConfigKey.TESTS_PARAM_MH_TEST_PROPERTIES, testInfo.properties().getAll());
    testParams.put(
        MoblyConstant.ConfigKey.TESTS_PARAM_MH_JOB_PROPERTIES,
        testInfo.jobInfo().properties().getAll());
    testParams.put(
        MoblyConstant.ConfigKey.TEST_PARAM_ACTUAL_USER,
        testInfo.jobInfo().jobUser().getActualUser());

    // Pass test ID to mobly test. It is needed when Mobly test sends in-test request message to
    // MH decorators.
    testParams.put(ROOT_TEST_ID, testInfo.locator().getId());

    JSONArray testbedConfigArray = new JSONArray();
    testbedConfigArray.put(testbedConfig);
    config.put(MoblyConstant.ConfigKey.TESTBEDS, testbedConfigArray);

    logger.atInfo().log("Final config: %s", config);
    return config;
  }

  /**
   * Runs the Mobly test class.
   *
   * @param testInfo information about the test being run
   * @param configFile config to pass to Mobly in the -c flag
   * @return true if Mobly ran and returned a successful exit code, false otherwise
   * @throws MobileHarnessException if there was an issue running the Mobly test.
   */
  @VisibleForTesting
  boolean runMoblyCommand(TestInfo testInfo, File configFile)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet.Builder<String> paths = new ImmutableSet.Builder<>();

    // Use the adb and fastboot binaries that ship with Mobile Harness.
    Adb adb = new Adb();
    getSdkToolDir(adb.getAdbPath(), "adb").ifPresent(paths::add);
    Fastboot fastboot = new Fastboot();
    getSdkToolDir(fastboot.getFastbootPath(), "fastboot").ifPresent(paths::add);

    // System PATH
    String systemPath = systemUtil.getEnv("PATH");
    if (systemPath != null) {
      paths.add(systemPath);
    }

    String path = Joiner.on(':').join(paths.build());
    if (systemUtil.isOnMac()) {
      logger.atSevere().log("ATS 2.0 Mobly driver doesn't support macOS.");
      return false;
    }

    // The MH temp files dir has a long path name which can cause some issues with python libs
    // that may be used in a Mobly test (e.g., multiprocessing). Therefore we alias the long
    // pathname using a symbolic link that points to MH test tempfiles.
    Path tempDir;
    try {
      String tmpFileDir = testInfo.getTmpFileDir();
      tempDir =
          localFileUtil.linkDir(
              /* linkBaseDirPath= */ JAVA_IO_TMPDIR.value(),
              /* linkPrefixName= */ String.format("%05d-", new Random().nextInt(10000)),
              /* targetDirPath= */ tmpFileDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_FAILED_TO_CREATE_TEMP_DIRECTORY_ERROR,
          "Failed to create temp directory. ",
          e);
    }
    logger.atInfo().log("Mobly temp directory alias (sym link) is %s", tempDir);

    ImmutableMap<String, String> envVars =
        new ImmutableMap.Builder<String, String>()
            /*
             * Avoid propagating the runfiles dir, otherwise when executing java binaries from
             * within an Mobly test it will have trouble locating the main class at runtime.
             */
            .put("JAVA_RUNFILES", "")
            .put("JAVA_BIN", systemUtil.getJavaBin())
            /*
             */
            .put("PYTHON_RUNFILES", "")
            .put("PATH", path)
            .put("ADB_VENDOR_KEYS", adb.getAdbKeyPath())
            /*
             * Set TMPDIR so that python lib usage of the tempfile package
             * will use the MH test temp files dir. Even if the test par is killed due to a timeout,
             * MH will cleanup this dir.
             */
            .put("TMPDIR", tempDir.toString())
            .buildOrThrow();

    // Run! :)
    String[] cmd = generateTestCommand(testInfo, configFile);
    CommandProcess moblyProcess = null;
    try {
      moblyProcess = runCommand(testInfo, envVars, cmd);
      return moblyProcess.await().exitCode() == 0;
    } catch (CommandFailureException e) {
      // This will be thrown when Mobly returns a non-zero exit code which will happen when there
      // are any failed tests. Instead of propagating this as an exception (which will be considered
      // device/driver errors by MH instead of test failures), transform this into a boolean
      // indicating that there were test failures.
      return false;
    } catch (CommandTimeoutException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.TIMEOUT,
              new MobileHarnessException(
                  ExtErrorId.MOBLY_TEST_TIMEOUT, "Mobly test timed out.", e));
      return false;
    } catch (InterruptedException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Mobly was interrupted by Mobile Harness");
      throw e;
    } finally {
      if (moblyProcess != null && moblyProcess.isAlive()) {
        moblyProcess.killWithSignal(SystemUtil.KillSignal.SIGINT.value());
      }
      try {
        Files.delete(tempDir);
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Failed to clean up temp directory alias: %s", tempDir);
      }
    }
  }

  /** Massages Mobly log output structure into a format convenient for Sponge. */
  protected void handleOutput(TestInfo testInfo) throws IOException, MobileHarnessException {
    // Mobly creates a timestamped folder for the results and symlinks it to 'latest'. To avoid
    // Sponge getting two copies of the file, we will delete the symlink and move the files to the
    // root of the log folder.
    if (testbedName == null) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_TESTBED_NAME_EMPTY_ERROR, "Testbed name was not set.");
    }
    Path logDirLatest = Path.of(getLogDir(testInfo).getPath(), testbedName, "latest");
    Path logDirTimestamped = Files.readSymbolicLink(logDirLatest);
    Path logDirFinal = Path.of(testInfo.getGenFileDir(), MOBLY_LOG_DIR);
    Files.delete(logDirLatest);
    Files.move(logDirTimestamped, logDirFinal);
  }

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] generateTestCommand(TestInfo testInfo, File configFile)
      throws MobileHarnessException, InterruptedException {
    // Get par file.
    String testLibPar = testInfo.jobInfo().files().getSingle(FILE_TEST_LIB_PAR);

    if (testbedName == null) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_TESTBED_NAME_EMPTY_ERROR, "Testbed name was not set.");
    }

    // Create execution command.
    ArrayList<String> cmdElements =
        Lists.newArrayList(
            testLibPar,
            "--blog_dir=" + getLogDir(testInfo).getPath(),
            "--rpc_log_full_messages=all",
            // These two flags prevent the script from running as "nobody" on remote lab servers
            // running as root.
            "--uid=",
            "--gid=",
            // This flag is a seconday workaround for remote labs running as root failing with:
            // "getpwuid_r(3) failed on this machine for the uid 0."
            "--loas_pwd_fallback_in_corp",
            "--alsologtostderr",
            "--undefok=blog_dir,rpc_log_full_messages,alsologtostderr,uid,gid,loas_pwd_fallback_in_corp");

    cmdElements.addAll(
        ImmutableList.of(
            "--convert_results_to_sponge",
            String.format(
                "--sponge_root_directory=%s", testInfo.jobInfo().setting().getGenFileDir())));
    cmdElements.addAll(
        ImmutableList.of(
            "--",
            String.format("--config=%s", configFile.getPath()),
            String.format("--test_bed=%s", testbedName)));
    String testCaseSelectorInput =
        testInfo.jobInfo().params().get(TEST_SELECTOR_KEY, TEST_SELECTOR_ALL);
    testInfo.log().atInfo().alsoTo(logger).log("Selected test cases: %s", testCaseSelectorInput);
    if (!testCaseSelectorInput.equals(TEST_SELECTOR_ALL)) {
      cmdElements.add("--test_case");
      for (String testCase : Splitter.on(" ").split(testCaseSelectorInput)) {
        cmdElements.add(testCase);
      }
    }
    return cmdElements.toArray(new String[0]);
  }

  /**
   * Runs a command and checks its exit code.
   *
   * @param testInfo the testInfo instance where to report results
   * @param env environment to give to command
   * @param commandArgs commands to execute
   * @return a handle to the newly created process
   * @throws MobileHarnessException if the command failed or could not be executed
   */
  protected CommandProcess runCommand(
      final TestInfo testInfo, Map<String, String> env, String[] commandArgs)
      throws MobileHarnessException {
    // Prepare the debug string for logging purposes
    StringBuilder debugString =
        new StringBuilder(Joiner.on(' ').withKeyValueSeparator("=").join(env));
    if (debugString.length() > 0) {
      debugString.append(' ');
    }
    Joiner.on(" ").appendTo(debugString, commandArgs);

    // Prepare a callback to echo command output to the console
    try {
      // Run!
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Running command: %s; output of stdout/stderr is saved in file %s",
              debugString, RAW_MOBLY_LOG_ALL_IN_ONE);

      BufferedWriter writer =
          Files.newBufferedWriter(
              Path.of(testInfo.getGenFileDir()).resolve(RAW_MOBLY_LOG_ALL_IN_ONE));

      return executor.start(
          Command.of(commandArgs)
              .extraEnv(env)
              .onStdout(
                  LineCallback.does(
                      line -> {
                        testInfo.log().atInfo().alsoTo(logger).log("[Mobly] %s", line);
                        try {
                          writer.write(line + "\n");
                        } catch (IOException e) {
                          throw new LineCallbackException(
                              "Failed to write",
                              e,
                              /* killCommand= */ false, /* stopReadingOutput */
                              true);
                        }
                      }))
              .onExit(
                  unusedResult -> {
                    try {
                      writer.close();
                    } catch (IOException e) {
                      testInfo
                          .log()
                          .atWarning()
                          .alsoTo(logger)
                          .log("Unable to close writer for %s", RAW_MOBLY_LOG_ALL_IN_ONE);
                    }
                  })
              .needStdoutInResult(false)
              .needStderrInResult(false)
              .redirectStderr(true)
              .timeout(
                  // Use remaining time to run the Mobly command but leave 2 minutes for post
                  // processing and upload of artifacts.
                  testInfo.timer().remainingTimeJava().minus(Duration.ofMinutes(2))));
    } catch (IOException | CommandStartException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_EXECUTE_ERROR, "Failed to execute command " + debugString, e);
    }
  }

  /** Folder where Mobly should write its output files. */
  public static File getLogDir(TestInfo testInfo) throws MobileHarnessException {
    return new File(testInfo.getGenFileDir(), RAW_MOBLY_LOG_DIR);
  }

  /**
   * Method for parsing Mobly results from the {@link
   * com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant.TestGenOutput.SUMMARY_FILE_NAME}
   * file. The results are parsed to a generic {@code MoblyYamlDocEntry} container list and then
   * mapped to the {@link com.google.wireless.qa.mobileharness.shared.model.job.TestInfo} object
   * given.
   */
  protected void parseResults(TestInfo testInfo) throws IOException, MobileHarnessException {
    try {
      // Build the path to the test_summary.yaml file
      String summaryFilePath =
          PathUtil.join(
              testInfo.getGenFileDir(),
              MoblyConstant.TestGenOutput.MOBLY_LOG_DIR,
              MoblyConstant.TestGenOutput.SUMMARY_FILE_NAME);

      ImmutableList<MoblyYamlDocEntry> results = parser.parse(summaryFilePath);
      mapper.map(testInfo, results);
    } catch (MobileHarnessException | IOException | ScannerException e) {
      // Parsing failed. Update TestInfo with parsing error and reraise. When ScannerException is
      // raised, it may be because the host ran out of space and did not finish writing the YAML
      // file.
      testInfo
          .warnings()
          .add(
              new MobileHarnessException(
                  ExtErrorId.MOBLY_TEST_SUMMARY_YAML_PARSING_ERROR,
                  String.format(
                      "Unable to parse %s\n%s:\n%s",
                      MoblyConstant.TestGenOutput.SUMMARY_FILE_NAME,
                      e.getClass().getSimpleName(),
                      e.getMessage()),
                  e));
      throw e;
    }
  }

  private void setTestResult(TestInfo testInfo, boolean passed) {
    MobileHarnessException exception =
        new MobileHarnessException(
            ExtErrorId.MOBLY_TEST_FAILURE,
            "The Mobly test run had some failures. Please see Mobly test results.");

    if (passed) {
      testInfo.resultWithCause().setPass();
    } else {
      testInfo
          .resultWithCause()
          .setNonPassing(TestResult.FAIL, ErrorModelConverter.toExceptionDetail(exception));
    }

    // Set device specific TestInfo to have the same result
    for (String deviceId : getDeviceIds()) {
      TestInfo subTest = testInfo.subTests().getById(deviceId);
      if (subTest == null) {
        continue;
      }
      if (passed) {
        subTest.resultWithCause().setPass();
      } else {
        subTest
            .resultWithCause()
            .setNonPassing(TestResult.FAIL, ErrorModelConverter.toExceptionDetail(exception));
      }
      subTest.status().set(TestStatus.DONE);
    }
  }

  private ImmutableList<String> getDeviceIds() {
    Device device = getDevice();
    if (!(device instanceof CompositeDevice)) {
      return ImmutableList.of(device.getDeviceId());
    }
    CompositeDevice compositeDevice = (CompositeDevice) device;
    return compositeDevice.getManagedDevices().stream()
        .map(Device::getDeviceId)
        .collect(toImmutableList());
  }

  private Optional<String> getSdkToolDir(String mhSdkToolPath, String sdkToolName)
      throws MobileHarnessException, InterruptedException {
    File sdkFile;
    if (isNullOrEmpty(mhSdkToolPath) || Ascii.equalsIgnoreCase(mhSdkToolPath, sdkToolName)) {
      CommandResult result = executor.exec(Command.of("which", sdkToolName).successExitCodes(0, 1));

      if (result.exitCode() != 0) {
        String possibleSdkTool = executor.run(Command.of("whereis", sdkToolName));
        getTest()
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    ExtErrorId.MOBLY_SDK_TOOL_NOT_FOUND_ERROR,
                    String.format(
                        "Unable to find the sdk tool \"%s\". Executables found: %s",
                        sdkToolName, possibleSdkTool)),
                logger);
        return Optional.empty();
      }
      sdkFile = new File(result.stdout().trim());
    } else {
      sdkFile = new File(mhSdkToolPath);
    }

    return Optional.ofNullable(sdkFile.getParent());
  }
}
