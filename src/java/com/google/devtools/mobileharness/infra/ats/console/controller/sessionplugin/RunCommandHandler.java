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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";
  @VisibleForTesting static final String XTS_NON_TF_JOB_PROP = "xts-non-tradefed-job";
  @VisibleForTesting static final String XTS_MODULE_NAME_PROP = "xts-module-name";
  private static final String TEST_RESULT_XML_FILE_NAME = "test_result.xml";
  private static final ImmutableSet<String> MOBLY_TEST_RESULT_FILE_NAMES =
      ImmutableSet.of(
          "test_summary.yaml",
          "device_build_fingerprint.txt",
          "mobly_run_build_attributes.textproto",
          "mobly_run_result_attributes.textproto");

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;
  private final ConfigurationUtil configurationUtil;
  private final CompatibilityReportMerger compatibilityReportMerger;
  private final CompatibilityReportCreator reportCreator;

  @Inject
  RunCommandHandler(
      LocalFileUtil localFileUtil,
      ModuleConfigurationHelper moduleConfigurationHelper,
      ConfigurationUtil configurationUtil,
      CompatibilityReportMerger compatibilityReportMerger,
      CompatibilityReportCreator reportCreator,
      SessionRequestHandlerUtil sessionRequestHandlerUtil) {
    this.localFileUtil = localFileUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
    this.configurationUtil = configurationUtil;
    this.compatibilityReportMerger = compatibilityReportMerger;
    this.reportCreator = reportCreator;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
  }

  /**
   * Creates tradefed jobs based on the {@code command} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * <p>Jobs added to the session by the plugin will be started by the session job runner later.
   *
   * @return a list of added tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addTradefedJobs(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfo =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(generateSessionRequestInfo(command));
    if (jobInfo.isEmpty()) {
      logger.atInfo().log(
          "No tradefed jobs created, double check device availability. The run command -> %s",
          shortDebugString(command));
      return ImmutableList.of();
    }
    jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
    sessionInfo.addJob(jobInfo.get());
    String jobId = jobInfo.get().locator().getId();
    logger.atInfo().log(
        "Added tradefed job[%s] to the session %s", jobId, sessionInfo.getSessionId());
    return ImmutableList.of(jobId);
  }

  /**
   * Creates non-tradefed jobs based on the {@code runCommand} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * @return a list of added non-tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addNonTradefedJobs(RunCommand runCommand, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfos = createXtsNonTradefedJobs(runCommand);
    if (jobInfos.isEmpty()) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The run command -> %s",
          shortDebugString(runCommand));

      return ImmutableList.of();
    }
    ImmutableList.Builder<String> nonTradefedJobIds = ImmutableList.builder();
    jobInfos.forEach(
        jobInfo -> {
          sessionInfo.addJob(jobInfo);
          nonTradefedJobIds.add(jobInfo.locator().getId());
          logger.atInfo().log(
              "Added non-tradefed job[%s] to the session %s",
              jobInfo.locator().getId(), sessionInfo.getSessionId());
        });
    return nonTradefedJobIds.build();
  }

  @VisibleForTesting
  ImmutableList<JobInfo> createXtsNonTradefedJobs(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    String testPlan = runCommand.getTestPlan();
    // Currently only support CTS
    if (!testPlan.equals("cts")) {
      return ImmutableList.of();
    }

    String xtsRootDir = runCommand.getXtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating non-tradefed jobs.", xtsRootDir);
      return ImmutableList.of();
    }

    XtsType xtsType = runCommand.getXtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsV2FromDirs(
            ImmutableList.of(getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));

    List<String> modules = runCommand.getModuleNameList();
    ImmutableSet<String> allNonTfModules =
        configsMap.values().stream()
            .map(config -> config.getMetadata().getXtsModule())
            .collect(toImmutableSet());
    ImmutableSet<String> givenMatchedNonTfModules =
        modules.stream().filter(allNonTfModules::contains).collect(toImmutableSet());
    boolean noGivenModuleForNonTf = !modules.isEmpty() && givenMatchedNonTfModules.isEmpty();
    if (noGivenModuleForNonTf) {
      logger.atInfo().log(
          "Skip creating non-tradefed jobs as none of given modules is for non-tradefed module: %s",
          modules);
      return ImmutableList.of();
    }

    ImmutableList.Builder<JobInfo> jobInfos = ImmutableList.builder();

    List<String> androidDeviceSerials = runCommand.getDeviceSerialList();

    for (Map.Entry<String, Configuration> entry : configsMap.entrySet()) {
      String configModuleName = entry.getValue().getMetadata().getXtsModule();
      if (givenMatchedNonTfModules.isEmpty()
          || givenMatchedNonTfModules.contains(configModuleName)) {
        Optional<JobInfo> jobInfoOpt =
            createXtsNonTradefedJob(
                Path.of(xtsRootDir), xtsType, testPlan, Path.of(entry.getKey()), entry.getValue());
        if (jobInfoOpt.isPresent()) {
          JobInfo jobInfo = jobInfoOpt.get();
          if (!androidDeviceSerials.isEmpty()) {
            jobInfo
                .subDeviceSpecs()
                .getAllSubDevices()
                .forEach(
                    subDeviceSpec -> {
                      if (!subDeviceSpec.type().equals(ANDROID_REAL_DEVICE_TYPE)) {
                        return;
                      }
                      subDeviceSpec
                          .deviceRequirement()
                          .dimensions()
                          .add(
                              "serial",
                              String.format(
                                  "regex:(%s)", Joiner.on('|').join(androidDeviceSerials)));
                    });
          }
          jobInfos.add(jobInfo);
        }
      }
    }

    return jobInfos.build();
  }

  private Optional<JobInfo> createXtsNonTradefedJob(
      Path xtsRootDir,
      XtsType xtsType,
      String testPlan,
      Path moduleConfigPath,
      Configuration moduleConfig)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfoOpt = createBaseXtsNonTradefedJob(moduleConfig);
    if (jobInfoOpt.isEmpty()) {
      return Optional.empty();
    }

    ImmutableList<File> fileDepDirs =
        ImmutableList.of(
            moduleConfigPath.getParent().toFile(),
            getXtsTestCasesDir(xtsRootDir, xtsType).toFile());

    JobInfo jobInfo = jobInfoOpt.get();
    moduleConfigurationHelper.updateJobInfo(jobInfo, moduleConfig, fileDepDirs);
    jobInfo.properties().add(XTS_NON_TF_JOB_PROP, "true");
    jobInfo.properties().add(XTS_MODULE_NAME_PROP, moduleConfig.getMetadata().getXtsModule());
    jobInfo.params().add("xts_test_plan", testPlan);
    return Optional.of(jobInfo);
  }

  private Optional<JobInfo> createBaseXtsNonTradefedJob(Configuration moduleConfig)
      throws MobileHarnessException, InterruptedException {
    String xtsModule = moduleConfig.getMetadata().getXtsModule();
    List<Device> moduleDevices = moduleConfig.getDevicesList();
    if (moduleDevices.isEmpty()) {
      logger.atInfo().log(
          "Found no devices to create the job config for xts non-tradefed job with module %s.",
          xtsModule);
      return Optional.empty();
    }

    List<SubDeviceSpec> subDeviceSpecList = new ArrayList<>();
    for (Device device : moduleDevices) {
      if (device.getName().isEmpty()) {
        logger.atWarning().log("Device name is missing in a <device> in module %s", xtsModule);
        return Optional.empty();
      } else {
        subDeviceSpecList.add(SubDeviceSpec.newBuilder().setType(device.getName()).build());
      }
    }

    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName(String.format("xts-mobly-aosp-package-job-%s", xtsModule))
            .setExecMode("local")
            .setJobTimeoutSec(5 * 24 * 60 * 60)
            .setTestTimeoutSec(5 * 24 * 60 * 60)
            .setStartTimeoutSec(1 * 60 * 60)
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-mobly-aosp-package-test-%s", xtsModule)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));
    jobConfigBuilder.setDriver(Driver.newBuilder().setName("MoblyAospPackageTest"));
    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log(
        "Non-tradefed job base config for module %s: %s", xtsModule, shortDebugString(jobConfig));

    return Optional.of(JobInfoCreator.createJobInfo(jobConfig, ImmutableList.of(), null));
  }

  /**
   * Copies xTS tradefed and non-tradefed generated logs/results into proper locations within the
   * given xts root dir.
   */
  void handleResultProcessing(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }

      Path xtsRootDir = Path.of(command.getXtsRootDir());

      ImmutableMap<JobInfo, Optional<TestInfo>> tradefedTests =
          sessionInfo.getAllJobs().stream()
              .filter(jobInfo -> jobInfo.properties().has(XTS_TF_JOB_PROP))
              .collect(
                  toImmutableMap(
                      Function.identity(),
                      jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

      ImmutableMap<JobInfo, Optional<TestInfo>> nonTradefedTests =
          sessionInfo.getAllJobs().stream()
              .filter(jobInfo -> jobInfo.properties().has(XTS_NON_TF_JOB_PROP))
              .collect(
                  toImmutableMap(
                      Function.identity(),
                      jobInfo -> jobInfo.tests().getAll().values().stream().findFirst()));

      XtsType xtsType = command.getXtsType();
      String timestampDirName = getTimestampDirName();
      Path resultDir = getResultDir(xtsRootDir, xtsType, timestampDirName);
      Path tradefedTestResultsDir = resultDir.resolve("tradefed_results");
      Path nonTradefedTestResultsDir = resultDir.resolve("non-tradefed_results");
      Path logDir = getLogDir(xtsRootDir, xtsType, timestampDirName);
      Path tradefedTestLogsDir = logDir.resolve("tradefed_logs");
      Path nonTradefedTestLogsDir = logDir.resolve("non-tradefed_logs");

      List<Path> tradefedTestResultXmlFiles = new ArrayList<>();
      // Copies tradefed test relevant log and result files to dedicated locations
      for (Entry<JobInfo, Optional<TestInfo>> testEntry : tradefedTests.entrySet()) {
        if (testEntry.getValue().isEmpty()) {
          logger.atInfo().log(
              "Found no test in tradefed job [%s], skip it.", testEntry.getKey().locator().getId());
          continue;
        }

        TestInfo test = testEntry.getValue().get();

        copyTradefedTestLogFiles(test, tradefedTestLogsDir);
        Optional<Path> tradefedTestResultXmlFile =
            copyTradefedTestResultFiles(test, tradefedTestResultsDir);
        tradefedTestResultXmlFile.ifPresent(tradefedTestResultXmlFiles::add);
      }

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
        Optional<NonTradefedTestResult> nonTradefedTestResult =
            copyNonTradefedTestResultFiles(
                test,
                nonTradefedTestResultsDir,
                testEntry.getKey().properties().get(XTS_MODULE_NAME_PROP));
        nonTradefedTestResult.ifPresent(
            res ->
                moblyReportInfos.add(
                    MoblyReportInfo.of(
                        res.moduleName(),
                        res.testSummaryFile(),
                        res.resultAttributesFile(),
                        res.deviceBuildFingerprint(),
                        res.buildAttributesFile())));
      }

      Optional<Result> mergedTradefedReport = Optional.empty();
      if (!tradefedTestResultXmlFiles.isEmpty()) {
        mergedTradefedReport =
            compatibilityReportMerger.mergeXmlReports(tradefedTestResultXmlFiles);
      }

      Optional<Result> mergedNonTradefedReport = Optional.empty();
      if (!moblyReportInfos.isEmpty()) {
        mergedNonTradefedReport = compatibilityReportMerger.mergeMoblyReports(moblyReportInfos);
      }

      List<Result> reportList = new ArrayList<>();
      mergedTradefedReport.ifPresent(reportList::add);
      mergedNonTradefedReport.ifPresent(reportList::add);

      Optional<Result> finalReport =
          compatibilityReportMerger.mergeReports(reportList, /* validateReports= */ true);
      if (finalReport.isEmpty()) {
        logger.atWarning().log("Failed to merge reports.");
      } else {
        reportCreator.createReport(finalReport.get(), resultDir, null);
      }
    } finally {
      cleanUpJobGenDirs(sessionInfo.getAllJobs());
      sessionInfo.setSessionPluginOutput(
          oldOutput ->
              (oldOutput == null ? AtsSessionPluginOutput.newBuilder() : oldOutput.toBuilder())
                  .setSuccess(
                      Success.newBuilder()
                          .setOutputMessage(
                              String.format(
                                  "run_command session [%s] ended", sessionInfo.getSessionId())))
                  .build(),
          AtsSessionPluginOutput.class);
    }
  }

  private SessionRequestHandlerUtil.SessionRequestInfo generateSessionRequestInfo(
      RunCommand runCommand) {
    SessionRequestHandlerUtil.SessionRequestInfo.Builder builder =
        SessionRequestHandlerUtil.SessionRequestInfo.builder()
            .setTestPlan(runCommand.getTestPlan())
            .setXtsRootDir(runCommand.getXtsRootDir())
            .setXtsType(runCommand.getXtsType());

    builder.setDeviceSerials(runCommand.getDeviceSerialList());
    builder.setModuleNames(runCommand.getModuleNameList());
    builder.setExtraArgs(runCommand.getExtraArgList());

    if (runCommand.hasShardCount()) {
      builder.setShardCount(runCommand.getShardCount());
    }
    if (runCommand.hasPythonPkgIndexUrl()) {
      builder.setPythonPkgIndexUrl(runCommand.getPythonPkgIndexUrl());
    }
    return builder.build();
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
   *        mobly_run_result_attrubutes.textproto
   *        ...
   *        raw_mobly_logs/
   * </pre>
   */
  private void copyTradefedTestLogFiles(TestInfo tradefedTestInfo, Path logDir)
      throws MobileHarnessException, InterruptedException {
    Path testLogDir = prepareLogOrResultDirForTest(tradefedTestInfo, logDir);

    String testGenFileDir = tradefedTestInfo.getGenFileDir();
    List<Path> genFiles = localFileUtil.listFilesOrDirs(Paths.get(testGenFileDir), path -> true);

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
                logger.atInfo().log(
                    "Copying tradefed test log relevant file/dir [%s] into dir [%s]",
                    logFileOrDir, testLogDir);
                localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                    logFileOrDir, testLogDir, ImmutableList.of("-rf"));
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
   * Copies tradefed test relevant result files to directory {@code resultDir} for the given
   * tradefed test.
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
   * @return the path to the tradefed test result xml file if any
   */
  @CanIgnoreReturnValue
  private Optional<Path> copyTradefedTestResultFiles(TestInfo tradefedTestInfo, Path resultDir)
      throws MobileHarnessException, InterruptedException {
    Path testResultDir = prepareLogOrResultDirForTest(tradefedTestInfo, resultDir);

    String testGenFileDir = tradefedTestInfo.getGenFileDir();
    List<Path> genFiles = localFileUtil.listFilesOrDirs(Paths.get(testGenFileDir), path -> true);

    for (Path genFile : genFiles) {
      if (genFile.getFileName().toString().endsWith("gen-files")) {
        Path genFileResultsDir = genFile.resolve("results");
        if (genFileResultsDir.toFile().exists()) {
          List<Path> resultsSubFilesOrDirs =
              localFileUtil.listFilesOrDirs(
                  genFileResultsDir,
                  filePath -> !filePath.getFileName().toString().equals("latest"));
          for (Path resultsSubFileOrDir : resultsSubFilesOrDirs) {
            if (resultsSubFileOrDir.toFile().isDirectory()) {
              // If it's a dir, copy its content into the new result dir.
              List<Path> resultFilesOrDirs =
                  localFileUtil.listFilesOrDirs(resultsSubFileOrDir, path -> true);
              for (Path resultFileOrDir : resultFilesOrDirs) {
                logger.atInfo().log(
                    "Copying tradefed test result relevant file/dir [%s] into dir [%s]",
                    resultFileOrDir, testResultDir);
                localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                    resultFileOrDir, testResultDir, ImmutableList.of("-rf"));
              }
            }
          }
        }
      }
    }

    List<Path> testResultXmlFiles =
        localFileUtil.listFilePaths(
            testResultDir,
            /* recursively= */ false,
            path -> path.getFileName().toString().equals(TEST_RESULT_XML_FILE_NAME));

    return testResultXmlFiles.stream().findFirst();
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
   *        mobly_run_result_attrubutes.textproto
   *        ...
   *        raw_mobly_logs/
   * </pre>
   */
  private void copyNonTradefedTestLogFiles(TestInfo nonTradefedTestInfo, Path logDir)
      throws MobileHarnessException, InterruptedException {
    Path testLogDir = prepareLogOrResultDirForTest(nonTradefedTestInfo, logDir);

    String testGenFileDir = nonTradefedTestInfo.getGenFileDir();
    List<Path> genFiles = localFileUtil.listFilesOrDirs(Paths.get(testGenFileDir), path -> true);

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
      TestInfo nonTradefedTestInfo, Path resultDir, String moduleName)
      throws MobileHarnessException, InterruptedException {
    Path testResultDir = prepareLogOrResultDirForTest(nonTradefedTestInfo, resultDir);

    NonTradefedTestResult.Builder nonTradefedTestResultBuilder =
        NonTradefedTestResult.builder().setModuleName(moduleName);
    String testGenFileDir = nonTradefedTestInfo.getGenFileDir();
    List<Path> moblyTestResultFiles =
        localFileUtil.listFilePaths(
            Paths.get(testGenFileDir),
            /* recursively= */ true,
            path -> MOBLY_TEST_RESULT_FILE_NAMES.contains(path.getFileName().toString()));

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
      default:
        break;
    }
  }

  private Path getResultDir(Path xtsRootDir, XtsType xtsType, String timestampDirName) {
    return getXtsResultsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  private Path getLogDir(Path xtsRootDir, XtsType xtsType, String timestampDirName) {
    return getXtsLogsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  @VisibleForTesting
  String getTimestampDirName() {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(Clock.systemUTC().millis()));
  }

  private Path getXtsResultsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/results", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsLogsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/logs", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsTestCasesDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/testcases", Ascii.toLowerCase(xtsType.name())));
  }

  @VisibleForTesting
  void cleanUpJobGenDirs(List<JobInfo> jobInfoList)
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

  /** Data class for the non-tradefed test result. */
  @AutoValue
  public abstract static class NonTradefedTestResult {

    /** The xTS module name. */
    public abstract String moduleName();

    /**
     * The build fingerprint for the major device on which the test run, it's used to identify the
     * generated report.
     */
    public abstract String deviceBuildFingerprint();

    /** The path of the test summary file being parsed. */
    public abstract Path testSummaryFile();

    /**
     * The path of the text proto file that stores {@link AttributeList} which will be set in the
     * {@link Result}.{@code attribute}.
     */
    public abstract Path resultAttributesFile();

    /**
     * The path of the text proto file that stores {@link AttributeList} which will be set in the
     * {@link BuildInfo}.{@code attribute}.
     */
    public abstract Path buildAttributesFile();

    public static Builder builder() {
      return new AutoValue_RunCommandHandler_NonTradefedTestResult.Builder();
    }

    /** Builder for {@link NonTradefedTestResult}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setModuleName(String moduleName);

      public abstract Builder setDeviceBuildFingerprint(String deviceBuildFingerprint);

      public abstract Builder setTestSummaryFile(Path testSummaryFile);

      public abstract Builder setResultAttributesFile(Path resultAttributesFile);

      public abstract Builder setBuildAttributesFile(Path buildAttributesFile);

      public abstract NonTradefedTestResult build();
    }
  }
}
