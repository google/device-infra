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
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionCancellation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DeviceType;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteResultReporter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
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
  private final SuiteResultReporter suiteResultReporter;
  private final XtsJobCreator xtsJobCreator;

  private final Object addingJobLock = new Object();

  @GuardedBy("addingJobLock")
  private AtsSessionCancellation sessionCancellation;

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
      SuiteResultReporter suiteResultReporter,
      XtsJobCreator xtsJobCreator) {
    this.localFileUtil = localFileUtil;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.sessionResultHandlerUtil = sessionResultHandlerUtil;
    this.sessionInfo = sessionInfo;
    this.suiteResultReporter = suiteResultReporter;
    this.xtsJobCreator = xtsJobCreator;
  }

  void initialize(RunCommand command) throws MobileHarnessException, InterruptedException {
    sessionInfo.putSessionProperty(
        SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME,
        TIMESTAMP_DIR_NAME_FORMATTER.format(Instant.now()) + "_" + getRandom4Digits());
    sessionRequestInfo = generateSessionRequestInfo(command);
    initialized = true;
  }

  /** Stop adding new jobs. */
  void onSessionCancellation(AtsSessionCancellation sessionCancellation) {
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Stop adding new jobs due to [%s]", shortDebugString(sessionCancellation));
    synchronized (addingJobLock) {
      this.sessionCancellation = sessionCancellation;
    }
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
   * @return a list of added tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addTradefedJobs(RunCommand command)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfo = xtsJobCreator.createXtsTradefedTestJob(sessionRequestInfo);
    if (jobInfo.isEmpty()) {
      logger.atInfo().log(
          "No tradefed jobs created, double check device availability. The run command -> %s",
          shortDebugString(command));
      return ImmutableList.of();
    }

    // Lets the driver write TF output to XTS log dir directly.
    jobInfo
        .get()
        .params()
        .add(
            "xts_log_root_path",
            XtsDirUtil.getXtsLogsDir(Path.of(command.getXtsRootDir()), command.getXtsType())
                .resolve(
                    sessionInfo
                        .getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME)
                        .orElseThrow())
                .toString());

    addEnableXtsDynamicDownloadToJob(jobInfo.get(), command);

    if (addJobToSession(jobInfo.get())) {
      String jobId = jobInfo.get().locator().getId();
      logger.atInfo().log(
          "Added tradefed job[%s] to the session %s", jobId, sessionInfo.getSessionId());
      return ImmutableList.of(jobId);
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Creates non-tradefed jobs based on the {@code runCommand} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * @return a list of added non-tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addNonTradefedJobs(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfos = xtsJobCreator.createXtsNonTradefedJobs(sessionRequestInfo);
    if (jobInfos.isEmpty()) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The run command -> %s",
          shortDebugString(runCommand));

      return ImmutableList.of();
    }
    ImmutableList.Builder<String> nonTradefedJobIds = ImmutableList.builder();
    jobInfos.forEach(
        jobInfo -> {
          addEnableXtsDynamicDownloadToJob(jobInfo, runCommand);

          if (addJobToSession(jobInfo)) {
            nonTradefedJobIds.add(jobInfo.locator().getId());
            logger.atInfo().log(
                "Added non-tradefed job[%s] to the session %s",
                jobInfo.locator().getId(), sessionInfo.getSessionId());
          }
        });
    return nonTradefedJobIds.build();
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
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }
      Path xtsRootDir = Path.of(command.getXtsRootDir());
      String xtsType = command.getXtsType();
      String timestampDirName =
          sessionInfo.getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME).orElseThrow();
      Path resultsDir = XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType);
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
    } finally {
      sessionResultHandlerUtil.cleanUpJobGenDirs(allJobs);

      String xtsTestResultSummary = createXtsTestResultSummary(result, resultDir, logDir);
      boolean isSessionCompleted = sessionResultHandlerUtil.isSessionCompleted(allJobs);
      String sessionSummary =
          String.format(
              "run_command session_id: [%s], command_id: [%s], result: %s.\n"
                  + "command_line_args: %s\n%s",
              sessionInfo.getSessionId(),
              runCommandState.getCommandId(),
              isSessionCompleted ? "COMPLETED" : "ERROR",
              command.getInitialState().getCommandLineArgs(),
              xtsTestResultSummary);

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

  /**
   * Returns true if the job has been added to the session successfully, false if the job was not
   * added to the session.
   */
  private boolean addJobToSession(JobInfo jobInfo) {
    synchronized (addingJobLock) {
      if (sessionCancellation == null) {
        sessionInfo.addJob(jobInfo);
        return true;
      } else {
        logger.atInfo().log(
            "Skip adding job [%s] to session due to [%s]",
            jobInfo.locator().getId(), shortDebugString(sessionCancellation));
        return false;
      }
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
            .setIncludeFilters(runCommand.getIncludeFilterList())
            .setExcludeFilters(runCommand.getExcludeFilterList())
            .setExtraArgs(runCommand.getExtraArgList());
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
    if (runCommand.hasRetryType()) {
      builder.setRetryType(RetryType.valueOf(toUpperCase(runCommand.getRetryType())));
    }
    if (runCommand.hasSubPlanName()) {
      builder.setSubPlanName(runCommand.getSubPlanName());
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

    sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_CLIENT_ID)
        .ifPresent(builder::setSessionClientId);
    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(builder.build());
  }

  private String createXtsTestResultSummary(
      @Nullable Result result, @Nullable Path resultDir, @Nullable Path logDir) {
    return String.format(
            "%s=========== Result/Log Location ============\n",
            suiteResultReporter.getSummary(result))
        + (logDir != null && localFileUtil.isDirExist(logDir)
            ? String.format("LOG DIRECTORY               : %s\n", logDir)
            : "")
        + (resultDir != null && localFileUtil.isDirExist(resultDir)
            ? String.format("RESULT DIRECTORY            : %s\n", resultDir)
            : "")
        + "=================== End ====================\n";
  }

  private static void addEnableXtsDynamicDownloadToJob(JobInfo jobInfo, RunCommand runCommand) {
    if (runCommand.getEnableXtsDynamicDownload()) {
      jobInfo.properties().add(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED, "true");
    }
  }
}
