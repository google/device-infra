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
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyConfigGenerator;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutionException;
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
import com.google.devtools.mobileharness.shared.util.testcomponents.TestComponentsDirUtil;
import com.google.devtools.mobileharness.shared.util.testdiagnostics.TestDiagnosticsHelper;
import com.google.wireless.qa.mobileharness.shared.api.CompositeDeviceUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyTestSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.sponge.TestXmlParser;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
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
public class MoblyTest extends BaseDriver implements MoblyTestSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String RAW_MOBLY_LOG_DIR = "raw_mobly_logs";

  /** Name of file that catches stdout & stderr output of Mobly process. */
  private static final String RAW_MOBLY_LOG_ALL_IN_ONE = "mobly_command_output.log";

  private static final String ENV_MOBLY_LOGPATH = "MOBLY_LOGPATH";

  public static final String TEST_SELECTOR_ALL = "all";

  public static final Duration DEFAULT_CLEANUP_TIMEOUT = Duration.ofSeconds(30);

  private static final String ROOT_TEST_ID = "test_id";

  @VisibleForTesting static final String PARAM_PRIVATE_PARAMS = "mobly_mh_only_params";

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final TestDiagnosticsHelper testDiagnosticsHelper;
  private final TestComponentsDirUtil testComponentsDirUtil;
  private final CommandExecutor executor;

  @Nullable protected String testbedName;

  /**
   * Creates the driver for running Mobly tests.
   *
   * <p>This constructor is required by the lab server framework. Do NOT modify the parameter list.
   *
   * @param device the device that loads this driver to run the test
   */
  public MoblyTest(Device device, TestInfo testInfo) {
    this(device, testInfo, new CommandExecutor());
  }

  // TODO: This @VisibleForTesting annotation was being ignored by prod code.
  // Please check that removing it is correct, and remove this comment along with it.
  // @VisibleForTesting
  MoblyTest(Device device, TestInfo testInfo, CommandExecutor executor) {
    super(device, testInfo);
    this.localFileUtil = new LocalFileUtil();
    this.systemUtil = new SystemUtil();
    this.executor = executor;
    this.testDiagnosticsHelper = new TestDiagnosticsHelper();
    this.testComponentsDirUtil = new TestComponentsDirUtil();
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
      if (!passed && testInfo.resultWithCause().get().type() == TestResult.UNKNOWN) {
        MobileHarnessException exception =
            new MobileHarnessException(
                ExtErrorId.MOBLY_TEST_FAILURE,
                "The Mobly test run had some failures. Please check mobly_command_output.log.");
        testInfo.resultWithCause().setNonPassing(TestResult.ERROR, exception);
      }
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
    processTestOutput(testInfo);
  }

  /**
   * Processes Mobly's output artifacts.
   *
   * @param testInfo the testInfo for this particular test
   * @throws MobileHarnessException if test output processing failed
   * @throws InterruptedException if the thread was interrupted while processing the output
   */
  protected void processTestOutput(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Path spongeXmlPath = getSpongeXmlPath(testInfo);
    if (!localFileUtil.isFileExist(spongeXmlPath)) {
      String summaryFilePath =
          PathUtil.join(
              testInfo.getGenFileDir(),
              MoblyConstant.TestGenOutput.MOBLY_LOG_DIR,
              MoblyConstant.TestGenOutput.SUMMARY_FILE_NAME);
      if (!localFileUtil.isFileExist(summaryFilePath)) {
        logger.atInfo().log(
            "summaryFile [%s] doesn't exist. Starting to process command output:", summaryFilePath);
        processCommandOutput(testInfo);
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_TEST_SUMMARY_YAML_MISSING_ERROR,
            "Mobly test_summary.yaml is missing. Please check mobly_command_output.log.");
      }
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_SPONGE_XML_MISSING_ERROR,
          "Mobly did not produce a test.xml. Please check mobly_command_output.log.");
    } else {
      TestXmlParser testXmlParser = new TestXmlParser(true);
      testXmlParser.parseTestXmlFileToTestInfo(testInfo, spongeXmlPath.toString(), false);
    }
  }

  private void processCommandOutput(TestInfo testInfo) throws MobileHarnessException {
    try {
      String rawMoblyCommandLogPath =
          Path.of(testInfo.getGenFileDir()).resolve(RAW_MOBLY_LOG_ALL_IN_ONE).toString();
      List<String> rawMoblyCommandLogLn =
          localFileUtil.readLineListFromFile(rawMoblyCommandLogPath);
      for (String line : rawMoblyCommandLogLn) {
        if (line.contains("GLIBC")) {
          throw new MobileHarnessException(
              ExtErrorId.MOBLY_LAB_GLIBC_ERROR,
              String.format(
                  "GLIBC error found in Mobly command output. Original error: %s.\n", line));
        } else if (line.contains("*** Check failure stack trace: ***")) {
          throw new MobileHarnessException(
              ExtErrorId.MOBLY_TEST_CRASH, "Found a crash in Mobly command output.");
        }
      }
    } catch (InvalidPathException ipe) {
      MobileHarnessException exception =
          new MobileHarnessException(
              ExtErrorId.MOBLY_COMMAND_OUTPUT_LOG_MISSING,
              String.format("%s does not exist.", RAW_MOBLY_LOG_ALL_IN_ONE),
              ipe);
      testInfo.warnings().addAndLog(exception);
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == ExtErrorId.MOBLY_LAB_GLIBC_ERROR
          || e.getErrorId() == ExtErrorId.MOBLY_TEST_CRASH) {
        throw e;
      } else {
        testInfo.warnings().addAndLog(e);
      }
    }
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
        "# Mobly config automatically generated by MoblyTest for test "
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
    // Get params passed in from the build rule and merge them into local TestParams. In the event
    // of merge conflict, local testbed parameter will take precedence.
    JSONObject blazeParams = new JSONObject();
    ImmutableSet<String> privateParams = getPrivateParamNames(testInfo);
    for (Map.Entry<String, String> entry : testInfo.jobInfo().params().getAll().entrySet()) {
      if (!privateParams.contains(entry.getKey())) {
        blazeParams.put(entry.getKey(), entry.getValue());
      }
    }
    JSONObject testbedConfig = MoblyConfigGenerator.getLocalMoblyConfig(device);
    JSONObject testParams;
    if (testbedConfig.isNull(MoblyConstant.ConfigKey.TEST_PARAMS)) {
      testParams = new JSONObject();
      testbedConfig.put(MoblyConstant.ConfigKey.TEST_PARAMS, testParams);
    } else {
      testParams = testbedConfig.getJSONObject(MoblyConstant.ConfigKey.TEST_PARAMS);
    }
    // Dump blazeParams into TestBed:TestParams part of the config.
    if (blazeParams.length() > 0) {
      for (String key : JSONObject.getNames(blazeParams)) {
        if (testParams.isNull(key)) {
          testParams.put(key, blazeParams.get(key));
        }
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
    // Dump the sponge link (as recognized by MH) into a key called 'mh_sponge_link' in userparams.
    // MH can only compute this link for tests launched from Moscar or Guitar.
    Optional<String> spongeLink =
        testInfo
            .jobInfo()
            .properties()
            .getOptional(Ascii.toLowerCase(PropertyName.Job.SPONGE_LINK_OF_TEST.toString()));
    if (spongeLink.isPresent()) {
      testParams.put(MoblyConstant.ConfigKey.TEST_PARAM_MH_SPONGE_LINK, spongeLink.get());
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

  private static ImmutableSet<String> getPrivateParamNames(TestInfo testInfo) {
    return ImmutableSet.copyOf(
        Splitter.on(",")
            .trimResults()
            .omitEmptyStrings()
            .split(testInfo.jobInfo().params().get(PARAM_PRIVATE_PARAMS, "")));
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

    // Use the adb and fastboot binaries that ship with Mobile Harness
    Adb adb = new Adb();
    getSdkToolDir(adb.getAdbPath(), "adb").ifPresent(paths::add);
    Fastboot fastboot = new Fastboot();
    // Android build tools provided by Mobile Harness, including fastboot and mke2fs.
    getSdkToolDir(fastboot.getFastbootPath(), "fastboot").ifPresent(paths::add);

    // System PATH
    String systemPath = systemUtil.getEnv("PATH");
    if (systemPath != null) {
      paths.add(systemPath);
    }

    String path = Joiner.on(':').join(paths.build());

    /* Path to {@code genFileDir} for the test, these are fetched into undeclared outputs at the
     * client side by MH. */
    String testGenFileDir = testInfo.getGenFileDir();
    // Folder where all Sponge annotation part files should be written on the lab side.
    File annotationsDir = createAnnotationsDir(testGenFileDir);

    // The MH temp files dir has a long path name which can cause some issues with python libs
    // that may be used in a Mobly test (e.g., multiprocessing). Therefore we alias the long
    // pathname using a symbolic link that points to MH test tempfiles.
    Path tempDir;
    Path testDiagnosticsDir;
    Path spongeXmlPath;
    Path testComponentsDir;
    try {
      String tmpFileDir = testInfo.getTmpFileDir();
      String testId = testInfo.locator().getId();
      String testDiagnosticsFileDir = testDiagnosticsHelper.getTestDiagnosticsDir(testInfo);
      String testComponentsDirHard = testComponentsDirUtil.prepareAndGetTestComponentsDir(testInfo);
      localFileUtil.prepareDir(testDiagnosticsFileDir);
      tempDir =
          localFileUtil.checkDir(
              localFileUtil.linkDir(
                  /* linkBaseDirPath= */ JAVA_IO_TMPDIR.value(),
                  /* linkPrefixName= */ String.format("testTemp-%s-", testId),
                  /* targetDirPath= */ tmpFileDir));
      testDiagnosticsDir =
          localFileUtil.checkDir(
              localFileUtil.linkDir(
                  /* linkBaseDirPath= */ JAVA_IO_TMPDIR.value(),
                  /* linkPrefixName= */ String.format("testDiagnostics-%s-", testId),
                  /* targetDirPath= */ testDiagnosticsFileDir));
      testComponentsDir =
          localFileUtil.checkDir(
              localFileUtil.linkDir(
                  /* linkBaseDirPath= */ JAVA_IO_TMPDIR.value(),
                  /* linkPrefixName= */ String.format("testComponents-%s-", testId),
                  /* targetDirPath= */ testComponentsDirHard));
      spongeXmlPath = getSpongeXmlPath(testInfo);
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
             * Avoid propagating the runfiles dir of a local MH test. If PYTHON_RUNFILES is set, the
             * py_binary wrapper script generated by blaze will look there for python module files,
             * instead of searching its cwd and parent for python modules.
             * This breaks bundled Mobly tests because it tries to find Mobly modules defined in the
             * MobileHarness tree instead of the Mobly testcase par.
             */
            .put("PYTHON_RUNFILES", "")
            .put("PATH", path)
            .put("ADB_VENDOR_KEYS", adb.getAdbKeyPath())
            .put("TMPDIR", tempDir.toString())
            /*
             * Set the environmental variables for Sponge undeclared outputs and annotations at the
             * lab to the test genfiles. Note: Only test genfiles are pulled back into the client
             * side undeclared outputs.
             */
            .put(SystemUtil.ENV_TEST_UNDECLARED_OUTPUTS_DIR, testGenFileDir)
            .put(SystemUtil.ENV_TEST_UNDECLARED_OUTPUTS_ANNOTATIONS_DIR, annotationsDir.getPath())
            .put(SystemUtil.ENV_TEST_DIAGNOSTICS_OUTPUT_DIR, testDiagnosticsDir.toString())
            .put(SystemUtil.ENV_TEST_COMPONENTS_DIR, testComponentsDir.toString())
            .put(SystemUtil.ENV_XML_OUTPUT_FILE_NAME, spongeXmlPath.toString())
            .put(ENV_MOBLY_LOGPATH, getLogDir(testInfo).getPath())
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
        logger.atInfo().log("Sending SIGINT to Mobly process");
        moblyProcess.killWithSignal(SystemUtil.KillSignal.SIGINT.value());
        try {
          CommandResult unusedResult = moblyProcess.await(getCleanupTimeout(testInfo));
        } catch (TimeoutException | InterruptedException | CommandExecutionException e) {
          if (moblyProcess != null && moblyProcess.isAlive()) {
            logger.atInfo().log("Forcibly killing Mobly process");
            moblyProcess.killForcibly();
          }
        }
      }
      try {
        Files.delete(tempDir);
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Failed to clean up temp directory alias: %s", tempDir);
      }
    }
  }

  @VisibleForTesting
  static Duration getCleanupTimeout(TestInfo testInfo) {
    String timeout = testInfo.jobInfo().params().get(CLEANUP_TIMEOUT_KEY);
    if (timeout == null) {
      return DEFAULT_CLEANUP_TIMEOUT;
    }
    try {
      long parsedTmeout = Long.parseLong(timeout);
      if (parsedTmeout <= 0) {
        logger.atWarning().log(
            "Value of `%s` must be positive. The default value [%s s] will be used instead.",
            CLEANUP_TIMEOUT_KEY, DEFAULT_CLEANUP_TIMEOUT.toSeconds());
        return DEFAULT_CLEANUP_TIMEOUT;
      }
      return Duration.ofSeconds(parsedTmeout);
    } catch (NumberFormatException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse value of `%s` (%s). The default value [%s s] will be used instead.",
          CLEANUP_TIMEOUT_KEY, timeout, DEFAULT_CLEANUP_TIMEOUT.toSeconds());
      return DEFAULT_CLEANUP_TIMEOUT;
    }
  }

  private File createAnnotationsDir(String rootDir) throws MobileHarnessException {
    File dir = new File(rootDir, "annotations");
    localFileUtil.prepareDir(dir.getPath());
    localFileUtil.grantFileOrDirFullAccess(dir.getPath());
    return dir;
  }

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] generateTestCommand(TestInfo testInfo, File configFile)
      throws MobileHarnessException, InterruptedException {
    // Get par file.
    String testLibPar = testInfo.jobInfo().files().getSingle(FILE_TEST_LIB_PAR);
    // Make sure this file is executable; by default transferred files have no execute permissions.
    localFileUtil.grantFileOrDirFullAccess(testLibPar);

    if (testbedName == null) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_TESTBED_NAME_EMPTY_ERROR, "Testbed name was not set.");
    }

    boolean secureWrapperUserNone =
        testInfo.jobInfo().params().getBool(SECURE_WRAPPER_USER_NONE, false);
    ArrayList<String> parFlags =
        Lists.newArrayList(
            "--blog_dir=" + getLogDir(testInfo).getPath(),
            "--rpc_log_full_messages=all",
            // These two flags prevent the script from running as "nobody" on remote lab servers
            // running as root.
            "--uid=",
            "--gid=",
            // This flag is a secondary workaround for remote labs running as root failing with:
            // "getpwuid_r(3) failed on this machine for the uid 0."
            "--loas_pwd_fallback_in_corp",
            "--alsologtostderr");
    if (secureWrapperUserNone) {
      // This is causing crashes for some users. See b/324318060#comment14
      parFlags.add("--securewrapper_dummyimpl_auth_user=nobody");
    }

    // Create execution command.
    ArrayList<String> cmdElements = Lists.newArrayList(testLibPar);
    cmdElements.addAll(parFlags);
    cmdElements.addAll(
        ImmutableList.of(
            "--undefok=blog_dir,rpc_log_full_messages,alsologtostderr,uid,gid,loas_pwd_fallback_in_corp,securewrapper_dummyimpl_auth_user",
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
    if (testInfo.jobInfo().params().getBool(MoblyConstant.TestProperty.MOBLY_TEST_VERBOSE, false)) {
      cmdElements.add("--verbose");
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

  /** Folder where Mobly should write its output files. */
  public static File getLogDir(TestInfo testInfo) throws MobileHarnessException {
    return new File(testInfo.getGenFileDir(), RAW_MOBLY_LOG_DIR);
  }

  protected Path getSpongeXmlPath(TestInfo testInfo) throws MobileHarnessException {
    return Path.of(testInfo.getGenFileDir(), "test.xml");
  }
}
