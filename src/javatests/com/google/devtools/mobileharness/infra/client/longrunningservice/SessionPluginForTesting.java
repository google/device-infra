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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionPluginForTestingProto.SessionPluginForTestingOutput;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import java.time.Duration;
import javax.inject.Inject;

public class SessionPluginForTesting {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionInfo sessionInfo;

  @Inject
  SessionPluginForTesting(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws MobileHarnessException, InvalidProtocolBufferException {
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
                    .setTimeout(
                        Timeout.newBuilder()
                            .setStartTimeoutMs(
                                Duration.ofSeconds(
                                        config.hasStartTimeoutSec()
                                            ? config.getStartTimeoutSec()
                                            : 15L)
                                    .toMillis())
                            .build())
                    .build())
            .build();
    jobInfo.dimensions().addAll(config.getJobDeviceDimensionsMap());
    jobInfo.params().add("sleep_time_sec", Integer.toString(config.getNoOpDriverSleepTimeSec()));
    jobInfo.files().addAll(config.getExtraJobFilesMap());

    sessionInfo.addJob(jobInfo);
    logger.atInfo().log("JobInfo added");
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) {
    logger.atInfo().log("Handling TestStartingEvent");

    sessionInfo.putSessionProperty(
        "allocated_device_control_id",
        event.getDeviceFeature().getCompositeDimension().getSupportedDimensionList().stream()
            .filter(dimension -> dimension.getName().equals(Name.CONTROL_ID.lowerCaseName()))
            .map(DeviceDimension::getValue)
            .findFirst()
            .orElse("n/a"));

    logger.atInfo().log("TestStartingEvent handled");
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

    JobInfo jobInfo = requireNonNull(getOnlyElement(sessionInfo.getAllJobs()));
    String jobResultTypeName = jobInfo.resultWithCause().get().type().name();
    sessionInfo.putSessionProperty("job_result", jobResultTypeName);

    sessionInfo.setSessionPluginOutput(
        oldOutput ->
            SessionPluginForTestingOutput.newBuilder()
                .setJobResultTypeName(jobResultTypeName)
                .build(),
        SessionPluginForTestingOutput.class);

    logger.atInfo().log("Job result parsed");
  }
}
