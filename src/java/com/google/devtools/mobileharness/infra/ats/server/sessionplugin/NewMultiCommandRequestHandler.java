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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.CreateJobConfigUtil;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CancelReason;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.CommandState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.NewMultiCommandRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.RequestDetail.RequestState;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.gson.Gson;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Handler for ATS server's create test jobs request. */
final class NewMultiCommandRequestHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern ANDROID_XTS_ZIP_FILENAME_REGEX =
      Pattern.compile("android-[a-z]+\\.zip");
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";

  private final CreateJobConfigUtil createJobConfigUtil;

  @Inject
  NewMultiCommandRequestHandler(DeviceQuerier deviceQuerier) {
    this.createJobConfigUtil = new CreateJobConfigUtil(deviceQuerier);
  }

  void handle(NewMultiCommandRequest request, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    if (request.getCommandsList().isEmpty()) {
      sessionInfo.setSessionPluginOutput(
          oldOutput ->
              (oldOutput == null ? RequestDetail.newBuilder() : oldOutput.toBuilder())
                  .setId(sessionInfo.getSessionId())
                  .addAllCommandInfos(request.getCommandsList())
                  .setCancelReason(CancelReason.COMMAND_NOT_AVAILABLE)
                  .setState(RequestState.CANCELED)
                  .build(),
          RequestDetail.class);
      return;
    }
    for (CommandInfo commandInfo : request.getCommandsList()) {
      Optional<JobInfo> jobInfo = createXtsTradefedTestJob(request, commandInfo);
      if (jobInfo.isEmpty()) {
        CommandDetail commandDetail =
            CommandDetail.newBuilder()
                .setCommandLine(commandInfo.getCommandLine())
                .setState(CommandState.CANCELED)
                .setCancelReason(CancelReason.INVALID_REQUEST)
                .build();
        sessionInfo.setSessionPluginOutput(
            oldOutput ->
                (oldOutput == null ? RequestDetail.newBuilder() : oldOutput.toBuilder())
                    .setId(sessionInfo.getSessionId())
                    .addAllCommandInfos(request.getCommandsList())
                    .addCommandDetails(commandDetail)
                    .setCancelReason(CancelReason.INVALID_REQUEST)
                    .setState(RequestState.CANCELED)
                    .build(),
            RequestDetail.class);
        return;
      }
      jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
      sessionInfo.addJob(jobInfo.get());
      logger.atInfo().log(
          "Added job[%s] to the session %s",
          jobInfo.get().locator().getId(), sessionInfo.getSessionId());
    }
  }

  private Optional<JobInfo> createXtsTradefedTestJob(
      NewMultiCommandRequest request, CommandInfo commandInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobConfig> jobConfig = createXtsTradefedTestJobConfig(request, commandInfo);
    if (jobConfig.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(JobInfoCreator.createJobInfo(jobConfig.get(), ImmutableList.of(), null));
  }

  Optional<JobConfig> createXtsTradefedTestJobConfig(
      NewMultiCommandRequest request, CommandInfo commandInfo)
      throws MobileHarnessException, InterruptedException {
    // TODO: need to handle sharding.
    List<String> deviceSerials = new ArrayList<>();
    for (Entry<String, String> entry : commandInfo.getDeviceDimensionsMap().entrySet()) {
      if (entry.getKey().equals("device_serial")) {
        deviceSerials.add(entry.getValue());
      }
      // TODO: need to handle non device serial case.
    }
    String androidXtsZipPath = "";
    String fileUrlPrefix = "file://";
    for (TestResource testResource : request.getTestResourcesList()) {
      if (ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()
          && testResource.getUrl().startsWith(fileUrlPrefix)) {
        androidXtsZipPath = testResource.getUrl();
        break;
      }
    }
    if (androidXtsZipPath.isEmpty()) {
      logger.atInfo().log(
          "Didn't find android xts zip file in request resources: %s",
          request.getTestResourcesList());
      return Optional.empty();
    }
    int shardCount = commandInfo.getShardCount();
    ImmutableList<SubDeviceSpec> subDeviceSpecList =
        createJobConfigUtil.getSubDeviceSpecList(deviceSerials, shardCount);
    if (subDeviceSpecList.isEmpty()) {
      logger.atInfo().log("Found no devices to create the job config.");
      return Optional.empty();
    }
    String xtsType = Iterables.get(Splitter.on(' ').split(commandInfo.getCommandLine()), 0);
    String testPlan = xtsType;
    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName("xts-tradefed-test-job")
            .setExecMode("local")
            .setJobTimeoutSec(3 * 24 * 60 * 60)
            .setTestTimeoutSec(3 * 24 * 60 * 60)
            .setStartTimeoutSec(5 * 60)
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-tradefed-test-%s", testPlan)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));

    Map<String, String> driverParams = new HashMap<>();

    driverParams.put("xts_type", xtsType.toUpperCase(Locale.ROOT));
    driverParams.put("android_xts_zip", androidXtsZipPath);
    driverParams.put("xts_test_plan", testPlan);

    String commandArgs =
        commandInfo.getCommandLine().substring(commandInfo.getCommandLine().indexOf(" ") + 1);
    if (!commandArgs.isEmpty()) {
      driverParams.put("run_command_args", commandArgs);
    }

    jobConfigBuilder.setDriver(
        Driver.newBuilder().setName("XtsTradefedTest").setParam(new Gson().toJson(driverParams)));
    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log("XtsTradefedTest job config: %s", shortDebugString(jobConfig));

    return Optional.of(jobConfig);
  }

  void handleResultProcessing(
      NewMultiCommandRequest newMultiCommandRequest, SessionInfo sessionInfo) {
    // TODO: To be implemented.
  }
}
