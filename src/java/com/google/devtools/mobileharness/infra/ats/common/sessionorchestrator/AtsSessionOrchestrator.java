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

package com.google.devtools.mobileharness.infra.ats.common.sessionorchestrator;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.concurrent.GuardedBy;

/**
 * Shared orchestrator managing session job lifecycles across ATS Console and ATS Server.
 *
 * <p>Centralizes phase transitions ({@code SETUP} -> {@code MAIN_TRADEFED} -> {@code
 * MAIN_NON_TRADEFED} -> {@code TEARDOWN}), dynamic MCTS module extraction, module-sharded
 * concurrency, runner-mode device pinning, teardown state relaying, and cancellation message
 * broadcasting.
 */
public class AtsSessionOrchestrator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TRADEFED_DRIVER_NAME = "TradefedTest";

  private final SessionInfo sessionInfo;
  private final SessionOrchestratorDelegate delegate;
  private final TestMessageUtil testMessageUtil;

  // Synchronization locks
  private final Object addingJobLock = new Object();
  private final Object testCancellationLock = new Object();

  // State Tracking
  @GuardedBy("itself")
  private final Map<String, Boolean> runningTradefedJobs = new HashMap<>();

  @GuardedBy("itself")
  private final Map<String, Boolean> runningNonTradefedJobs = new HashMap<>();

  @GuardedBy("testCancellationLock")
  private final List<TestInfo> startedTests = new ArrayList<>();

  @GuardedBy("testCancellationLock")
  private XtsTradefedRunCancellation lastCancellationTestMessage;

  @GuardedBy("addingJobLock")
  private boolean sessionCancellation;

  @GuardedBy("addingJobLock")
  private boolean sessionEnded;

  private final Queue<JobInfo> additionalTradefedJobs = new ConcurrentLinkedQueue<>();

  private volatile ImmutableList<JobInfo> tradefedJobs = ImmutableList.of();
  private final AtomicReference<ImmutableList<JobInfo>> nonTradefedJobsRef =
      new AtomicReference<>(ImmutableList.of());

  private final AtomicReference<JobInfo> setupJobRef = new AtomicReference<>();
  private final AtomicReference<JobInfo> teardownJobRef = new AtomicReference<>();
  private final AtomicReference<String> runningSetupJobId = new AtomicReference<>();
  private final AtomicReference<String> runningTeardownJobId = new AtomicReference<>();

  public AtsSessionOrchestrator(
      SessionInfo sessionInfo,
      SessionOrchestratorDelegate delegate,
      TestMessageUtil testMessageUtil) {
    this.sessionInfo = sessionInfo;
    this.delegate = delegate;
    this.testMessageUtil = testMessageUtil;
  }

  /** Starts session execution by creating and scheduling either the setup job or main jobs. */
  public void startSession() throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> setupJobOpt = delegate.createSetupJob();
    Optional<JobInfo> teardownJobOpt = delegate.createTeardownJob();

    setupJobOpt.ifPresent(setupJobRef::set);
    teardownJobOpt.ifPresent(teardownJobRef::set);

    if (setupJobOpt.isPresent()) {
      addSetupJob(setupJobOpt.get());
    } else {
      createMainJobs(/* dynamicMctsModules= */ ImmutableSet.of(), /* skipDynamicMctsJob= */ false);
      addMainJobs();
    }
  }

  /** Handles job completion and coordinates subsequent phase transitions. */
  public void onJobEnd(JobEndEvent jobEndEvent)
      throws MobileHarnessException, InterruptedException {
    JobInfo currentJob = jobEndEvent.getJob();

    String jobId = currentJob.locator().getId();
    // Fall back to checking isSetupJob(currentJob) when runningSetupJobId is null (e.g. when
    // resuming a session that restarted while the setup job was still running).
    boolean isSetupJobEnd =
        runningSetupJobId.compareAndSet(jobId, null)
            || (runningSetupJobId.get() == null && isSetupJob(currentJob));
    if (isSetupJobEnd) {
      // If the session resumed during the setup job, re-populate setupJobRef and teardownJobRef so
      // teardown state relay and scheduling still work after main jobs finish.
      if (setupJobRef.get() == null) {
        setupJobRef.set(currentJob);
        delegate.createTeardownJob().ifPresent(teardownJobRef::set);
      }
      logger.atInfo().log("Setup job [%s] ended, starting main jobs.", jobId);
      // Extract dynamic MCTS module names downloaded during the setup job, and create Tradefed jobs
      // now that the canonical list of dynamic modules is known.
      ImmutableSet<String> dynamicMctsModules = extractDynamicMctsModules(currentJob);
      // If the setup job reported the device has no preloaded Mainline modules (e.g. Auto / AOSP
      // builds), it does not need dynamic MCTS, so skip creating the dynamic MCTS job to avoid
      // booting Tradefed for 0 tests.
      boolean skipDynamicMctsJob = !extractHasPreloadedMainlineModules(currentJob);
      createMainJobs(dynamicMctsModules, skipDynamicMctsJob);
      addMainJobs();
      return;
    }

    // Fall back to checking isTeardownJob(currentJob) when runningTeardownJobId is null (e.g. when
    // resuming a session that restarted while the teardown job was running).
    boolean isTeardownJobEnd =
        runningTeardownJobId.compareAndSet(jobId, null)
            || (runningTeardownJobId.get() == null && isTeardownJob(currentJob));
    if (isTeardownJobEnd) {
      logger.atInfo().log("Teardown job [%s] ended.", jobId);
      return;
    }

    // If a main job finishes that is not tracked in the in-memory running maps, the session was
    // resumed after a server restart; reconstruct orchestration state from
    // sessionInfo.getAllJobs().
    boolean isResumed;
    synchronized (runningTradefedJobs) {
      synchronized (runningNonTradefedJobs) {
        isResumed =
            !runningTradefedJobs.containsKey(jobId) && !runningNonTradefedJobs.containsKey(jobId);
      }
    }
    if (isResumed) {
      restoreResumedSessionState(currentJob);
      return;
    }

    synchronized (runningTradefedJobs) {
      if (runningTradefedJobs.containsKey(jobId)) {
        runningTradefedJobs.put(jobId, false);

        // Add the additional tradefed jobs if needed.
        // The static xts job is the first job in the list, if the test result is not complete, we
        // don't execute any MCTS jobs.
        if (isStaticXtsJobAndFailed(currentJob)) {
          logger.atInfo().log(
              "Session [%s]: Static XTS job [%s] ended but result is not complete, clearing"
                  + " remaining tradefed jobs.",
              sessionInfo.getSessionId(), currentJob.locator().getId());
          additionalTradefedJobs.clear();
        } else {
          JobInfo nextJobToAdd = additionalTradefedJobs.poll();
          if (nextJobToAdd != null) {
            // In MODULE sharding mode, each job has a SubDeviceSpec matching any available device
            // (via regex), allowing the scheduler to dynamically allocate whichever device is free.
            // In RUNNER sharding mode, pin the sub-device specs to the exact device IDs used by the
            // completed static job so that the subsequent dynamic job runs on the same devices.
            if (delegate.getEffectiveShardingMode() != ShardingMode.MODULE) {
              ImmutableSet<String> devicesOfCurrentJob = getDeviceSerials(currentJob);
              // Add the device ids of the current job to the sub device specs of the next tradefed
              // job.
              addDeviceIdsToSubDeviceSpecs(
                  nextJobToAdd.subDeviceSpecs().getAllSubDevices(), devicesOfCurrentJob);
            }
            addAndTrackTradefedJobs(ImmutableList.of(nextJobToAdd));
          }
        }

        if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log(
              "All added tradefed jobs have completed, trying to add non-tradefed jobs if needed.");
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

  /**
   * Handles test start, tracks started tests for cancellation, and sends cancellation messages if
   * the session has already been cancelled.
   */
  public void onTestStarting(TestInfo testInfo) {
    // Sends cancellation test message if necessary.
    XtsTradefedRunCancellation cancellationTestMessage;
    synchronized (testCancellationLock) {
      startedTests.add(testInfo);
      cancellationTestMessage = this.lastCancellationTestMessage;
    }
    if (cancellationTestMessage != null) {
      sendCancellationMessageToStartedTest(testInfo, cancellationTestMessage);
    }
  }

  /**
   * Broadcasts cancellation to all active tests and prevents new jobs from being scheduled.
   *
   * <p>TODO: Support killing jobs here (for non-TF jobs or jobs during allocation).
   */
  public void onSessionCancellation(XtsTradefedRunCancellation cancellationTestMessage) {
    // Stops adding new jobs.
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Stop adding new jobs due to [%s]", shortDebugString(cancellationTestMessage));
    synchronized (addingJobLock) {
      this.sessionCancellation = true;
    }
    additionalTradefedJobs.clear();

    // Sends test message to started tests.
    Set<TestInfo> testsToCancel = new LinkedHashSet<>();
    synchronized (testCancellationLock) {
      this.lastCancellationTestMessage = cancellationTestMessage;
      testsToCancel.addAll(this.startedTests);
    }
    // Also include RUNNING tests from sessionInfo.getAllJobs() so tests that started before a
    // session resume (whose onTestStarting event was missed in this process) are still cancelled.
    sessionInfo.getAllJobs().stream()
        .flatMap(jobInfo -> jobInfo.tests().getAll().values().stream())
        .filter(testInfo -> testInfo.status().get() == TestStatus.RUNNING)
        .forEach(testsToCancel::add);
    testsToCancel.forEach(
        testInfo -> sendCancellationMessageToStartedTest(testInfo, cancellationTestMessage));
  }

  /** Marks session orchestration as ended so no further jobs can be added to the session. */
  public void onSessionEnded() {
    synchronized (addingJobLock) {
      sessionEnded = true;
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
      if (sessionCancellation || sessionEnded) {
        logger.atInfo().log(
            "Skip adding jobs to session (cancelled: [%b], ended: [%b])",
            sessionCancellation, sessionEnded);
        return ImmutableList.of();
      }
      // Adds jobs to session.
      jobInfos.forEach(sessionInfo::addJob);
    }
    return jobInfos.stream().map(jobInfo -> jobInfo.locator().getId()).collect(toImmutableList());
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
   * Populates {@code tradefedJobs} and {@code nonTradefedJobs} using the session orchestrator
   * delegate, skipping skippable job creation exceptions.
   */
  private void populateMainJobs(ImmutableSet<String> dynamicMctsModules, boolean skipDynamicMctsJob)
      throws MobileHarnessException, InterruptedException {
    // Create tradefed jobs.
    try {
      tradefedJobs = delegate.createTradefedJobs(dynamicMctsModules, skipDynamicMctsJob);
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
      nonTradefedJobsRef.set(delegate.createNonTradefedJobs());
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
      nonTradefedJobsRef.set(ImmutableList.of());
    }
  }

  /**
   * Creates the main Tradefed and non-Tradefed jobs based on the session config.
   *
   * @param dynamicMctsModules the canonical set of dynamic MCTS module names downloaded during the
   *     setup job, or an empty set if dynamic MCTS is disabled, no modules were requested, or the
   *     setup job is unavailable. If provided, they replace static MCTS modules for Tradefed job
   *     filtering and creation.
   * @param skipDynamicMctsJob when {@code true}, the dynamic MCTS job is not created in RUNNER mode
   */
  private void createMainJobs(ImmutableSet<String> dynamicMctsModules, boolean skipDynamicMctsJob)
      throws MobileHarnessException, InterruptedException {
    populateMainJobs(dynamicMctsModules, skipDynamicMctsJob);
    if (tradefedJobs.isEmpty() && nonTradefedJobsRef.get().isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION,
          "No jobs created for session " + sessionInfo.getSessionId(),
          /* cause= */ null);
    }
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

  /**
   * Partitions and selects the initial set of Tradefed jobs to start immediately, queuing any
   * remaining sequential jobs in {@code additionalTradefedJobs}.
   */
  private List<JobInfo> prepareTradefedJobsToStart() {
    if (delegate.getEffectiveShardingMode() == ShardingMode.MODULE) {
      // In MODULE sharding mode, each job requires a single device. All jobs can be scheduled
      // concurrently across all available devices.
      return tradefedJobs;
    }

    // In RUNNER (or default) sharding mode:
    // Order static xTS jobs before dynamic MCTS jobs. Start the first job immediately,
    // and queue the remaining jobs to execute sequentially in onJobEnd using the same devices.
    ImmutableList<JobInfo> orderedJobs = orderStaticJobsFirst(tradefedJobs);
    if (orderedJobs.size() <= 1) {
      return orderedJobs;
    }
    additionalTradefedJobs.addAll(orderedJobs.subList(1, orderedJobs.size()));
    return orderedJobs.subList(0, 1);
  }

  /** Orders static xTS jobs before non-static (e.g. dynamic MCTS) Tradefed jobs. */
  private static ImmutableList<JobInfo> orderStaticJobsFirst(List<JobInfo> jobs) {
    Map<Boolean, List<JobInfo>> partitionedJobs =
        jobs.stream()
            .collect(
                partitioningBy(
                    job -> job.locator().getName().contains(XtsConstants.STATIC_XTS_JOB_NAME)));
    return ImmutableList.<JobInfo>builder()
        .addAll(partitionedJobs.get(true))
        .addAll(partitionedJobs.get(false))
        .build();
  }

  /**
   * Adds Tradefed jobs to the session and records them in {@code runningTradefedJobs}.
   *
   * @return true if at least one Tradefed job was added and tracked; false otherwise
   */
  @CanIgnoreReturnValue
  private boolean addAndTrackTradefedJobs(List<JobInfo> jobs) {
    ImmutableList<String> tradefedJobIds = addJobsToSession(ImmutableList.copyOf(jobs));
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
    ImmutableList<JobInfo> jobsToAdd = nonTradefedJobsRef.getAndSet(ImmutableList.of());
    if (jobsToAdd.isEmpty()) {
      return false;
    }
    ImmutableList<String> nonTfJobIds = addJobsToSession(jobsToAdd);
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
      if (setupJob != null) {
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

  /**
   * Extracts the set of dynamic MCTS module names relayed via test properties from the completed
   * setup job.
   */
  private static ImmutableSet<String> extractDynamicMctsModules(JobInfo setupJob) {
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
   * Returns whether the dynamic MCTS Tradefed job should be kept, based on the "has preloaded
   * Mainline modules" signal that the setup job relays via a test property.
   *
   * <p>Defaults to keeping the job (returns {@code true}) unless the setup test explicitly reported
   * that the device has no preloaded Mainline modules. If the signal is missing (e.g. the setup
   * plugin failed to execute), dynamic MCTS is still run so test coverage is not accidentally
   * skipped.
   */
  private static boolean extractHasPreloadedMainlineModules(JobInfo setupJob) {
    return setupJob.tests().getAll().values().stream()
        .anyMatch(
            testInfo -> {
              String value =
                  testInfo
                      .properties()
                      .get(
                          XtsConstants
                              .XTS_DYNAMIC_DOWNLOAD_HAS_PRELOADED_MAINLINE_MODULES_PROPERTY_KEY);
              return value == null || Boolean.parseBoolean(value);
            });
  }

  /** Extracts the set of device serials allocated to the given job's tests. */
  private static ImmutableSet<String> getDeviceSerials(JobInfo jobInfo) {
    return jobInfo.tests().getAll().values().stream()
        .map(testInfo -> testInfo.properties().getOptional(Test.DEVICE_ID_LIST))
        .filter(Optional::isPresent)
        .flatMap(ids -> stream(ids.get().split(",")))
        .collect(toImmutableSet());
  }

  /** Pins each sub-device spec in {@code subDeviceSpecs} to the corresponding device ID. */
  private static void addDeviceIdsToSubDeviceSpecs(
      List<SubDeviceSpec> subDeviceSpecs, ImmutableSet<String> deviceIds) {
    // Return if the number of device IDs is not equal to the number of sub-device specs.
    if (subDeviceSpecs.isEmpty()
        || deviceIds.isEmpty()
        || subDeviceSpecs.size() != deviceIds.size()) {
      return;
    }
    Iterator<String> deviceIdIterator = deviceIds.iterator();
    for (SubDeviceSpec subDeviceSpec : subDeviceSpecs) {
      String deviceId = deviceIdIterator.next();
      subDeviceSpec.dimensions().add(Name.ID.lowerCaseName(), deviceId);
    }
  }

  /**
   * Re-initializes session state and schedules remaining jobs when resuming a session after a
   * restart.
   *
   * <p>In-memory job lists ({@code tradefedJobs}, {@code nonTradefedJobs}) and tracking maps are
   * lost in resumed sessions. This method reconstructs active job tracking from {@code
   * sessionInfo.getAllJobs()}, re-creates the job definitions via the delegate, filters out jobs
   * that have already been triggered in the session, and schedules the next remaining jobs.
   */
  private void restoreResumedSessionState(JobInfo currentJob)
      throws MobileHarnessException, InterruptedException {
    if (sessionInfo.getAllJobs().isEmpty()) {
      return;
    }
    String jobId = currentJob.locator().getId();
    boolean isTf = isTradefedJob(currentJob);

    // 1. Rebuild running job tracking maps from all jobs already added to the session.
    synchronized (runningTradefedJobs) {
      synchronized (runningNonTradefedJobs) {
        for (JobInfo job : sessionInfo.getAllJobs()) {
          if (isSetupJob(job) || isTeardownJob(job)) {
            continue;
          }
          boolean isJobRunning = job.status().get() != TestStatus.DONE;
          if (isTradefedJob(job)) {
            runningTradefedJobs.putIfAbsent(job.locator().getId(), isJobRunning);
          } else {
            runningNonTradefedJobs.putIfAbsent(job.locator().getId(), isJobRunning);
          }
        }
      }
    }

    // Collect names of jobs that have already been added to the session so we don't re-schedule
    // them.
    ImmutableSet<String> triggeredJobNames =
        sessionInfo.getAllJobs().stream()
            .map(job -> job.locator().getName())
            .collect(toImmutableSet());

    // 2. Re-create the full set of expected main and teardown jobs if our in-memory lists are
    // empty, using the completed setup job (if present) to recover dynamic MCTS parameters.
    if (tradefedJobs.isEmpty() && nonTradefedJobsRef.get().isEmpty()) {
      Optional<JobInfo> setupJobOpt =
          sessionInfo.getAllJobs().stream().filter(AtsSessionOrchestrator::isSetupJob).findFirst();
      setupJobOpt.ifPresent(setupJobRef::set);
      ImmutableSet<String> dynamicMctsModules =
          setupJobOpt
              .map(AtsSessionOrchestrator::extractDynamicMctsModules)
              .orElse(ImmutableSet.of());
      boolean skipDynamicMctsJob =
          setupJobOpt.map(setupJob -> !extractHasPreloadedMainlineModules(setupJob)).orElse(false);
      populateMainJobs(dynamicMctsModules, skipDynamicMctsJob);
      // Filter out any non-Tradefed or teardown jobs that were already triggered before the
      // restart.
      nonTradefedJobsRef.updateAndGet(
          jobs ->
              jobs.stream()
                  .filter(job -> !triggeredJobNames.contains(job.locator().getName()))
                  .collect(toImmutableList()));
      delegate
          .createTeardownJob()
          .filter(job -> !triggeredJobNames.contains(job.locator().getName()))
          .ifPresent(teardownJobRef::set);
    }

    // 3. Mark the current job as finished and advance to the next remaining Tradefed, non-Tradefed,
    // or teardown phase.
    if (isTf) {
      synchronized (runningTradefedJobs) {
        runningTradefedJobs.put(jobId, false);

        List<JobInfo> remainingTfJobs =
            orderStaticJobsFirst(tradefedJobs).stream()
                .filter(job -> !triggeredJobNames.contains(job.locator().getName()))
                .collect(toCollection(ArrayList::new));

        // The static xts job is the first job in the list, if the test result is not complete, we
        // don't execute any MCTS jobs.
        if (isStaticXtsJobAndFailed(currentJob)) {
          logger.atInfo().log(
              "Session [%s]: Static XTS job [%s] ended but result is not complete, clearing"
                  + " remaining tradefed jobs.",
              sessionInfo.getSessionId(), currentJob.locator().getId());
          remainingTfJobs.clear();
          additionalTradefedJobs.clear();
        }

        if (delegate.getEffectiveShardingMode() == ShardingMode.MODULE) {
          // In MODULE sharding mode, schedule all remaining un-triggered module jobs concurrently.
          addAndTrackTradefedJobs(remainingTfJobs);
        } else if (!remainingTfJobs.isEmpty()) {
          // In RUNNER sharding mode, start the next job pinned to the current job's devices and
          // queue the rest in additionalTradefedJobs.
          JobInfo nextJob = remainingTfJobs.remove(0);
          additionalTradefedJobs.addAll(remainingTfJobs);
          ImmutableSet<String> devicesOfCurrentJob = getDeviceSerials(currentJob);
          addDeviceIdsToSubDeviceSpecs(
              nextJob.subDeviceSpecs().getAllSubDevices(), devicesOfCurrentJob);
          addAndTrackTradefedJobs(ImmutableList.of(nextJob));
        }

        if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log(
              "All added tradefed jobs have completed, trying to add non-tradefed jobs if needed.");
          if (!addMainNonTradefedJobs()) {
            addTeardownJobIfAny();
          }
        }
      }
    } else {
      synchronized (runningNonTradefedJobs) {
        runningNonTradefedJobs.put(jobId, false);
        if (runningNonTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log("All non-tradefed main jobs have completed.");
          // Schedule any remaining un-triggered non-Tradefed jobs, or proceed to teardown.
          if (!addMainNonTradefedJobs()) {
            addTeardownJobIfAny();
          }
        }
      }
    }
  }

  /** Checks whether the given job is a Tradefed job. */
  private static boolean isTradefedJob(JobInfo jobInfo) {
    return jobInfo.type().getDriver().equals(TRADEFED_DRIVER_NAME);
  }

  /** Checks whether the given job is the ATS setup job. */
  private static boolean isSetupJob(JobInfo jobInfo) {
    return Objects.equals(
        jobInfo.properties().get(XtsConstants.XTS_JOB_NAME), XtsConstants.SETUP_JOB_NAME);
  }

  /** Checks whether the given job is the ATS teardown job. */
  private static boolean isTeardownJob(JobInfo jobInfo) {
    return Objects.equals(
        jobInfo.properties().get(XtsConstants.XTS_JOB_NAME), XtsConstants.TEARDOWN_JOB_NAME);
  }

  /**
   * Checks whether the given job is a static xTS dynamic-download job that ended without complete
   * passing results.
   */
  private static boolean isStaticXtsJobAndFailed(JobInfo jobInfo) {
    return jobInfo
            .properties()
            .getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED)
            .orElse(false)
        && Objects.equals(
            jobInfo.properties().get(XtsConstants.XTS_JOB_NAME), XtsConstants.STATIC_XTS_JOB_NAME)
        && jobInfo.tests().getAll().values().stream()
            .noneMatch(
                testInfo ->
                    testInfo.resultWithCause().get().type() == TestResult.PASS
                        && testInfo
                            .properties()
                            .getBoolean(XtsConstants.TRADEFED_JOBS_HAS_RESULT_FILE)
                            .orElse(false));
  }
}
