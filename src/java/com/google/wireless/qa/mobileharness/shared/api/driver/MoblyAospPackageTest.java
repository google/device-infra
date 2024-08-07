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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.InstallMoblyTestDepsArgs;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospPackageTestSetupUtil;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospPackageTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONObject;

/** Driver for running Mobly tests packaged in AOSP and distributed via the Android Build. */
@DriverAnnotation(
    help = "For running Mobly tests packaged in AOSP and distributed via the Android Build.")
public class MoblyAospPackageTest extends MoblyGenericTest {

  private static final String MOBLY_CONFIG_KEY_PREFIX_MH = "mh_";

  private final MoblyAospPackageTestSetupUtil setupUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  MoblyAospPackageTest(
      Device device,
      TestInfo testInfo,
      MoblyYamlParser parser,
      MoblyTestInfoMapHelper mapper,
      CommandExecutor executor,
      MoblyAospPackageTestSetupUtil setupUtil,
      LocalFileUtil localFileUtil) {
    super(device, testInfo, parser, mapper, executor);
    this.setupUtil = setupUtil;
    this.localFileUtil = localFileUtil;
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
    JSONObject moblyJson =
        convertMoblyConfig(MoblyGenericTest.generateMoblyConfig(testInfo, getDevice()));
    testbedName = MoblyGenericTest.getTestbedName(moblyJson);
    return prepareMoblyConfig(testInfo, moblyJson, localFileUtil);
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
}
