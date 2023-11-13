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

package com.google.devtools.mobileharness.infra.client.api.controller.job;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.job.JobRunnerCore.EventScope;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.api.plugin.TestRetryHandler;
import com.google.devtools.mobileharness.infra.controller.test.event.TestExecutionEndedEvent;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Job manager which manages all the running jobs. It can start and kill a job. It's feasible to use
 * it in an external environment. E.g. in a RBE instance. The logic different from the old
 * JobManager includes:
 *
 * <ul>
 *   <li>trace.* that may cause the library crashed is removed.
 *   <li>JobFileResolver which is useless currently is removed.
 *   <li>references to useless plugins are removed.
 *   <li>JobRunner -> JobRunnerCore.
 * </ul>
 */
public class JobManagerCore implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of checking the current active jobs. */
  private static final long CHECK_JOB_INTERVAL_MS = java.time.Duration.ofMinutes(1).toMillis();

  /** Interval of waiting jobs. */
  private static final Duration WAIT_JOB_INTERVAL = Duration.ofSeconds(2);

  /** Job runner thread pool. */
  protected final ExecutorService jobThreadPool;

  /** Event bus for global Mobile Harness framework logic. */
  protected final EventBus globalInternalBus;

  /** Some special internal plugins which needs to be executed before API/JAR plugins. */
  private final ImmutableList<Object> internalPlugins;

  @GuardedBy("itself")
  private final Map<String, JobRunnerAndFuture> jobRunners;

  /** Creates a job manager to manage all running jobs. */
  public JobManagerCore(
      ExecutorService jobThreadPool,
      EventBus globalInternalEventBus,
      List<Object> internalPlugins) {
    this(jobThreadPool, globalInternalEventBus, internalPlugins, new HashMap<>());
  }

  /** Constructor for testing. */
  protected JobManagerCore(
      ExecutorService jobThreadPool,
      EventBus globalInternalEventBus,
      List<Object> internalPlugins,
      Map<String, JobRunnerAndFuture> jobRunners) {
    this.jobThreadPool = jobThreadPool;
    this.globalInternalBus = globalInternalEventBus;
    this.internalPlugins = ImmutableList.copyOf(internalPlugins);
    this.jobRunners = jobRunners;
  }

  /**
   * Starts a job runner to execute a job.
   *
   * @throws MobileHarnessException if the job is already started, or failed to start the new thread
   */
  public void startJob(JobInfo jobInfo, ExecMode execMode, @Nullable Collection<Object> jobPlugins)
      throws InterruptedException, MobileHarnessException {
    synchronized (jobRunners) {
      String jobId = jobInfo.locator().getId();
      JobRunnerAndFuture runnerFuture = jobRunners.get(jobId);
      if (runnerFuture != null) {
        JobRunnerCore runner = runnerFuture.jobRunner();
        Future<?> future = runnerFuture.jobRunnerFuture();
        if (runner.isRunning() || !future.isDone()) {
          throw new MobileHarnessException(
              ErrorCode.JOB_DUPLICATED, "Job " + jobId + " is already running");
        }
      }
      DeviceAllocator deviceAllocator = execMode.createDeviceAllocator(jobInfo, globalInternalBus);
      JobRunnerCore jobRunner = getJobRunner(jobInfo, deviceAllocator, execMode);

      // Loads internal plugins.
      for (Object internalPlugin : internalPlugins) {
        jobRunner.registerEventHandler(internalPlugin, EventScope.INTERNAL_PLUGIN);
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Loaded internal plugin: %s", internalPlugin.getClass().getCanonicalName());
      }

      // Loads job plugins.
      if (jobPlugins != null) {
        for (Object jobEventHandler : jobPlugins) {
          jobRunner.registerEventHandler(jobEventHandler, EventScope.API_PLUGIN);
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Loaded API plugin: %s", jobEventHandler.getClass().getCanonicalName());
        }
      }

      // Registers retry handler.
      jobRunner.registerEventHandler(new TestRetryHandler(deviceAllocator), EventScope.API_PLUGIN);

      registerPlugins(jobRunner, jobInfo, deviceAllocator);

      jobRunners.put(
          jobId, JobRunnerAndFuture.of(jobRunner, startJobRunnerThread(jobRunner, jobId)));

      jobInfo.log().atInfo().alsoTo(logger).log("Started job %s", jobId);
    }
  }

  /** Kills a job if the job exists and is running. */
  @SuppressWarnings("Interruption")
  public void killJob(String jobId) {
    synchronized (jobRunners) {
      JobRunnerAndFuture runnerFuture = jobRunners.get(jobId);
      if (runnerFuture != null) {
        JobRunnerCore runner = runnerFuture.jobRunner();
        Future<?> future = runnerFuture.jobRunnerFuture();
        JobInfo jobInfo = runner.getJobInfo();
        if (runner.isRunning() || !future.isDone()) {
          future.cancel(true);
          logger.atInfo().log(
              "Call stack for killJob: %s", Throwables.getStackTraceAsString(new Throwable()));
          jobInfo.log().atInfo().alsoTo(logger).log("Kill job %s", jobId);
        } else {
          jobRunners.remove(jobId);
          jobInfo.log().atInfo().alsoTo(logger).log("Job %s has already stopped", jobId);
        }
      } else {
        logger.atInfo().log("Job %s not found", jobId);
      }
    }
  }

  /** Checks whether the job is done. */
  public boolean isJobDone(String jobId) {
    synchronized (jobRunners) {
      JobRunnerAndFuture runnerFuture = jobRunners.get(jobId);
      if (runnerFuture != null) {
        JobRunnerCore runner = runnerFuture.jobRunner();
        Future<?> future = runnerFuture.jobRunnerFuture();
        if (runner.isRunning() || !future.isDone()) {
          return false;
        } else {
          jobRunners.remove(jobId);
        }
      }
      return true;
    }
  }

  /**
   * Waits until the job is finished or timeout.
   *
   * @return whether this job is done.
   */
  public boolean waitForJob(String jobId) {
    try {
      while (!Thread.currentThread().isInterrupted() && !isJobDone(jobId)) {
        Sleeper.defaultSleeper().sleep(WAIT_JOB_INTERVAL);
      }
    } catch (InterruptedException e) {
      logger.atWarning().log("Interrupted: %s", e.getMessage());
      Thread.currentThread().interrupt();
    }
    return isJobDone(jobId);
  }

  @Override
  public void run() {
    logger.atInfo().log("JobManager is started");
    Clock clock = Clock.systemUTC();
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Instant beforeSleep = clock.instant();
        Sleeper.defaultSleeper().sleep(Duration.ofMillis(CHECK_JOB_INTERVAL_MS));
        if (clock
            .instant()
            .isAfter(beforeSleep.plus(Duration.ofMillis((long) (CHECK_JOB_INTERVAL_MS * 1.5))))) {
          logger.atInfo().log(
              "Sleep too long in JobManager.run for %d ms. Before: %s, After %s.",
              clock.millis() - beforeSleep.toEpochMilli(), beforeSleep, clock.instant());
        }
        synchronized (jobRunners) {
          List<String> deadJobIds = new ArrayList<>();
          for (Entry<String, JobRunnerAndFuture> entry : jobRunners.entrySet()) {
            String jobId = entry.getKey();
            JobRunnerCore runner = entry.getValue().jobRunner();
            Future<?> future = entry.getValue().jobRunnerFuture();
            if (!runner.isRunning() && future.isDone()) {
              deadJobIds.add(jobId);
            }
          }
          for (String jobId : deadJobIds) {
            logger.atInfo().log("Remove stopped job: %s", jobId);
            jobRunners.remove(jobId);
          }
          if (!jobRunners.isEmpty()) {
            logger.atInfo().log(
                "(%d) Job Ids: %s", jobRunners.size(), Joiner.on(", ").join(jobRunners.keySet()));
          }
        }
      } catch (InterruptedException e) {
        logger.atWarning().log("Interrupted: %s", e.getMessage());
        Thread.currentThread().interrupt();
        break;
      } catch (RuntimeException e) {
        // Catches all the other runtime exceptions to keep this JobManager thread running.
        logger.atSevere().withCause(e).log("FATAL ERROR");
      }
    }
    logger.atInfo().log("JobManager is stopped!");
  }

  /** Gets the test message poster by the test id. */
  public Optional<TestMessagePoster> getTestMessagePoster(String testId) {
    synchronized (jobRunners) {
      // Tries to get the test message poster in each job runners and stops when
      // it finds the first result, or returns an empty if the test message poster is not found.
      return jobRunners.values().stream()
          .map(runnerFuture -> runnerFuture.jobRunner().getTestMessagePoster(testId))
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    }
  }

  /** Gets the instance of a JobRunnerCore thread. */
  protected JobRunnerCore getJobRunner(
      JobInfo jobInfo, DeviceAllocator deviceAllocator, ExecMode execMode)
      throws MobileHarnessException, InterruptedException {
    return new JobRunnerCore(jobInfo, deviceAllocator, execMode, globalInternalBus);
  }

  /** Registers job level plugins, does nothing by default. */
  protected void registerPlugins(
      JobRunnerCore jobRunner, JobInfo jobInfo, DeviceAllocator deviceAllocator)
      throws MobileHarnessException {
    // Does nothing.
  }

  /** Actually starts the JobRunnerCore thread. */
  protected Future<?> startJobRunnerThread(JobRunnerCore jobRunner, String jobId) {
    return jobThreadPool.submit(jobRunner);
  }

  /** Core logic to handle the test execution ended event in job manager. */
  protected void onTestExecutionEnded(TestExecutionEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    String jobId = event.getAllocation().getTest().jobLocator().id();
    JobRunnerAndFuture jobRunnerFuture;
    synchronized (jobRunners) {
      jobRunnerFuture = jobRunners.get(jobId);
    }
    if (jobRunnerFuture != null) {
      logger.atInfo().log(
          "Release allocation %s, result=%s, device_dirty=%b",
          event.getAllocation(), event.getTestResult(), event.needReboot());
      DeviceAllocator deviceAllocator = jobRunnerFuture.jobRunner().getDeviceAllocator();
      deviceAllocator.releaseAllocation(
          event.getAllocation(),
          TestResult.valueOf(event.getTestResult().name()),
          event.needReboot());
    } else {
      logger.atWarning().log(
          "Test %s execution ends after job runner %s stops",
          event.getAllocation().getTest().id(), jobId);
    }
  }

  /** Job runner and the future of its thread. */
  @AutoValue
  public abstract static class JobRunnerAndFuture {

    abstract JobRunnerCore jobRunner();

    @Nullable
    abstract Future<?> jobRunnerFuture();

    public static JobRunnerAndFuture of(
        JobRunnerCore jobRunner, @Nullable Future<?> jobRunnerFuture) {
      return new AutoValue_JobManagerCore_JobRunnerAndFuture(jobRunner, jobRunnerFuture);
    }
  }
}
