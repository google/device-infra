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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.TradefedResultBundle;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessCallable;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Helper class for ATS applications to create job config. */
public class SessionResultHandlerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> MOBLY_TEST_RESULT_FILE_NAMES =
      ImmutableSet.of(
          "test_summary.yaml",
          "device_build_fingerprint.txt",
          "mobly_run_build_attributes.textproto",
          "mobly_run_result_attributes.textproto",
          "ats_module_run_result.textproto");

  private static final ImmutableSet<String> EXCLUDED_TF_GEN_RESULT_FILES =
      ImmutableSet.of(
          "checksum-suite.data",
          "compatibility_result.css",
          "compatibility_result.xsl",
          "logo.png",
          "test_result.html",
          "test_result.xml",
          "test_result_failures_suite.html");
  private static final ImmutableSet<String> EXCLUDED_TF_GEN_RESULT_DIRS =
      ImmutableSet.of("proto", "module_reports");

  private final LocalFileUtil localFileUtil;
  private final CompatibilityReportMerger compatibilityReportMerger;
  private final CompatibilityReportCreator reportCreator;
  private final RetryReportMerger retryReportMerger;
  private final SessionInfo sessionInfo;

  @Inject
  SessionResultHandlerUtil(
      LocalFileUtil localFileUtil,
      CompatibilityReportMerger compatibilityReportMerger,
      CompatibilityReportCreator reportCreator,
      RetryReportMerger retryReportMerger,
      SessionInfo sessionInfo) {
    this.localFileUtil = localFileUtil;
    this.compatibilityReportMerger = compatibilityReportMerger;
    this.reportCreator = reportCreator;
    this.retryReportMerger = retryReportMerger;
    this.sessionInfo = sessionInfo;
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
    ImmutableMap<JobInfo, Optional<TestInfo>> tradefedTests =
        jobs.stream()
            .filter(jobInfo -> jobInfo.properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false))
            .collect(
                toImmutableMap(
                    Function.identity(),
                    jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

    ImmutableMap<JobInfo, Optional<TestInfo>> nonTradefedTests =
        jobs.stream()
            .filter(jobInfo -> jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false))
            .collect(
                toImmutableMap(
                    Function.identity(),
                    jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

    Path nonTradefedTestResultsDir = resultDir.resolve("non-tradefed_results");
    Path nonTradefedTestLogsDir = logDir.resolve("non-tradefed_logs");
    Path serverSessionLogsDir = logDir.resolve("olc_server_session_logs");
    Path tmpTradefedTestResultsDir = null;
    try {
      localFileUtil.prepareDir(logDir);
      localFileUtil.prepareDir(resultDir);
      tmpTradefedTestResultsDir =
          Path.of(localFileUtil.createTempDir(Flags.instance().tmpDirRoot.getNonNull()));
      if (sessionRequestInfo == null) {
        return Optional.empty();
      }
      return processResultHelper(
          sessionRequestInfo,
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
    }
  }

  private Optional<Result> processResultHelper(
      SessionRequestInfo sessionRequestInfo,
      ImmutableMap<JobInfo, Optional<TestInfo>> tradefedTests,
      ImmutableMap<JobInfo, Optional<TestInfo>> nonTradefedTests,
      Path resultDir,
      Path logsRootDir,
      Path nonTradefedTestLogsDir,
      Path tmpTradefedTestResultsDir,
      Path nonTradefedTestResultsDir)
      throws MobileHarnessException, InterruptedException {
    Result finalReport = null;
    ImmutableList.Builder<TradefedResultBundle> tradefedResultBundlesBuilder =
        ImmutableList.builder();
    // Copies tradefed test relevant log and result files to dedicated locations
    for (Entry<JobInfo, Optional<TestInfo>> testEntry : tradefedTests.entrySet()) {
      if (testEntry.getValue().isEmpty()) {
        logger.atInfo().log(
            "Found no test in tradefed job [%s], skip it.", testEntry.getKey().locator().getId());
        continue;
      }
      TestInfo test = testEntry.getValue().get();

      copyTradefedTestLogFiles(test, logsRootDir);
      Optional<TradefedResultBundle> bundle =
          copyTradefedTestResultFiles(test, tmpTradefedTestResultsDir, resultDir);
      bundle.ifPresent(tradefedResultBundlesBuilder::add);
    }
    ImmutableList<TradefedResultBundle> tradefedResultBundles =
        tradefedResultBundlesBuilder.build();

    List<MoblyReportInfo> moblyReportInfos = new ArrayList<>();
    // Copies non-tradefed test relevant log and result files to dedicated locations
    for (Entry<JobInfo, Optional<TestInfo>> testEntry : nonTradefedTests.entrySet()) {
      if (testEntry.getValue().isEmpty()) {
        logger.atInfo().log(
            "Found no test in non-tradefed job [%s], skip it.",
            testEntry.getKey().locator().getId());
        continue;
      }
      TestInfo test = testEntry.getValue().get();

      copyNonTradefedTestLogFiles(test, nonTradefedTestLogsDir);
      if (!test.jobInfo()
          .properties()
          .getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS)
          .orElse(false)) {
        Optional<NonTradefedTestResult> nonTradefedTestResult =
            copyNonTradefedTestResultFiles(
                test,
                nonTradefedTestResultsDir,
                testEntry.getKey().properties().get(SessionHandlerHelper.XTS_MODULE_NAME_PROP),
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
                        res.testSummaryFile().orElse(null),
                        res.resultAttributesFile(),
                        res.deviceBuildFingerprint(),
                        res.buildAttributesFile(),
                        res.moduleResultFile())));
      }
    }

    Optional<Result> mergedTradefedReport = Optional.empty();
    if (!tradefedResultBundles.isEmpty()) {
      mergedTradefedReport = compatibilityReportMerger.mergeResultBundles(tradefedResultBundles);
    }

    Optional<Result> mergedNonTradefedReport = Optional.empty();
    if (!moblyReportInfos.isEmpty()) {
      mergedNonTradefedReport = compatibilityReportMerger.mergeMoblyReports(moblyReportInfos);
    }

    List<Result> reportList = new ArrayList<>();
    mergedTradefedReport.ifPresent(reportList::add);
    mergedNonTradefedReport.ifPresent(reportList::add);

    Optional<Result> mergedReport =
        compatibilityReportMerger.mergeReports(reportList, /* validateReports= */ true);
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
        SubPlan subPlan =
            SessionHandlerHelper.loadSubPlan(
                Path.of(sessionRequestInfo.xtsRootDir()),
                sessionRequestInfo.xtsType(),
                sessionRequestInfo.subPlanName().get());
        includeFilters.addAll(subPlan.getAllIncludeFilters());
        excludeFilters.addAll(subPlan.getAllExcludeFilters());
      }
      finalReport =
          finalReportBuilder
              .addAllIncludeFilter(includeFilters.build())
              .addAllExcludeFilter(excludeFilters.build())
              .build();
      reportCreator.createReport(finalReport, resultDir, null, sessionRequestInfo.htmlInZip());
    } else if (isRunRetry) {
      if (sessionRequestInfo.retrySessionId().isPresent()) {
        finalReport =
            retryReportMerger.mergeReports(
                Path.of(sessionRequestInfo.retryResultDir().orElseThrow()),
                sessionRequestInfo.retrySessionId().get(),
                sessionRequestInfo.retryType().orElse(null),
                mergedReport.orElse(null),
                sessionRequestInfo.moduleNames());
      } else {
        int previousSessionIndex =
            sessionRequestInfo
                .retrySessionIndex()
                .orElseThrow(
                    () ->
                        new MobileHarnessException(
                            InfraErrorId.ATSC_RUN_RETRY_COMMAND_MISSING_SESSION_INDEX_ERROR,
                            "Missing session index for retry"));
        finalReport =
            retryReportMerger.mergeReports(
                XtsDirUtil.getXtsResultsDir(
                    Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType()),
                previousSessionIndex,
                sessionRequestInfo.retryType().orElse(null),
                mergedReport.orElse(null),
                sessionRequestInfo.moduleNames());
      }
      reportCreator.createReport(finalReport, resultDir, null, sessionRequestInfo.htmlInZip());
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
   * .../android-<xts>/logs/YYYY.MM.DD_HH.mm.ss/
   *    tradefed_logs/
   *      <driver>_test_<test_id>/
   *        command_history.txt
   *        xts_tf_output.log
   *        raw_tradefed_log/
   *    non-tradefed_logs/
   *      <driver>_test_<test_id>/
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
    Path invocationDir;
    if (tradefedTestInfo.properties().has(XtsConstants.TRADEFED_INVOCATION_DIR_NAME)) {
      invocationDir =
          logRootDir.resolve(
              tradefedTestInfo.properties().get(XtsConstants.TRADEFED_INVOCATION_DIR_NAME));
    } else {
      invocationDir =
          localFileUtil.createTempDir(logRootDir, XtsConstants.TRADEFED_INVOCATION_DIR_NAME_PREFIX);
    }
    Path testLogDir = prepareLogOrResultDirForTest(tradefedTestInfo, invocationDir);

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
   * .../android-<xts>/results/YYYY.MM.DD_HH.mm.ss/
   *    the merged report relevant files (test_result.xml, html, checksum-suite.data, etc)
   *    config/
   *    device-info-files/
   *    invocation_summary.txt
   *    report-log-files/
   *    vintf-files/
   *    non-tradefed_results/
   *      <driver>_test_<test_id>/
   *        test_summary.yaml
   *        mobly_run_build_attributes.textproto
   *        mobly_run_result_attributes.textproto
   *        ...
   * </pre>
   *
   * @return the path to the tradefed test result xml file if any
   */
  @CanIgnoreReturnValue
  private Optional<TradefedResultBundle> copyTradefedTestResultFiles(
      TestInfo tradefedTestInfo, Path tmpResultDir, Path resultDirInZip)
      throws MobileHarnessException, InterruptedException {
    Path tmpTestResultDir = prepareLogOrResultDirForTest(tradefedTestInfo, tmpResultDir);
    localFileUtil.prepareDir(resultDirInZip);
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
            logger.atInfo().log("Not found TF result dir in dir %s", genFileResultsDir);
            continue;
          }
          List<Path> resultFilesOrDirs =
              localFileUtil.listFilesOrDirs(tfGenResultDir.get(), path -> true);
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

    List<Path> testResultXmlFiles =
        localFileUtil.listFilePaths(tmpTestResultDir, /* recursively= */ true);
    Path testResultXmlFile =
        testResultXmlFiles.stream()
            .filter(
                file ->
                    file.getFileName()
                        .toString()
                        .equals(SessionHandlerHelper.TEST_RESULT_XML_FILE_NAME))
            .findFirst()
            .orElse(null);
    Optional<Path> testRecordFile =
        testResultXmlFiles.stream()
            .filter(
                file ->
                    file.getFileName()
                        .toString()
                        .equals(SessionHandlerHelper.TEST_RECORD_PROTOBUFFER_FILE_NAME))
            .findFirst();
    return Optional.ofNullable(testResultXmlFile)
        .map(value -> TradefedResultBundle.of(value, testRecordFile));
  }

  /**
   * Copies non-tradefed test relevant log files to directory {@code logDir} for the given
   * non-tradefed test.
   *
   * <p>The destination log files structure looks like:
   *
   * <pre>
   * .../android-<xts>/logs/YYYY.MM.DD_HH.mm.ss/
   *    tradefed_logs/
   *      <driver>_test_<test_id>/
   *        command_history.txt
   *        xts_tf_output.log
   *        raw_tradefed_log/
   *    non-tradefed_logs/
   *      <driver>_test_<test_id>/
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
   * .../android-<xts>/results/YYYY.MM.DD_HH.mm.ss/
   *    the merged report relevant files (test_result.xml, html, checksum-suite.data, etc)
   *    tradefed_results/
   *      <driver>_test_<test_id>/
   *        test_result.xml
   *        test_result.html
   *        ...
   *    non-tradefed_results/
   *      <driver>_test_<test_id>/
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
      Path resultDir,
      String moduleName,
      @Nullable String moduleAbi,
      @Nullable String moduleParameter)
      throws MobileHarnessException, InterruptedException {
    Path testResultDir = prepareLogOrResultDirForTest(nonTradefedTestInfo, resultDir);

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

    Optional<String> labGenFileDir = getLabGenFileDir(nonTradefedTestInfo);
    if (labGenFileDir.isPresent() && !labGenFileDir.get().equals(testGenFileDir)) {
      moblyTestResultFiles.addAll(
          localFileUtil.listFilePaths(
              Path.of(labGenFileDir.get()),
              /* recursively= */ true,
              path -> MOBLY_TEST_RESULT_FILE_NAMES.contains(path.getFileName().toString())));
    }

    for (Path moblyTestResultFile : moblyTestResultFiles) {
      logger.atInfo().log(
          "Copying non-tradefed test result relevant file [%s] into dir [%s]",
          moblyTestResultFile, testResultDir);
      localFileUtil.copyFileOrDirWithOverridingCopyOptions(
          moblyTestResultFile, testResultDir, ImmutableList.of("-rf"));
      updateNonTradefedTestResult(
          nonTradefedTestResultBuilder,
          moblyTestResultFile.getFileName().toString(),
          moblyTestResultFile);
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

  private Path prepareLogOrResultDirForTest(TestInfo test, Path parentDir)
      throws MobileHarnessException {
    Path targetDir =
        parentDir.resolve(
            String.format("%s_test_%s", test.jobInfo().type().getDriver(), test.locator().getId()));
    localFileUtil.prepareDir(targetDir);
    return targetDir;
  }

  private void updateNonTradefedTestResult(
      NonTradefedTestResult.Builder resultBuilder, String fileName, Path filePath)
      throws MobileHarnessException {
    switch (fileName) {
      case "test_summary.yaml":
        resultBuilder.setTestSummaryFile(filePath);
        break;
      case "device_build_fingerprint.txt":
        resultBuilder.setDeviceBuildFingerprint(localFileUtil.readFile(filePath).trim());
        break;
      case "mobly_run_result_attributes.textproto":
        resultBuilder.setResultAttributesFile(filePath);
        break;
      case "mobly_run_build_attributes.textproto":
        resultBuilder.setBuildAttributesFile(filePath);
        break;
      case "ats_module_run_result.textproto":
        resultBuilder.setModuleResultFile(filePath);
        break;
      default:
        break;
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
    public abstract String deviceBuildFingerprint();

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
    public abstract Path resultAttributesFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.AttributeList}
     * which will be set in the {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo}.{@code
     * attribute}.
     */
    public abstract Path buildAttributesFile();

    /**
     * The path of the text proto file that stores {@link
     * com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult}
     * which is used as a backup result from ATS if the Mobly result file wasn't created.
     */
    public abstract Path moduleResultFile();

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

  public boolean isSessionPassed(List<JobInfo> jobInfos) {
    if (jobInfos.isEmpty()) {
      return false;
    }
    for (JobInfo jobInfo : jobInfos) {
      // Tradefed Jobs.
      if (jobInfo.properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false)) {
        for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
          if (!testInfo.properties().has(XtsConstants.TRADEFED_JOBS_PASSED)) {
            return false;
          }
        }
      }
      // Non Tradefed Jobs.
      if (jobInfo.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)) {
        for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
          if (!testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_JOBS_PASSED)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
