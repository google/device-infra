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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.TextFormat.shortDebugString;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.DEVICE_ID_LIST;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandAttemptDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest.RequestCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Session Plugin to serve test requests coming from ATS server. */
final class AtsServerSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TRADEFED_DRIVER_NAME = "XtsTradefedTest";

  /** Set in {@link #onSessionStarting}. */
  private volatile SessionRequest request;

  private final Object requestDetailLock = new Object();

  // The source of truth for this session's request states.
  @GuardedBy("requestDetailLock")
  private final RequestDetail.Builder requestDetail = RequestDetail.newBuilder();

  @GuardedBy("requestDetailLock")
  private Boolean nonTradefedJobsHasBeenCreated = false;

  private final SessionInfo sessionInfo;
  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo,
      NewMultiCommandRequestHandler newMultiCommandRequestHandler,
      SessionRequestHandlerUtil sessionRequestHandlerUtil) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InvalidProtocolBufferException, InterruptedException, MobileHarnessException {
    request =
        sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
    logger.atInfo().log("Config: %s", shortDebugString(request));
    if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
      synchronized (requestDetailLock) {
        requestDetail.mergeFrom(
            newMultiCommandRequestHandler.addTradefedJobs(
                request.getNewMultiCommandRequest(), sessionInfo));
        // If no tradefed job was created and the request does not contain any error that cause it
        // to cancel, create non tradefed jobs directly.
        if (requestDetail.getCommandDetailsCount() == 0
            && requestDetail.getState() == RequestState.RUNNING) {
          createNonTradefedJobs();
        }
        RequestDetail latestRequestDetail = requestDetail.build();
        sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
      }
    }
  }

  @Subscribe
  public void onJobEnded(JobEndEvent jobEndEvent)
      throws InterruptedException, MobileHarnessException {
    JobInfo jobInfo = jobEndEvent.getJobInfo().toNewJobInfo();

    long jobTotalTestCount = 0;
    long jobFailedTestCount = 0;
    long jobPassedTestCount = 0;
    for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
      Optional<com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result>
          result = sessionRequestHandlerUtil.getTestResultFromTest(testInfo);
      logger.atInfo().log("Result: %s", result);
      if (result.isPresent()) {
        long passedTestCount = result.get().getSummary().getPassed();
        long failedTestCount = result.get().getSummary().getFailed();
        long totalTestCount = passedTestCount + failedTestCount;
        testInfo.properties().add("ats_test_passed_count", String.valueOf(passedTestCount));
        testInfo.properties().add("ats_test_failed_count", String.valueOf(failedTestCount));
        testInfo.properties().add("ats_test_total_count", String.valueOf(totalTestCount));

        jobTotalTestCount += totalTestCount;
        jobPassedTestCount += passedTestCount;
        jobFailedTestCount += failedTestCount;
      }
    }
    jobInfo.properties().add("ats_job_passed_count", String.valueOf(jobPassedTestCount));
    jobInfo.properties().add("ats_job_failed_count", String.valueOf(jobFailedTestCount));
    jobInfo.properties().add("ats_job_total_count", String.valueOf(jobTotalTestCount));
    updateCommandDetail(jobInfo);
    addCommandAttemptDetails(jobInfo);

    // If a non-tradefed tests ended, that means all non-tradefed tests had already been created and
    // no need to create more.
    if (!jobInfo.type().getDriver().equals(TRADEFED_DRIVER_NAME)) {
      return;
    }

    // If all tradefed jobs have ended, create non tradefed jobs.
    synchronized (requestDetailLock) {
      // In case another thread has created the non TF jobs, skip the rest of the steps.
      if (nonTradefedJobsHasBeenCreated) {
        return;
      }
      for (CommandDetail commandDetail : requestDetail.getCommandDetailsMap().values()) {
        if (!commandDetail.getState().equals(CommandState.COMPLETED)
            && !commandDetail.getState().equals(CommandState.ERROR)) {
          return;
        }
      }
      createNonTradefedJobs();
    }
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event)
      throws InterruptedException, MobileHarnessException {
    // TODO: Result processing to be implemented.
    synchronized (requestDetailLock) {
      newMultiCommandRequestHandler.handleResultProcessing(
          requestDetail.getOriginalRequest(), sessionInfo);
      requestDetail.setState(RequestState.COMPLETED);
      logger.atInfo().log("RequestDetail: %s", shortDebugString(requestDetail.build()));
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private void createNonTradefedJobs() throws MobileHarnessException, InterruptedException {
    synchronized (requestDetailLock) {
      if (nonTradefedJobsHasBeenCreated) {
        return;
      }
      nonTradefedJobsHasBeenCreated = true;
      List<CommandDetail> nonTradefedCommandDetails = new ArrayList<>();
      for (CommandInfo commandInfo : requestDetail.getOriginalRequest().getCommandsList()) {
        nonTradefedCommandDetails.addAll(
            newMultiCommandRequestHandler.addNonTradefedJobs(
                requestDetail.getOriginalRequest(), commandInfo, sessionInfo));
      }
      nonTradefedCommandDetails.forEach(
          commandDetail -> requestDetail.putCommandDetails(commandDetail.getId(), commandDetail));
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private void addCommandAttemptDetails(JobInfo jobInfo) {
    synchronized (requestDetailLock) {
      ImmutableList<CommandAttemptDetail> attemptDetails =
          jobInfo.tests().getAll().values().stream()
              .map(this::generateCommandAttemptDetail)
              .collect(toImmutableList());
      requestDetail.addAllCommandAttemptDetails(attemptDetails);
      requestDetail.setUpdateTime(TimeUtils.toProtoTimestamp(Instant.now()));
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private CommandAttemptDetail generateCommandAttemptDetail(TestInfo testInfo) {
    CommandAttemptDetail.Builder builder = CommandAttemptDetail.newBuilder();
    builder.setId(testInfo.locator().getId());
    builder.setRequestId(sessionInfo.getSessionId());
    builder.setCommandId(testInfo.jobInfo().locator().getId());
    if (testInfo.properties().has(DEVICE_ID_LIST)) {
      ImmutableList<String> deviceSerials =
          ImmutableList.copyOf(testInfo.properties().get(DEVICE_ID_LIST).split(","));
      builder.addAllDeviceSerials(deviceSerials);
    }
    if (testInfo.properties().has("ats_test_passed_count")) {
      logger.atInfo().log(
          "Passed test count: %s", testInfo.properties().get("ats_test_passed_count"));
      builder.setPassedTestCount(
          Long.parseLong(testInfo.properties().get("ats_test_passed_count")));
    }
    if (testInfo.properties().has("ats_test_failed_count")) {
      builder.setFailedTestCount(
          Long.parseLong(testInfo.properties().get("ats_test_failed_count")));
    }
    if (testInfo.properties().has("ats_test_total_count")) {
      builder.setTotalTestCount(Long.parseLong(testInfo.properties().get("ats_test_total_count")));
    }
    builder.setStartTime(TimeUtils.toProtoTimestamp(testInfo.timing().getStartTime()));
    builder.setEndTime(TimeUtils.toProtoTimestamp(testInfo.timing().getEndTime()));
    builder.setCreateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getCreateTime()));
    builder.setUpdateTime(TimeUtils.toProtoTimestamp(testInfo.timing().getModifyTime()));
    builder.setState(convertStatusAndResultToCommandState(testInfo.status(), testInfo.result()));
    return builder.build();
  }

  private void updateCommandDetail(JobInfo jobInfo) {
    synchronized (requestDetailLock) {
      if (!requestDetail.containsCommandDetails(jobInfo.locator().getId())) {
        logger.atWarning().log(
            "Tradefed job %s not found in requestDetail", jobInfo.locator().getId());
        return;
      }
      CommandDetail oldCommandDetail =
          requestDetail.getCommandDetailsOrThrow(jobInfo.locator().getId());
      CommandDetail.Builder newCommandDetail =
          oldCommandDetail.toBuilder()
              .setState(convertStatusAndResultToCommandState(jobInfo.status(), jobInfo.result()));
      if (jobInfo.properties().has("ats_job_passed_count")) {
        logger.atInfo().log(
            "Passed job count: %s", jobInfo.properties().get("ats_job_passed_count"));
        newCommandDetail.setPassedTestCount(
            Long.parseLong(jobInfo.properties().get("ats_job_passed_count")));
      }
      if (jobInfo.properties().has("ats_job_failed_count")) {
        newCommandDetail.setFailedTestCount(
            Long.parseLong(jobInfo.properties().get("ats_job_failed_count")));
      }
      if (jobInfo.properties().has("ats_job_total_count")) {
        newCommandDetail.setTotalTestCount(
            Long.parseLong(jobInfo.properties().get("ats_job_total_count")));
      }
      newCommandDetail.setStartTime(TimeUtils.toProtoTimestamp(jobInfo.timing().getStartTime()));
      newCommandDetail.setEndTime(TimeUtils.toProtoTimestamp(jobInfo.timing().getEndTime()));
      newCommandDetail.setCreateTime(TimeUtils.toProtoTimestamp(jobInfo.timing().getCreateTime()));
      newCommandDetail.setUpdateTime(TimeUtils.toProtoTimestamp(jobInfo.timing().getModifyTime()));
      requestDetail.putCommandDetails(jobInfo.locator().getId(), newCommandDetail.build());
      requestDetail.setUpdateTime(TimeUtils.toProtoTimestamp(Instant.now()));
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private CommandState convertStatusAndResultToCommandState(Status status, Result result) {
    switch (status.get()) {
      case NEW:
        return CommandState.UNKNOWN_STATE;
      case ASSIGNED:
        return CommandState.QUEUED;
      case RUNNING:
        return CommandState.RUNNING;
      case DONE:
        if (result.get().equals(TestResult.PASS)) {
          return CommandState.COMPLETED;
        } else {
          return CommandState.ERROR;
        }
      case SUSPENDED:
        return CommandState.CANCELED;
    }
    return CommandState.UNKNOWN_STATE;
  }
}
