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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.messaging.MessageDestinationNotFoundException;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.job.JobRunner.EventScope;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.api.plugin.TestRetryHandler;
import com.google.devtools.mobileharness.infra.client.api.util.uploader.ResultUploader;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSender;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSenderFinder;
import com.google.devtools.mobileharness.infra.controller.test.event.TestExecutionEndedEvent;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Job manager which manages all the running jobs. It can start and kill a job. */
public class JobManager implements Runnable, MessageSenderFinder {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of checking the current active jobs. */
  private static final Duration CHECK_JOB_INTERVAL = Duration.ofMinutes(1L);

  /** Interval of waiting jobs. */
  private static final Duration WAIT_JOB_INTERVAL = Duration.ofSeconds(2L);

  /** Job runner thread pool. */
  private final ListeningExecutorService jobThreadPool;

  /** Event bus for global Mobile Harness framework logic. */
  private final EventBus globalInternalBus;

  /** Some special internal plugins which needs to be executed before API/JAR plugins. */
  private final ImmutableList<Object> internalPlugins;

  /** Uploader for test results, passed to each `JobRunner` created by this manager. */
  private final List<ResultUploader> resultUploaders;

  @GuardedBy("itself")
  private final Map<String, JobRunnerAndFuture> jobRunners;

  public JobManager(
      ListeningExecutorService jobThreadPool,
      List<ResultUploader> resultUploaders,
      EventBus globalInternalEventBus,
      List<Object> internalPlugins) {
    this(jobThreadPool, resultUploaders, globalInternalEventBus, internalPlugins, new HashMap<>());
  }

  @VisibleForTesting
  JobManager(
      ListeningExecutorService jobThreadPool,
      List<ResultUploader> resultUploaders,
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
        JobRunner runner = runnerFuture.jobRunner();
        Future<?> future = runnerFuture.jobRunnerFuture();
        if (runner.isRunning() || !future.isDone()) {
          throw new MobileHarnessException(
              InfraErrorId.CLIENT_JR_JOB_START_DUPLICATED_ID,
              "Job " + jobId + " is already running");
        }
      }
      DeviceAllocator deviceAllocator = execMode.createDeviceAllocator(jobInfo, globalInternalBus);
      JobRunner jobRunner =
          new JobRunner(jobInfo, deviceAllocator, execMode, resultUploaders, globalInternalBus);

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
          jobId,
          JobRunnerAndFuture.of(
              jobRunner,
              jobThreadPool.submit(threadRenaming(jobRunner, () -> "job-runner-" + jobId))));

      jobInfo.log().atInfo().alsoTo(logger).log("Started job %s", jobId);
    }
  }

  /**
   * Kills a job if the job exists and is running.
   *
   * @param isManuallyAborted whether the kill signal is due to manual abortion.
   */
  @SuppressWarnings("Interruption")
  public void killJob(String jobId, boolean isManuallyAborted) {
    synchronized (jobRunners) {
      JobRunnerAndFuture runnerFuture = jobRunners.get(jobId);
      if (runnerFuture != null) {
        JobRunner runner = runnerFuture.jobRunner();
        Future<?> future = runnerFuture.jobRunnerFuture();
        JobInfo jobInfo = runner.getJobInfo();
        if (runner.isRunning() || !future.isDone()) {
          if (isManuallyAborted) {
            jobInfo.properties().add(PropertyName.Job.MANUALLY_ABORTED, "true");
          }
          // Interrupts test runner thread.
          try {
            runner.killAllTests();
          } catch (RuntimeException | Error e) {
            logger.atWarning().withCause(e).log(
                "Failed to kill tests of job %s", jobInfo.locator());
          }

          // Interrupts job runner thread.
          future.cancel(true);
          logger.atInfo().log(
              "Call stack for killJob: %s",
              MoreThrowables.shortDebugCurrentStackTrace(/* maxLength= */ 0));
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
        JobRunner runner = runnerFuture.jobRunner();
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
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Sleeper.defaultSleeper().sleep(CHECK_JOB_INTERVAL);
        synchronized (jobRunners) {
          List<String> deadJobIds = new ArrayList<>();
          for (Entry<String, JobRunnerAndFuture> entry : jobRunners.entrySet()) {
            String jobId = entry.getKey();
            JobRunner runner = entry.getValue().jobRunner();
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

  /** Gets a message sender. */
  public Optional<MessageSender> getMessageSender(MessageSend messageSend) {
    synchronized (jobRunners) {
      return jobRunners.values().stream()
          .map(runnerFuture -> runnerFuture.jobRunner().getMessageSender(messageSend))
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    }
  }

  @Override
  public MessageSender findMessageSender(MessageSend messageSend)
      throws MessageDestinationNotFoundException {
    synchronized (jobRunners) {
      Optional<MessageSender> messageSender = getMessageSender(messageSend);
      if (messageSender.isPresent()) {
        return messageSender.get();
      } else {
        throw new MessageDestinationNotFoundException(
            String.format(
                "Message destination is not found, message_send=[%s], current_jobs=%s",
                shortDebugString(messageSend), jobRunners.keySet()));
      }
    }
  }

  /** Registers job level builtin plugins. */
  @VisibleForTesting
  void registerPlugins(JobRunner jobRunner, JobInfo jobInfo, DeviceAllocator deviceAllocator)
      throws MobileHarnessException {}

  /** Core logic to handle the test execution ended event in job manager. */
  @Subscribe
  @VisibleForTesting
  void onTestExecutionEnded(TestExecutionEndedEvent event)
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

    abstract JobRunner jobRunner();

    abstract Future<?> jobRunnerFuture();

    public static JobRunnerAndFuture of(JobRunner jobRunner, Future<?> jobRunnerFuture) {
      return new AutoValue_JobManager_JobRunnerAndFuture(jobRunner, jobRunnerFuture);
    }
  }
}
