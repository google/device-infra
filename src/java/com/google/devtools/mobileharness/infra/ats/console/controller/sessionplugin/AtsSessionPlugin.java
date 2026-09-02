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
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.sessionplugin.SessionJobProvider;
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
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.ModuleRunResult;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionNotificationEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.WithProto;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache.CacheRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache.InvalidateCacheRequest;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
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
import com.google.protobuf.TextFormat;
import com.google.protobuf.Timestamp;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
public class AtsSessionPlugin implements SessionJobProvider {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final AtomicInteger NEXT_RUN_COMMAND_ID = new AtomicInteger(1);

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
  private final SessionDeviceCache sessionDeviceCache;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  @GuardedBy("itself")
  private final Map<String, Boolean> runningTradefedJobs = new HashMap<>();

  @GuardedBy("itself")
  private final Map<String, Boolean> runningNonTradefedJobs = new HashMap<>();

  @GuardedBy("runningTestsLock")
  private final Map<String, RunningTradefedTest> runningTradefedTests = new ConcurrentHashMap<>();

  @GuardedBy("testCancellationLock")
  private final List<TestInfo> startedTests = new ArrayList<>();

  @GuardedBy("testCancellationLock")
  private XtsTradefedRunCancellation lastCancellationTestMessage;

  private final Object addingJobLock = new Object();

  @GuardedBy("addingJobLock")
  private AtsSessionCancellation sessionCancellation;

  @GuardedBy("itself")
  private final Set<String> cachedDeviceControlIds = new HashSet<>();

  @GuardedBy("addingJobLock")
  private boolean sessionEnded;

  private final Queue<JobInfo> additionalTradefedJobs = new ConcurrentLinkedQueue<>();

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsSessionPluginConfig config;

  private volatile ImmutableList<JobInfo> tradefedJobs = ImmutableList.of();
  private volatile ImmutableList<JobInfo> nonTradefedJobs = ImmutableList.of();

  private final AtomicReference<JobInfo> setupJobRef = new AtomicReference<>();
  private final AtomicReference<JobInfo> teardownJobRef = new AtomicReference<>();
  private final AtomicReference<String> runningSetupJobId = new AtomicReference<>();
  private final AtomicReference<String> runningTeardownJobId = new AtomicReference<>();

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
      SessionDeviceCache sessionDeviceCache) {
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
    this.sessionDeviceCache = sessionDeviceCache;
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

      Optional<JobInfo> setupJobOpt = this.createSetupJob();
      Optional<JobInfo> teardownJobOpt = this.createTeardownJob();

      setupJobOpt.ifPresent(setupJobRef::set);
      teardownJobOpt.ifPresent(teardownJobRef::set);

      if (setupJobOpt.isPresent()) {
        addSetupJob(setupJobOpt.get());
      } else {
        createMainJobs(runCommand, /* dynamicMctsModules= */ ImmutableSet.of());
        addMainJobs();
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
        try {
          sessionDeviceCache.invalidateCache(
              new InvalidateCacheRequest(
                  LabLocator.LOCALHOST,
                  ImmutableList.copyOf(cachedDeviceControlIds),
                  "xts",
                  sessionInfo.getSessionId()));
        } catch (MobileHarnessException | InterruptedException e) {
          if (MoreThrowables.isInterruption(e)) {
            Thread.currentThread().interrupt();
          }
          logger.atWarning().withCause(e).log(
              "Failed to invalidate cache for devices %s", cachedDeviceControlIds);
        }
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
    JobInfo currentJob = jobEndEvent.getJob();
    if (currentJob.properties().getBoolean(Job.IS_XTS_NON_TF_JOB).orElse(false)) {
      for (TestInfo testInfo : currentJob.tests().getAll().values()) {
        ResultTypeWithCause resultWithCause = testInfo.resultWithCause().get();
        ModuleRunResult.Builder resultBuilder =
            ModuleRunResult.newBuilder().setResult(resultWithCause.type());
        if (resultWithCause.causeProto().isPresent()) {
          resultBuilder.setCause(resultWithCause.toStringWithDetail());
        }
        localFileUtil.writeToFile(
            Path.of(testInfo.getGenFileDir())
                .resolve("ats_module_run_result.textproto")
                .toAbsolutePath()
                .toString(),
            TextFormat.printer().printToString(resultBuilder.build()));
      }
    }

    String jobId = currentJob.locator().getId();
    boolean isSetupJobEnd = runningSetupJobId.compareAndSet(jobId, null);
    if (isSetupJobEnd) {
      logger.atInfo().log("Setup job [%s] ended, starting main jobs.", jobId);
      // Extract dynamic MCTS module names downloaded during the setup job, and create Tradefed jobs
      // now that the canonical list of dynamic modules is known.
      ImmutableSet<String> dynamicMctsModules = extractDynamicMctsModules(currentJob);
      createMainJobs(config.getRunCommand(), dynamicMctsModules);
      addMainJobs();
      return;
    }

    boolean isTeardownJobEnd = runningTeardownJobId.compareAndSet(jobId, null);
    if (isTeardownJobEnd) {
      logger.atInfo().log("Teardown job [%s] ended.", jobId);
      return;
    }

    synchronized (runningTradefedJobs) {
      if (runningTradefedJobs.containsKey(jobId)) {
        runningTradefedJobs.put(jobId, false);

        // Add the additional tradefed jobs if needed.
        JobInfo nextJobToAdd = additionalTradefedJobs.poll();
        if (nextJobToAdd != null) {
          // In MODULE sharding mode, each job has a SubDeviceSpec matching any available device
          // (via regex), allowing the scheduler to dynamically allocate whichever device is free.
          // In RUNNER sharding mode, pin the sub-device specs to the exact device IDs used by the
          // completed static job so that the subsequent dynamic job runs on the same devices.
          if (!runCommandHandler.shouldEnableModuleSharding()) {
            ImmutableSet<String> devicesOfCurrentJob = getDeviceSerials(currentJob);
            // Add the device ids of the current job to the sub device specs of the next tradefed
            // job.
            addDeviceIdsToSubDeviceSpecs(
                nextJobToAdd.subDeviceSpecs().getAllSubDevices(), devicesOfCurrentJob);
          }
          addAndTrackTradefedJobs(ImmutableList.of(nextJobToAdd));
        }

        if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log(
              "All added tradefed jobs have been done, trying to add non-tradefed jobs if needed.");
          if (!addMainNonTradefedJobs()) {
            addTeardownJobIfAny();
          }
        }
        return;
      }
    }

    synchronized (runningNonTradefedJobs) {
      if (runningNonTradefedJobs.containsKey(jobId)) {
        runningNonTradefedJobs.put(jobId, false);
        if (runningNonTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log("All non-tradefed main jobs have completed.");
          addTeardownJobIfAny();
        }
        return;
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
    XtsTradefedRunCancellation lastCancellationTestMessage;
    synchronized (testCancellationLock) {
      startedTests.add(testInfo);
      lastCancellationTestMessage = this.lastCancellationTestMessage;
    }
    if (lastCancellationTestMessage != null) {
      sendCancellationMessageToStartedTest(testInfo, lastCancellationTestMessage);
    }

    // Caches devices (as a xTS type) used in the test.
    // The intention is to make sure if any device goes offline between job runs, the next job
    // shouldn't be blocked on waiting for the device to become online, so we cache the devices
    // here.
    if (Flags.atsConsoleCacheXtsDevices.getNonNull()) {
      ImmutableList<String> deviceIds = event.getLocalDevices().keySet().asList();
      if (!deviceIds.isEmpty()) {
        synchronized (cachedDeviceControlIds) {
          try {
            sessionDeviceCache.cache(
                new CacheRequest(
                    LabLocator.LOCALHOST,
                    deviceIds,
                    ChronoUnit.YEARS.getDuration(),
                    "xts",
                    sessionInfo.getSessionId()));
            cachedDeviceControlIds.addAll(deviceIds);
          } catch (MobileHarnessException | InterruptedException e) {
            if (MoreThrowables.isInterruption(e)) {
              Thread.currentThread().interrupt();
            }
            logger.atWarning().withCause(e).log("Failed to cache devices %s", deviceIds);
          }
        }
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
      if (sessionCancellation != null || sessionEnded) {
        logger.atInfo().log(
            "Skip adding jobs to session (cancelled: [%s], ended: [%b])",
            sessionCancellation != null ? shortDebugString(sessionCancellation) : "null",
            sessionEnded);
        return ImmutableList.of();
      }

      // Adds jobs to session.
      jobInfos.forEach(sessionInfo::addJob);
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
    additionalTradefedJobs.clear();

    int killTradefedSignal;
    if (sessionCancellation.hasSignal()) {
      killTradefedSignal = sessionCancellation.getSignal();
    } else {
      killTradefedSignal =
          sessionCancellation.getAggressive()
              ? KillSignal.SIGTERM.value()
              : KillSignal.SIGTSTP.value();
    }

    // Creates test message.
    XtsTradefedRunCancellation cancellationTestMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(killTradefedSignal)
            .setCancelReason(sessionCancellation.getReason())
            .build();

    // Sends test message to started tests.
    ImmutableList<TestInfo> startedTests;
    synchronized (testCancellationLock) {
      this.lastCancellationTestMessage = cancellationTestMessage;
      startedTests = ImmutableList.copyOf(this.startedTests);
    }
    for (TestInfo testInfo : startedTests) {
      sendCancellationMessageToStartedTest(testInfo, cancellationTestMessage);
    }
  }

  /** TODO: Don't send to non-TF tests. */
  private void sendCancellationMessageToStartedTest(
      TestInfo testInfo, XtsTradefedRunCancellation cancellationTestMessage) {
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log(
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

  /**
   * Adds the ATS setup job to the session and records its execution ID in {@code
   * runningSetupJobId}.
   */
  private void addSetupJob(JobInfo setupJob) {
    logger.atInfo().log("Adding setup job [%s].", setupJob.locator().getId());
    ImmutableList<String> setupJobIds = addJobsToSession(ImmutableList.of(setupJob));
    if (!setupJobIds.isEmpty()) {
      runningSetupJobId.set(setupJobIds.get(0));
    }
  }

  /**
   * Creates the main Tradefed and non-Tradefed jobs based on the given RunCommand.
   *
   * @param runCommand the run command representing the session config
   * @param dynamicMctsModules the canonical set of dynamic MCTS module names downloaded during the
   *     setup job, or an empty set if dynamic MCTS is disabled, no modules were requested, or the
   *     setup job is unavailable. If provided, they replace static MCTS modules for Tradefed job
   *     filtering and creation.
   */
  private void createMainJobs(RunCommand runCommand, ImmutableSet<String> dynamicMctsModules)
      throws MobileHarnessException, InterruptedException {
    // Create tradefed jobs.
    tradefedJobs = this.createTradefedJobs(dynamicMctsModules.asList());

    // Create non-tradefed jobs.
    nonTradefedJobs = this.createNonTradefedJobs();
    if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION,
          "No jobs created for session " + sessionInfo.getSessionId(),
          /* cause= */ null);
    }
  }

  /**
   * Extracts the set of dynamic MCTS module names relayed via test properties from the completed
   * setup job.
   */
  private static ImmutableSet<String> extractDynamicMctsModules(JobInfo setupJob) {
    if (setupJob.tests() == null || setupJob.tests().getAll() == null) {
      return ImmutableSet.of();
    }
    return setupJob.tests().getAll().values().stream()
        .map(
            testInfo ->
                testInfo
                    .properties()
                    .get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_TEST_MODULES_PROPERTY_KEY))
        .filter(Objects::nonNull)
        .flatMap(
            modulesStr ->
                Splitter.on(',').omitEmptyStrings().trimResults().splitToStream(modulesStr))
        .collect(toImmutableSet());
  }

  /**
   * Adds main Tradefed jobs to the session based on the sharding mode:
   *
   * <ul>
   *   <li>In <b>MODULE sharding mode</b>, each module-level job requires only one device. All
   *       module jobs (both static and dynamic) are added directly to the session to run
   *       concurrently across all available devices.
   *   <li>In <b>RUNNER sharding mode</b> (default), the static xTS job is started first using all
   *       allocated devices, and dynamic MCTS jobs in {@code additionalTradefedJobs} execute after
   *       the static job completes in {@link #onJobEnd}.
   * </ul>
   *
   * <p>If no Tradefed jobs could be started, falls back to adding non-Tradefed jobs.
   */
  private void addMainJobs() {
    List<JobInfo> initialJobsToStart = prepareTradefedJobsToStart();
    if (!addAndTrackTradefedJobs(initialJobsToStart)) {
      logger.atInfo().log("No tradefed job was added, trying to add non-tradefed jobs if needed.");
      if (!addMainNonTradefedJobs()) {
        addTeardownJobIfAny();
      }
    }
  }

  private List<JobInfo> prepareTradefedJobsToStart() {
    if (runCommandHandler.shouldEnableModuleSharding()) {
      // In MODULE sharding mode, each job requires a single device. All jobs can be scheduled
      // concurrently across all available devices.
      return tradefedJobs;
    }

    // In RUNNER (or default) sharding mode:
    // Partition jobs into static xTS jobs and dynamic MCTS jobs. Start the static job first,
    // and queue the dynamic job to execute sequentially in onJobEnd using the same devices.
    Map<Boolean, List<JobInfo>> partitionedJobs =
        tradefedJobs.stream()
            .collect(
                partitioningBy(
                    job -> job.locator().getName().contains(XtsConstants.STATIC_XTS_JOB_NAME)));
    List<JobInfo> staticXtsJobs = partitionedJobs.get(true);
    List<JobInfo> nonStaticXtsJobs = partitionedJobs.get(false);

    if (!staticXtsJobs.isEmpty()) {
      additionalTradefedJobs.addAll(nonStaticXtsJobs);
      return staticXtsJobs;
    } else if (nonStaticXtsJobs.size() <= 1) {
      return nonStaticXtsJobs;
    } else {
      additionalTradefedJobs.addAll(nonStaticXtsJobs.subList(1, nonStaticXtsJobs.size()));
      return nonStaticXtsJobs.subList(0, 1);
    }
  }

  /**
   * Adds Tradefed jobs to the session and records them in {@code runningTradefedJobs}.
   *
   * @return true if at least one Tradefed job was added and tracked; false otherwise
   */
  @CanIgnoreReturnValue
  private boolean addAndTrackTradefedJobs(List<JobInfo> tradefedJobs) {
    ImmutableList<String> tradefedJobIds = addJobsToSession(ImmutableList.copyOf(tradefedJobs));
    if (!tradefedJobIds.isEmpty()) {
      synchronized (runningTradefedJobs) {
        tradefedJobIds.forEach(id -> runningTradefedJobs.putIfAbsent(id, true));
      }
      return true;
    }
    return false;
  }

  /**
   * Adds main non-Tradefed jobs to the session and records them in {@code runningNonTradefedJobs}.
   *
   * @return true if at least one non-Tradefed job was added and tracked; false otherwise
   */
  @CanIgnoreReturnValue
  private boolean addMainNonTradefedJobs() {
    ImmutableList<String> nonTfJobIds = addJobsToSession(nonTradefedJobs);
    if (!nonTfJobIds.isEmpty()) {
      synchronized (runningNonTradefedJobs) {
        nonTfJobIds.forEach(id -> runningNonTradefedJobs.putIfAbsent(id, true));
      }
      return true;
    }
    return false;
  }

  /** Adds the teardown job to the session if present. */
  private void addTeardownJobIfAny() {
    JobInfo teardownJob = teardownJobRef.getAndSet(null);
    if (teardownJob != null) {
      JobInfo setupJob = setupJobRef.get();
      if (setupJob != null && setupJob.tests() != null && teardownJob.tests() != null) {
        setupJob.tests().getAll().values().stream()
            .findFirst()
            .ifPresent(
                setupTest ->
                    teardownJob
                        .tests()
                        .getAll()
                        .values()
                        .forEach(
                            teardownTest ->
                                PhaseSkippableDecoratorUtil.relayStates(setupTest, teardownTest)));
      }
      logger.atInfo().log("Adding teardown job [%s].", teardownJob.locator().getId());
      ImmutableList<String> jobIds = addJobsToSession(ImmutableList.of(teardownJob));
      if (!jobIds.isEmpty()) {
        runningTeardownJobId.set(jobIds.get(0));
      }
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

  @Override
  public Optional<JobInfo> createSetupJob() throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase() == CommandCase.RUN_COMMAND) {
      return runCommandHandler.createSetupJob();
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<JobInfo> createTradefedJobs(List<String> mctsModules)
      throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase() != CommandCase.RUN_COMMAND) {
      return ImmutableList.of();
    }
    try {
      return runCommandHandler.createTradefedJobs(
          config.getRunCommand(), ImmutableSet.copyOf(mctsModules));
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
      return ImmutableList.of();
    }
  }

  @Override
  public ImmutableList<JobInfo> createNonTradefedJobs()
      throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase() != CommandCase.RUN_COMMAND) {
      return ImmutableList.of();
    }
    try {
      return runCommandHandler.createNonTradefedJobs(config.getRunCommand());
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
      return ImmutableList.of();
    }
  }

  @Override
  public Optional<JobInfo> createTeardownJob() throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase() != CommandCase.RUN_COMMAND) {
      return Optional.empty();
    }
    return runCommandHandler.createTeardownJob();
  }
}
