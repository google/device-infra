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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoDuration;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toProtoTimestamp;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.XtsDeviceCache;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionCancellation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig.CommandCase;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginNotification;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginNotification.TypeCase;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState.Invocation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommandState.Invocations;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.WithProto;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** OmniLab long-running client session plugin for ATS console. */
@WithProto({
  AtsSessionPluginConfig.class,
  AtsSessionPluginOutput.class,
  AtsSessionPluginNotification.class
})
public class AtsSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final AtomicInteger NEXT_RUN_COMMAND_ID = new AtomicInteger(1);

  private final Object testCancellationLock = new Object();
  private final Object runningTestsLock = new Object();

  private final SessionInfo sessionInfo;
  private final DumpEnvVarCommandHandler dumpEnvVarCommandHandler;
  private final DumpStackTraceCommandHandler dumpStackCommandHandler;
  private final DumpUptimeCommandHandler dumpUptimeCommandHandler;
  private final ListDevicesCommandHandler listDevicesCommandHandler;
  private final ListModulesCommandHandler listModulesCommandHandler;
  private final RunCommandHandler runCommandHandler;
  private final TestMessageUtil testMessageUtil;
  private final XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  private final LocalFileUtil localFileUtil;
  private final XtsDeviceCache xtsDeviceCache;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  @GuardedBy("itself")
  private final Map<String, Boolean> runningTradefedJobs = new HashMap<>();

  @GuardedBy("runningTestsLock")
  private final Map<String, RunningTradefedTest> runningTradefedTests = new ConcurrentHashMap<>();

  @GuardedBy("testCancellationLock")
  private final List<TestInfo> startedTestsBeforeCancellation = new ArrayList<>();

  @GuardedBy("testCancellationLock")
  private XtsTradefedRunCancellation cancellationTestMessage;

  private final Object addingJobLock = new Object();

  @GuardedBy("addingJobLock")
  private AtsSessionCancellation sessionCancellation;

  @GuardedBy("itself")
  private final Set<String> cachedDeviceControlIds = new HashSet<>();

  @GuardedBy("addingJobLock")
  private boolean sessionEnded;

  @GuardedBy("itself")
  private final List<JobInfo> additionalTradefedJobs = new ArrayList<>();

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsSessionPluginConfig config;

  private ImmutableList<JobInfo> nonTradefedJobs = ImmutableList.of();

  @Inject
  AtsSessionPlugin(
      SessionInfo sessionInfo,
      DumpEnvVarCommandHandler dumpEnvVarCommandHandler,
      DumpStackTraceCommandHandler dumpStackCommandHandler,
      DumpUptimeCommandHandler dumpUptimeCommandHandler,
      ListDevicesCommandHandler listDevicesCommandHandler,
      ListModulesCommandHandler listModulesCommandHandler,
      RunCommandHandler runCommandHandler,
      TestMessageUtil testMessageUtil,
      XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil,
      LocalFileUtil localFileUtil,
      XtsDeviceCache xtsDeviceCache) {
    this.sessionInfo = sessionInfo;
    this.dumpEnvVarCommandHandler = dumpEnvVarCommandHandler;
    this.dumpStackCommandHandler = dumpStackCommandHandler;
    this.dumpUptimeCommandHandler = dumpUptimeCommandHandler;
    this.listDevicesCommandHandler = listDevicesCommandHandler;
    this.listModulesCommandHandler = listModulesCommandHandler;
    this.runCommandHandler = runCommandHandler;
    this.testMessageUtil = testMessageUtil;
    this.xtsTradefedRuntimeInfoFileUtil = xtsTradefedRuntimeInfoFileUtil;
    this.localFileUtil = localFileUtil;
    this.xtsDeviceCache = xtsDeviceCache;
    this.scheduledThreadPool =
        ThreadPools.createStandardScheduledThreadPool(
            "ats-session-plugin-scheduled-thread-pool-" + sessionInfo.getSessionId(),
            /* corePoolSize= */ 2);
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
    if (config.getCommandCase() == CommandCase.RUN_COMMAND) {
      RunCommand runCommand = config.getRunCommand();

      String commandId = Integer.toString(NEXT_RUN_COMMAND_ID.getAndIncrement());

      synchronized (runningTestsLock) {
        setRunCommandState(
            oldState -> runCommand.getInitialState().toBuilder().setCommandId(commandId).build());
      }

      sessionInfo.putSessionProperty(SessionProperties.PROPERTY_KEY_COMMAND_ID, commandId);
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Command [%s] scheduled, args=[%s]",
              commandId, runCommand.getInitialState().getCommandLineArgs());
      return;
    } else if (config.getCommandCase() == CommandCase.LIST_COMMAND) {
      ListCommand listCommand = config.getListCommand();
      if (listCommand.getCommandCase() == ListCommand.CommandCase.LIST_DEVICES_COMMAND) {
        AtsSessionPluginOutput output =
            listDevicesCommandHandler.handle(listCommand.getListDevicesCommand());
        setFinalOutputForNonRunCommand(output);
        return;
      } else if (listCommand.getCommandCase() == ListCommand.CommandCase.LIST_MODULES_COMMAND) {
        AtsSessionPluginOutput output =
            listModulesCommandHandler.handle(listCommand.getListModulesCommand());
        setFinalOutputForNonRunCommand(output);
        return;
      }
    } else if (config.getCommandCase() == CommandCase.DUMP_COMMAND) {
      DumpCommand dumpCommand = config.getDumpCommand();
      if (dumpCommand.getCommandCase() == DumpCommand.CommandCase.DUMP_STACK_TRACE_COMMAND) {
        AtsSessionPluginOutput output =
            dumpStackCommandHandler.handle(dumpCommand.getDumpStackTraceCommand());
        setFinalOutputForNonRunCommand(output);
        return;
      } else if (dumpCommand.getCommandCase() == DumpCommand.CommandCase.DUMP_ENV_VAR_COMMAND) {
        AtsSessionPluginOutput output =
            dumpEnvVarCommandHandler.handle(dumpCommand.getDumpEnvVarCommand());
        setFinalOutputForNonRunCommand(output);
        return;
      } else if (dumpCommand.getCommandCase() == DumpCommand.CommandCase.DUMP_UPTIME_COMMAND) {
        AtsSessionPluginOutput output =
            dumpUptimeCommandHandler.handle(dumpCommand.getDumpUptimeCommand());
        setFinalOutputForNonRunCommand(output);
        return;
      }
    }
    setFinalOutputForNonRunCommand(
        AtsSessionPluginOutput.newBuilder()
            .setFailure(
                Failure.newBuilder()
                    .setErrorMessage(String.format("Unimplemented [%s]", shortDebugString(config))))
            .build());
  }

  @Subscribe
  public void onSessionStarted(SessionStartedEvent event)
      throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase() == CommandCase.RUN_COMMAND) {
      RunCommand runCommand = config.getRunCommand();
      RunCommandState runCommandState = getRunCommandState();

      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Command [%s] started, args=[%s]",
              runCommandState.getCommandId(), runCommand.getInitialState().getCommandLineArgs());
      runCommandHandler.initialize(runCommand);

      // Create tradefed jobs.
      ImmutableList<JobInfo> tradefedJobs;
      try {
        tradefedJobs = runCommandHandler.createTradefedJobs(runCommand);
      } catch (MobileHarnessException e) {
        if (!XtsJobCreator.isSkippableException(e)) {
          throw e;
        }
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "Failed to create tradefed jobs for session [%s] due to skippable exception: [%s].",
                sessionInfo.getSessionId(), MoreThrowables.shortDebugString(e));
        tradefedJobs = ImmutableList.of();
      }

      // Create non-tradefed jobs.
      try {
        nonTradefedJobs = runCommandHandler.createNonTradefedJobs(runCommand);
      } catch (MobileHarnessException e) {
        if (!XtsJobCreator.isSkippableException(e)) {
          throw e;
        }
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "Failed to create non-tradefed jobs for session [%s] due to skippable exception:"
                    + " [%s].",
                sessionInfo.getSessionId(), MoreThrowables.shortDebugString(e));
        nonTradefedJobs = ImmutableList.of();
      }
      if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION,
            "No jobs created for session " + sessionInfo.getSessionId(),
            /* cause= */ null);
      }

      // If have several tradefed jobs, add them to session one by one. Save the unstarted jobs in
      // additionalTradefedJobs.
      if (tradefedJobs.size() > 1) {
        List<JobInfo> tradefedJobsToAdd = new ArrayList<>();
        for (JobInfo tradefedJob : tradefedJobs) {
          // To first execute the CTS job.
          if (tradefedJob.locator().getName().contains(XtsConstants.STATIC_XTS_JOB_NAME)) {
            tradefedJobsToAdd.add(tradefedJob);
          } else {
            synchronized (additionalTradefedJobs) {
              additionalTradefedJobs.add(tradefedJob);
            }
          }
        }

        // No static CTS job found.
        if (tradefedJobsToAdd.isEmpty()) {
          synchronized (additionalTradefedJobs) {
            tradefedJobsToAdd.add(additionalTradefedJobs.remove(0));
          }
        }

        addJobListToSession(tradefedJobsToAdd);
      } else {
        addJobListToSession(tradefedJobs);
      }

      // Starts TF runtime info updater.
      logFailure(
          scheduledThreadPool.scheduleWithFixedDelay(
              threadRenaming(
                  this::updateTradefedRuntimeInfo,
                  () -> "tradefed-runtime-info-updater-" + sessionInfo.getSessionId()),
              Duration.ofSeconds(5L),
              Duration.ofSeconds(5L)),
          Level.WARNING,
          "Fatal error in Tradefed runtime info updater");
    }
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    synchronized (addingJobLock) {
      sessionEnded = true;
    }

    // Invalidates xTS device caches.
    synchronized (cachedDeviceControlIds) {
      if (!cachedDeviceControlIds.isEmpty()) {
        logger.atInfo().log("Invalidate xTS device caches: %s", cachedDeviceControlIds);
        cachedDeviceControlIds.forEach(xtsDeviceCache::invalidateCache);
        cachedDeviceControlIds.clear();
      }
    }

    scheduledThreadPool.shutdown();

    if (config.getCommandCase().equals(CommandCase.RUN_COMMAND)) {
      // Processes results.
      runCommandHandler.handleResultProcessing(config.getRunCommand(), getRunCommandState());
    }
  }

  @Subscribe
  public void onJobEnd(JobEndEvent jobEndEvent)
      throws MobileHarnessException, InterruptedException {
    synchronized (runningTradefedJobs) {
      String jobId = jobEndEvent.getJob().locator().getId();
      if (!runningTradefedJobs.containsKey(jobId)) {
        return;
      }
      runningTradefedJobs.put(jobId, false);

      // Add the additional tradefed jobs if needed.
      synchronized (additionalTradefedJobs) {
        if (!additionalTradefedJobs.isEmpty()) {
          ImmutableSet<String> devicesOfCurrentJob = getDeviceSerials(jobEndEvent.getJob());
          List<JobInfo> additionalTradefedJobsToAdd = new ArrayList<>();
          for (JobInfo additionalTradefedJob : additionalTradefedJobs) {
            // Add the device ids of the current job to the sub device specs of the additional
            // tradefed job.
            addDeviceIdsToSubDeviceSpecs(
                additionalTradefedJob.subDeviceSpecs().getAllSubDevices(), devicesOfCurrentJob);
          }
          additionalTradefedJobsToAdd.add(additionalTradefedJobs.remove(0));

          addJobListToSession(additionalTradefedJobsToAdd);
        }
      }

      if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
        logger.atInfo().log(
            "All added tradefed jobs have been done, try add non-tradefed jobs if needed.");
        addJobsToSession(nonTradefedJobs);
      }
    }
  }

  @Subscribe
  public void onJobStart(JobStartEvent jobStartEvent) {
    ImmutableList<String> testIds =
        jobStartEvent.getJob().tests().getAll().values().stream()
            .map(testInfo -> testInfo.locator().getId())
            .collect(toImmutableList());
    RunCommandState runCommandState = getRunCommandState();
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log(
            "Command [%s]'s invocations [%s] are waiting for devices.",
            runCommandState.getCommandId(), String.join(", ", testIds));
  }

  @Subscribe
  public void onTestStarting(LocalTestStartingEvent event) {
    TestInfo testInfo = event.getTest();
    boolean tfTest = testInfo.jobInfo().properties().getBoolean(Job.IS_XTS_TF_JOB).orElse(false);
    ImmutableList<String> deviceSerials =
        event.getAllocation().getAllDeviceLocators().stream()
            .map(DeviceLocator::getSerial)
            .collect(toImmutableList());

    synchronized (runningTestsLock) {
      AtomicReference<Invocations> testInvocations = new AtomicReference<>();
      setRunCommandState(
          oldState -> {
            String testId = testInfo.locator().getId();
            logger
                .atInfo()
                .with(IMPORTANCE, IMPORTANT)
                .log(
                    "Command [%s]'s invocation [%s] allocated devices [%s].",
                    oldState.getCommandId(), testId, String.join(", ", deviceSerials));
            Timestamp now = toProtoTimestamp(Instant.now());
            testInvocations.set(
                Invocations.newBuilder()
                    .setStartTime(now)
                    .addInvocation(
                        Invocation.newBuilder()
                            .setCommandId(oldState.getCommandId())
                            .setStartTime(now)
                            .addAllDeviceId(deviceSerials)
                            .setStateSummary(config.getRunCommand().getTestPlan()))
                    .build());
            return oldState.toBuilder().putRunningInvocation(testId, testInvocations.get()).build();
          });

      if (tfTest) {
        runningTradefedTests.put(
            testInfo.locator().getId(), new RunningTradefedTest(testInfo, testInvocations.get()));
      }
    }

    sessionInfo.putSessionProperty(
        SessionProperties.PROPERTY_KEY_SESSION_CONTAIN_STARTED_TEST, "true");

    // Sends cancellation test message if necessary.
    synchronized (testCancellationLock) {
      if (cancellationTestMessage == null) {
        startedTestsBeforeCancellation.add(testInfo);
      } else {
        sendCancellationMessageToStartedTest(testInfo, cancellationTestMessage);
      }
    }

    // Caches devices (as a xTS type) used in the test.
    // The intention is to make sure if any device goes offline between job runs, the next job
    // shouldn't be blocked on waiting for the device to become online, so we cache the devices
    // here.
    if (Flags.instance().atsConsoleCacheXtsDevices.getNonNull()) {
      synchronized (cachedDeviceControlIds) {
        event
            .getLocalDevices()
            .forEach(
                (deviceId, device) -> {
                  xtsDeviceCache.cache(
                      deviceId, device.getClass().getSimpleName(), ChronoUnit.YEARS.getDuration());
                  cachedDeviceControlIds.add(deviceId);
                });
      }
    }
  }

  @Subscribe
  public void onTestEnded(TestEndedEvent event) {
    TestInfo testInfo = event.getTest();

    synchronized (runningTestsLock) {
      runningTradefedTests.remove(testInfo.locator().getId());

      setRunCommandState(
          oldState -> {
            String testId = testInfo.locator().getId();
            logger
                .atInfo()
                .with(IMPORTANCE, IMPORTANT)
                .log("Command [%s]'s invocations completed.", oldState.getCommandId());
            return oldState.toBuilder()
                .setTotalExecutionTime(
                    toProtoDuration(
                        toJavaDuration(oldState.getTotalExecutionTime())
                            .plus(
                                Duration.between(testInfo.timing().getStartTime(), Instant.now()))))
                .removeRunningInvocation(testId)
                .build();
          });
    }

    ResultTypeWithCause resultTypeWithCause = testInfo.resultWithCause().get();
    if (resultTypeWithCause.type().equals(TestResult.SKIP)) {
      String shortSkipReason = "";
      if (resultTypeWithCause.causeProto().isPresent()) {
        shortSkipReason = resultTypeWithCause.causeProto().get().getSummary().getMessage();
      }
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Test [%s/%s] was skipped with reason [%s]",
              testInfo.locator().getId(), testInfo.locator().getName(), shortSkipReason);
      // Don't show detailed skip reason with stack trace in the console but just in olc server logs
      logger.atInfo().log(
          "Skip reason of test [%s/%s]:\n%s",
          testInfo.locator().getId(),
          testInfo.locator().getName(),
          resultTypeWithCause.toStringWithDetail());
    } else if (!resultTypeWithCause.type().equals(TestResult.PASS)) {
      logger
          .atWarning()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Warning of test [%s/%s]:\n%s",
              testInfo.locator().getId(),
              testInfo.locator().getName(),
              resultTypeWithCause.toStringWithDetail());
    }
  }

  @Subscribe
  public void onSessionNotification(SessionNotificationEvent event)
      throws InvalidProtocolBufferException {
    AtsSessionPluginNotification notification =
        event.sessionNotification().getNotification().unpack(AtsSessionPluginNotification.class);
    logger.atInfo().log("Notification: %s", shortDebugString(notification));

    if (notification.getTypeCase() == TypeCase.SESSION_CANCELLATION) {
      onSessionCancellation(notification.getSessionCancellation());
    }
  }

  private void updateTradefedRuntimeInfo() {
    // Gets a snapshot of running TF tests.
    Collection<RunningTradefedTest> runningTradefedTests;
    synchronized (runningTestsLock) {
      runningTradefedTests = this.runningTradefedTests.values();
    }

    // Updates runtime info.
    List<RunningTradefedTest> updatedTests = new ArrayList<>();
    for (RunningTradefedTest test : runningTradefedTests) {
      if (test.update()) {
        updatedTests.add(test);
      }
    }

    // Writes to RunCommandState.
    if (!updatedTests.isEmpty()) {
      synchronized (runningTestsLock) {
        setRunCommandState(
            oldState -> {
              RunCommandState.Builder result = oldState.toBuilder();
              for (RunningTradefedTest updatedTest : updatedTests) {
                String testId = updatedTest.testInfo.locator().getId();

                // Checks if the test still exists.
                if (oldState.containsRunningInvocation(testId)) {
                  Invocations invocations = updatedTest.invocations;
                  logger
                      .atInfo()
                      .with(IMPORTANCE, IMPORTANT)
                      .log(
                          "Updated invocation info of test [%s]: %s",
                          testId, shortDebugString(invocations));
                  result.putRunningInvocation(testId, invocations);
                }
              }
              return result.build();
            });
      }
    }
  }

  /**
   * Add jobs to the session.
   *
   * @return a list of job IDs of the added jobs
   */
  @CanIgnoreReturnValue
  private ImmutableList<String> addJobsToSession(ImmutableList<JobInfo> jobInfos) {
    synchronized (addingJobLock) {
      if (sessionCancellation != null) {
        logger.atInfo().log(
            "Skip adding jobs to session due to [%s]", shortDebugString(sessionCancellation));
        return ImmutableList.of();
      }
      if (sessionEnded) {
        logger.atInfo().log("Skip adding jobs to session because session ended");
        return ImmutableList.of();
      }

      // Adds jobs to session.
      for (JobInfo jobInfo : jobInfos) {
        sessionInfo.addJob(jobInfo);
      }
    }

    return jobInfos.stream().map(jobInfo -> jobInfo.locator().getId()).collect(toImmutableList());
  }

  /** TODO: Support killing jobs here (for non-TF jobs or jobs during allocation). */
  private void onSessionCancellation(AtsSessionCancellation sessionCancellation) {
    // Stops adding new jobs.
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Stop adding new jobs due to [%s]", shortDebugString(sessionCancellation));
    synchronized (addingJobLock) {
      this.sessionCancellation = sessionCancellation;
    }

    // Creates test message.
    XtsTradefedRunCancellation cancellationTestMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(
                sessionCancellation.getAggressive()
                    ? KillSignal.SIGTERM.value()
                    : KillSignal.SIGTSTP.value())
            .setCancelReason(sessionCancellation.getReason())
            .build();

    // Sends test message to started tests.
    ImmutableList<TestInfo> startedTestsBeforeCancellation;
    synchronized (testCancellationLock) {
      if (this.cancellationTestMessage != null) {
        logger.atInfo().log(
            "Session has been cancelled, current cancellation [%s], previous"
                + " cancellation [%s]",
            shortDebugString(cancellationTestMessage),
            shortDebugString(this.cancellationTestMessage));
      }
      this.cancellationTestMessage = cancellationTestMessage;
      startedTestsBeforeCancellation = ImmutableList.copyOf(this.startedTestsBeforeCancellation);
      this.startedTestsBeforeCancellation.clear();
    }
    for (TestInfo testInfo : startedTestsBeforeCancellation) {
      sendCancellationMessageToStartedTest(testInfo, cancellationTestMessage);
    }
  }

  /** TODO: Don't send to non-TF tests. */
  private void sendCancellationMessageToStartedTest(
      TestInfo testInfo, XtsTradefedRunCancellation cancellationTestMessage) {
    logger.atInfo().log(
        "Send cancellation message to test [%s]: [%s]",
        testInfo.locator().getId(), shortDebugString(cancellationTestMessage));
    try {
      testMessageUtil.sendProtoMessageToTest(testInfo, cancellationTestMessage);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to send cancellation message to test [%s]: [%s]",
          testInfo.locator().getId(), shortDebugString(cancellationTestMessage));
    }
  }

  /** Notes that this method will override the whole previous output if any. */
  private void setFinalOutputForNonRunCommand(AtsSessionPluginOutput finalOutput) {
    sessionInfo.setSessionPluginOutput(oldOutput -> finalOutput, AtsSessionPluginOutput.class);
    logger.atInfo().log("Output: %s", shortDebugString(finalOutput));
  }

  @GuardedBy("runningTestsLock")
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

  private RunCommandState getRunCommandState() {
    return sessionInfo
        .getSessionPluginOutput(AtsSessionPluginOutput.class)
        .orElse(AtsSessionPluginOutput.getDefaultInstance())
        .getRunCommandState();
  }

  private ImmutableSet<String> getDeviceSerials(JobInfo jobInfo) {
    return jobInfo.tests().getAll().values().stream()
        .map(testInfo -> testInfo.properties().getOptional(Test.DEVICE_ID_LIST))
        .filter(Optional::isPresent)
        .flatMap(ids -> stream(ids.get().split(",")))
        .collect(toImmutableSet());
  }

  private void addDeviceIdsToSubDeviceSpecs(
      List<SubDeviceSpec> subDeviceSpecs, ImmutableSet<String> deviceIds) {

    if (subDeviceSpecs.isEmpty() || deviceIds.isEmpty()) {
      return;
    }

    // Return if the number of device IDs is not equal to the number of sub-device specs.
    if (subDeviceSpecs.size() != deviceIds.size()) {
      return;
    }

    Iterator<String> deviceIdIterator = deviceIds.iterator();
    for (SubDeviceSpec subDeviceSpec : subDeviceSpecs) {
      String deviceId = deviceIdIterator.next();
      subDeviceSpec.dimensions().add(Name.ID.lowerCaseName(), deviceId);
    }
  }

  private void addJobListToSession(List<JobInfo> jobInfos) {
    ImmutableList<String> tradefedJobIds = addJobsToSession(ImmutableList.copyOf(jobInfos));
    if (!tradefedJobIds.isEmpty()) {
      synchronized (runningTradefedJobs) {
        for (String tradefedJobId : tradefedJobIds) {
          runningTradefedJobs.putIfAbsent(tradefedJobId, true);
        }
      }
    } else {
      logger.atInfo().log(
          "On session [%s] starting, no tradefed job was added, try add non-tradefed jobs if"
              + " needed.",
          sessionInfo.getSessionId());
      addJobsToSession(nonTradefedJobs);
    }
  }

  private class RunningTradefedTest {

    private final TestInfo testInfo;

    /** It should contain at least one invocation. */
    private final Invocations initialInvocations;

    /** Updated by {@link #update()}. */
    private volatile Invocations invocations;

    /** Updated by {@link #update()}. */
    @Nullable private volatile Instant runtimeInfoFileLastModifiedTime;

    private RunningTradefedTest(TestInfo testInfo, Invocations initialInvocations) {
      checkArgument(initialInvocations.getInvocationCount() > 0);
      this.testInfo = testInfo;
      this.initialInvocations = initialInvocations;
      this.invocations = initialInvocations;
    }

    /** Returns whether {@link #invocations} has been updated. */
    private boolean update() {
      // Checks if the file exists.
      Optional<Path> runtimeInfoFilePath =
          testInfo
              .properties()
              .getOptional(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_PATH)
              .filter(localFileUtil::isFileExist)
              .map(Path::of);
      if (runtimeInfoFilePath.isEmpty()) {
        return false;
      }

      // Reads the file.
      Optional<XtsTradefedRuntimeInfoFileDetail> fileDetailOptional;
      try {
        fileDetailOptional =
            xtsTradefedRuntimeInfoFileUtil.readInfo(
                runtimeInfoFilePath.get(), runtimeInfoFileLastModifiedTime);
      } catch (IOException | RuntimeException | Error e) {
        logger.atWarning().log(
            "Failed to read Tradefed runtime info of test %s from file %s",
            testInfo.locator().getId(), runtimeInfoFilePath.get());
        return false;
      }

      // If the file doesn't exist or is not updated, returns directly.
      if (fileDetailOptional.isEmpty()) {
        return false;
      }

      // Updates invocations.
      XtsTradefedRuntimeInfoFileDetail fileDetail = fileDetailOptional.get();
      invocations = convert(fileDetail.runtimeInfo());
      runtimeInfoFileLastModifiedTime = fileDetail.lastModifiedTime();
      return true;
    }

    private Invocations convert(XtsTradefedRuntimeInfo runtimeInfo) {
      Invocation initialFirstInvocation = initialInvocations.getInvocation(0);
      return initialInvocations.toBuilder()
          .clearInvocation()
          .addAllInvocation(
              runtimeInfo.invocations().stream()
                  .filter(TradefedInvocation::isRunning)
                  .map(
                      invocation ->
                          initialFirstInvocation.toBuilder()
                              .clearDeviceId()
                              .addAllDeviceId(invocation.deviceIds())
                              .setStateSummary(invocation.status())
                              .build())
                  .collect(toImmutableList()))
          .build();
    }
  }
}
