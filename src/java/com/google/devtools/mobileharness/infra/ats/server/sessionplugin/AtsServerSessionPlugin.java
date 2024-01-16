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

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.ArrayList;
import java.util.List;
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
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo, NewMultiCommandRequestHandler newMultiCommandRequestHandler) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
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
    updateCommandDetail(jobEndEvent.getJobInfo().toNewJobInfo());
    JobInfo jobInfo = jobEndEvent.getJobInfo().toNewJobInfo();

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
  public void onSessionEnded(SessionEndedEvent event) throws InterruptedException {
    // TODO: Result processing to be implemented.
    synchronized (requestDetailLock) {
      newMultiCommandRequestHandler.cleanup(requestDetail.getOriginalRequest(), sessionInfo);
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

  private void updateCommandDetail(JobInfo jobInfo) {
    synchronized (requestDetailLock) {
      if (!requestDetail.containsCommandDetails(jobInfo.locator().getId())) {
        logger.atWarning().log(
            "Tradefed job %s not found in requestDetail", jobInfo.locator().getId());
        return;
      }
      CommandDetail oldCommandDetail =
          requestDetail.getCommandDetailsOrThrow(jobInfo.locator().getId());
      CommandDetail newCommandDetail =
          oldCommandDetail.toBuilder().setState(convertJobStatusToCommandState(jobInfo)).build();
      requestDetail.putCommandDetails(jobInfo.locator().getId(), newCommandDetail);
      RequestDetail latestRequestDetail = requestDetail.build();
      sessionInfo.setSessionPluginOutput(empty -> latestRequestDetail, RequestDetail.class);
    }
  }

  private CommandState convertJobStatusToCommandState(JobInfo jobInfo) {
    switch (jobInfo.status().get()) {
      case NEW:
        return CommandState.UNKNOWN_STATE;
      case ASSIGNED:
        return CommandState.QUEUED;
      case RUNNING:
        return CommandState.RUNNING;
      case DONE:
        if (jobInfo.result().get().equals(TestResult.PASS)) {
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
