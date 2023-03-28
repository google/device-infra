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

package com.google.devtools.mobileharness.infra.client.longrunningservice;

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.collect.Iterables;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import javax.inject.Inject;

public class SessionPluginForTesting {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionInfo sessionInfo;

  @Inject
  SessionPluginForTesting(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event) throws InvalidProtocolBufferException {
    SessionPluginForTestingConfig config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(SessionPluginForTestingConfig.class);

    logger.atInfo().log("Creating JobInfo, config=%s", shortDebugString(config));

    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("fake_job_name"))
            .setType(JobType.newBuilder().setDevice("NoOpDevice").setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .build())
            .build();
    jobInfo.params().add("sleep_time_sec", Integer.toString(config.getNoOpDriverSleepTimeSec()));

    sessionInfo.addJob(jobInfo);
    logger.atInfo().log("JobInfo added");
  }

  @Subscribe
  public void onJobEnded(JobEndEvent event) {
    logger.atInfo().log("Handling JobEndEvent");

    sessionInfo.putSessionProperty(
        "job_result_from_job_event", event.getJob().resultWithCause().get().type().name());

    logger.atInfo().log("JobEndEvent handled");
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) {
    logger.atInfo().log("Parsing job result");

    JobInfo jobInfo = Iterables.getOnlyElement(sessionInfo.getAllJobs());
    String jobResultTypeName = jobInfo.resultWithCause().get().type().name();
    sessionInfo.putSessionProperty("job_result", jobResultTypeName);

    sessionInfo.setSessionPluginOutput(
        oldOutput ->
            SessionPluginOutput.newBuilder()
                .setOutput(
                    Any.pack(
                        SessionPluginForTestingOutput.newBuilder()
                            .setJobResultTypeName(jobResultTypeName)
                            .build()))
                .build());

    logger.atInfo().log("Job result parsed");
  }
}
