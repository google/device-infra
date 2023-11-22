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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyConfigGenerator;
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
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.CompositeDeviceUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
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

/** Driver for running Mobly tests using third_party/py/mobly. */
@DriverAnnotation(help = "For running Mobly tests.")
public class MoblyGenericTest extends BaseDriver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FileAnnotation(required = true, help = "The .par file of your Mobly testcases.")
  public static final String FILE_TEST_LIB_PAR = "test_lib_par";

  private static final String RAW_MOBLY_LOG_DIR = "raw_mobly_logs";

  /** Name of file that catches stdout & stderr output of Mobly process. */
  private static final String RAW_MOBLY_LOG_ALL_IN_ONE = "mobly_command_output.log";

  public static final String TEST_SELECTOR_ALL = "all";

  @ParamAnnotation(
      required = false,
      help =
          "By default, all testcases in a Mobly test class are executed. To only execute a subset"
              + " of tests, supply the test names in this param as: \"test_a test_b ...\"")
  public static final String TEST_SELECTOR_KEY = "test_case_selector";

  private static final String ROOT_TEST_ID = "test_id";

  /**
   * This test parameter is a comma separated list of other test parameters which are intended to be
   * used only by MobileHarness or MobileHarness plugins and must not be passed to Mobly. It can be
   * used to pass some sensitive information, which is not required by Mobly, to MobileHarness
   * decorators or plugins. For example, this is used to pass account passwords to MH plugins
   * designed to securely handle them (see go/google-ota-mh for more details).
   */
  @VisibleForTesting static final String PARAM_PRIVATE_PARAMS = "mobly_mh_only_params";

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final CommandExecutor executor;
  private final Clock clock;

  @Nullable protected String testbedName;

  @Inject
  MoblyGenericTest(Device device, TestInfo testInfo, CommandExecutor executor, Clock clock) {
    super(device, testInfo);
    this.localFileUtil = new LocalFileUtil();
    this.systemUtil = new SystemUtil();
    this.executor = executor;
    this.clock = clock;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    boolean usePythonSpongeConverter = false;
    File configFile = prepareMoblyConfig(testInfo);
    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    boolean passed;
    Instant startTime = clock.instant();
    Instant endTime;
    try {
      passed = runMoblyCommand(testInfo, configFile, usePythonSpongeConverter);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Finished running Mobly test. Success: %s", passed);
    } finally {
      CompositeDeviceUtil.uncacheTestbed(getDevice());
      endTime = clock.instant();
    }
    postMoblyCommandExec(startTime, endTime);

    if (!passed && TestResult.TIMEOUT.equals(testInfo.resultWithCause().get().type())) {
      // If we timed out there is a chance that the "latest" dir will not have been created so
      // handleOutput will throw a NoSuchFileException. Return early so users don't have to deal
      // with noise from infra issues. All test outputs will still be uploaded but there may be
      // duplicates.
      return;
    }

    if (passed) {
      testInfo.resultWithCause().setPass();
    }
  }

  protected void postMoblyCommandExec(Instant testStartTime, Instant testEndTime)
      throws InterruptedException {
    // Do nothing by default.
  }

  protected File prepareMoblyConfig(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    JSONObject moblyJson = generateMoblyConfig(testInfo, getDevice());
    testbedName = getTestbedName(moblyJson);
    return prepareMoblyConfig(testInfo, moblyJson, localFileUtil);
  }

  public static File prepareMoblyConfig(
      TestInfo testInfo, JSONObject moblyJson, LocalFileUtil localFileUtil)
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

  public static JSONObject generateMoblyConfig(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException {
    try {
      return generateMoblyConfigHelper(testInfo, device);
    } catch (JSONException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_CONFIG_GENERATION_ERROR, "Failed to create Mobly config", e);
    }
  }

  private static JSONObject generateMoblyConfigHelper(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException, JSONException {
    JSONObject config = new JSONObject();
    // Set Framework params (logdir).
    File logDir = getLogDir(testInfo);
    JSONObject moblyParams = new JSONObject();
    moblyParams.put("LogPath", logDir);
    config.put(MoblyConstant.ConfigKey.MOBLY_PARAMS, moblyParams);
    JSONObject testbedConfig = MoblyConfigGenerator.getLocalMoblyConfig(device);
    JSONObject testParams;
    if (testbedConfig.isNull(MoblyConstant.ConfigKey.TEST_PARAMS)) {
      testParams = new JSONObject();
      testbedConfig.put(MoblyConstant.ConfigKey.TEST_PARAMS, testParams);
    } else {
      testParams = testbedConfig.getJSONObject(MoblyConstant.ConfigKey.TEST_PARAMS);
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
   * @param usePythonSpongeConverter whether or not to use the Python Sponge converter results
   * @return true if Mobly ran and returned a successful exit code, false otherwise
   * @throws MobileHarnessException if there was an issue running the Mobly test.
   */
  @VisibleForTesting
  boolean runMoblyCommand(TestInfo testInfo, File configFile, boolean usePythonSpongeConverter)
      throws MobileHarnessException, InterruptedException {

    // Use the adb and fastboot binaries that ship with Mobile Harness.
    String adbPathStr = new Adb().getAdbPath();
    File adbPath =
        Ascii.equalsIgnoreCase(adbPathStr, "adb")
            ? getSdkToolPath("adb").toFile()
            : new File(adbPathStr);
    String fastbootPathStr = new Fastboot().getFastbootPath();
    File fastbootPath =
        Ascii.equalsIgnoreCase(fastbootPathStr, "fastboot")
            ? getSdkToolPath("fastboot").toFile()
            : new File(fastbootPathStr);
    String path =
        Joiner.on(':')
            .join(
                adbPath.getParent(), // Android platform tools provided by Mobile Harness, including
                // adb
                // and aapt.
                fastbootPath
                    .getParent(), // Android build tools provided by Mobile Harness, including
                // fastboot and mke2fs.
                systemUtil.getEnv("PATH"));
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
            /*
             * Set TMPDIR so that python lib usage of the tempfile package
             * will use the MH test temp files dir. Even if the test par is killed due to a timeout,
             * MH will cleanup this dir.
             */
            .put("TMPDIR", tempDir.toString())
            .buildOrThrow();

    // Run! :)
    String[] cmd = generateTestCommand(testInfo, configFile, usePythonSpongeConverter);
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

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] generateTestCommand(TestInfo testInfo, File configFile, boolean usePythonSpongeConverter)
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

    if (usePythonSpongeConverter) {
      cmdElements.addAll(
          ImmutableList.of(
              "--convert_results_to_sponge",
              String.format(
                  "--sponge_root_directory=%s", testInfo.jobInfo().setting().getGenFileDir())));
    }
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

  private Path getSdkToolPath(String sdkToolName)
      throws MobileHarnessException, InterruptedException {
    CommandResult result = executor.exec(Command.of("which", sdkToolName).successExitCodes(0, 1));

    if (result.exitCode() != 0) {
      String possibleSdkTool = executor.run(Command.of("whereis", sdkToolName));
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_SDK_TOOL_NOT_FOUND_ERROR,
          String.format(
              "Unable to find the sdk tool \"%s\". Executables found: %s",
              sdkToolName, possibleSdkTool));
    }
    return Path.of(result.stdout().trim());
  }
}
