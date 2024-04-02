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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestEndedEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig.CommandCase;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.ResultCase;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyTestInfoMapHelper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.api.driver.XtsTradefedTest;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** OmniLab long-running client session plugin for ATS 2.0. */
public class AtsSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Object tradefedJobsLock = new Object();

  @GuardedBy("tradefedJobsLock")
  private final Map<String, Boolean> runningTradefedJobs = new HashMap<>();

  private final SessionInfo sessionInfo;
  private final DumpEnvVarCommandHandler dumpEnvVarCommandHandler;
  private final DumpStackTraceCommandHandler dumpStackCommandHandler;
  private final DumpUptimeCommandHandler dumpUptimeCommandHandler;
  private final ListDevicesCommandHandler listDevicesCommandHandler;
  private final ListModulesCommandHandler listModulesCommandHandler;
  private final RunCommandHandler runCommandHandler;

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsSessionPluginConfig config;

  /** Whether the first device is allocated. */
  private final AtomicBoolean started = new AtomicBoolean();

  /** Devices that are being allocated to tests of this session, sorted by adding order. */
  @GuardedBy("itself")
  private final Map<Device.DeviceLocator, DeviceInfo> allocatedDevices = new LinkedHashMap<>();

  @Inject
  AtsSessionPlugin(
      SessionInfo sessionInfo,
      DumpEnvVarCommandHandler dumpEnvVarCommandHandler,
      DumpStackTraceCommandHandler dumpStackCommandHandler,
      DumpUptimeCommandHandler dumpUptimeCommandHandler,
      ListDevicesCommandHandler listDevicesCommandHandler,
      ListModulesCommandHandler listModulesCommandHandler,
      RunCommandHandler runCommandHandler) {
    this.sessionInfo = sessionInfo;
    this.dumpEnvVarCommandHandler = dumpEnvVarCommandHandler;
    this.dumpStackCommandHandler = dumpStackCommandHandler;
    this.dumpUptimeCommandHandler = dumpUptimeCommandHandler;
    this.listDevicesCommandHandler = listDevicesCommandHandler;
    this.listModulesCommandHandler = listModulesCommandHandler;
    this.runCommandHandler = runCommandHandler;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws MobileHarnessException, InvalidProtocolBufferException, InterruptedException {
    config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsSessionPluginConfig.class);
    logger.atInfo().log("Config: %s", shortDebugString(config));

    onSessionStarting();
  }

  private void onSessionStarting() throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase().equals(CommandCase.RUN_COMMAND)) {
      RunCommand runCommand = config.getRunCommand();

      // Sets state.
      setRunCommandState(
          oldState ->
              runCommand.getInitialState().toBuilder()
                  .setStateSummary(String.format("running %s", runCommand.getTestPlan()))
                  .build());

      ImmutableList<String> tradefedJobIds =
          runCommandHandler.addTradefedJobs(runCommand, sessionInfo);
      synchronized (tradefedJobsLock) {
        for (String tradefedJobId : tradefedJobIds) {
          runningTradefedJobs.putIfAbsent(tradefedJobId, true);
        }
      }
      if (tradefedJobIds.isEmpty()) {
        logger.atInfo().log(
            "On session [%s] starting, no tradefed job was added, try add non-tradefed jobs if"
                + " needed.",
            sessionInfo.getSessionId());
        runCommandHandler.addNonTradefedJobs(runCommand, sessionInfo);
      }
      return;
    } else if (config.getCommandCase().equals(CommandCase.LIST_COMMAND)) {
      ListCommand listCommand = config.getListCommand();
      if (listCommand.getCommandCase().equals(ListCommand.CommandCase.LIST_DEVICES_COMMAND)) {
        AtsSessionPluginOutput output =
            listDevicesCommandHandler.handle(listCommand.getListDevicesCommand());
        setOutput(output);
        return;
      } else if (listCommand
          .getCommandCase()
          .equals(ListCommand.CommandCase.LIST_MODULES_COMMAND)) {
        AtsSessionPluginOutput output =
            listModulesCommandHandler.handle(listCommand.getListModulesCommand());
        setOutput(output);
        return;
      }
    } else if (config.getCommandCase().equals(CommandCase.DUMP_COMMAND)) {
      DumpCommand dumpCommand = config.getDumpCommand();
      if (dumpCommand.getCommandCase().equals(DumpCommand.CommandCase.DUMP_STACK_TRACE_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpStackCommandHandler.handle(dumpCommand.getDumpStackTraceCommand());
        setOutput(output);
        return;
      } else if (dumpCommand
          .getCommandCase()
          .equals(DumpCommand.CommandCase.DUMP_ENV_VAR_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpEnvVarCommandHandler.handle(dumpCommand.getDumpEnvVarCommand());
        setOutput(output);
        return;
      } else if (dumpCommand.getCommandCase().equals(DumpCommand.CommandCase.DUMP_UPTIME_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpUptimeCommandHandler.handle(dumpCommand.getDumpUptimeCommand());
        setOutput(output);
        return;
      }
    }
    setOutput(
        AtsSessionPluginOutput.newBuilder()
            .setFailure(
                Failure.newBuilder()
                    .setErrorMessage(String.format("Unimplemented [%s]", shortDebugString(config))))
            .build());
  }

  private String collectXtsTestResultSummary(List<JobInfo> jobInfos) {
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
    int nonTradefedTotalTestCaseNumber = 0;
    int nonTradefedDoneTestCaseNumber = 0;

    for (JobInfo jobInfo : jobInfos) {
      // Collect Tradefed Jobs test summary.
      if (jobInfo.properties().has(SessionRequestHandlerUtil.XTS_TF_JOB_PROP)) {
        tradefedDoneModuleNumber +=
            Integer.parseInt(jobInfo.params().get(XtsTradefedTest.TRADEFED_TESTS_DONE, "0"));
        tradefedTotalModuleNumber +=
            Integer.parseInt(jobInfo.params().get(XtsTradefedTest.TRADEFED_TESTS_TOTAL, "0"));
        tradefedPassedTestNumber +=
            Integer.parseInt(jobInfo.params().get(XtsTradefedTest.TRADEFED_TESTS_PASSED, "0"));
        tradefedFailedTestNumber +=
            Integer.parseInt(jobInfo.params().get(XtsTradefedTest.TRADEFED_TESTS_FAILED, "0"));
      }
      // Collect Non Tradefed Jobs test summary.
      if (jobInfo.properties().has(SessionRequestHandlerUtil.XTS_NON_TF_JOB_PROP)) {
        nonTradefedTotalModuleNumber++;
        if (jobInfo.params().has(MoblyTestInfoMapHelper.MOBLY_TESTS_DONE)) {
          nonTradefedDoneModuleNumber++;
        }
        nonTradefedPassedTestNumber +=
            Integer.parseInt(jobInfo.params().get(MoblyTestInfoMapHelper.MOBLY_TESTS_PASSED, "0"));
        nonTradefedFailedTestNumber +=
            Integer.parseInt(
                jobInfo.params().get(MoblyTestInfoMapHelper.MOBLY_TESTS_FAILED_AND_ERROR, "0"));
        nonTradefedSkippedTestNumber +=
            Integer.parseInt(jobInfo.params().get(MoblyTestInfoMapHelper.MOBLY_TESTS_SKIPPED, "0"));
        nonTradefedTotalTestCaseNumber +=
            Integer.parseInt(jobInfo.params().get(MoblyTestInfoMapHelper.MOBLY_TESTS_TOTAL, "0"));
        nonTradefedDoneTestCaseNumber +=
            Integer.parseInt(jobInfo.params().get(MoblyTestInfoMapHelper.MOBLY_TESTS_DONE, "0"));
      }
    }

    // Print out the xts test result summary.
    StringBuilder summaryReport = new StringBuilder();
    summaryReport.append("\n================= xTS total test result summary ================\n");
    summaryReport.append("=============== xTS Tradefed test result summary ===============\n");
    summaryReport.append(
        String.format(
            "%s/%s modules completed\n", tradefedDoneModuleNumber, tradefedTotalModuleNumber));
    summaryReport.append(
        String.format("PASSED TESTCASES           : %s\n", tradefedPassedTestNumber));
    summaryReport.append(
        String.format("FAILED TESTCASES           : %s\n", tradefedFailedTestNumber));
    summaryReport.append("=============== xTS Non Tradefed test result summary ===========\n");
    summaryReport.append(
        String.format(
            "%s/%s modules completed\n",
            nonTradefedDoneModuleNumber, nonTradefedTotalModuleNumber));
    summaryReport.append(
        String.format(
            "%s/%s testcases completed\n",
            nonTradefedDoneTestCaseNumber, nonTradefedTotalTestCaseNumber));
    summaryReport.append(
        String.format("PASSED TESTCASES           : %s\n", nonTradefedPassedTestNumber));
    summaryReport.append(
        String.format("FAILED TESTCASES           : %s\n", nonTradefedFailedTestNumber));
    summaryReport.append(
        String.format("SKIPPED TESTCASES          : %s\n", nonTradefedSkippedTestNumber));
    summaryReport.append("=================== End of Results =============================\n");
    summaryReport.append("================================================================\n");
    return summaryReport.toString();
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    String summaryReport = collectXtsTestResultSummary(sessionInfo.getAllJobs());
    // If the result has been set, returns immediately.
    if (sessionInfo
        .getSessionPluginOutput(AtsSessionPluginOutput.class)
        .map(output -> !output.getResultCase().equals(ResultCase.RESULT_NOT_SET))
        .orElse(false)) {
      return;
    }

    if (config.getCommandCase().equals(CommandCase.RUN_COMMAND)) {
      RunCommand runCommand = config.getRunCommand();
      try {
        runCommandHandler.handleResultProcessing(runCommand, sessionInfo, summaryReport);
      } finally {
        setRunCommandState(
            oldState ->
                oldState.toBuilder()
                    .setStateSummary(String.format("finished %s", runCommand.getTestPlan()))
                    .build());
      }
    }
  }

  @Subscribe
  public void onJobEnd(JobEndEvent jobEndEvent)
      throws MobileHarnessException, InterruptedException {
    synchronized (tradefedJobsLock) {
      String jobId = jobEndEvent.getJob().locator().getId();
      if (!runningTradefedJobs.containsKey(jobId)) {
        return;
      }
      runningTradefedJobs.put(jobId, false);
      if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
        logger.atInfo().log(
            "All added tradefed jobs have been done, try add non-tradefed jobs if needed.");
        runCommandHandler.addNonTradefedJobs(config.getRunCommand(), sessionInfo);
      }
    }
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) {
    // Sets start time.
    if (started.compareAndSet(false, true)) {
      setRunCommandState(
          oldState ->
              oldState.toBuilder().setStartTime(TimeUtils.toProtoTimestamp(Instant.now())).build());
    }

    // Updates allocated devices.
    ImmutableList<DeviceInfo> devices = event.getAllDeviceInfos();
    synchronized (allocatedDevices) {
      for (DeviceInfo device : devices) {
        allocatedDevices.put(device.getDeviceLocator(), device);
      }
      updateAllocatedDevicesInState();
    }
  }

  @Subscribe
  public void onTestEnded(TestEndedEvent event) {
    // Updates allocated devices.
    ImmutableList<DeviceLocator> devices = event.getAllocation().getAllDevices();
    synchronized (allocatedDevices) {
      for (DeviceLocator device : devices) {
        allocatedDevices.remove(device.toProto());
      }
      updateAllocatedDevicesInState();
    }
  }

  private void setOutput(AtsSessionPluginOutput output) {
    sessionInfo.setSessionPluginOutput(oldOutput -> output, AtsSessionPluginOutput.class);
    logger.atInfo().log("Output: %s", shortDebugString(output));
  }

  @GuardedBy("allocatedDevices")
  private void updateAllocatedDevicesInState() {
    ImmutableList<String> deviceIds =
        allocatedDevices.keySet().stream()
            .map(Device.DeviceLocator::getId)
            .collect(toImmutableList());
    setRunCommandState(
        oldState -> oldState.toBuilder().clearDeviceId().addAllDeviceId(deviceIds).build());
  }

  private void setRunCommandState(UnaryOperator<RunCommandState> runCommandStateUpdater) {
    sessionInfo.setSessionPluginOutput(
        oldOutput -> {
          AtsSessionPluginOutput.Builder newOutput =
              oldOutput == null ? AtsSessionPluginOutput.newBuilder() : oldOutput.toBuilder();
          RunCommandState oldState = newOutput.getRunCommandState();
          RunCommandState newState = runCommandStateUpdater.apply(oldState);
          return newOutput.setRunCommandState(newState).build();
        },
        AtsSessionPluginOutput.class);
  }
}
