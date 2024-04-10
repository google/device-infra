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

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  RunCommandHandler(
      LocalFileUtil localFileUtil, SessionRequestHandlerUtil sessionRequestHandlerUtil) {
    this.localFileUtil = localFileUtil;
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
    jobInfo.get().properties().add(SessionRequestHandlerUtil.XTS_TF_JOB_PROP, "true");
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
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(generateSessionRequestInfo(runCommand));
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

  /**
   * Copies xTS tradefed and non-tradefed generated logs/results into proper locations within the
   * given xts root dir.
   */
  void handleResultProcessing(
      RunCommand command, SessionInfo sessionInfo, String summaryReport, boolean isSessionPassed)
      throws MobileHarnessException, InterruptedException {
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }
      Path xtsRootDir = Path.of(command.getXtsRootDir());
      String xtsType = command.getXtsType();
      String timestampDirName = getTimestampDirName();
      Path resultDir = getResultDir(xtsRootDir, xtsType, timestampDirName);
      Path logDir = getLogDir(xtsRootDir, xtsType, timestampDirName);

      sessionRequestHandlerUtil.processResult(
          resultDir, logDir, sessionInfo.getAllJobs(), generateSessionRequestInfo(command));
    } finally {
      sessionRequestHandlerUtil.cleanUpJobGenDirs(sessionInfo.getAllJobs());
      String sessionSummary =
          String.format(
              "run_command session_id: [%s], command_id: [%s], result: %s.\n"
                  + "command_line_args: %s\n%s",
              sessionInfo.getSessionId(),
              command.getInitialState().getCommandId(),
              isSessionPassed ? "SUCCESS" : "FAILURE",
              command.getInitialState().getCommandLineArgs(),
              summaryReport);
      sessionInfo.setSessionPluginOutput(
          oldOutput -> {
            AtsSessionPluginOutput.Builder builder =
                oldOutput == null ? AtsSessionPluginOutput.newBuilder() : oldOutput.toBuilder();
            if (isSessionPassed) {
              builder.setSuccess(Success.newBuilder().setOutputMessage(sessionSummary).build());
            } else {
              builder.setFailure(Failure.newBuilder().setErrorMessage(sessionSummary).build());
            }
            return builder.build();
          },
          AtsSessionPluginOutput.class);
    }
  }

  private SessionRequestInfo generateSessionRequestInfo(RunCommand runCommand)
      throws MobileHarnessException {
    SessionRequestInfo.Builder builder =
        SessionRequestInfo.builder()
            .setTestPlan(runCommand.getTestPlan())
            .setXtsRootDir(runCommand.getXtsRootDir())
            .setXtsType(runCommand.getXtsType())
            .setEnableModuleParameter(true)
            .setEnableModuleOptionalParameter(false);

    builder.setDeviceSerials(runCommand.getDeviceSerialList());
    builder.setModuleNames(runCommand.getModuleNameList());
    if (runCommand.hasTestName()) {
      builder.setTestName(runCommand.getTestName());
    }
    builder.setIncludeFilters(runCommand.getIncludeFilterList());
    builder.setExcludeFilters(runCommand.getExcludeFilterList());
    builder.setExtraArgs(runCommand.getExtraArgList());

    if (runCommand.hasShardCount()) {
      builder.setShardCount(runCommand.getShardCount());
    }
    if (runCommand.hasPythonPkgIndexUrl()) {
      builder.setPythonPkgIndexUrl(runCommand.getPythonPkgIndexUrl());
    }
    if (runCommand.hasRetrySessionId()) {
      builder.setRetrySessionId(runCommand.getRetrySessionId());
    }
    if (runCommand.hasRetryType()) {
      builder.setRetryType(RetryType.valueOf(Ascii.toUpperCase(runCommand.getRetryType())));
    }
    if (runCommand.hasSubPlanName()) {
      builder.setSubPlanName(runCommand.getSubPlanName());
    }
    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(builder.build());
  }

  private Path getResultDir(Path xtsRootDir, String xtsType, String timestampDirName) {
    return getXtsResultsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  private Path getLogDir(Path xtsRootDir, String xtsType, String timestampDirName) {
    return getXtsLogsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  @VisibleForTesting
  String getTimestampDirName() {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(Clock.systemUTC().millis()));
  }

  private Path getXtsResultsDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/results", xtsType));
  }

  private Path getXtsLogsDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/logs", xtsType));
  }
}
