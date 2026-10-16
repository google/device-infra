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
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.jobcreator.XtsJobCreator;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
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
import javax.inject.Inject;

/**
 * Shared state machine for ATS session job orchestration.
 *
 * <p>Manages the lifecycle and transition rules for an ATS session's jobs across phases:
 *
 * <pre>
 * [Setup Job] -> [Main Tradefed Jobs (Static -> Dynamic)] -> [Main Non-Tradefed Jobs] -> [Teardown Job]
 * </pre>
 */
public class AtsSessionOrchestrator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TRADEFED_DRIVER_NAME = "TradefedTest";

  private final Object testCancellationLock = new Object();

  private final SessionInfo sessionInfo;
  private final XtsJobCreator xtsJobCreator;
  private final TestMessageUtil testMessageUtil;

  @GuardedBy("itself")
  private final Map<String, Boolean> runningTradefedJobs = new HashMap<>();

  @GuardedBy("itself")
  private final Map<String, Boolean> runningNonTradefedJobs = new HashMap<>();

  @GuardedBy("testCancellationLock")
  private final List<TestInfo> startedTests = new ArrayList<>();

  @GuardedBy("testCancellationLock")
  private XtsTradefedRunCancellation lastCancellationTestMessage;

  private final Object addingJobLock = new Object();

  @GuardedBy("addingJobLock")
  private String sessionCancelReason;

  @GuardedBy("addingJobLock")
  private boolean sessionEnded;

  private final Queue<JobInfo> additionalTradefedJobs = new ConcurrentLinkedQueue<>();

  private volatile ImmutableList<JobInfo> tradefedJobs = ImmutableList.of();
  private volatile ImmutableList<JobInfo> nonTradefedJobs = ImmutableList.of();

  private final AtomicReference<JobInfo> setupJobRef = new AtomicReference<>();
  private final AtomicReference<JobInfo> teardownJobRef = new AtomicReference<>();
  private final AtomicReference<String> runningSetupJobId = new AtomicReference<>();
  private final AtomicReference<String> runningTeardownJobId = new AtomicReference<>();

  @Inject
  AtsSessionOrchestrator(
      SessionInfo sessionInfo, XtsJobCreator xtsJobCreator, TestMessageUtil testMessageUtil) {
    this.sessionInfo = sessionInfo;
    this.xtsJobCreator = xtsJobCreator;
    this.testMessageUtil = testMessageUtil;
  }

  /**
   * Starts the session orchestration for the given {@link SessionRequestInfo}: creates setup and
   * teardown jobs if needed, and schedules either the setup job or the main jobs immediately.
   */
  public void startSession(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> setupJobOpt = xtsJobCreator.createXtsSetupJob(sessionRequestInfo);
    Optional<JobInfo> teardownJobOpt = xtsJobCreator.createXtsTearDownJob(sessionRequestInfo);

    setupJobOpt.ifPresent(setupJobRef::set);
    teardownJobOpt.ifPresent(teardownJobRef::set);

    if (setupJobOpt.isPresent()) {
      addSetupJob(setupJobOpt.get());
    } else {
      createMainJobs(
          sessionRequestInfo,
          /* dynamicMctsModules= */ ImmutableSet.of(),
          /* skipDynamicMctsJob= */ false);
      addMainJobs(sessionRequestInfo);
    }
  }

  /** Marks the session as ended so no further jobs are scheduled. */
  public void onSessionEnded() {
    synchronized (addingJobLock) {
      sessionEnded = true;
    }
  }

  /**
   * Handles the completion of a job for the given {@link SessionRequestInfo} and advances the
   * session state machine to the next phase when all jobs in the current phase have completed.
   */
  public void onJobEnd(JobEndEvent jobEndEvent, SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    JobInfo currentJob = jobEndEvent.getJob();

    String jobId = currentJob.locator().getId();
    // Also check isSetupJob(currentJob) when runningSetupJobId is null to handle a session resumed
    // after a server restart while the setup job was in flight.
    boolean isSetupJobEnd =
        runningSetupJobId.compareAndSet(jobId, null)
            || (runningSetupJobId.get() == null && isSetupJob(currentJob));
    if (isSetupJobEnd) {
      // If the session was resumed after a server restart, setupJobRef and teardownJobRef were not
      // populated by startSession in this process; initialize them now.
      if (setupJobRef.get() == null) {
        setupJobRef.set(currentJob);
        xtsJobCreator.createXtsTearDownJob(sessionRequestInfo).ifPresent(teardownJobRef::set);
      }
      logger.atInfo().log("Setup job [%s] ended, starting main jobs.", jobId);
      // Extract dynamic MCTS module names downloaded during the setup job, and create Tradefed jobs
      // now that the canonical list of dynamic modules is known.
      ImmutableSet<String> dynamicMctsModules = extractDynamicMctsModules(currentJob);
      // If the setup job reported the device has no preloaded Mainline modules (e.g. Auto / AOSP
      // builds), it does not need dynamic MCTS, so skip creating the dynamic MCTS job to avoid
      // booting Tradefed for 0 tests.
      boolean skipDynamicMctsJob = !extractHasPreloadedMainlineModules(currentJob);
      createMainJobs(sessionRequestInfo, dynamicMctsModules, skipDynamicMctsJob);
      addMainJobs(sessionRequestInfo);
      return;
    }

    // Also check isTeardownJob(currentJob) in case the session was resumed after a server restart
    // while the teardown job was in flight (so runningTeardownJobId is null in this process).
    boolean isTeardownJobEnd =
        runningTeardownJobId.compareAndSet(jobId, null) || isTeardownJob(currentJob);
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
      restoreResumedSessionState(currentJob, sessionRequestInfo);
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
          if (!SessionRequestHandlerUtil.shouldEnableModuleSharding(sessionRequestInfo)) {
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

  /**
   * Records a started test and sends a pending cancellation message to it immediately if the
   * session has already been cancelled.
   */
  public void onTestStarting(TestInfo testInfo) {
    // Sends cancellation test message if necessary.
    XtsTradefedRunCancellation lastCancellationTestMessage;
    synchronized (testCancellationLock) {
      startedTests.add(testInfo);
      lastCancellationTestMessage = this.lastCancellationTestMessage;
    }
    if (lastCancellationTestMessage != null) {
      sendCancellationMessageToStartedTest(testInfo, lastCancellationTestMessage);
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
      if (sessionCancelReason != null || sessionEnded) {
        logger.atInfo().log(
            "Skip adding jobs to session (cancelled: [%s], ended: [%b])",
            String.valueOf(sessionCancelReason), sessionEnded);
        return ImmutableList.of();
      }

      // Adds jobs to session.
      jobInfos.forEach(sessionInfo::addJob);
    }

    return jobInfos.stream().map(jobInfo -> jobInfo.locator().getId()).collect(toImmutableList());
  }

  /**
   * Cancels the session using the given signal for Tradefed tests and stops scheduling any new
   * jobs.
   *
   * <p>TODO: Support killing jobs here (for non-TF jobs or jobs during allocation).
   */
  public void onSessionCancellation(String cancelReason, int killTradefedSignal) {
    // Stops adding new jobs.
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log(
            "Stop adding new jobs due to session cancellation: reason=[%s], signal=[%d]",
            cancelReason, killTradefedSignal);
    synchronized (addingJobLock) {
      this.sessionCancelReason = cancelReason;
    }
    additionalTradefedJobs.clear();
    teardownJobRef.set(null);

    // Creates test message.
    XtsTradefedRunCancellation cancellationTestMessage =
        XtsTradefedRunCancellation.newBuilder()
            .setKillTradefedSignal(killTradefedSignal)
            .setCancelReason(cancelReason)
            .build();

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
    for (TestInfo testInfo : testsToCancel) {
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

  /** Extracts the allocated device serials from the tests of the given {@link JobInfo}. */
  private static ImmutableSet<String> getDeviceSerials(JobInfo jobInfo) {
    return jobInfo.tests().getAll().values().stream()
        .map(testInfo -> testInfo.properties().getOptional(Test.DEVICE_ID_LIST))
        .filter(Optional::isPresent)
        .flatMap(ids -> stream(ids.get().split(",")))
        .collect(toImmutableSet());
  }

  /**
   * Pins the given {@link SubDeviceSpec} list to the specified device IDs by adding {@code id}
   * dimensions.
   */
  private static void addDeviceIdsToSubDeviceSpecs(
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
   * Creates the main Tradefed and non-Tradefed jobs based on the given {@link SessionRequestInfo}
   * and verifies that at least one job was created.
   */
  private void createMainJobs(
      SessionRequestInfo sessionRequestInfo,
      ImmutableSet<String> dynamicMctsModules,
      boolean skipDynamicMctsJob)
      throws MobileHarnessException, InterruptedException {
    populateMainJobs(sessionRequestInfo, dynamicMctsModules, skipDynamicMctsJob);
    if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
      throw new MobileHarnessException(
          InfraErrorId.XTS_NO_JOB_CREATED_FOR_SESSION,
          "No jobs created for session " + sessionInfo.getSessionId());
    }
  }

  /**
   * Populates {@link #tradefedJobs} and {@link #nonTradefedJobs} based on the given {@link
   * SessionRequestInfo}.
   *
   * <p>Separated from {@link #createMainJobs} so that {@link #restoreResumedSessionState} can
   * reconstruct expected job lists after a server restart without enforcing the initial non-empty
   * check.
   *
   * @param sessionRequestInfo the session request info representing the session config
   * @param dynamicMctsModules the canonical set of dynamic MCTS module names downloaded during the
   *     setup job, or an empty set if dynamic MCTS is disabled, no modules were requested, or the
   *     setup job is unavailable. If provided, they replace static MCTS modules for Tradefed job
   *     filtering and creation.
   * @param skipDynamicMctsJob when {@code true}, the dynamic MCTS job is not created in RUNNER mode
   */
  private void populateMainJobs(
      SessionRequestInfo sessionRequestInfo,
      ImmutableSet<String> dynamicMctsModules,
      boolean skipDynamicMctsJob)
      throws MobileHarnessException, InterruptedException {
    // Create tradefed jobs.
    try {
      tradefedJobs =
          xtsJobCreator.createXtsTradefedTestJob(
              sessionRequestInfo, dynamicMctsModules, skipDynamicMctsJob);
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
      nonTradefedJobs = xtsJobCreator.createXtsNonTradefedJobs(sessionRequestInfo);
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
  private void addMainJobs(SessionRequestInfo sessionRequestInfo) {
    ImmutableList<JobInfo> initialJobsToStart = prepareTradefedJobsToStart(sessionRequestInfo);
    if (!addAndTrackTradefedJobs(initialJobsToStart)) {
      logger.atInfo().log("No tradefed job was added, trying to add non-tradefed jobs if needed.");
      if (!addMainNonTradefedJobs()) {
        addTeardownJobIfAny();
      }
    }
  }

  private ImmutableList<JobInfo> prepareTradefedJobsToStart(SessionRequestInfo sessionRequestInfo) {
    if (SessionRequestHandlerUtil.shouldEnableModuleSharding(sessionRequestInfo)) {
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

  /**
   * Orders static xTS jobs before non-static (e.g. dynamic MCTS) Tradefed jobs so that both initial
   * scheduling and resumed-session recovery execute static jobs before dynamic jobs in RUNNER
   * sharding mode.
   */
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

  /**
   * Re-initializes session state and schedules remaining jobs when resuming a session after a
   * restart.
   *
   * <p>Jobs might be lost from in-memory state in resumed sessions. This method reconstructs active
   * running job maps and re-creates remaining expected jobs while filtering out jobs that have
   * already been triggered in the session.
   */
  private void restoreResumedSessionState(JobInfo currentJob, SessionRequestInfo sessionRequestInfo)
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
    // empty, using a completed setup job (if present) to recover dynamic MCTS parameters.
    if (tradefedJobs.isEmpty() && nonTradefedJobs.isEmpty()) {
      ImmutableSet<String> dynamicMctsModules = ImmutableSet.of();
      boolean skipDynamicMctsJob = false;
      Optional<JobInfo> existingSetupJob =
          sessionInfo.getAllJobs().stream().filter(AtsSessionOrchestrator::isSetupJob).findFirst();
      if (existingSetupJob.isPresent()) {
        setupJobRef.set(existingSetupJob.get());
        dynamicMctsModules = extractDynamicMctsModules(existingSetupJob.get());
        skipDynamicMctsJob = !extractHasPreloadedMainlineModules(existingSetupJob.get());
      }
      populateMainJobs(sessionRequestInfo, dynamicMctsModules, skipDynamicMctsJob);
      nonTradefedJobs =
          nonTradefedJobs.stream()
              .filter(job -> !triggeredJobNames.contains(job.locator().getName()))
              .collect(toImmutableList());
      xtsJobCreator
          .createXtsTearDownJob(sessionRequestInfo)
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

        if (SessionRequestHandlerUtil.shouldEnableModuleSharding(sessionRequestInfo)) {
          addAndTrackTradefedJobs(remainingTfJobs);
        } else if (!remainingTfJobs.isEmpty()) {
          JobInfo nextJob = remainingTfJobs.remove(0);
          additionalTradefedJobs.addAll(remainingTfJobs);
          ImmutableSet<String> devicesOfCurrentJob = getDeviceSerials(currentJob);
          // Add the device ids of the current job to the sub device specs of the next tradefed job.
          addDeviceIdsToSubDeviceSpecs(
              nextJob.subDeviceSpecs().getAllSubDevices(), devicesOfCurrentJob);
          addAndTrackTradefedJobs(ImmutableList.of(nextJob));
        }

        if (runningTradefedJobs.values().stream().noneMatch(running -> running)) {
          logger.atInfo().log(
              "All added tradefed jobs have been done, trying to add non-tradefed jobs if needed.");
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
          // Schedule any remaining untriggered non-Tradefed jobs before advancing to teardown.
          if (!addMainNonTradefedJobs()) {
            addTeardownJobIfAny();
          }
        }
      }
    }
  }

  /** Returns whether the given {@link JobInfo} uses the Tradefed test driver. */
  private static boolean isTradefedJob(JobInfo jobInfo) {
    return jobInfo.type().getDriver().equals(TRADEFED_DRIVER_NAME);
  }

  /** Returns whether the given {@link JobInfo} is an xTS setup job. */
  private static boolean isSetupJob(JobInfo jobInfo) {
    return Objects.equals(
        jobInfo.properties().get(XtsConstants.XTS_JOB_NAME), XtsConstants.SETUP_JOB_NAME);
  }

  /** Returns whether the given {@link JobInfo} is an xTS teardown job. */
  private static boolean isTeardownJob(JobInfo jobInfo) {
    return Objects.equals(
        jobInfo.properties().get(XtsConstants.XTS_JOB_NAME), XtsConstants.TEARDOWN_JOB_NAME);
  }
}
