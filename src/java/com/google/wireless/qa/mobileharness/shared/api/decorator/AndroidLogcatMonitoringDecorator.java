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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.logcat.AndroidRuntimeCrashDetector;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatLineProxy;
import com.google.devtools.mobileharness.platform.android.logcat.MonitoringConfig;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import javax.inject.Inject;

/** Decorator for monitoring for crashes, ANRs and device events in logcat. */
@DecoratorAnnotation(help = "Decorator to monitor logcat, report crashes  and specific events.")
public class AndroidLogcatMonitoringDecorator extends BaseDecorator
    implements SpecConfigable<AndroidLogcatMonitoringDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DATE_COMMAND = "date +%m-%d\\ %H:%M:%S.000";
  private static final String UNPARSED_LOGCAT_FILE = "unparsed_logcat.txt";

  private final Adb adb;
  private final LogcatLineProxy logcatLineProxy;
  private final LocalFileUtil localFileUtil;

  @Inject
  AndroidLogcatMonitoringDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      LogcatLineProxy logcatLineProxy,
      LocalFileUtil localFileUtil) {
    super(decorated, testInfo);
    this.adb = adb;
    this.logcatLineProxy = logcatLineProxy;
    this.localFileUtil = localFileUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    AndroidLogcatMonitoringDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this);

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- In Android LogcatMonitoring Decorator --------\n Spec: %s", spec);

    String deviceId = getDevice().getDeviceId();

    var monitoringConfig =
        new MonitoringConfig(
            spec.getReportAsFailurePackagesList(),
            spec.getErrorOnCrashPackagesList(),
            spec.getPackagesToIgnoreList());
    var artProcessor = new AndroidRuntimeCrashDetector(monitoringConfig);
    logcatLineProxy.addLineProcessor(artProcessor);
    String currentDeviceTime = adb.runShell(deviceId, DATE_COMMAND);

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- Device time: %s  --------\n", currentDeviceTime);

    CommandProcess process =
        adb.runShellAsync(
            getDevice().getDeviceId(),
            String.format("logcat -v threadtime -T \"%s\"", currentDeviceTime),
            testInfo.jobInfo().timer().remainingTimeJava(),
            logcatLineProxy);

    getDecorated().run(testInfo);
    process.killAndThenKillForcibly(Duration.ofSeconds(5));
    finish(testInfo);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "\n\n################### Crash Events #####################\n%s\n\n",
            Joiner.on("\n").join(artProcessor.getEvents()));
  }

  private void finish(TestInfo testInfo) throws MobileHarnessException {
    ImmutableList<String> unparsedLogcatLines = logcatLineProxy.getUnparsedLines();
    if (unparsedLogcatLines.isEmpty()) {
      return;
    }
    Path unparsedLogcatPath = Path.of(testInfo.getGenFileDir(), UNPARSED_LOGCAT_FILE);
    localFileUtil.writeToFile(
        unparsedLogcatPath.toString(), Joiner.on(System.lineSeparator()).join(unparsedLogcatLines));
  }
}
