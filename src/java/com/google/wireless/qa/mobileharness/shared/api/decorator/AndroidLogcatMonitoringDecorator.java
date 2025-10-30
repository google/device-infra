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

import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_ANR;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_NATIVE_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_ANR;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_NATIVE_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_TOMBSTONE;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxExtractor;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag;
import com.google.devtools.mobileharness.platform.android.logcat.AndroidRuntimeCrashDetector;
import com.google.devtools.mobileharness.platform.android.logcat.AnrDetector;
import com.google.devtools.mobileharness.platform.android.logcat.DeviceEventDetector;
import com.google.devtools.mobileharness.platform.android.logcat.DeviceEventDetector.DeviceEventConfig;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.DeviceEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatLineProxy;
import com.google.devtools.mobileharness.platform.android.logcat.MonitoringConfig;
import com.google.devtools.mobileharness.platform.android.logcat.NativeCrashDetector;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport.Category;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport.CrashType;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;

/** Decorator for monitoring for crashes, ANRs and device events in logcat. */
@DecoratorAnnotation(help = "Decorator to monitor logcat, report crashes  and specific events.")
public class AndroidLogcatMonitoringDecorator extends BaseDecorator
    implements SpecConfigable<AndroidLogcatMonitoringDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DATE_COMMAND = "date +%Y-%m-%d\\ %H:%M:%S.000";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
  private static final String UNPARSED_LOGCAT_FILE = "unparsed_logcat.txt";
  private static final String LOGCAT_MONITORING_REPORT_PROTO = "logcat_monitoring_report.proto";

  private final Adb adb;
  private final LogcatLineProxy logcatLineProxy;
  private final LocalFileUtil localFileUtil;
  private final DropboxExtractor dropboxExtractor;

  private LocalDateTime deviceTimeOnStart = null;

  @Inject
  AndroidLogcatMonitoringDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      LogcatLineProxy logcatLineProxy,
      LocalFileUtil localFileUtil,
      DropboxExtractor dropboxExtractor) {
    super(decorated, testInfo);
    this.adb = adb;
    this.logcatLineProxy = logcatLineProxy;
    this.localFileUtil = localFileUtil;
    this.dropboxExtractor = dropboxExtractor;
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
    var anrProcessor = new AnrDetector(monitoringConfig);
    var nativeCrashProcessor = new NativeCrashDetector(monitoringConfig);
    var deviceEventDetector = new DeviceEventDetector(makeDeviceEventDetectorConfig(spec));

    logcatLineProxy.addLineProcessor(artProcessor);
    logcatLineProxy.addLineProcessor(anrProcessor);
    logcatLineProxy.addLineProcessor(nativeCrashProcessor);
    logcatLineProxy.addLineProcessor(deviceEventDetector);

    String timeOnDevice = adb.runShell(deviceId, DATE_COMMAND);
    deviceTimeOnStart = LocalDateTime.parse(timeOnDevice, DATE_TIME_FORMATTER);
    testInfo.log().atInfo().alsoTo(logger).log("---- Device time: %s ----\n", deviceTimeOnStart);

    CommandProcess process =
        adb.runShellAsync(
            getDevice().getDeviceId(),
            String.format("logcat -v threadtime -T \"%s\"", timeOnDevice),
            testInfo.jobInfo().timer().remainingTimeJava(),
            logcatLineProxy);

    try {
      getDecorated().run(testInfo);
    } finally {
      process.killAndThenKillForcibly(Duration.ofSeconds(5));
      writeMonitoringReport(testInfo);
      writeUnparsedLogcatLines(testInfo);
      extractDropboxEntries(testInfo);
    }
  }

  private void writeMonitoringReport(TestInfo testInfo) throws MobileHarnessException {
    ImmutableList<LogcatEvent> logcatEvents = logcatLineProxy.getLogcatEventsFromProcessors();
    if (logcatEvents.isEmpty()) {
      return;
    }
    var report = createReport(logcatEvents);
    testInfo.log().atInfo().alsoTo(logger).log("\n#### Logcat Monitoring Report ####\n%s", report);
    Path reportPath = Path.of(testInfo.getGenFileDir(), LOGCAT_MONITORING_REPORT_PROTO);
    localFileUtil.writeToFile(reportPath.toString(), report.toByteArray());
  }

  private void writeUnparsedLogcatLines(TestInfo testInfo) throws MobileHarnessException {
    ImmutableList<String> unparsedLogcatLines = logcatLineProxy.getUnparsedLines();
    if (unparsedLogcatLines.isEmpty()) {
      return;
    }
    Path unparsedLogcatPath = Path.of(testInfo.getGenFileDir(), UNPARSED_LOGCAT_FILE);
    localFileUtil.writeToFile(
        unparsedLogcatPath.toString(), Joiner.on(System.lineSeparator()).join(unparsedLogcatLines));
  }

  private static LogcatMonitoringReport createReport(ImmutableList<LogcatEvent> logcatEvents) {
    LogcatMonitoringReport.Builder reportBuilder = LogcatMonitoringReport.newBuilder();
    for (var event : logcatEvents) {
      if (event instanceof CrashEvent crashEvent) {
        var crashBuilder = LogcatMonitoringReport.CrashEvent.newBuilder();
        var category =
            switch (crashEvent.process().category()) {
              case FAILURE -> LogcatMonitoringReport.Category.FAILURE;
              case ERROR -> LogcatMonitoringReport.Category.ERROR;
              default -> Category.IGNORED;
            };
        var crashType =
            switch (crashEvent.process().type()) {
              case ANDROID_RUNTIME -> CrashType.ANDROID_RUNTIME;
              case NATIVE -> CrashType.NATIVE;
              case ANR -> CrashType.ANR;
              case UNKNOWN -> CrashType.UNKNOWN_CRASH_TYPE;
            };
        crashBuilder
            .setProcessName(crashEvent.process().name())
            .setPid(crashEvent.process().pid())
            .setCrashType(crashType)
            .setCategory(category)
            .setLogLines(crashEvent.crashLogs());
        reportBuilder.addCrashEvents(crashBuilder.build());
      } else if (event instanceof DeviceEvent deviceEvent) {
        var deviceEventBuilder =
            LogcatMonitoringReport.DeviceEvent.newBuilder()
                .setEventName(deviceEvent.eventName())
                .setTag(deviceEvent.tag())
                .setLogLines(deviceEvent.logLines());
        reportBuilder.addDeviceEvents(deviceEventBuilder.build());
      }
    }
    return reportBuilder.build();
  }

  private void extractDropboxEntries(TestInfo testInfo)
      throws InterruptedException, MobileHarnessException {
    ImmutableList<LogcatEvent> logcatEvents = logcatLineProxy.getLogcatEventsFromProcessors();
    if (logcatEvents.isEmpty()) {
      return;
    }
    var tagsBuilder = ImmutableSet.<DropboxTag>builder();
    var packagesToScanBuilder = ImmutableSet.<String>builder();
    for (var event : logcatEvents) {
      if (event instanceof CrashEvent crashEvent) {
        // Only extract dropbox entries which cause a test failure.
        if (!crashEvent.process().category().equals(ProcessCategory.FAILURE)) {
          continue;
        }
        packagesToScanBuilder.add(crashEvent.process().name());
        switch (crashEvent.process().type()) {
          case ANDROID_RUNTIME -> tagsBuilder.add(DATA_APP_CRASH, SYSTEM_APP_CRASH);
          case NATIVE ->
              tagsBuilder.add(DATA_APP_NATIVE_CRASH, SYSTEM_APP_NATIVE_CRASH, SYSTEM_TOMBSTONE);
          case ANR -> tagsBuilder.add(DATA_APP_ANR, SYSTEM_APP_ANR);
          case UNKNOWN -> {}
        }
      }
    }
    var tags = tagsBuilder.build();
    var packagesToScan = packagesToScanBuilder.build();
    if (packagesToScan.isEmpty() || tags.isEmpty()) {
      return;
    }

    dropboxExtractor.extract(
        getDevice().getDeviceId(),
        packagesToScan,
        tags,
        deviceTimeOnStart,
        Path.of(testInfo.getGenFileDir()));
  }

  private static ImmutableList<DeviceEventDetector.DeviceEventConfig> makeDeviceEventDetectorConfig(
      AndroidLogcatMonitoringDecoratorSpec spec) {
    var builder = ImmutableList.<DeviceEventDetector.DeviceEventConfig>builder();
    for (var eventConfig : spec.getDeviceEventConfigList()) {
      builder.add(
          new DeviceEventConfig(
              eventConfig.getEventName(), eventConfig.getTag(), eventConfig.getLineRegex()));
    }
    return builder.build();
  }
}
