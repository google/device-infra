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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportHelper;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** A lab plugin to create Mobly test report of certification test suites only. */
@Plugin(type = PluginType.LAB)
public final class NonTradefedReportGenerator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help = "Whether to run certification test suite. Default to false.")
  public static final String PARAM_RUN_CERTIFICATION_TEST_SUITE = "run_certification_test_suite";

  @ParamAnnotation(
      required = false,
      help = "xTS suite info. Should be key value pairs like 'key1=value1,key2=value2'.")
  public static final String PARAM_XTS_SUITE_INFO = "xts_suite_info";

  private final Clock clock;
  private final Adb adb;
  private final AndroidAdbUtil androidAdbUtil;
  private final LocalFileUtil localFileUtil;
  private final CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  private final MoblyReportHelper moblyReportHelper;

  private Instant startTime = null;

  public NonTradefedReportGenerator() {
    this.clock = Clock.system(ZoneId.systemDefault());
    this.adb = new Adb();
    this.androidAdbUtil = new AndroidAdbUtil();
    this.localFileUtil = new LocalFileUtil();

    this.certificationSuiteInfoFactory = new CertificationSuiteInfoFactory();
    this.moblyReportHelper = new MoblyReportHelper(adb, localFileUtil);
  }

  @VisibleForTesting
  NonTradefedReportGenerator(
      Clock clock,
      Adb adb,
      AndroidAdbUtil androidAdbUtil,
      LocalFileUtil localFileUtil,
      CertificationSuiteInfoFactory certificationSuiteInfoFactory,
      MoblyReportHelper moblyReportHelper) {
    this.clock = clock;
    this.adb = adb;
    this.androidAdbUtil = androidAdbUtil;
    this.localFileUtil = localFileUtil;
    this.certificationSuiteInfoFactory = certificationSuiteInfoFactory;
    this.moblyReportHelper = moblyReportHelper;
  }

  @Subscribe
  public void onLocalDriverStarting(LocalDriverStartingEvent event) {
    if (!event.getDriverName().equals("MoblyAospPackageTest")) {
      return;
    }
    startTime = clock.instant();
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event) throws InterruptedException {
    Instant endTime = clock.instant();
    startTime = startTime == null ? endTime : startTime;
    try {
      createTestReport(
          event.getTest(),
          event.getAllocation().getAllDeviceLocators().stream()
              .map(DeviceLocator::getSerial)
              .collect(toImmutableList()),
          startTime,
          endTime);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to generate result attributes file for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e));
    }
  }

  void createTestReport(
      TestInfo testInfo,
      ImmutableList<String> deviceIds,
      Instant testStartTime,
      Instant testEndTime)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();

    boolean runCertificationTestSuite =
        jobInfo.params().getBool(PARAM_RUN_CERTIFICATION_TEST_SUITE, false);
    if (!runCertificationTestSuite) {
      return;
    }

    try {
      moblyReportHelper.formatLogDir(testInfo.getGenFileDir());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to format the log directory for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e));
    }

    if (jobInfo.properties().getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)) {
      logger.atInfo().log("Skip collecting non tradefed reports.");
      return;
    }

    if (deviceIds.isEmpty()) {
      logger.atWarning().log("No device ids found for xTS Mobly run.");
      return;
    }

    Map<String, String> suiteInfoMap =
        new HashMap<>(
            StrUtil.toMap(
                jobInfo.params().get(PARAM_XTS_SUITE_INFO, ""), /* allowDelimiterInValue= */ true));
    CertificationSuiteInfo suiteInfo = certificationSuiteInfoFactory.createSuiteInfo(suiteInfoMap);

    try {
      moblyReportHelper.generateResultAttributesFile(
          testStartTime, testEndTime, deviceIds, suiteInfo, Path.of(testInfo.getGenFileDir()));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to generate result attributes file for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e));
    }

    boolean skipCollectingDeviceInfo =
        jobInfo.properties().getBoolean(Job.SKIP_COLLECTING_DEVICE_INFO).orElse(false);
    logger.atInfo().log("Skip collecting device info: %s", skipCollectingDeviceInfo);
    try {
      moblyReportHelper.generateBuildAttributesFile(
          deviceIds.get(0), Path.of(testInfo.getGenFileDir()), skipCollectingDeviceInfo);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to generate build attributes file for xTS Mobly run: %s",
          MoreThrowables.shortDebugString(e));
    }

    if (!skipCollectingDeviceInfo) {
      // Writes first device build fingerprint to a file for post processing
      localFileUtil.writeToFile(
          Path.of(testInfo.getGenFileDir())
              .resolve("device_build_fingerprint.txt")
              .toAbsolutePath()
              .toString(),
          androidAdbUtil
              .getProperty(deviceIds.get(0), ImmutableList.of("ro.build.fingerprint"))
              .trim());
    }

    ResultTypeWithCause resultWithCause = testInfo.resultWithCause().get();
    ModuleRunResult.Builder resultBuilder =
        ModuleRunResult.newBuilder().setResult(resultWithCause.type());
    if (resultWithCause.causeProto().isPresent()) {
      resultBuilder.setCause(resultWithCause.toStringWithDetail());
    }
    localFileUtil.writeToFile(
        Path.of(testInfo.getGenFileDir())
            .resolve("ats_module_run_result.textproto")
            .toAbsolutePath()
            .toString(),
        TextFormat.printer().printToString(resultBuilder.build()));
  }
}
