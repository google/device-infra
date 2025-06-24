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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.testcomponents.TestComponentsDirUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.util.MoblyPostTestProcessor;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONObject;

/** Driver for running Mobly tests packaged in AOSP and distributed via the Android Build. */
@DriverAnnotation(
    help = "For running Mobly tests packaged in AOSP and distributed via the Android Build.")
public class MoblyAospTest extends MoblyTest implements MoblyAospTestSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MOBLY_CONFIG_KEY_PREFIX_MH = "mh_";

  private final MoblyAospTestSetupUtil setupUtil;
  private final ResUtil resUtil;
  private final TestComponentsDirUtil testComponentsDirUtil;
  private final CommandExecutor commandExecutor;
  private final MoblyPostTestProcessor postTestProcessor;

  @Inject
  MoblyAospTest(
      Device device,
      TestInfo testInfo,
      MoblyAospTestSetupUtil setupUtil,
      ResUtil resUtil,
      TestComponentsDirUtil testComponentsDirUtil,
      CommandExecutor commandExecutor,
      MoblyPostTestProcessor postTestProcessor) {
    super(device, testInfo);
    this.setupUtil = setupUtil;
    this.resUtil = resUtil;
    this.testComponentsDirUtil = testComponentsDirUtil;
    this.commandExecutor = commandExecutor;
    this.postTestProcessor = postTestProcessor;
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
   * <p>Invokes {@link MoblyTest#generateMoblyConfig} to populate the config fields, but also strips
   * any key names with a "mh_" prefix so that a test written in AOSP can avoid making explicit
   * references to MH (e.g. "mh_files" becomes "files" in the config).
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
    Path moblyPkg = Path.of(testInfo.jobInfo().files().getSingle(FILE_MOBLY_PKG));
    Path moblyUnzipDir = Path.of(testInfo.getTmpFileDir(), "mobly");
    Path venvPath = Path.of(testInfo.getTmpFileDir(), "venv");
    Path configPath = Path.of(configFile.getPath());
    String testPath = testInfo.jobInfo().params().get(PARAM_TEST_PATH);
    String testCaseSelector = testInfo.jobInfo().params().get(TEST_SELECTOR_KEY);
    String pythonVersion = testInfo.jobInfo().params().get(PARAM_PYTHON_VERSION);

    return setupUtil.setupEnvAndGenerateTestCommand(
        moblyPkg,
        moblyUnzipDir,
        venvPath,
        configPath,
        testPath,
        testCaseSelector,
        pythonVersion,
        /* installMoblyTestDepsArgs= */ null);
  }

  /**
   * Processes Mobly's output artifacts.
   *
   * @param testInfo the testInfo for this particular test
   * @throws MobileHarnessException if test output processing failed
   * @throws InterruptedException if the thread was interrupted while processing the output
   */
  @Override
  protected void processTestOutput(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      postTestProcessor.handleOutput(testInfo, getLogDir(testInfo).toPath(), testbedName);
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_MISSING_OUTPUT_ERROR,
          "Mobly failed to generate a log directory. Please check mobly_command_output.log for"
              + " details.",
          e);
    }

    logger.atInfo().log("Processing Mobly test output without converter.");
    postTestProcessor.processTestOutput(
        testInfo, getDevice(), Path.of(testInfo.getGenFileDir()).resolve(RAW_MOBLY_LOG_ALL_IN_ONE));
  }
}
