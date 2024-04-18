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
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.driver.XtsTradefedTest;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.List;
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
  void handleResultProcessing(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    List<JobInfo> allJobs = sessionInfo.getAllJobs();
    Path resultDir = null;
    Path logDir = null;
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }
      Path xtsRootDir = Path.of(command.getXtsRootDir());
      String xtsType = command.getXtsType();
      String timestampDirName = getTimestampDirName();
      resultDir = XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType).resolve(timestampDirName);
      logDir = XtsDirUtil.getXtsLogsDir(xtsRootDir, xtsType).resolve(timestampDirName);
      sessionRequestHandlerUtil.processResult(
          resultDir, logDir, allJobs, generateSessionRequestInfo(command));
    } finally {
      sessionRequestHandlerUtil.cleanUpJobGenDirs(allJobs);

      String xtsTestResultSummary =
          createXtsTestResultSummary(
              allJobs,
              command,
              resultDir != null ? resultDir.toString() : "N/A",
              logDir != null ? logDir.toString() : "N/A");
      boolean isSessionPassed = sessionRequestHandlerUtil.isSessionPassed(allJobs);
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

  private SessionRequestInfo generateSessionRequestInfo(RunCommand runCommand)
      throws MobileHarnessException {
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
            .setHtmlInZip(runCommand.getHtmlInZip());
    if (runCommand.hasTestName()) {
      builder.setTestName(runCommand.getTestName());
    }
    builder
        .setIncludeFilters(runCommand.getIncludeFilterList())
        .setExcludeFilters(runCommand.getExcludeFilterList())
        .setExtraArgs(runCommand.getExtraArgList());

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
    return sessionRequestHandlerUtil.addNonTradefedModuleInfo(builder.build());
  }

  @VisibleForTesting
  String getTimestampDirName() {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(Clock.systemUTC().millis()));
  }

  private static String createXtsTestResultSummary(
      List<JobInfo> jobInfos, RunCommand runCommand, String resultDir, String logDir) {
    // Print the time from xts run start to end
    int tradefedDoneModuleNumber = 0;
    int tradefedTotalModuleNumber = 0;
    int tradefedPassedTestNumber = 0;
    int tradefedFailedTestNumber = 0;
    int nonTradefedTotalModuleNumber = 0;
    int nonTradefedDoneModuleNumber = 0;
    int nonTradefedPassedTestNumber = 0;
    int nonTradefedFailedTestNumber = 0;
    int nonTradefedSkippedTestNumber = 0;
    @SuppressWarnings("unused")
    int nonTradefedTotalTestCaseNumber = 0;
    @SuppressWarnings("unused")
    int nonTradefedDoneTestCaseNumber = 0;

    for (JobInfo jobInfo : jobInfos) {
      TestInfo testInfo = jobInfo.tests().getOnly();

      // Collect Tradefed Jobs test summary.
      if (jobInfo.properties().has(SessionRequestHandlerUtil.XTS_TF_JOB_PROP)) {
        tradefedDoneModuleNumber +=
            Integer.parseInt(
                testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_DONE)
                    ? testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_DONE)
                    : "0");
        tradefedTotalModuleNumber +=
            Integer.parseInt(
                testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_TOTAL)
                    ? testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_TOTAL)
                    : "0");
        tradefedPassedTestNumber +=
            Integer.parseInt(
                testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_PASSED)
                    ? testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_PASSED)
                    : "0");
        tradefedFailedTestNumber +=
            Integer.parseInt(
                testInfo.properties().has(XtsTradefedTest.TRADEFED_TESTS_FAILED)
                    ? testInfo.properties().get(XtsTradefedTest.TRADEFED_TESTS_FAILED)
                    : "0");
      }
      // Collect Non Tradefed Jobs test summary.
      if (jobInfo.properties().has(SessionRequestHandlerUtil.XTS_NON_TF_JOB_PROP)) {
        nonTradefedTotalModuleNumber++;
        if (testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_DONE)) {
          nonTradefedDoneModuleNumber++;
        }
        nonTradefedPassedTestNumber +=
            Integer.parseInt(
                testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_PASSED)
                    ? testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_PASSED)
                    : "0");
        nonTradefedFailedTestNumber +=
            Integer.parseInt(
                testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_FAILED_AND_ERROR)
                    ? testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_FAILED_AND_ERROR)
                    : "0");
        nonTradefedSkippedTestNumber +=
            Integer.parseInt(
                testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_SKIPPED)
                    ? testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_SKIPPED)
                    : "0");
        nonTradefedTotalTestCaseNumber +=
            Integer.parseInt(
                testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_TOTAL)
                    ? testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_TOTAL)
                    : "0");
        nonTradefedDoneTestCaseNumber +=
            Integer.parseInt(
                testInfo.properties().has(MoblyTestInfoMapHelper.MOBLY_TESTS_DONE)
                    ? testInfo.properties().get(MoblyTestInfoMapHelper.MOBLY_TESTS_DONE)
                    : "0");
      }
    }

    // Print out the xts test result summary.
    return String.format(
            "\n================= %s test result summary ================\n",
            toUpperCase(runCommand.getXtsType()))
        + String.format(
            "%s/%s modules completed\n",
            tradefedDoneModuleNumber + nonTradefedDoneModuleNumber,
            tradefedTotalModuleNumber + nonTradefedTotalModuleNumber)
        + String.format(
            "PASSED TESTCASES           : %s\n",
            tradefedPassedTestNumber + nonTradefedPassedTestNumber)
        + String.format(
            "FAILED TESTCASES           : %s\n",
            tradefedFailedTestNumber + nonTradefedFailedTestNumber)
        + String.format("SKIPPED TESTCASES          : %s\n", nonTradefedSkippedTestNumber)
        + String.format("RESULT DIRECTORY           : %s\n", resultDir)
        + String.format("LOG DIRECTORY              : %s\n", logDir)
        + "=================== End of Results =============================\n"
        + "================================================================\n";
  }
}
