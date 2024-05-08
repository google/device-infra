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
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionResultHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
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
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME = "timestamp_dir_name";
  private static final DateTimeFormatter TIMESTAMP_DIR_NAME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu.MM.dd_HH.mm.ss").withZone(ZoneId.systemDefault());

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final SessionResultHandlerUtil sessionResultHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final SessionInfo sessionInfo;

  private volatile SessionRequestInfo sessionRequestInfo;

  @Inject
  RunCommandHandler(
      LocalFileUtil localFileUtil,
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      SessionResultHandlerUtil sessionResultHandlerUtil,
      SessionInfo sessionInfo) {
    this.localFileUtil = localFileUtil;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.sessionResultHandlerUtil = sessionResultHandlerUtil;
    this.sessionInfo = sessionInfo;
  }

  void initialize(RunCommand command) throws MobileHarnessException, InterruptedException {
    sessionInfo.putSessionProperty(
        SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME,
        TIMESTAMP_DIR_NAME_FORMATTER.format(Instant.now()));
    sessionRequestInfo = generateSessionRequestInfo(command);
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
    Optional<JobInfo> jobInfo =
        sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfo);
    if (jobInfo.isEmpty()) {
      logger.atInfo().log(
          "No tradefed jobs created, double check device availability. The run command -> %s",
          shortDebugString(command));
      return ImmutableList.of();
    }
    jobInfo.get().properties().add(SessionHandlerHelper.XTS_TF_JOB_PROP, "true");

    // Lets the driver write TF output to XTS log dir directly.
    jobInfo
        .get()
        .params()
        .add(
            "xts_tf_output_path",
            XtsDirUtil.getXtsLogsDir(Path.of(command.getXtsRootDir()), command.getXtsType())
                .resolve(
                    sessionInfo
                        .getSessionProperty(SESSION_PROPERTY_NAME_TIMESTAMP_DIR_NAME)
                        .orElseThrow())
                .resolve(XtsConstants.TRADEFED_LOGS_DIR_NAME)
                .resolve(XtsConstants.TRADEFED_OUTPUT_FILE_NAME)
                .toString());

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
  ImmutableList<String> addNonTradefedJobs(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfos =
        sessionRequestHandlerUtil.createXtsNonTradefedJobs(sessionRequestInfo);
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
  void handleResultProcessing(RunCommand command)
      throws MobileHarnessException, InterruptedException {
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
      localFileUtil.prepareDir(logDir);
      SessionRequestInfo sessionRequestInfo = this.sessionRequestInfo;
      if (sessionRequestInfo != null) {
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
      } else {
        logger.atInfo().log("Skip result processing due to initialization failure");
      }
    } finally {
      sessionResultHandlerUtil.cleanUpJobGenDirs(allJobs);

      String xtsTestResultSummary = createXtsTestResultSummary(result, command, resultDir, logDir);
      boolean isSessionPassed = sessionResultHandlerUtil.isSessionPassed(allJobs);
      String sessionSummary =
          String.format(
              "run_command session_id: [%s], command_id: [%s], result: %s.\n"
                  + "command_line_args: %s\n%s",
              sessionInfo.getSessionId(),
              command.getInitialState().getCommandId(),
              isSessionPassed ? "SUCCESS" : "FAILURE",
              command.getInitialState().getCommandLineArgs(),
              xtsTestResultSummary);

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
    sessionInfo
        .getSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_CLIENT_ID)
        .ifPresent(builder::setSessionClientId);
    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(builder.build());
  }

  private String createXtsTestResultSummary(
      @Nullable Result result,
      RunCommand runCommand,
      @Nullable Path resultDir,
      @Nullable Path logDir) {
    int doneModuleNumber = 0;
    int totalModuleNumber = 0;
    long totalPassedTestNumber = 0L;
    long totalFailedTestNumber = 0L;
    long totalSkippedTestNumber = 0L;
    long totalAssumeFailureTestNumber = 0L;
    if (result != null) {
      doneModuleNumber = result.getSummary().getModulesDone();
      totalModuleNumber = result.getSummary().getModulesTotal();
      totalPassedTestNumber = result.getSummary().getPassed();
      totalFailedTestNumber = result.getSummary().getFailed();
      for (Module module : result.getModuleInfoList()) {
        for (TestCase testCase : module.getTestCaseList()) {
          for (Test test : testCase.getTestList()) {
            if (test.getResult().equals("ASSUMPTION_FAILURE")) {
              totalAssumeFailureTestNumber += 1;
            } else if (test.getSkipped() || test.getResult().equals("IGNORED")) {
              totalSkippedTestNumber += 1;
            }
          }
        }
      }
    }

    // Print out the xts test result summary.
    return String.format(
            "\n================= %s test result summary ================\n",
            toUpperCase(runCommand.getXtsType()))
        + String.format("%s/%s modules completed\n", doneModuleNumber, totalModuleNumber)
        + String.format(
            "TOTAL TESTCASES             : %s\n",
            totalPassedTestNumber
                + totalFailedTestNumber
                + totalSkippedTestNumber
                + totalAssumeFailureTestNumber)
        + String.format("PASSED TESTCASES            : %s\n", totalPassedTestNumber)
        + String.format("FAILED TESTCASES            : %s\n", totalFailedTestNumber)
        + (totalSkippedTestNumber > 0
            ? String.format("SKIPPED TESTCASES           : %s\n", totalSkippedTestNumber)
            : "")
        + (totalAssumeFailureTestNumber > 0
            ? String.format("ASSUMPTION_FAILURE TESTCASES: %s\n", totalAssumeFailureTestNumber)
            : "")
        + (logDir != null && localFileUtil.isDirExist(logDir)
            ? String.format("LOG DIRECTORY               : %s\n", logDir)
            : "")
        + (resultDir != null && localFileUtil.isDirExist(resultDir)
            ? String.format("RESULT DIRECTORY            : %s\n", resultDir)
            : "")
        + "=================== End of Results =============================\n"
        + "================================================================\n";
  }
}
