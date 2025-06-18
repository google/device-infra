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

package com.google.wireless.qa.mobileharness.shared.api.driver.util;

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
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.scanner.ScannerException;

/** Helper class for processing Mobly output artifacts. */
public class MoblyPostTestProcessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MOBLY_SIDE_ERROR_MESSAGE =
      "\n\n"
          + "     ============================================================\n"
          + "     ||                                                        ||\n"
          + "     ||            Mobly did not execute correctly             ||\n"
          + "     ||                                                        ||\n"
          + "     ||         Please check mobly_command_output.log          ||\n"
          + "     ||                                                        ||\n"
          + "     ============================================================\n\n";

  private final LocalFileUtil localFileUtil;
  private final MoblyYamlParser parser;
  private final MoblyTestInfoMapHelper mapper;

  public MoblyPostTestProcessor() {
    this(new MoblyYamlParser(), new MoblyTestInfoMapHelper(), new LocalFileUtil());
  }

  @VisibleForTesting
  MoblyPostTestProcessor(
      MoblyYamlParser parser, MoblyTestInfoMapHelper mapper, LocalFileUtil localFileUtil) {
    this.parser = parser;
    this.mapper = mapper;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Processes Mobly's output artifacts.
   *
   * <p>Mainly for oss.
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
   * @param device the device under test, which could be a composite device
   * @param rawMoblyCommandLogFilePath the path to the raw Mobly command log file
   * @throws MobileHarnessException if test output processing failed
   * @throws InterruptedException if the thread was interrupted while processing the output
   */
  public void processTestOutput(TestInfo testInfo, Device device, Path rawMoblyCommandLogFilePath)
      throws MobileHarnessException, InterruptedException {
    Optional<Boolean> isMoblyCmdPassed =
        testInfo.properties().getBoolean(PropertyName.Test.MoblyTest.IS_MOBLY_COMMAND_SUCCESS);
    logger.atInfo().log("Is Mobly command passed: %s", isMoblyCmdPassed);
    try {
      parseResults(testInfo);
      setTestResult(testInfo, isMoblyCmdPassed.orElse(false), device);
    } catch (MobileHarnessException | IOException e) {
      String moblyCommandOutput;
      try {
        moblyCommandOutput = localFileUtil.readFile(rawMoblyCommandLogFilePath);
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
  public void handleOutput(TestInfo testInfo, Path rawMoblyLogDir, @Nullable String testbedName)
      throws IOException, MobileHarnessException {
    // Mobly creates a timestamped folder for the results and symlinks it to 'latest'. To avoid
    // Sponge getting two copies of the file, we will delete the symlink and move the files to the
    // root of the log folder.
    if (testbedName == null) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_TESTBED_NAME_EMPTY_ERROR, "Testbed name was not set.");
    }
    Path logDirLatest = rawMoblyLogDir.resolve(testbedName).resolve("latest");
    Path logDirTimestamped = Files.readSymbolicLink(logDirLatest);
    Path logDirFinal = Path.of(testInfo.getGenFileDir(), MoblyConstant.TestGenOutput.MOBLY_LOG_DIR);
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

  private void setTestResult(TestInfo testInfo, boolean passed, Device device) {
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
    for (String deviceId : getDeviceIds(device)) {
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

  private ImmutableList<String> getDeviceIds(Device device) {
    if (!(device instanceof CompositeDevice)) {
      return ImmutableList.of(device.getDeviceId());
    }
    CompositeDevice compositeDevice = (CompositeDevice) device;
    return compositeDevice.getManagedDevices().stream()
        .map(Device::getDeviceId)
        .collect(toImmutableList());
  }
}
