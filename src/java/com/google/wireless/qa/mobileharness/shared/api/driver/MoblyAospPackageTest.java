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
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory.SuiteType;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportHelper;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.InstallMoblyTestDepsArgs;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import javax.inject.Inject;
import org.json.JSONArray;
import org.json.JSONObject;

/** Driver for running Mobly tests packaged in AOSP and distributed via the Android Build. */
@DriverAnnotation(
    help = "For running Mobly tests packaged in AOSP and distributed via the Android Build.")
public class MoblyAospPackageTest extends MoblyGenericTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Driver-specific files and params

  @FileAnnotation(
      required = true,
      help =
          "The package containing your Mobly testcases and data files. It can be generated via "
              + "a `python_test_host` rule in your test project's `Android.bp` file.")
  public static final String FILE_MOBLY_PKG = "mobly_pkg";

  @ParamAnnotation(
      required = false,
      help = "Relative path of Mobly test/suite within the test package.")
  public static final String PARAM_TEST_PATH = "test_path";

  @ParamAnnotation(
      required = false,
      help =
          "Specifies version of python you wish to use for your test. Note that only 3.4+ is"
              + " supported. The expected format is ^(python)?3(\\.[4-9])?$. Note that the version"
              + " supplied here must match the executable name.")
  public static final String PARAM_PYTHON_VERSION = "python_version";

  @ParamAnnotation(required = false, help = "Certification suite type for the xTS Mobly run.")
  public static final String PARAM_CERTIFICATION_SUITE_TYPE = "certification_suite_type";

  @ParamAnnotation(required = false, help = "Test plan for the xTS Mobly run.")
  public static final String PARAM_XTS_TEST_PLAN = "xts_test_plan";

  @ParamAnnotation(required = false, help = "Base URL of Python Package Index.")
  public static final String PARAM_PY_PKG_INDEX_URL = "python_pkg_index_url";

  private static final String MOBLY_CONFIG_KEY_PREFIX_MH = "mh_";

  private final MoblyAospTestSetupUtil setupUtil;
  private final LocalFileUtil localFileUtil;
  private final MoblyReportHelper moblyReportHelper;
  private final CertificationSuiteInfoFactory certificationSuiteInfoFactory;

  @Inject
  MoblyAospPackageTest(
      Device device,
      TestInfo testInfo,
      CommandExecutor executor,
      Clock clock,
      MoblyAospTestSetupUtil setupUtil,
      LocalFileUtil localFileUtil,
      MoblyReportHelper moblyReportHelper,
      CertificationSuiteInfoFactory certificationSuiteInfoFactory) {
    super(device, testInfo, executor, clock);
    this.setupUtil = setupUtil;
    this.localFileUtil = localFileUtil;
    this.moblyReportHelper = moblyReportHelper;
    this.certificationSuiteInfoFactory = certificationSuiteInfoFactory;
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
  String[] generateTestCommand(TestInfo testInfo, File configFile, boolean usePythonSpongeConverter)
      throws MobileHarnessException, InterruptedException {
    Path moblyPkg = Path.of(testInfo.jobInfo().files().getSingle(FILE_MOBLY_PKG));
    Path moblyUnzipDir = Path.of(testInfo.getTmpFileDir(), "mobly");
    Path venvPath = Path.of(testInfo.getTmpFileDir(), "venv");
    Path configPath = Path.of(configFile.getPath());
    String testPath = testInfo.jobInfo().params().get(PARAM_TEST_PATH);
    String testCaseSelector = testInfo.jobInfo().params().get(TEST_SELECTOR_KEY);
    String pythonVersion = testInfo.jobInfo().params().get(PARAM_PYTHON_VERSION);

    InstallMoblyTestDepsArgs.Builder installMoblyTestDepsArgsBuilder =
        InstallMoblyTestDepsArgs.builder().setDefaultTimeout(Duration.ofMinutes(30));

    if (testInfo.jobInfo().params().getOptional(PARAM_PY_PKG_INDEX_URL).isPresent()) {
      installMoblyTestDepsArgsBuilder.setIndexUrl(
          testInfo.jobInfo().params().getOptional(PARAM_PY_PKG_INDEX_URL).get());
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

  @Override
  protected void postMoblyCommandExec(Instant testStartTime, Instant testEndTime)
      throws InterruptedException {
    TestInfo testInfo = getTest();
    String suiteType = testInfo.jobInfo().params().get(PARAM_CERTIFICATION_SUITE_TYPE, "");
    // If certification suite type is not defined, it means this is not a xTS Mobly test.
    if (suiteType.isEmpty()) {
      return;
    }
    ImmutableList<String> deviceIds = getDeviceIds();
    if (deviceIds.isEmpty()) {
      return;
    }
    String xtsTestPlan = testInfo.jobInfo().params().get(PARAM_XTS_TEST_PLAN, "");
    CertificationSuiteInfo suiteInfo =
        certificationSuiteInfoFactory.createSuiteInfo(
            SuiteType.valueOf(Ascii.toUpperCase(suiteType)), xtsTestPlan);
    try {
      moblyReportHelper.generateResultAttributesFile(
          testStartTime, testEndTime, deviceIds, suiteInfo, Paths.get(testInfo.getGenFileDir()));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to generate result attributes file for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e, 0));
    }

    try {
      moblyReportHelper.generateBuildAttributesFile(
          deviceIds.get(0), Paths.get(testInfo.getGenFileDir()));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to generate build attributes file for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e, 0));
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
