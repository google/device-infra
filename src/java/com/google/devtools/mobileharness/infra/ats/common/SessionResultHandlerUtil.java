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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportFormat;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.ParseResult;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.TradefedResultBundle;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteResultReporter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger.MergedResult;
import com.google.devtools.mobileharness.platform.android.xts.suite.screenshots.ScreenshotsMetadataUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessCallable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Helper class for ATS applications to create session results. */
public class SessionResultHandlerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> MOBLY_TEST_RESULT_FILE_NAMES =
      ImmutableSet.of(
          "test_summary.yaml",
          "device_build_fingerprint.txt",
          "mobly_run_build_attributes.textproto",
          "mobly_run_result_attributes.textproto",
          "ats_module_run_result.textproto");

  // Mobly result directories that are copied to the root result dir.
  private static final ImmutableSet<String> MOBLY_ROOT_TEST_RESULT_DIR_NAMES =
      ImmutableSet.of("report-log-files");

  private static final ImmutableSet<String> EXCLUDED_TF_GEN_RESULT_FILES =
      ImmutableSet.of(
          "checksum-suite.data",
          "compatibility_result.css",
          "compatibility_result.xsl",
          "logo.png",
          "test_result.html",
          "test_result.xml",
          "test_result_failures_suite.html");
  private static final ImmutableSet<String> NOT_RETRY_FILES =
      ImmutableSet.of(
          "checksum.data",
          "checksum.previous.data",
          "test_result_failures_suite.html",
          "test_result.html",
          XtsConstants.INVOCATION_SUMMARY_FILE_NAME,
          "checksum-suite.data",
          "diffs",
          "module_reports",
          "proto",
          "logs");

  private static final ImmutableSet<String> EXCLUDED_TF_GEN_RESULT_DIRS =
      ImmutableSet.of("module_reports");

  private static final ImmutableSet<TestResult> COMPLETED_RESULTS =
      ImmutableSet.of(TestResult.PASS, TestResult.FAIL, TestResult.SKIP);

  // Ensure only one thread is processing the results at a time.
  private static final Object processResultLock = new Object();

  private final LocalFileUtil localFileUtil;
  private final CompatibilityReportMerger compatibilityReportMerger;
  private final CompatibilityReportCreator reportCreator;
  private final RetryReportMerger retryReportMerger;
  private final PreviousResultLoader previousResultLoader;
  private final SessionInfo sessionInfo;
  private final ScreenshotsMetadataUtil screenshotsMetadataUtil;
  private final SuiteResultReporter suiteResultReporter;

  @Inject
  SessionResultHandlerUtil(
      LocalFileUtil localFileUtil,
      CompatibilityReportMerger compatibilityReportMerger,
      CompatibilityReportCreator reportCreator,
      RetryReportMerger retryReportMerger,
      PreviousResultLoader previousResultLoader,
      SessionInfo sessionInfo,
      ScreenshotsMetadataUtil screenshotsMetadataUtil,
      SuiteResultReporter suiteResultReporter) {
    this.localFileUtil = localFileUtil;
    this.compatibilityReportMerger = compatibilityReportMerger;
    this.reportCreator = reportCreator;
    this.retryReportMerger = retryReportMerger;
    this.previousResultLoader = previousResultLoader;
    this.sessionInfo = sessionInfo;
    this.screenshotsMetadataUtil = screenshotsMetadataUtil;
    this.suiteResultReporter = suiteResultReporter;
  }

  /**
   * Processes the results of the given jobs.
   *
   * <p>The results are merged and saved to the given result directory. The logs are saved to the
   * given log directory. Also the content of {@code resultDir} will be zipped so ensure only needed
   * files are put under {@code resultDir} before the zipping. This method returns optional final
   * result in case the caller want to check it.
   *
   * @param resultDir the directory to save the merged result
   * @param logDir the directory to save the logs
   * @param latestResultLink the symbolic link path to the latest result directory.
   * @param latestLogLink the symbolic link path to the latest log directory.
   * @param jobs the jobs to process
   * @param sessionRequestInfo session request info stores info about the command
   * @return the final result generated from the jobs' results.
   */
  @CanIgnoreReturnValue
  public Optional<Result> processResult(
      Path resultDir,
      Path logDir,
      @Nullable Path latestResultLink,
      @Nullable Path latestLogLink,
      List<JobInfo> jobs,
      @Nullable SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log(
        "Acquiring lock to process result. Session id: %s", sessionInfo.getSessionId());
    synchronized (processResultLock) {
      logger.atInfo().log(
          "Acquired lock to process result. Session id: %s", sessionInfo.getSessionId());
      ImmutableMap<JobInfo, Optional<TestInfo>> tradefedTests =
          jobs.stream()
              .filter(jobInfo -> jobInfo.properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false))
              .collect(
                  toImmutableMap(
                      Function.identity(),
                      jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

      ImmutableMap<JobInfo, Optional<TestInfo>> nonTradefedTests =
          jobs.stream()
              .filter(
                  jobInfo -> jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false))
              .collect(
                  toImmutableMap(
                      Function.identity(),
                      jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

      Path nonTradefedTestResultsDir = resultDir.resolve("non-tradefed_results");
      Path nonTradefedTestLogsDir = logDir.resolve("non-tradefed_logs");
      Path serverSessionLogsDir = logDir.resolve("olc_server_session_logs");
      Path tmpTradefedTestResultsDir = null;
      try {
        // Prepares the log directory. To avoid creating empty result directory, the result
        // directory will be prepared when needed.
        localFileUtil.prepareDir(logDir);
        tmpTradefedTestResultsDir =
            Path.of(localFileUtil.createTempDir(Flags.instance().tmpDirRoot.getNonNull()));
        if (sessionRequestInfo == null) {
          return Optional.empty();
        }
        return processResultHelper(
            sessionRequestInfo,
            jobs,
            tradefedTests,
            nonTradefedTests,
            resultDir,
            logDir,
            nonTradefedTestLogsDir,
            tmpTradefedTestResultsDir,
            nonTradefedTestResultsDir);
      } finally {
        // Copies OLC server session logs.
        MobileHarnessCallable<Void> prepareOlcServerSessionLogsDir =
            () -> {
              localFileUtil.prepareDir(serverSessionLogsDir);
              sessionInfo.putSessionProperty(
                  SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH,
                  serverSessionLogsDir.resolve("olc_server_session_log.txt").toString());
              return null;
            };
        // Cleans up the temp tradefed test results dir
        final Path tmpTradefedTestResultsDirFinal = tmpTradefedTestResultsDir;
        MobileHarnessCallable<Void> cleanupTmpTradefedTestResultsDir =
            () -> {
              if (tmpTradefedTestResultsDirFinal != null
                  && localFileUtil.isDirExist(tmpTradefedTestResultsDirFinal)) {
                localFileUtil.removeFileOrDir(tmpTradefedTestResultsDirFinal);
              }
              return null;
            };
        // Create the latest result link and the latest log link.
        MobileHarnessCallable<Void> createLatestResultLink =
            () -> {
              if (latestResultLink != null && localFileUtil.isDirExist(resultDir)) {
                localFileUtil.removeFileOrDir(latestResultLink);
                localFileUtil.linkFileOrDir(resultDir.toString(), latestResultLink.toString());
              }
              return null;
            };
        MobileHarnessCallable<Void> createLatestLogLink =
            () -> {
              if (latestLogLink != null && localFileUtil.isDirExist(logDir)) {
                localFileUtil.removeFileOrDir(latestLogLink);
                localFileUtil.linkFileOrDir(logDir.toString(), latestLogLink.toString());
              }
              return null;
            };

        Callables.callAll(
            prepareOlcServerSessionLogsDir,
            cleanupTmpTradefedTestResultsDir,
            createLatestResultLink,
            createLatestLogLink);
        logger.atInfo().log(
            "Result processing finished. Session id: %s", sessionInfo.getSessionId());
      }
    }
  }

  private Optional<Result> processResultHelper(
      SessionRequestInfo sessionRequestInfo,
      List<JobInfo> sessionAllJobs,
      ImmutableMap<JobInfo, Optional<TestInfo>> tradefedTests,
      ImmutableMap<JobInfo, Optional<TestInfo>> nonTradefedTests,
      Path resultDir,
      Path logDir,
      Path nonTradefedTestLogsDir,
      Path tmpTradefedTestResultsDir,
      Path nonTradefedTestResultsDir)
      throws MobileHarnessException, InterruptedException {
    Result finalReport = null;
    ImmutableMap.Builder<String, String> testReportProperties = ImmutableMap.builder();
    ImmutableList.Builder<TradefedResultBundle> tradefedResultBundlesBuilder =
        ImmutableList.builder();
    boolean curSessionHasNonTfJob = false;
    boolean curSessionHasTfJob = false;
    boolean previousSessionHasNonTfModule = false;
    boolean previousSessionHasTfModule = false;
    List<Path> extraFilesOrDirsToZip = new ArrayList<>();

    // Copies tradefed test relevant log and result files to dedicated locations
    for (Entry<JobInfo, Optional<TestInfo>> testEntry : tradefedTests.entrySet()) {
      if (testEntry.getValue().isEmpty()) {
        logger.atInfo().log(
            "Found no test in tradefed job [%s], skip it.", testEntry.getKey().locator().getId());
        continue;
      }
      curSessionHasTfJob = true;
      TestInfo test = testEntry.getValue().get();

      addExtraFilesToResultDir(extraFilesOrDirsToZip, test);

      if (!previousSessionHasNonTfModule) {
        previousSessionHasNonTfModule =
            Boolean.parseBoolean(
                test.jobInfo().properties().get(Job.PREV_SESSION_HAS_NON_TF_MODULE));
      }

      callAndLogException(
          () -> {
            copyTradefedTestLogFiles(test, logDir);
            return null;
          },
          String.format(
              "Failed to copy tradefed test [%s]'s log files to log dir [%s].",
              test.locator().getId(), logDir));
      callAndLogException(
          () -> {
            Optional<TradefedResultBundle> bundle =
                copyTradefedTestResultFiles(test, tmpTradefedTestResultsDir, resultDir);
            bundle.ifPresent(tradefedResultBundlesBuilder::add);
            return null;
          },
          String.format(
              "Failed to copy tradefed test [%s]'s result files to tmp result dir [%s], result dir"
                  + " [%s].",
              test.locator().getId(), tmpTradefedTestResultsDir, resultDir));
    }
    ImmutableList<TradefedResultBundle> tradefedResultBundles =
        tradefedResultBundlesBuilder.build();

    ImmutableSet.Builder<String> skippedModuleIdsBuilder = ImmutableSet.builder();
    List<MoblyReportInfo> moblyReportInfos = new ArrayList<>();
    // Copies non-tradefed test relevant log and result files to dedicated locations
    for (Entry<JobInfo, Optional<TestInfo>> testEntry : nonTradefedTests.entrySet()) {
      if (testEntry.getValue().isEmpty()) {
        logger.atInfo().log(
            "Found no test in non-tradefed job [%s], skip it.",
            testEntry.getKey().locator().getId());
        continue;
      }
      curSessionHasNonTfJob = true;
      TestInfo test = testEntry.getValue().get();

      // Check if the module is skipped by feature checker.
      JobInfo jobInfo = testEntry.getKey();
      if (jobInfo.resultWithCause().get().type() == TestResult.SKIP
          && !jobInfo.properties().getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS).orElse(false)) {
        String moduleId = getExpandedNonTfModuleId(jobInfo);
        skippedModuleIdsBuilder.add(moduleId);
      }

      if (!previousSessionHasTfModule) {
        previousSessionHasTfModule =
            Boolean.parseBoolean(test.jobInfo().properties().get(Job.PREV_SESSION_HAS_TF_MODULE));
      }

      callAndLogException(
          () -> {
            copyNonTradefedTestLogFiles(test, nonTradefedTestLogsDir);
            return null;
          },
          String.format(
              "Failed to copy non-tradefed test [%s]'s log files to log dir [%s].",
              test.locator().getId(), nonTradefedTestLogsDir));
      if (!test.jobInfo()
          .properties()
          .getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS)
          .orElse(false)) {
        callAndLogException(
            () -> {
              Optional<NonTradefedTestResult> nonTradefedTestResult =
                  copyNonTradefedTestResultFiles(
                      test,
                      nonTradefedTestResultsDir,
                      resultDir,
                      testEntry
                          .getKey()
                          .properties()
                          .get(SessionHandlerHelper.XTS_MODULE_NAME_PROP),
                      testEntry.getKey().properties().get(SessionHandlerHelper.XTS_MODULE_ABI_PROP),
                      testEntry
                          .getKey()
                          .properties()
                          .get(SessionHandlerHelper.XTS_MODULE_PARAMETER_PROP));
              nonTradefedTestResult.ifPresent(
                  res ->
                      moblyReportInfos.add(
                          MoblyReportInfo.of(
                              res.moduleName(),
                              res.moduleAbi().orElse(null),
                              res.moduleParameter().orElse(null),
                              res.testSummaryFile(),
                              res.resultAttributesFile(),
                              res.deviceBuildFingerprint(),
                              res.buildAttributesFile(),
                              res.moduleResultFile(),
                              test.jobInfo()
                                  .params()
                                  .getOptional(
                                      MoblyReportInfo.MOBLY_TEST_ENTRY_CONVERTER_CLASS_PARAM))));
              return null;
            },
            String.format(
                "Failed to copy non-tradefed test [%s]'s result files to result dir [%s].",
                test.locator().getId(), nonTradefedTestResultsDir));
      }
    }

    Optional<Result> mergedTradefedReport = Optional.empty();
    if (!tradefedResultBundles.isEmpty()) {
      ParseResult parseResult =
          compatibilityReportMerger.mergeResultBundles(
              tradefedResultBundles, sessionRequestInfo.skipDeviceInfo().orElse(false));
      mergedTradefedReport = parseResult.report();
      skippedModuleIdsBuilder.addAll(parseResult.skippedModuleIds());
    }
    ImmutableSet<String> skippedModuleIds = skippedModuleIdsBuilder.build();

    boolean skipDeviceInfo = sessionRequestInfo.skipDeviceInfo().orElse(false);
    Optional<Result> mergedNonTradefedReport = Optional.empty();
    if (!moblyReportInfos.isEmpty()) {
      mergedNonTradefedReport =
          compatibilityReportMerger.mergeMoblyReports(moblyReportInfos, skipDeviceInfo);
    }

    List<Result> reportList = new ArrayList<>();
    mergedTradefedReport.ifPresent(reportList::add);
    mergedNonTradefedReport.ifPresent(reportList::add);

    Optional<Result> mergedReport =
        compatibilityReportMerger.mergeReports(
            reportList, /* validateReports= */ true, skipDeviceInfo);

    boolean testReportHasNonTfModule = curSessionHasNonTfJob || previousSessionHasNonTfModule;
    boolean testReportHasTfModule = curSessionHasTfJob || previousSessionHasTfModule;
    testReportProperties
        .put(
            SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE,
            String.valueOf(testReportHasNonTfModule))
        .put(SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE, String.valueOf(testReportHasTfModule));

    boolean isRunRetry = SessionHandlerHelper.isRunRetry(sessionRequestInfo.testPlan());
    if (!isRunRetry && mergedReport.isPresent()) {
      Result.Builder finalReportBuilder = mergedReport.get().toBuilder();
      List<Attribute> attributes =
          finalReportBuilder.getAttributeList().stream()
              .filter(attribute -> !attribute.getKey().equals(XmlConstants.COMMAND_LINE_ARGS))
              .collect(toCollection(ArrayList::new));
      attributes.add(
          Attribute.newBuilder()
              .setKey(XmlConstants.COMMAND_LINE_ARGS)
              .setValue(sessionRequestInfo.commandLineArgs())
              .build());
      finalReportBuilder.clearAttribute().addAllAttribute(attributes);
      if (!sessionRequestInfo.moduleNames().isEmpty()) {
        finalReportBuilder.addAllModuleFilter(sessionRequestInfo.moduleNames());
      }
      if (sessionRequestInfo.testName().isPresent()
          && !sessionRequestInfo.testName().get().isEmpty()) {
        finalReportBuilder.setTestFilter(sessionRequestInfo.testName().get());
      }
      ImmutableSet.Builder<String> includeFilters = ImmutableSet.builder();
      ImmutableSet.Builder<String> excludeFilters = ImmutableSet.builder();
      if (!sessionRequestInfo.includeFilters().isEmpty()) {
        includeFilters.addAll(sessionRequestInfo.includeFilters());
      }
      if (!sessionRequestInfo.excludeFilters().isEmpty()) {
        excludeFilters.addAll(sessionRequestInfo.excludeFilters());
      }
      if (sessionRequestInfo.subPlanName().isPresent()) {
        // Add all filters in the sub-plan so these filters are loaded in retry
        Path subPlanPath =
            SessionHandlerHelper.getSubPlanFilePath(
                Path.of(sessionRequestInfo.xtsRootDir()),
                sessionRequestInfo.xtsType(),
                sessionRequestInfo.subPlanNameBackup().isPresent()
                    ? sessionRequestInfo.subPlanNameBackup().get()
                    : sessionRequestInfo.subPlanName().get());
        logger.atInfo().log("Loading subplan file from %s", subPlanPath);
        SubPlan subPlan = SessionHandlerHelper.loadSubPlan(subPlanPath.toFile());
        includeFilters.addAll(subPlan.getAllIncludeFilters());
        excludeFilters.addAll(subPlan.getAllExcludeFilters());
      }
      finalReport =
          finalReportBuilder
              .addAllIncludeFilter(includeFilters.build())
              .addAllExcludeFilter(excludeFilters.build())
              .build();
      Result userfacingReport = preprocessReport(finalReport, sessionRequestInfo);
      // Generates the invocation summary file before creating the report and zipping result
      // folder.
      generateInvocationSummaryFile(
          sessionAllJobs,
          userfacingReport,
          resultDir,
          logDir,
          /* previousResult= */ null,
          sessionRequestInfo);
      reportCreator.createReport(
          finalReport,
          userfacingReport,
          resultDir,
          /* testRecord= */ null,
          /* includeHtmlInZip= */ sessionRequestInfo.htmlInZip(),
          /* testPlan= */ sessionRequestInfo.testPlan(),
          testReportProperties.buildOrThrow(),
          extraFilesOrDirsToZip);
    } else if (isRunRetry) {
      if (testReportHasTfModule && !curSessionHasTfJob) {
        // If the test report will have TF module but the current session doesn't retry any TF
        // module, need to copy test-record.pb from previous session to the current session.
        ImmutableList<Path> testRecordProtoFiles = ImmutableList.of();
        if (sessionRequestInfo.retrySessionIndex().isPresent()
            || sessionRequestInfo.retrySessionResultDirName().isPresent()) {
          testRecordProtoFiles =
              previousResultLoader.getPrevSessionTestRecordProtoFiles(
                  XtsDirUtil.getXtsResultsDir(
                      Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType()),
                  sessionRequestInfo.retrySessionIndex().orElse(null),
                  sessionRequestInfo.retrySessionResultDirName().orElse(null));
        } else {
          testRecordProtoFiles =
              previousResultLoader.getPrevSessionTestRecordProtoFiles(
                  Path.of(sessionRequestInfo.retryResultDir().orElseThrow()));
        }
        if (!testRecordProtoFiles.isEmpty()) {
          Path testRecordProtoDir = resultDir.resolve("proto");
          localFileUtil.prepareDir(testRecordProtoDir);
          for (Path testRecordProtoFile : testRecordProtoFiles) {
            localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                testRecordProtoFile, testRecordProtoDir, ImmutableList.of("-rf"));
          }
        }
      }

      Result previousResult = null;
      if (testReportHasNonTfModule
          || !SessionHandlerHelper.useTfRetry(
              sessionRequestInfo.isAtsServerRequest(),
              sessionRequestInfo.xtsType(),
              sessionRequestInfo
                  .testSuiteInfo()
                  .map(testSuiteInfo -> testSuiteInfo.getTestSuiteVersion().orElse(null))
                  .orElse(null))) {
        MergedResult mergedResult =
            sessionRequestInfo.retrySessionId().isPresent()
                ? retryReportMerger.mergeReports(
                    Path.of(sessionRequestInfo.retryResultDir().orElseThrow()),
                    sessionRequestInfo.retrySessionId().get(),
                    sessionRequestInfo.retryType().orElse(null),
                    mergedReport.orElse(null),
                    sessionRequestInfo.moduleNames(),
                    skippedModuleIds)
                : retryReportMerger.mergeReports(
                    XtsDirUtil.getXtsResultsDir(
                        Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType()),
                    sessionRequestInfo.retrySessionIndex().orElse(null),
                    sessionRequestInfo.retrySessionResultDirName().orElse(null),
                    sessionRequestInfo.retryType().orElse(null),
                    mergedReport.orElse(null),
                    sessionRequestInfo.moduleNames(),
                    skippedModuleIds);
        finalReport = mergedResult.mergedResult();
        previousResult = mergedResult.previousResult();
      } else {
        finalReport = mergedReport.orElse(null);
      }
      if (finalReport != null) {
        // For console, before creating the report and zipping result folder, copy previous session
        // result files into final result folder.
        if (isConsoleRetry(sessionRequestInfo)) {
          callAndLogException(
              () -> {
                copyRetryFilesForConsole(sessionRequestInfo, resultDir);
                return null;
              },
              String.format(
                  "Failed to copy files from previsou session to result dir [%s].", resultDir));
          callAndLogException(
              () -> {
                generateScreenshotsMetadataFile(sessionRequestInfo, resultDir);
                return null;
              },
              String.format(
                  "Failed to generate screenshots metadata file for result dir [%s].", resultDir));
        }
        Result userfacingReport = preprocessReport(finalReport, sessionRequestInfo);
        // Generates the invocation summary file before creating the report and zipping result
        // folder.
        generateInvocationSummaryFile(
            sessionAllJobs,
            userfacingReport,
            resultDir,
            logDir,
            previousResult,
            sessionRequestInfo);
        String previousTestPlanForRetry =
            finalReport.getAttributeList().stream()
                .filter(attribute -> attribute.getKey().equals(XmlConstants.SUITE_PLAN_ATTR))
                .findFirst()
                .map(Attribute::getValue)
                .orElse("");
        reportCreator.createReport(
            finalReport,
            userfacingReport,
            resultDir,
            /* testRecord= */ null,
            /* includeHtmlInZip= */ sessionRequestInfo.htmlInZip(),
            /* testPlan= */ previousTestPlanForRetry,
            testReportProperties.buildOrThrow(),
            extraFilesOrDirsToZip);
      }
    } else {
      logger.atWarning().log("Failed to merge reports.");
    }

    return Optional.ofNullable(finalReport);
  }

  /**
   * Copies tradefed test relevant log files to directory {@code logDir} for the given tradefed
   * test.
   *
   * <p>The destination log files structure looks like:
   *
   * <pre>
   * .../android-<xts>/logs/uuuu.MM.dd_HH.mm.ss.SSS_1234/
   *    inv_<invocation_id>/
   *      raw tradefed logs
   *      <driver>_test_<test_id>/
   *        command_history.txt
   *        xts_tf_output.log
   *    non-tradefed_logs/
   *      <module_id>_test_<test_id>/
   *        command_history.txt
   *        mobly_command_output.log
   *        mobly_run_build_attributes.textproto
   *        mobly_run_result_attributes.textproto
   *        ...
   *        raw_mobly_logs/
   * </pre>
   */
  private void copyTradefedTestLogFiles(TestInfo tradefedTestInfo, Path logRootDir)
      throws MobileHarnessException, InterruptedException {
    Path invocationDir = prepareTradefedInvocationDir(tradefedTestInfo, logRootDir);
    Path testLogDir = prepareLogOrResultDirForTest(tradefedTestInfo, invocationDir);

    tradefedTestInfo
        .properties()
        .add(XtsConstants.XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY, invocationDir.toString());
    ImmutableList<Path> genFiles = getGenFilesFromTest(tradefedTestInfo);
    for (Path genFile : genFiles) {
      if (genFile.getFileName().toString().endsWith("gen-files")) {
        Path logsDir = genFile.resolve("logs");
        if (logsDir.toFile().exists()) {
          List<Path> logsSubFilesOrDirs =
              localFileUtil.listFilesOrDirs(
                  logsDir, filePath -> !filePath.getFileName().toString().equals("latest"));
          for (Path logsSubFileOrDir : logsSubFilesOrDirs) {
            if (logsSubFileOrDir.toFile().isDirectory()) {
              // If it's a dir, copy its content into the new log dir.
              List<Path> logFilesOrDirs =
                  localFileUtil.listFilesOrDirs(logsSubFileOrDir, path -> true);
              for (Path logFileOrDir : logFilesOrDirs) {
                String fileName = logFileOrDir.getFileName().toString();
                if (logFileOrDir.toFile().isDirectory()
                    && fileName.startsWith(XtsConstants.TRADEFED_INVOCATION_DIR_NAME_PREFIX)) {
                  // Copy all details under TF invocation dir to the new invocation dir.
                  List<Path> innovationDetails =
                      localFileUtil.listFilesOrDirs(logFileOrDir, path -> true);
                  for (Path fileOrDir : innovationDetails) {
                    localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                        fileOrDir, invocationDir, ImmutableList.of("-rf"));
                  }
                } else {
                  logger.atInfo().log(
                      "Copying tradefed test log relevant file/dir [%s] into dir [%s]",
                      logFileOrDir, invocationDir);
                  localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                      logFileOrDir, invocationDir, ImmutableList.of("-rf"));
                }
              }
            }
          }
        }
      } else {
        logger.atInfo().log(
            "Copying tradefed test log relevant file/dir [%s] into dir [%s]", genFile, testLogDir);
        localFileUtil.copyFileOrDirWithOverridingCopyOptions(
            genFile, testLogDir, ImmutableList.of("-rf"));
      }
    }
  }

  /**
   * Copies tradefed test relevant result files to directory {@code tmpResultDir} for the given
   * tradefed test. Returns a pair of test result xml file and test record proto file where the test
   * record proto file is optional.
   *
   * <p>Contents in the directory {@code resultDirInZip} will be put in the result zip file.
   *
   * <p>The destination result files structure looks like:
   *
   * <pre>
   * .../android-<xts>/results/uuuu.MM.dd_HH.mm.ss.SSS_1234/
   *    the merged report relevant files (test_result.xml, html, checksum-suite.data, etc)
   *    config/
   *    device-info-files/
   *    invocation_summary.txt
   *    report-log-files/
   *    vintf-files/
   *    non-tradefed_results/
   *      <module_id>_test_<test_id>/
   *        test_summary.yaml
   *        mobly_run_build_attributes.textproto
   *        mobly_run_result_attributes.textproto
   *        ...
   * </pre>
   */
  @CanIgnoreReturnValue
  private Optional<TradefedResultBundle> copyTradefedTestResultFiles(
      TestInfo tradefedTestInfo, Path tmpResultDir, Path resultDirInZip)
      throws MobileHarnessException, InterruptedException {
    Path tmpTestResultDir = prepareLogOrResultDirForTest(tradefedTestInfo, tmpResultDir);
    ImmutableList<Path> genFiles = getGenFilesFromTest(tradefedTestInfo);
    for (Path genFile : genFiles) {
      if (genFile.getFileName().toString().endsWith("gen-files")) {
        Path genFileResultsDir = genFile.resolve("results");
        if (genFileResultsDir.toFile().exists()) {
          Optional<Path> tfGenResultDir =
              localFileUtil
                  .listFilesOrDirs(
                      genFileResultsDir,
                      filePath ->
                          !filePath.getFileName().toString().equals("latest")
                              && filePath.toFile().isDirectory())
                  .stream()
                  .findFirst();
          if (tfGenResultDir.isEmpty()) {
            logger.atInfo().log("TF result dir not found in dir %s", genFileResultsDir);
            continue;
          }
          List<Path> resultFilesOrDirs =
              localFileUtil.listFilesOrDirs(tfGenResultDir.get(), path -> true);
          if (!resultFilesOrDirs.isEmpty()) {
            localFileUtil.prepareDir(resultDirInZip);
          }
          for (Path resultFileOrDir : resultFilesOrDirs) {
            logger.atInfo().log(
                "Copying tradefed test result relevant file/dir [%s] into tmp dir [%s]",
                resultFileOrDir, tmpTestResultDir);
            localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                resultFileOrDir, tmpTestResultDir, ImmutableList.of("-rf"));
          }
          // Copy the needed TF result files to the result dir being zipped
          for (Path resultFileOrDir : resultFilesOrDirs) {
            if ((resultFileOrDir.toFile().isDirectory()
                    && EXCLUDED_TF_GEN_RESULT_DIRS.contains(
                        resultFileOrDir.getFileName().toString()))
                || EXCLUDED_TF_GEN_RESULT_FILES.contains(
                    resultFileOrDir.getFileName().toString())) {
              continue;
            }

            logger.atInfo().log(
                "Copying tradefed test result relevant file/dir [%s] into dir [%s]",
                resultFileOrDir, resultDirInZip);
            localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                resultFileOrDir, resultDirInZip, ImmutableList.of("-rf"));
          }
        }
      }
    }

    List<Path> testResultFiles =
        localFileUtil.listFilePaths(tmpTestResultDir, /* recursively= */ true);
    Path testResultXmlFile =
        testResultFiles.stream()
            .filter(
                file ->
                    file.getFileName()
                        .toString()
                        .equals(SessionHandlerHelper.TEST_RESULT_XML_FILE_NAME))
            .findFirst()
            .orElse(null);
    Optional<Path> testRecordFile =
        testResultFiles.stream()
            .filter(
                file ->
                    file.getFileName()
                        .toString()
                        .equals(SessionHandlerHelper.TEST_RECORD_PROTOBUFFER_FILE_NAME))
            .findFirst();
    // Retrieve the list of filtered (by include/exclude filters, the given module names, or
    // subplan) expanded module names (e.g. `arm64-v8a CtsBatteryHealthTestCases`) for the Tradefed
    // test, from test properties.
    ImmutableList<TradefedResultBundle.ModuleInfo> filteredExpandedTradefedModules =
        Splitter.on(",")
            .omitEmptyStrings()
            .splitToStream(
                tradefedTestInfo
                    .properties()
                    .getOptional(
                        XtsConstants.TRADEFED_FILTERED_EXPANDED_MODULES_FOR_TEST_PROPERTY_KEY)
                    .orElse(""))
            .map(AbiUtil::parseId)
            .map(
                id ->
                    TradefedResultBundle.ModuleInfo.of(/* abi= */ id.get(0), /* name= */ id.get(1)))
            .collect(toImmutableList());
    return Optional.ofNullable(testResultXmlFile)
        .map(
            resultXmlFile ->
                TradefedResultBundle.of(
                    resultXmlFile, testRecordFile, filteredExpandedTradefedModules));
  }

  /**
   * Copies non-tradefed test relevant log files to directory {@code logDir} for the given
   * non-tradefed test.
   *
   * <p>The destination log files structure looks like:
   *
   * <pre>
   * .../android-<xts>/logs/uuuu.MM.dd_HH.mm.ss.SSS_1234/
   *    inv_<invocation_id>/
   *      raw tradefed logs
   *      <driver>_test_<test_id>/
   *        command_history.txt
   *        xts_tf_output.log
   *    non-tradefed_logs/
   *      <module_id>_test_<test_id>/
   *        command_history.txt
   *        mobly_command_output.log
   *        mobly_run_build_attributes.textproto
   *        mobly_run_result_attributes.textproto
   *        ...
   *        raw_mobly_logs/
   * </pre>
   */
  private void copyNonTradefedTestLogFiles(TestInfo nonTradefedTestInfo, Path logDir)
      throws MobileHarnessException, InterruptedException {
    Path testLogDir = prepareLogOrResultDirForTest(nonTradefedTestInfo, logDir);
    ImmutableList<Path> genFiles = getGenFilesFromTest(nonTradefedTestInfo);
    nonTradefedTestInfo
        .properties()
        .add(XtsConstants.XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY, testLogDir.toString());
    for (Path genFile : genFiles) {
      logger.atInfo().log(
          "Copying non-tradefed test log relevant file/dir [%s] into dir [%s]",
          genFile, testLogDir);
      localFileUtil.copyFileOrDirWithOverridingCopyOptions(
          genFile, testLogDir, ImmutableList.of("-rf"));
    }
  }

  /**
   * Copies non-tradefed test relevant result files to directory {@code resultDir} for the given
   * non-tradefed test.
   *
   * <p>The destination result files structure looks like:
   *
   * <pre>
   * .../android-<xts>/results/uuuu.MM.dd_HH.mm.ss.SSS_1234/
   *    the merged report relevant files (test_result.xml, html, checksum-suite.data, etc)
   *    config/
   *    device-info-files/
   *    invocation_summary.txt
   *    report-log-files/
   *    vintf-files/
   *    non-tradefed_results/
   *      <module_id>_test_<test_id>/
   *        test_summary.yaml
   *        mobly_run_build_attributes.textproto
   *        mobly_run_result_attributes.textproto
   *        ...
   * </pre>
   *
   * @param moduleName the xts module name
   * @return {@code NonTradefedTestResult} if any
   */
  @CanIgnoreReturnValue
  private Optional<NonTradefedTestResult> copyNonTradefedTestResultFiles(
      TestInfo nonTradefedTestInfo,
      Path nonTradefedResultDir,
      Path rootResultDir,
      String moduleName,
      @Nullable String moduleAbi,
      @Nullable String moduleParameter)
      throws MobileHarnessException, InterruptedException {
    NonTradefedTestResult.Builder nonTradefedTestResultBuilder =
        NonTradefedTestResult.builder().setModuleName(moduleName);
    if (moduleAbi != null) {
      nonTradefedTestResultBuilder.setModuleAbi(moduleAbi);
    }
    if (moduleParameter != null) {
      nonTradefedTestResultBuilder.setModuleParameter(moduleParameter);
    }
    String testGenFileDir = nonTradefedTestInfo.getGenFileDir();

    List<Path> moblyTestResultFiles =
        localFileUtil.listFilePaths(
            Path.of(testGenFileDir),
            /* recursively= */ true,
            path -> MOBLY_TEST_RESULT_FILE_NAMES.contains(path.getFileName().toString()));
    List<String> moblyRootResultDirs =
        localFileUtil.listDirs(
            testGenFileDir,
            /* depth= */ Integer.MAX_VALUE,
            /* recursively= */ true,
            file -> MOBLY_ROOT_TEST_RESULT_DIR_NAMES.contains(file.getName()));

    Optional<String> labGenFileDir = getLabGenFileDir(nonTradefedTestInfo);
    if (labGenFileDir.isPresent() && !labGenFileDir.get().equals(testGenFileDir)) {
      moblyTestResultFiles.addAll(
          localFileUtil.listFilePaths(
              Path.of(labGenFileDir.get()),
              /* recursively= */ true,
              path -> MOBLY_TEST_RESULT_FILE_NAMES.contains(path.getFileName().toString())));
      moblyRootResultDirs.addAll(
          localFileUtil.listDirs(
              labGenFileDir.get(),
              /* depth= */ Integer.MAX_VALUE,
              /* recursively= */ true,
              file -> MOBLY_ROOT_TEST_RESULT_DIR_NAMES.contains(file.getName())));
    }

    // Prepares the test result dir for the non-tradefed test if needed.
    Optional<Path> testResultDir =
        Optional.ofNullable(
            moblyTestResultFiles.isEmpty()
                ? null
                : prepareLogOrResultDirForTest(nonTradefedTestInfo, nonTradefedResultDir));
    for (Path moblyTestResultFile : moblyTestResultFiles) {
      logger.atInfo().log(
          "Copying non-tradefed test result relevant file [%s] into dir [%s]",
          moblyTestResultFile, testResultDir.get());
      localFileUtil.copyFileOrDirWithOverridingCopyOptions(
          moblyTestResultFile, testResultDir.get(), ImmutableList.of("-rf"));
      updateNonTradefedTestResult(
          nonTradefedTestResultBuilder,
          moblyTestResultFile.getFileName().toString(),
          moblyTestResultFile);
    }

    // Copy mobly result dirs to the root result dir.
    for (String path : moblyRootResultDirs) {
      logger.atInfo().log(
          "Copying non-tradefed test result relevant dir [%s] into dir [%s]", path, rootResultDir);
      localFileUtil.copyFileOrDirWithOverridingCopyOptions(
          Path.of(path), rootResultDir, ImmutableList.of("-rf"));
    }

    return Optional.of(nonTradefedTestResultBuilder.build());
  }

  private ImmutableList<Path> getGenFilesFromTest(TestInfo test) throws MobileHarnessException {
    String testGenFileDir = test.getGenFileDir();
    List<Path> genFiles = localFileUtil.listFilesOrDirs(Path.of(testGenFileDir), path -> true);

    Optional<String> labGenFilesDir = getLabGenFileDir(test);
    if (labGenFilesDir.isPresent() && !labGenFilesDir.get().equals(testGenFileDir)) {
      genFiles.addAll(localFileUtil.listFilesOrDirs(Path.of(labGenFilesDir.get()), path -> true));
    }
    return ImmutableList.copyOf(genFiles);
  }

  private Optional<String> getLabGenFileDir(TestInfo test) {
    String path =
        PathUtil.join(
            Flags.instance().atsStoragePath.getNonNull(), "genfiles", test.locator().getId());
    if (localFileUtil.isDirExist(path)) {
      return Optional.of(path);
    } else {
      return Optional.empty();
    }
  }

  public Path getTradefedInvocationLogDir(TestInfo tradefedTestInfo, Path logRootDir)
      throws MobileHarnessException {
    Path invocationDir = prepareTradefedInvocationDir(tradefedTestInfo, logRootDir);
    return prepareLogOrResultDirForTest(tradefedTestInfo, invocationDir);
  }

  private Path prepareTradefedInvocationDir(TestInfo tradefedTestInfo, Path logRootDir) {
    Path invocationDir;
    if (tradefedTestInfo.properties().has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)) {
      invocationDir =
          logRootDir.resolve(
              tradefedTestInfo.properties().get(XtsConstants.TRADEFED_INVOCATION_DIR_NAME));
    } else {
      String xtsJobType =
          tradefedTestInfo
              .jobInfo()
              .properties()
              .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
              .map(s -> Ascii.toLowerCase(s) + "_")
              .orElse("");
      invocationDir =
          logRootDir.resolve(
              XtsConstants.TRADEFED_INVOCATION_DIR_NAME_PREFIX
                  + xtsJobType
                  + tradefedTestInfo.locator().getId());
    }
    return invocationDir;
  }

  private Path prepareLogOrResultDirForTest(TestInfo test, Path parentDir)
      throws MobileHarnessException {
    String prefix =
        test.jobInfo().properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)
            ? getExpandedNonTfModuleId(test.jobInfo()).replace(' ', '_')
            : test.jobInfo().type().getDriver();
    Path targetDir = parentDir.resolve(getLogDirNameForTest(prefix, test.locator().getId()));
    localFileUtil.prepareDir(targetDir);
    return targetDir;
  }

  private void updateNonTradefedTestResult(
      NonTradefedTestResult.Builder resultBuilder, String fileName, Path filePath)
      throws MobileHarnessException {
    switch (fileName) {
      case "test_summary.yaml" -> resultBuilder.setTestSummaryFile(filePath);
      case "device_build_fingerprint.txt" ->
          resultBuilder.setDeviceBuildFingerprint(localFileUtil.readFile(filePath).trim());
      case "mobly_run_result_attributes.textproto" ->
          resultBuilder.setResultAttributesFile(filePath);
      case "mobly_run_build_attributes.textproto" -> resultBuilder.setBuildAttributesFile(filePath);
      case "ats_module_run_result.textproto" -> resultBuilder.setModuleResultFile(filePath);
      default -> {}
    }
  }

  private static void callAndLogException(MobileHarnessCallable<Void> callable, String errorMessage)
      throws InterruptedException {
    try {
      @SuppressWarnings("unused")
      Void unused = callable.call();
    } catch (MobileHarnessException | RuntimeException | Error e) {
      logger.atWarning().with(IMPORTANCE, IMPORTANT).withCause(e).log("%s", errorMessage);
    }
  }

  /** Data class for the non-tradefed test result. */
  @AutoValue
  public abstract static class NonTradefedTestResult {

    /** The xTS module name. */
    public abstract String moduleName();

    /** The abi of the xTS module. */
    public abstract Optional<String> moduleAbi();

    /** The parameter of the xTS module. */
    public abstract Optional<String> moduleParameter();

    /**
     * The build fingerprint for the major device on which the test run, it's used to identify the
     * generated report.
     */
    public abstract Optional<String> deviceBuildFingerprint();

    /**
     * The path of the test summary file being parsed. It could be empty in some cases like the test
     * is skipped.
     */
    public abstract Optional<Path> testSummaryFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.AttributeList}
     * which will be set in the {@link Result}.{@code attribute}.
     */
    public abstract Optional<Path> resultAttributesFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.AttributeList}
     * which will be set in the {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo}.{@code
     * attribute}.
     */
    public abstract Optional<Path> buildAttributesFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult}
     * which is used as a backup result from ATS if the Mobly result file wasn't created.
     */
    public abstract Optional<Path> moduleResultFile();

    public static Builder builder() {
      return new AutoValue_SessionResultHandlerUtil_NonTradefedTestResult.Builder();
    }

    /** Builder for {@link NonTradefedTestResult}. */
    @SuppressWarnings("UnusedReturnValue")
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setModuleName(String moduleName);

      public abstract Builder setModuleAbi(String moduleAbi);

      public abstract Builder setModuleParameter(String moduleParameter);

      public abstract Builder setDeviceBuildFingerprint(String deviceBuildFingerprint);

      public abstract Builder setTestSummaryFile(Path testSummaryFile);

      public abstract Builder setResultAttributesFile(Path resultAttributesFile);

      public abstract Builder setBuildAttributesFile(Path buildAttributesFile);

      public abstract Builder setModuleResultFile(Path moduleResultFile);

      public abstract NonTradefedTestResult build();
    }
  }

  public void cleanUpJobGenDirs(List<JobInfo> jobInfoList)
      throws MobileHarnessException, InterruptedException {
    for (JobInfo jobInfo : jobInfoList) {
      if (jobInfo.setting().hasGenFileDir()) {
        logger.atInfo().log(
            "Cleaning up job [%s] gen dir [%s]",
            jobInfo.locator().getId(), jobInfo.setting().getGenFileDir());
        localFileUtil.removeFileOrDir(jobInfo.setting().getGenFileDir());
      }
    }
  }

  public void cleanUpLabGenFileDir(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<String> labGenFileDir = getLabGenFileDir(testInfo);
    if (labGenFileDir.isPresent()) {
      localFileUtil.removeFileOrDir(labGenFileDir.get());
    }
  }

  /** Returns true if any of the {@code jobInfos} has a test with a completed result. */
  public boolean isSessionCompleted(List<JobInfo> jobInfos) {
    if (jobInfos.isEmpty()) {
      return false;
    }
    for (JobInfo jobInfo : jobInfos) {
      for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
        if (COMPLETED_RESULTS.contains(testInfo.resultWithCause().get().type())) {
          return true;
        }
      }
    }
    return false;
  }

  /** Copy the previous attempts' result files to current session's result directory */
  public void copyRetryFiles(String oldDir, String newDir)
      throws MobileHarnessException, InterruptedException {
    File[] oldChildren =
        localFileUtil.listFilesOrDirs(
            oldDir,
            oldChild ->
                !NOT_RETRY_FILES.contains(oldChild.getName())
                    && !XtsConstants.RESULT_ZIP_FILENAME_PATTERN
                        .matcher(oldChild.getName())
                        .matches());
    for (File oldChild : oldChildren) {
      File newChild = new File(newDir, oldChild.getName());
      if (!newChild.exists()) {
        // If this old file or directory doesn't exist in new dir, simply copy it.
        logger.atFine().log("Copying %s to new session.", oldChild.getName());
        localFileUtil.copyFileOrDir(oldChild.getAbsolutePath(), newChild.getAbsolutePath());
      } else if (newChild.isDirectory() && oldChild.isDirectory()) {
        copyRetryFiles(oldChild.getAbsolutePath(), newChild.getAbsolutePath());
      }
    }
  }

  private void copyRetryFilesForConsole(SessionRequestInfo sessionRequestInfo, Path resultDir)
      throws MobileHarnessException, InterruptedException {
    if (localFileUtil.isDirExist(resultDir)) {
      Path prevResultDir =
          previousResultLoader.getPrevSessionResultDir(
              XtsDirUtil.getXtsResultsDir(
                  Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType()),
              sessionRequestInfo.retrySessionIndex().orElse(null),
              sessionRequestInfo.retrySessionResultDirName().orElse(null));
      try {
        copyRetryFiles(prevResultDir.toString(), resultDir.toString());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to copy contents of previous result dir %s to current result dir %s",
            prevResultDir, resultDir);
      }
    }
  }

  /** Re-generates the screenshots metadata file after retry for the given result directory. */
  public void generateScreenshotsMetadataFile(SessionRequestInfo sessionRequestInfo, Path resultDir)
      throws MobileHarnessException {
    if (localFileUtil.isDirExist(resultDir)) {
      Optional<Path> screenshotsMetadataFile =
          screenshotsMetadataUtil.genScreenshotsMetadataFile(resultDir);
      if (screenshotsMetadataFile.isPresent()) {
        logger.atInfo().log(
            "Successfully generated the screenshots metadata file for retry: %s",
            screenshotsMetadataFile.get());
      }
    }
  }

  private boolean isConsoleRetry(SessionRequestInfo sessionRequestInfo) {
    return SessionHandlerHelper.isRunRetry(sessionRequestInfo.testPlan())
        && (sessionRequestInfo.retrySessionIndex().isPresent()
            || sessionRequestInfo.retrySessionResultDirName().isPresent());
  }

  private void addExtraFilesToResultDir(List<Path> extraFilesOrDirsToZip, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String testListProperty =
        testInfo.properties().get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_LIST_PROPERTY_KEY);
    String preloadMainlineVersion =
        testInfo.properties().get(XtsConstants.PRELOAD_MAINLINE_VERSION_TEST_PROPERTY_KEY);
    if (testListProperty != null && preloadMainlineVersion != null) {
      if (localFileUtil.isFileOrDirExist(testListProperty)) {
        // Make a copy of the test list file with the preload mainline version as the prefix.
        String testListPath =
            PathUtil.dirname(testListProperty)
                + "/"
                + preloadMainlineVersion
                + "_"
                + PathUtil.basename(testListProperty);
        localFileUtil.copyFileOrDir(testListProperty, testListPath);
        extraFilesOrDirsToZip.add(Path.of(testListPath));
      }
    }
  }

  private void generateInvocationSummaryFile(
      List<JobInfo> sessionAllJobs,
      Result result,
      Path resultDir,
      Path logDir,
      @Nullable Result previousResult,
      SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    if (!localFileUtil.isDirExist(resultDir)) {
      logger.atInfo().log(
          "Result dir [%s] doesn't exist, skip generating invocation summary file.", resultDir);
      return;
    }
    if (SessionHandlerHelper.useTfRetry(
        sessionRequestInfo.isAtsServerRequest(),
        sessionRequestInfo.xtsType(),
        sessionRequestInfo
            .testSuiteInfo()
            .map(testSuiteInfo -> testSuiteInfo.getTestSuiteVersion().orElse(null))
            .orElse(null))) {
      // For TF retry, the invocation summary file is generated by TF.
      return;
    }
    // Only create the invocation_summary.txt when the result dir has been created by the previous
    // result processing steps.
    String invocationSummaryFile =
        resultDir.resolve(XtsConstants.INVOCATION_SUMMARY_FILE_NAME).toAbsolutePath().toString();
    String xtsTestResultSummary =
        createXtsInvocationSummaryText(sessionAllJobs, result, resultDir, logDir, previousResult);
    if (localFileUtil.isFileExist(invocationSummaryFile)) {
      logger.atInfo().log(
          "Invocation summary file [%s] exists, overriding it.", invocationSummaryFile);
      localFileUtil.removeFileOrDir(invocationSummaryFile);
    }
    localFileUtil.writeToFile(
        invocationSummaryFile, String.format("TEXT:\n%s", xtsTestResultSummary));
  }

  /** Creates the xTS invocation summary text. */
  public String createXtsInvocationSummaryText(
      List<JobInfo> allJobs,
      @Nullable Result result,
      @Nullable Path resultDir,
      @Nullable Path logDir,
      @Nullable Result previousResult) {
    return String.format(
            "%s=========== Result/Log Location ============\n%s",
            suiteResultReporter.getSummary(result, previousResult), getNonTfModuleLogPath(allJobs))
        + (logDir != null && localFileUtil.isDirExist(logDir)
            ? String.format("LOG DIRECTORY               : %s\n", logDir)
            : "")
        + (resultDir != null && localFileUtil.isDirExist(resultDir)
            ? String.format("RESULT DIRECTORY            : %s\n", resultDir)
            : "")
        + "=================== End ====================\n";
  }

  private String getNonTfModuleLogPath(List<JobInfo> jobInfos) {
    ImmutableMap<String, String> failedNonTfTestLogDirs =
        jobInfos.stream()
            .filter(
                jobInfo ->
                    jobInfo.resultWithCause().get().type() == TestResult.FAIL
                        && jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)
                        && jobInfo.properties().has(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
            .flatMap(jobInfo -> jobInfo.tests().getAll().values().stream())
            .filter(
                testInfo ->
                    testInfo.properties().has(XtsConstants.XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY)
                        && localFileUtil.isDirExist(
                            testInfo
                                .properties()
                                .get(XtsConstants.XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY)))
            .collect(
                toImmutableMap(
                    testInfo -> getExpandedNonTfModuleId(testInfo.jobInfo()),
                    testInfo ->
                        testInfo.properties().get(XtsConstants.XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY),
                    // If there are multiple tests for a module, use the latest one.
                    (first, second) -> second));
    if (failedNonTfTestLogDirs.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("Failed standalone module log locations:\n");
    for (Entry<String, String> entry : failedNonTfTestLogDirs.entrySet()) {
      builder.append(String.format("%s\t: %s\n", entry.getKey(), entry.getValue()));
    }
    builder.append("============================================\n");
    return builder.toString();
  }

  /** Returns the expanded non-tradefed module id of the given non-tradefed job. */
  public static String getExpandedNonTfModuleId(JobInfo jobInfo) {
    String moduleName =
        jobInfo.properties().getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP).orElse("");
    String abi = jobInfo.properties().get(SessionHandlerHelper.XTS_MODULE_ABI_PROP);
    String parameter = jobInfo.properties().get(SessionHandlerHelper.XTS_MODULE_PARAMETER_PROP);
    if (!isNullOrEmpty(parameter)) {
      moduleName = String.format("%s[%s]", moduleName, parameter);
    }
    if (!isNullOrEmpty(abi)) {
      moduleName = AbiUtil.createId(abi, moduleName);
    }
    return moduleName;
  }

  /** Gets the log directory name for a test. */
  public static String getLogDirNameForTest(String prefix, String testId) {
    return prefix + "_test_" + testId;
  }

  /**
   * Preprocesses the {@code report} for creating the user-facing report.
   *
   * <p>Currently only supports changing module failure level in the report.
   */
  @VisibleForTesting
  Result preprocessReport(Result report, SessionRequestInfo sessionRequestInfo) {
    Result.Builder resultBuilder = report.toBuilder();
    ImmutableMap<String, CompatibilityReportFormat> moduleReportFormat =
        sessionRequestInfo.expandedModules().values().stream()
            .distinct()
            .map(CompatibilityReportFormat::fromModuleConfig)
            .flatMap(Optional::stream)
            .collect(
                toImmutableMap(
                    CompatibilityReportFormat::targetName,
                    Function.identity(),
                    (first, second) -> first));

    // Update each module's failed tests based on the module report format.
    for (Module.Builder moduleBuilder : resultBuilder.getModuleInfoBuilderList()) {
      String moduleName = moduleBuilder.getName();
      if (moduleReportFormat.containsKey(moduleName)) {
        moduleReportFormat.get(moduleBuilder.getName()).applyToModule(moduleBuilder);
      }
    }

    // Update the Summary element's passed, failed and warning attributes.
    long passedCount =
        resultBuilder.getModuleInfoList().stream().map(Module::getPassed).mapToLong(x -> x).sum();
    long warningCount =
        resultBuilder.getModuleInfoList().stream()
            .map(Module::getWarningTests)
            .mapToLong(x -> x)
            .sum();
    long failedCount =
        resultBuilder.getModuleInfoList().stream()
            .map(Module::getFailedTests)
            .mapToLong(x -> x)
            .sum();
    resultBuilder
        .getSummaryBuilder()
        .setPassed(passedCount)
        .setFailed(failedCount)
        .setWarning(warningCount);

    return resultBuilder.build();
  }
}
