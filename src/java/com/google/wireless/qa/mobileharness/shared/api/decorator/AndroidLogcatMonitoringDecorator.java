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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_ANR;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.DATA_APP_NATIVE_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_ANR;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_APP_NATIVE_CRASH;
import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_TOMBSTONE;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.ANDROID_RUNTIME;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.ANR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Diagnostic.Finding.Severity;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxExtractor;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.logcat.AndroidRuntimeCrashDetector;
import com.google.devtools.mobileharness.platform.android.logcat.AnrDetector;
import com.google.devtools.mobileharness.platform.android.logcat.CrashDialogDetector;
import com.google.devtools.mobileharness.platform.android.logcat.DeviceEventDetector;
import com.google.devtools.mobileharness.platform.android.logcat.DeviceEventDetector.DeviceEventConfig;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.DeviceEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.TestEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatLineProxy;
import com.google.devtools.mobileharness.platform.android.logcat.MonitoringConfig;
import com.google.devtools.mobileharness.platform.android.logcat.NativeCrashDetector;
import com.google.devtools.mobileharness.platform.android.logcat.TestEventDetector;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport.Category;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.proto.LogcatMonitoringReport.TestEventType;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Decorator for monitoring for crashes, ANRs and device events in logcat. */
@DecoratorAnnotation(help = "Decorator to monitor logcat, report crashes  and specific events.")
public class AndroidLogcatMonitoringDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidLogcatMonitoringDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DATE_COMMAND = "date +%Y-%m-%d\\ %H:%M:%S.000";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
  private static final Duration PROCESS_STOP_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration DIALOG_SCAN_SHUTDOWN_TIMEOUT = Duration.ofSeconds(12);
  private static final String DIALOG_EXECUTOR_THREAD_PREFIX =
      "android-logcat-monitoring-dialog-detector";
  private static final int DIALOG_EXECUTOR_MAX_THREADS = 3;

  // Directory within the test genFilesDir which holds all the decorator outputs.
  private static final String DECORATOR_OUTPUTS_DIR = "logcat_monitoring";
  private static final String UNPARSED_LOGCAT_FILE = "unparsed_logcat.txt";
  private static final String LOGCAT_MONITORING_REPORT_PROTO = "logcat_monitoring_report.proto";

  private static final String ORCHESTRATOR_CONNECTION_FAILURE_MESSAGE =
      "Cannot connect to androidx.test.orchestrator.OrchestratorService";

  private static final String FINDING_METADATA_KEY_PROCESS_NAME = "process_name";
  private static final String FINDING_METADATA_KEY_CRASH_PACKAGE = "crash_package";

  /**
   * Active resources and configuration created by {@link #setUp} and cleaned up in {@link
   * #tearDown}.
   */
  private record MonitoringSession(
      AndroidLogcatMonitoringDecoratorSpec spec,
      Optional<LocalDateTime> deviceTimeOnStart,
      ImmutableList<DeviceEvent> initialWifiChecks,
      ExecutorService executorService,
      CommandProcess process) {

    void stopOutputAndAwaitDialogScan(TestInfo testInfo) throws InterruptedException {
      try {
        try {
          process.killAndThenKillForcibly(PROCESS_STOP_TIMEOUT);
        } finally {
          process.stopReadingOutput();
        }
        executorService.shutdown();
        if (!executorService.awaitTermination(
            DIALOG_SCAN_SHUTDOWN_TIMEOUT.toMillis(), MILLISECONDS)) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log(
                  "Timed out waiting %s for crash dialog scan to complete; cancelling.",
                  DIALOG_SCAN_SHUTDOWN_TIMEOUT);
          executorService.shutdownNow();
        }
      } catch (Throwable t) {
        process.killForcibly();
        executorService.shutdownNow();
        throw t;
      }
    }
  }

  private final Adb adb;
  private final LogcatLineProxy logcatLineProxy;
  private final LocalFileUtil localFileUtil;
  private final AndroidFileUtil androidFileUtil;
  private final DropboxExtractor dropboxExtractor;
  private final CrashDialogDetector crashDialogDetector;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final Supplier<ExecutorService> executorServiceSupplier;

  @Nullable private MonitoringSession activeSession;

  @Inject
  AndroidLogcatMonitoringDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      LogcatLineProxy logcatLineProxy,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      DropboxExtractor dropboxExtractor,
      CrashDialogDetector crashDialogDetector,
      AndroidSystemSettingUtil androidSystemSettingUtil) {
    this(
        decorated,
        testInfo,
        adb,
        logcatLineProxy,
        localFileUtil,
        androidFileUtil,
        dropboxExtractor,
        crashDialogDetector,
        androidSystemSettingUtil,
        AndroidLogcatMonitoringDecorator::createDefaultDialogExecutor);
  }

  @VisibleForTesting
  AndroidLogcatMonitoringDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      LogcatLineProxy logcatLineProxy,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      DropboxExtractor dropboxExtractor,
      CrashDialogDetector crashDialogDetector,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      Supplier<ExecutorService> executorServiceSupplier) {
    super(decorated, testInfo);
    this.adb = adb;
    this.logcatLineProxy = logcatLineProxy;
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.dropboxExtractor = dropboxExtractor;
    this.crashDialogDetector = crashDialogDetector;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.executorServiceSupplier = executorServiceSupplier;
  }

  private static ExecutorService createDefaultDialogExecutor() {
    return ThreadPools.createStandardThreadPoolWithMaxSize(
        DIALOG_EXECUTOR_THREAD_PREFIX, DIALOG_EXECUTOR_MAX_THREADS);
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    AndroidLogcatMonitoringDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- In Android LogcatMonitoring Decorator --------\n Spec: %s", spec);

    String deviceId = getDevice().getDeviceId();
    ImmutableList<DeviceEventConfig> deviceEventConfigs = makeDeviceEventDetectorConfig(spec);
    ImmutableList<DeviceEvent> initialWifiChecks = checkWifiStatus(testInfo, deviceId);
    Optional<LocalDateTime> deviceTimeOnStart = queryDeviceStartTime(testInfo, deviceId);

    ExecutorService executorService = executorServiceSupplier.get();
    try {
      var monitoringConfig =
          new MonitoringConfig(
              spec.getReportAsFailurePackagesList(),
              spec.getErrorOnCrashPackagesList(),
              spec.getPackagesToIgnoreList());
      var artProcessor =
          new AndroidRuntimeCrashDetector(
              testInfo, getDevice(), monitoringConfig, crashDialogDetector, executorService);
      var anrProcessor =
          new AnrDetector(
              testInfo, getDevice(), monitoringConfig, crashDialogDetector, executorService);
      var nativeCrashProcessor =
          new NativeCrashDetector(
              testInfo, getDevice(), monitoringConfig, crashDialogDetector, executorService);
      var deviceEventDetector = new DeviceEventDetector(deviceEventConfigs);
      var testEventDetector = new TestEventDetector(monitoringConfig);

      logcatLineProxy.addLineProcessor(artProcessor);
      logcatLineProxy.addLineProcessor(anrProcessor);
      logcatLineProxy.addLineProcessor(nativeCrashProcessor);
      logcatLineProxy.addLineProcessor(deviceEventDetector);
      logcatLineProxy.addLineProcessor(testEventDetector);

      String logcatCommand =
          deviceTimeOnStart
              .map(ts ->
                  String.format("logcat -v threadtime -T \"%s\"", DATE_TIME_FORMATTER.format(ts)))
              .orElse("logcat -v threadtime -T 1");
      CommandProcess process =
          adb.runShellAsync(
              deviceId, logcatCommand, testInfo.timer().remainingTimeJava(), logcatLineProxy);
      activeSession =
          new MonitoringSession(
              spec, deviceTimeOnStart, initialWifiChecks, executorService, process);
      return SetupResult.continueDecorated();
    } catch (Throwable t) {
      executorService.shutdownNow();
      throw t;
    }
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    MonitoringSession session = activeSession;
    if (session == null) {
      return;
    }
    activeSession = null;
    TestInfo testInfo = context.testInfo();
    session.stopOutputAndAwaitDialogScan(testInfo);
    ImmutableList<LogcatEvent> logcatEvents = getLogcatEvents();
    writeMonitoringReport(testInfo, session.initialWifiChecks(), logcatEvents);
    addCrashFindings(testInfo, logcatEvents);
    writeUnparsedLogcatLines(testInfo);
    extractDropboxEntries(testInfo, session.spec(), session.deviceTimeOnStart(), logcatEvents);
    pullCrashDialogScreenshot(testInfo);
    checkForCrashDialog(session.spec().getThrowExceptionOnCrashDialogDetection());
    checkForOrchestratorConnectionErrors(logcatEvents);
    checkForInfraError(logcatEvents);
    checkForTestFailureEvents(testInfo, session.spec(), logcatEvents);
    addTestEventFindings(testInfo, logcatEvents);
  }

  private ImmutableList<LogcatEvent> getLogcatEvents() {
    ImmutableList<LogcatEvent> logcatEvents = logcatLineProxy.getLogcatEventsFromProcessors();
    return ImmutableList.<LogcatEvent>builder()
        .addAll(logcatEvents)
        .addAll(getTestEventsFromCrashes(logcatEvents))
        .build();
  }

  private void writeMonitoringReport(
      TestInfo testInfo,
      ImmutableList<DeviceEvent> initialWifiChecks,
      ImmutableList<LogcatEvent> logcatEvents) {
    var allEvents =
        ImmutableList.<LogcatEvent>builder().addAll(initialWifiChecks).addAll(logcatEvents).build();
    Optional<String> crashDialogPackage = crashDialogDetector.crashDialogPackageName();
    if (allEvents.isEmpty() && crashDialogPackage.isEmpty()) {
      return;
    }
    var report = createReport(allEvents, crashDialogPackage);
    testInfo.log().atInfo().alsoTo(logger).log("\n#### Logcat Monitoring Report ####\n%s", report);
    try {
      Path reportPath = getDecoratorOutputsDir(testInfo).resolve(LOGCAT_MONITORING_REPORT_PROTO);
      localFileUtil.writeToFile(reportPath.toString(), report.toByteArray());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to write logcat monitoring report.");
    }
  }

  private static ImmutableList<TestEvent> getTestEventsFromCrashes(
      ImmutableList<LogcatEvent> logcatEvents) {
    return logcatEvents.stream()
        .filter(event -> event instanceof CrashEvent)
        .map(event -> TestEventDetector.processCrashEvent((CrashEvent) event))
        .flatMap(Optional::stream)
        .collect(toImmutableList());
  }

  private void writeUnparsedLogcatLines(TestInfo testInfo) {
    ImmutableList<String> unparsedLogcatLines = logcatLineProxy.getUnparsedLines();
    if (unparsedLogcatLines.isEmpty()) {
      return;
    }
    try {
      Path unparsedLogcatPath = getDecoratorOutputsDir(testInfo).resolve(UNPARSED_LOGCAT_FILE);
      localFileUtil.writeToFile(
          unparsedLogcatPath.toString(),
          Joiner.on(System.lineSeparator()).join(unparsedLogcatLines));
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to write unparsed logcat lines.");
    }
  }

  private static LogcatMonitoringReport createReport(
      ImmutableList<LogcatEvent> logcatEvents, Optional<String> crashDialogPackage) {
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
      } else if (event instanceof TestEvent testEvent) {
        var testEventType =
            switch (testEvent.type()) {
              case STARTUP_TIME_DEFAULT -> TestEventType.STARTUP_TIME_DEFAULT;
              case STARTUP_TIME_FULLY_DRAWN -> TestEventType.STARTUP_TIME_FULLY_DRAWN;
              case LICENSING_PROTECTION_TERMINATION ->
                  TestEventType.LICENSING_PROTECTION_TERMINATION;
              case ANTI_TAMPERING_TERMINATION -> TestEventType.ANTI_TAMPERING_TERMINATION;
              case UNITY_EXCEPTION -> TestEventType.UNITY_EXCEPTION;
            };
        var testEventBuilder =
            LogcatMonitoringReport.TestEvent.newBuilder()
                .setType(testEventType)
                .setPid(testEvent.pid())
                .setTag(testEvent.tag())
                .setLogLines(testEvent.logLines());
        reportBuilder.addTestEvents(testEventBuilder.build());
      }
    }
    crashDialogPackage.ifPresent(reportBuilder::setCrashDialogPackage);
    return reportBuilder.build();
  }

  private void addCrashFindings(TestInfo testInfo, ImmutableList<LogcatEvent> logcatEvents) {
    for (LogcatEvent event : logcatEvents) {
      if (event instanceof CrashEvent crashEvent
          && crashEvent.process().category().equals(ProcessCategory.ERROR)) {
        addErrorCrashEventFinding(testInfo, crashEvent);
      }
    }
    crashDialogDetector
        .crashDialogPackageName()
        .ifPresent(packageName -> addCrashDialogFinding(testInfo, packageName));
  }

  private static void addErrorCrashEventFinding(TestInfo testInfo, CrashEvent crashEvent) {
    AndroidErrorId errorId =
        switch (crashEvent.process().type()) {
          case ANDROID_RUNTIME -> AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_FATAL_CRASH;
          case NATIVE -> AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_NATIVE_CRASH;
          case ANR -> AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_ANR_CRASH;
          case UNKNOWN -> AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_UNKNOWN_CRASH;
        };
    String processName = crashEvent.process().name();
    testInfo
        .findings()
        .add(Severity.SEVERE, errorId, "Crash event detected in system process: " + processName)
        .addMetadata(FINDING_METADATA_KEY_PROCESS_NAME, processName);
  }

  private static void addCrashDialogFinding(TestInfo testInfo, String packageName) {
    testInfo
        .findings()
        .add(
            Severity.SEVERE,
            AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_TEST_ISSUE_CRASH_DIALOG,
            "Crash dialog detected during test.")
        .addMetadata(FINDING_METADATA_KEY_CRASH_PACKAGE, packageName);
  }

  private void extractDropboxEntries(
      TestInfo testInfo,
      AndroidLogcatMonitoringDecoratorSpec spec,
      Optional<LocalDateTime> deviceTimeOnStart,
      ImmutableList<LogcatEvent> logcatEvents)
      throws InterruptedException {
    if (deviceTimeOnStart.isEmpty() || logcatEvents.isEmpty()) {
      return;
    }
    int maxEntries =
        spec.hasMaxDropboxEntries()
            ? spec.getMaxDropboxEntries()
            : DropboxExtractor.DEFAULT_MAX_ENTRIES;
    if (maxEntries <= 0) {
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

    try {
      dropboxExtractor.extract(
          getDevice().getDeviceId(),
          packagesToScan,
          tags,
          deviceTimeOnStart.get(),
          getDecoratorOutputsDir(testInfo),
          maxEntries);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to extract dropbox entries.");
    }
  }

  private void pullCrashDialogScreenshot(TestInfo testInfo) throws InterruptedException {
    if (crashDialogDetector.crashDialogPackageName().isEmpty()) {
      return;
    }
    Optional<String> screenshotPath = crashDialogDetector.crashDialogScreenshot();
    if (screenshotPath.isEmpty()) {
      return;
    }
    try {
      androidFileUtil.pull(
          getDevice().getDeviceId(),
          screenshotPath.get(),
          getDecoratorOutputsDir(testInfo).toString());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to pull crash dialog screenshot %s from device.", screenshotPath.get());
    }
  }

  private void checkForCrashDialog(boolean throwOnCrashDialogDetected)
      throws MobileHarnessException {
    Optional<String> packageName = crashDialogDetector.crashDialogPackageName();
    if (throwOnCrashDialogDetected && packageName.isPresent()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_CRASH_DIALOG_DETECTED,
          String.format("Crash dialog detected during test. Process: %s", packageName.get()));
    }
  }

  private void checkForInfraError(ImmutableList<LogcatEvent> logcatEvents)
      throws MobileHarnessException {
    if (logcatEvents.isEmpty()) {
      return;
    }
    for (var event : logcatEvents) {
      if (event instanceof CrashEvent crashEvent) {
        if (crashEvent.process().category().equals(ProcessCategory.ERROR)
            && !crashEvent.process().type().equals(ANR)) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_INFRA_PROCESS_CRASHED,
              String.format("Crash detected in infra process: %s", crashEvent.process().name()));
        }
      }
    }
  }

  private void checkForOrchestratorConnectionErrors(ImmutableList<LogcatEvent> logcatEvents)
      throws MobileHarnessException {
    if (logcatEvents.isEmpty()) {
      return;
    }
    for (var event : logcatEvents) {
      if (event instanceof CrashEvent crashEvent) {
        var process = crashEvent.process();
        if (process.category().equals(ProcessCategory.FAILURE)
            && process.type().equals(ANDROID_RUNTIME)
            && crashEvent.crashLogs().contains(ORCHESTRATOR_CONNECTION_FAILURE_MESSAGE)) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_ORCHESTRATOR_CONNECTION_FAILURE,
              String.format(
                  "%s failed to connect to androidx.test.orchestrator.OrchestratorService",
                  crashEvent.process().name()));
        }
      }
    }
  }

  private void checkForTestFailureEvents(
      TestInfo testInfo,
      AndroidLogcatMonitoringDecoratorSpec spec,
      ImmutableList<LogcatEvent> logcatEvents) {
    if (logcatEvents.isEmpty()) {
      return;
    }
    var appsUnderTestThatCrashed =
        logcatEvents.stream()
            .filter(event -> event instanceof CrashEvent)
            .map(event -> ((CrashEvent) event).process())
            .filter(process -> process.category().equals(ProcessCategory.FAILURE))
            .map(process -> process.name())
            .collect(toImmutableList());
    if (appsUnderTestThatCrashed.isEmpty()) {
      return;
    }
    // Only set TestResult to FAIL if the test is currently passing and the spec allows it;
    // don't override existing non-passing results (e.g. FAIL, ERROR, TIMEOUT).
    if (testInfo.resultWithCause().get().type().equals(TestResult.PASS)
        && spec.getOverrideTestResultOnProcessCrash()) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.FAIL,
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_APP_UNDER_TEST_PROCESS_CRASHED,
                  String.format(
                      "Processes crashed: %s ", Joiner.on(", ").join(appsUnderTestThatCrashed))));
    } else {
      testInfo
          .findings()
          .add(
              Severity.SEVERE,
              AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_APP_UNDER_TEST_PROCESS_CRASHED,
              "App under test crashed.")
          .addMetadata("package_names", Joiner.on(",").join(appsUnderTestThatCrashed));
    }
  }

  private void addTestEventFindings(TestInfo testInfo, ImmutableList<LogcatEvent> logcatEvents) {
    if (logcatEvents.isEmpty()) {
      return;
    }
    for (var event : logcatEvents) {
      if (event instanceof TestEvent testEvent) {
        switch (testEvent.type()) {
          case LICENSING_PROTECTION_TERMINATION ->
              testInfo
                  .findings()
                  .add(
                      Severity.SEVERE,
                      AndroidErrorId
                          .ANDROID_LOGCAT_MONITORING_DECORATOR_LICENSING_PROTECTION_TERMINATION,
                      "Licensing protection termination detected.");
          case ANTI_TAMPERING_TERMINATION ->
              testInfo
                  .findings()
                  .add(
                      Severity.SEVERE,
                      AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_ANTI_TAMPERING_TERMINATION,
                      "Anti-tampering termination detected.");
          case UNITY_EXCEPTION ->
              testInfo
                  .findings()
                  .add(
                      Severity.SEVERE,
                      AndroidErrorId.ANDROID_LOGCAT_MONITORING_DECORATOR_UNITY_EXCEPTION,
                      "Unity exception detected.");
          default -> {}
        }
      }
    }
  }

  private static Path getDecoratorOutputsDir(TestInfo testInfo) throws MobileHarnessException {
    return Path.of(testInfo.getGenFileDir()).resolve(DECORATOR_OUTPUTS_DIR);
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

  private ImmutableList<DeviceEvent> checkWifiStatus(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    try {
      int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
      if (sdkVersion < 30) {
        return ImmutableList.of();
      }
      var wifiCheckEvents = ImmutableList.<DeviceEvent>builder();
      var output = adb.runShell(deviceId, "cmd wifi status");
      if (output != null && output.contains("Wifi is disabled")) {
        wifiCheckEvents.add(new DeviceEvent("WIFI_DISABLED", "ADBWifi", output));
      } else if (output != null && output.contains("Wifi is not connected")) {
        wifiCheckEvents.add(new DeviceEvent("WIFI_NOT_CONNECTED", "ADBWifi", output));
      }
      return wifiCheckEvents.build();
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to check WiFi status on device %s.", deviceId);
      return ImmutableList.of();
    }
  }

  private Optional<LocalDateTime> queryDeviceStartTime(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    try {
      String rawOutput = adb.runShell(deviceId, DATE_COMMAND);
      String timeOnDevice = rawOutput == null ? "" : rawOutput.trim();
      LocalDateTime parsedTime = LocalDateTime.parse(timeOnDevice, DATE_TIME_FORMATTER);
      testInfo.log().atInfo().alsoTo(logger).log("---- Device time: %s ----\n", parsedTime);
      return Optional.of(parsedTime);
    } catch (MobileHarnessException | DateTimeParseException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log(
              "Failed to query or parse device start time on %s; falling back to 'logcat -T 1'"
                  + " and skipping dropbox extraction.",
              deviceId);
      return Optional.empty();
    }
  }
}
