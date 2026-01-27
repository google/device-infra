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

import static com.google.common.base.Ascii.toUpperCase;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DeviceType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.util.verifier.VerifierResultHelper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteInfoProvider;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME = "timestamp_dir_name";
  private static final DateTimeFormatter TIMESTAMP_DIR_NAME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm.ss.SSS").withZone(ZoneId.systemDefault());

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final SessionResultHandlerUtil sessionResultHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final SessionInfo sessionInfo;
  private final XtsJobCreator xtsJobCreator;
  private final VerifierResultHelper verifierResultHelper;

  /** Set in {@link #initialize}. */
  private volatile boolean initialized;

  /** Set in {@link #initialize}. Present if {@link #initialized} is true. */
  private volatile SessionRequestInfo sessionRequestInfo;

  @Inject
  RunCommandHandler(
      LocalFileUtil localFileUtil,
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      SessionResultHandlerUtil sessionResultHandlerUtil,
      SessionInfo sessionInfo,
      XtsJobCreator xtsJobCreator,
      VerifierResultHelper verifierResultHelper) {
    this.localFileUtil = localFileUtil;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.sessionResultHandlerUtil = sessionResultHandlerUtil;
    this.sessionInfo = sessionInfo;
    this.xtsJobCreator = xtsJobCreator;
    this.verifierResultHelper = verifierResultHelper;
  }

  void initialize(RunCommand command) throws MobileHarnessException, InterruptedException {
    sessionInfo.putSessionProperty(
        SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME,
        TIMESTAMP_DIR_NAME_FORMATTER.format(Instant.now()) + "_" + getRandom4Digits());
    sessionRequestInfo = generateSessionRequestInfo(command);
    initialized = true;
  }

  private static int getRandom4Digits() {
    return ThreadLocalRandom.current().nextInt(1000, 10000);
  }

  /**
   * Creates tradefed jobs based on the {@code command} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * <p>Jobs added to the session by the plugin will be started by the session job runner later.
   *
   * @return a list of {@code JobInfo} to be started
   */
  ImmutableList<JobInfo> createTradefedJobs(RunCommand command)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfoList = xtsJobCreator.createXtsTradefedTestJob(sessionRequestInfo);
    if (jobInfoList.isEmpty()) {
      logger.atInfo().log(
          "No tradefed jobs created, double check device availability. The run command -> %s",
          shortDebugString(command));
      return ImmutableList.of();
    }

    Path xtsLogsDir =
        XtsDirUtil.getXtsLogsDir(Path.of(command.getXtsRootDir()), command.getXtsType())
            .resolve(
                sessionInfo
                    .getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME)
                    .orElseThrow());
    boolean disableTfResultLog = Flags.instance().xtsDisableTfResultLog.getNonNull();
    jobInfoList.forEach(
        jobInfo -> {
          jobInfo.params().add("xts_log_root_path", xtsLogsDir.toString());
          jobInfo.params().add("disable_tf_result_log", Boolean.toString(disableTfResultLog));
        });

    return jobInfoList;
  }

  /**
   * Creates non-tradefed jobs based on the {@code runCommand} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * @return a list of {@code JobInfo} to be started
   */
  ImmutableList<JobInfo> createNonTradefedJobs(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfos = xtsJobCreator.createXtsNonTradefedJobs(sessionRequestInfo);
    if (jobInfos.isEmpty()) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The run command -> %s",
          shortDebugString(runCommand));

      return ImmutableList.of();
    }
    return jobInfos;
  }

  /**
   * Copies xTS tradefed and non-tradefed generated logs/results into proper locations within the
   * given xts root dir.
   */
  void handleResultProcessing(RunCommand command, RunCommandState runCommandState)
      throws MobileHarnessException, InterruptedException {
    if (!initialized) {
      return;
    }
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log(
            "Command [%s] is done and start to handle result which may take several minutes"
                + " based on the session scale.",
            runCommandState.getCommandId());

    List<JobInfo> allJobs = sessionInfo.getAllJobs();
    Path resultDir = null;
    Path logDir = null;
    Result result = null;
    Path xtsRootDir = Path.of(command.getXtsRootDir());
    String xtsType = command.getXtsType();
    Path resultsDir = XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType);
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }
      String timestampDirName =
          sessionInfo.getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME).orElseThrow();
      resultDir = resultsDir.resolve(timestampDirName);
      Path logsDir = XtsDirUtil.getXtsLogsDir(xtsRootDir, xtsType);
      logDir = logsDir.resolve(timestampDirName);
      result =
          sessionResultHandlerUtil
              .processResult(
                  resultDir,
                  logDir,
                  resultsDir.resolve("latest"),
                  logsDir.resolve("latest"),
                  allJobs,
                  sessionRequestInfo)
              .orElse(null);
      if (command.getEnableCtsVerifierResultReporter() && result != null) {
        ImmutableSet<Module> skippedModules =
            allJobs.stream()
                .filter(
                    jobInfo ->
                        // There are two skipped cases:
                        // 1. The module is not executed at all (SKIP_COLLECTING_NON_TF_REPORTS)
                        // 2. The module is executed but skipped by feature checkers
                        // Only mark the second case as done.
                        jobInfo.resultWithCause().get().type() == TestResult.SKIP
                            && jobInfo.properties().has(SessionHandlerHelper.XTS_MODULE_NAME_PROP)
                            && !jobInfo
                                .properties()
                                .getBoolean(Job.SKIP_COLLECTING_NON_TF_REPORTS)
                                .orElse(false))
                .map(
                    jobInfo -> {
                      // Mark skipped modules done when broadcasting results to update the status of
                      // CTS Verifier APP.
                      Module.Builder builder =
                          Module.newBuilder()
                              .setName(
                                  jobInfo
                                      .properties()
                                      .get(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
                              .setDone(true);
                      jobInfo.tests().getAll().values().stream()
                          .findFirst()
                          .flatMap(testInfo -> testInfo.resultWithCause().get().causeException())
                          .ifPresent(
                              e -> builder.setReason(Reason.newBuilder().setMsg(e.getMessage())));
                      return builder.build();
                    })
                .collect(toImmutableSet());
        ImmutableSet<String> serials =
            allJobs.stream()
                .flatMap(jobInfo -> jobInfo.tests().getAll().values().stream())
                .map(testInfo -> testInfo.properties().getOptional(Test.DEVICE_ID_LIST))
                .filter(Optional::isPresent)
                .flatMap(ids -> stream(ids.get().split(",")))
                .collect(toImmutableSet());
        logger.atInfo().with(IMPORTANCE, IMPORTANT).log("Push cts-v-host results to %s", serials);
        verifierResultHelper.pushResults(
            serials,
            result.toBuilder().addAllModuleInfo(skippedModules).build(),
            xtsRootDir,
            resultDir);
      }
    } finally {
      sessionResultHandlerUtil.cleanUpJobGenDirs(allJobs);
      if (sessionRequestInfo.subPlanNameBackup().isPresent()) {
        Path subPlanBackupPath =
            SessionHandlerHelper.getSubPlanFilePath(
                xtsRootDir, xtsType, sessionRequestInfo.subPlanNameBackup().get());
        logger.atInfo().log("Deleting subplan backup file %s", subPlanBackupPath);
        localFileUtil.removeFileOrDir(subPlanBackupPath);
      }

      String invocationSummaryText = "";
      boolean foundInvocationSummaryFile = false;
      if (resultDir != null && localFileUtil.isDirExist(resultDir)) {
        String invocationSummaryFile =
            resultDir
                .resolve(XtsConstants.INVOCATION_SUMMARY_FILE_NAME)
                .toAbsolutePath()
                .toString();
        if (localFileUtil.isFileExist(invocationSummaryFile)) {
          foundInvocationSummaryFile = true;
          invocationSummaryText = localFileUtil.readFile(invocationSummaryFile);
          int startIndex = invocationSummaryText.indexOf("==");
          if (startIndex > -1) {
            invocationSummaryText = invocationSummaryText.substring(startIndex);
          }
        }
      }
      if (!foundInvocationSummaryFile) {
        logger.atInfo().log(
            "Not found existing invocation summary file under result dir [%s]", resultDir);
        invocationSummaryText =
            sessionResultHandlerUtil.createXtsInvocationSummaryText(
                allJobs, result, resultDir, logDir, /* previousResult= */ null);
      }
      boolean isSessionCompleted = sessionResultHandlerUtil.isSessionCompleted(allJobs);
      String sessionSummary =
          String.format(
              "run_command session_id: [%s], command_id: [%s], result: %s.\n"
                  + "command_line_args: %s\n\n%s",
              sessionInfo.getSessionId(),
              runCommandState.getCommandId(),
              isSessionCompleted ? "COMPLETED" : "ERROR",
              command.getInitialState().getCommandLineArgs(),
              invocationSummaryText);

      sessionInfo.setSessionPluginOutput(
          oldOutput -> {
            AtsSessionPluginOutput.Builder builder =
                oldOutput == null ? AtsSessionPluginOutput.newBuilder() : oldOutput.toBuilder();
            if (isSessionCompleted) {
              builder.setSuccess(Success.newBuilder().setOutputMessage(sessionSummary).build());
            } else {
              builder.setFailure(Failure.newBuilder().setErrorMessage(sessionSummary).build());
            }
            return builder.build();
          },
          AtsSessionPluginOutput.class);
    }
  }

  @VisibleForTesting
  SessionRequestInfo generateSessionRequestInfo(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    SessionRequestInfo.Builder builder =
        SessionRequestInfo.builder()
            .setTestPlan(runCommand.getTestPlan())
            .setXtsRootDir(runCommand.getXtsRootDir())
            .setXtsType(runCommand.getXtsType())
            .setEnableModuleParameter(true)
            .setEnableModuleOptionalParameter(false)
            .setCommandLineArgs(runCommand.getInitialState().getCommandLineArgs())
            .setDeviceSerials(runCommand.getDeviceSerialList())
            .setExcludeDeviceSerials(runCommand.getExcludeDeviceSerialList())
            .setProductTypes(runCommand.getProductTypeList())
            .setDeviceProperties(ImmutableMap.copyOf(runCommand.getDevicePropertyMap()))
            .setModuleNames(runCommand.getModuleNameList())
            .setHtmlInZip(runCommand.getHtmlInZip())
            .setReportSystemCheckers(runCommand.getReportSystemCheckers())
            .setIncludeFilters(runCommand.getIncludeFilterList())
            .setExcludeFilters(runCommand.getExcludeFilterList())
            .setStrictIncludeFilters(runCommand.getStrictIncludeFilterList())
            .setModuleArgs(runCommand.getModuleArgList())
            .setExtraArgs(runCommand.getExtraArgList())
            .setXtsSuiteInfo(ImmutableMap.copyOf(runCommand.getXtsSuiteInfoMap()))
            .setTestSuiteInfo(
                TestSuiteInfoProvider.getTestSuiteInfo(
                    runCommand.getXtsRootDir(), runCommand.getXtsType()))
            .setExcludeRunners(ImmutableSet.copyOf(runCommand.getExcludeRunnerList()))
            .setStartTimeout(TimeUtils.toJavaDuration(runCommand.getJobStartTimeout()));
    ImmutableMultimap.Builder<String, String> moduleMetadataIncludeFilters =
        ImmutableMultimap.builder();
    runCommand
        .getModuleMetadataIncludeFilterList()
        .forEach(entry -> moduleMetadataIncludeFilters.put(entry.getKey(), entry.getValue()));
    builder.setModuleMetadataIncludeFilters(moduleMetadataIncludeFilters.build());
    ImmutableMultimap.Builder<String, String> moduleMetadataExcludeFilters =
        ImmutableMultimap.builder();
    runCommand
        .getModuleMetadataExcludeFilterList()
        .forEach(entry -> moduleMetadataExcludeFilters.put(entry.getKey(), entry.getValue()));
    builder.setModuleMetadataExcludeFilters(moduleMetadataExcludeFilters.build());
    if (runCommand.hasSkipDeviceInfo()) {
      builder.setSkipDeviceInfo(runCommand.getSkipDeviceInfo());
    }
    if (runCommand.hasTestName()) {
      builder.setTestName(runCommand.getTestName());
    }
    if (runCommand.hasShardCount()) {
      builder.setShardCount(runCommand.getShardCount());
    }
    if (runCommand.hasPythonPkgIndexUrl()) {
      builder.setPythonPkgIndexUrl(runCommand.getPythonPkgIndexUrl());
    }
    if (runCommand.hasRetrySessionIndex()) {
      builder.setRetrySessionIndex(runCommand.getRetrySessionIndex());
    }
    if (runCommand.hasRetrySessionResultDirName()) {
      builder.setRetrySessionResultDirName(runCommand.getRetrySessionResultDirName());
    }
    if (runCommand.hasRetryType()) {
      builder.setRetryType(RetryType.valueOf(toUpperCase(runCommand.getRetryType())));
    }
    if (runCommand.hasSubPlanName()) {
      builder.setSubPlanName(runCommand.getSubPlanName());
      builder.setSubPlanNameBackup(
          String.format("%s_backup_%s", runCommand.getSubPlanName(), Instant.now().toEpochMilli()));
    }
    if (runCommand.getDeviceType() != DeviceType.DEVICE_TYPE_UNSPECIFIED) {
      builder.setDeviceType(
          runCommand.getDeviceType() == DeviceType.EMULATOR
              ? SessionRequestHandlerUtil.ANDROID_LOCAL_EMULATOR_TYPE
              : SessionRequestHandlerUtil.ANDROID_REAL_DEVICE_TYPE);
    }
    if (runCommand.hasMaxBatteryLevel()) {
      builder.setMaxBatteryLevel(runCommand.getMaxBatteryLevel());
    }
    if (runCommand.hasMinBatteryLevel()) {
      builder.setMinBatteryLevel(runCommand.getMinBatteryLevel());
    }
    if (runCommand.hasMaxBatteryTemperature()) {
      builder.setMaxBatteryTemperature(runCommand.getMaxBatteryTemperature());
    }
    if (runCommand.hasMinSdkLevel()) {
      builder.setMinSdkLevel(runCommand.getMinSdkLevel());
    }
    if (runCommand.hasMaxSdkLevel()) {
      builder.setMaxSdkLevel(runCommand.getMaxSdkLevel());
    }
    if (runCommand.getEnableXtsDynamicDownload()) {
      builder.setIsXtsDynamicDownloadEnabled(true);
    }
    if (runCommand.getEnableMoblyResultstoreUpload()) {
      builder.setIsMoblyResultstoreUploadEnabled(true);
    }
    builder.setEnableDefaultLogs(runCommand.getEnableDefaultLogs());

    sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_CLIENT_ID)
        .ifPresent(builder::setSessionClientId);

    Optional<DeviceInfo> deviceInfo = sessionRequestHandlerUtil.getDeviceInfo(builder.build());
    builder.setDeviceInfo(deviceInfo);
    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(builder.build());
  }
}
