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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.InstallMoblyTestDepsArgs;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospPackageTestSetupUtil;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospPackageTestSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.scanner.ScannerException;

/** Driver for running Mobly tests packaged in AOSP and distributed via the Android Build. */
@DriverAnnotation(
    help = "For running Mobly tests packaged in AOSP and distributed via the Android Build.")
public class MoblyAospPackageTest extends MoblyTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MOBLY_CONFIG_KEY_PREFIX_MH = "mh_";

  private static final String MOBLY_LOG_DIR = "mobly_logs";

  private static final String MOBLY_SIDE_ERROR_MESSAGE =
      "\n\n"
          + "     ============================================================\n"
          + "     ||                                                        ||\n"
          + "     ||            Mobly did not execute correctly             ||\n"
          + "     ||                                                        ||\n"
          + "     ||         Please check mobly_command_output.log          ||\n"
          + "     ||                                                        ||\n"
          + "     ============================================================\n\n";

  private final MoblyAospPackageTestSetupUtil setupUtil;
  private final LocalFileUtil localFileUtil;
  private final MoblyYamlParser parser;
  private final MoblyTestInfoMapHelper mapper;

  @Inject
  MoblyAospPackageTest(
      Device device,
      TestInfo testInfo,
      MoblyYamlParser parser,
      MoblyTestInfoMapHelper mapper,
      CommandExecutor executor,
      MoblyAospPackageTestSetupUtil setupUtil,
      LocalFileUtil localFileUtil) {
    super(device, testInfo, executor);
    this.setupUtil = setupUtil;
    this.localFileUtil = localFileUtil;
    this.parser = parser;
    this.mapper = mapper;
  }

  /** Recursively strips "mh_" prefix from Mobly config keys. */
  @VisibleForTesting
  static JSONObject convertMoblyConfig(JSONObject moblyJson) {
    JSONObject newMoblyJson = new JSONObject();
    Iterator<String> keys = moblyJson.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      String newKey = key.replaceFirst("^" + MOBLY_CONFIG_KEY_PREFIX_MH, "");
      Object value = moblyJson.get(key);
      if (value instanceof JSONObject) {
        JSONObject newJsonObject = convertMoblyConfig((JSONObject) value);
        newMoblyJson.put(newKey, newJsonObject);
      } else if (value instanceof JSONArray) {
        JSONArray newJsonArray = new JSONArray();
        for (Object element : (JSONArray) value) {
          if (element instanceof JSONObject) {
            JSONObject newJsonObject = convertMoblyConfig((JSONObject) element);
            newJsonArray.put(newJsonObject);
          } else {
            newJsonArray.put(element);
          }
        }
        newMoblyJson.put(newKey, newJsonArray);
      } else {
        newMoblyJson.put(newKey, value);
      }
    }
    return newMoblyJson;
  }

  /**
   * Prepares the Mobly config.
   *
   * <p>Invokes {@link MoblyGenericTest#generateMoblyConfig} to populate the config fields, but also
   * strips any key names with a "mh_" prefix so that a test written in AOSP can avoid making
   * explicit references to MH (e.g. "mh_files" becomes "files" in the config).
   */
  @Override
  protected File prepareMoblyConfig(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    JSONObject moblyJson = convertMoblyConfig(generateMoblyConfig(testInfo, getDevice()));
    testbedName = MoblyTest.getTestbedName(moblyJson);
    return prepareMoblyConfig(testInfo, moblyJson);
  }

  /** Generates the test execution command. */
  @Override
  @VisibleForTesting
  String[] generateTestCommand(TestInfo testInfo, File configFile)
      throws MobileHarnessException, InterruptedException {
    Path moblyPkg =
        Path.of(testInfo.jobInfo().files().getSingle(MoblyAospPackageTestSpec.FILE_MOBLY_PKG));
    localFileUtil.grantFileOrDirFullAccess(moblyPkg.toString());
    Path moblyUnzipDir = Path.of(testInfo.getTmpFileDir(), "mobly");
    localFileUtil.prepareDir(moblyUnzipDir);
    localFileUtil.grantFileOrDirFullAccess(moblyUnzipDir.toString());
    Path venvPath = Path.of(testInfo.getTmpFileDir(), "venv");
    Path configPath = Path.of(configFile.getPath());
    String testPath = testInfo.jobInfo().params().get(MoblyAospPackageTestSpec.PARAM_TEST_PATH);
    if (testPath != null && !testPath.isEmpty()) {
      localFileUtil.grantFileOrDirFullAccess(testPath);
    }
    String testCaseSelector = testInfo.jobInfo().params().get(TEST_SELECTOR_KEY);
    String pythonVersion =
        testInfo.jobInfo().params().get(MoblyAospPackageTestSpec.PARAM_PYTHON_VERSION);

    InstallMoblyTestDepsArgs.Builder installMoblyTestDepsArgsBuilder =
        InstallMoblyTestDepsArgs.builder().setDefaultTimeout(Duration.ofMinutes(30));

    if (testInfo
        .jobInfo()
        .params()
        .getOptional(MoblyAospPackageTestSpec.PARAM_PY_PKG_INDEX_URL)
        .isPresent()) {
      installMoblyTestDepsArgsBuilder.setIndexUrl(
          testInfo
              .jobInfo()
              .params()
              .getOptional(MoblyAospPackageTestSpec.PARAM_PY_PKG_INDEX_URL)
              .get());
    }

    return setupUtil.setupEnvAndGenerateTestCommand(
        moblyPkg,
        moblyUnzipDir,
        venvPath,
        configPath,
        testPath,
        testCaseSelector,
        pythonVersion,
        installMoblyTestDepsArgsBuilder.build());
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
  @Override
  protected void processTestOutput(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<Boolean> isMoblyCmdPassed =
        testInfo.properties().getBoolean(PropertyName.Test.MoblyTest.IS_MOBLY_COMMAND_SUCCESS);
    logger.atInfo().log("Is Mobly command passed: %s", isMoblyCmdPassed);
    try {
      handleOutput(testInfo);
      parseResults(testInfo);
      setTestResult(testInfo, isMoblyCmdPassed.orElse(false));
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

  /** Massages Mobly log output structure into a format convenient for Sponge. */
  private void handleOutput(TestInfo testInfo) throws IOException, MobileHarnessException {
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

  /**
   * Method for parsing Mobly results from the {@link
   * com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant.TestGenOutput.SUMMARY_FILE_NAME}
   * file. The results are parsed to a generic {@code MoblyYamlDocEntry} container list and then
   * mapped to the {@link com.google.wireless.qa.mobileharness.shared.model.job.TestInfo} object
   * given.
   */
  private void parseResults(TestInfo testInfo) throws IOException, MobileHarnessException {
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
}
