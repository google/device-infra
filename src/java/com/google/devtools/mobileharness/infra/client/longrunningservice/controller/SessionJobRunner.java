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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.client.api.event.internal.JobFirstAllocationEvent;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** Runner for running OmniLab jobs of a session. */
public class SessionJobRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of checking job status. */
  private static final Duration WAIT_FOR_JOB_INTERNAL = Duration.ofSeconds(2L);

  private final ClientApi clientApi;
  private final ExecMode execMode;
  private final Sleeper sleeper;

  private final SessionActivationListener sessionActivationListener =
      new SessionActivationListener();

  /**
   * Whether the session is activated, which means at least one test of one job of the session has
   * got an allocation.
   */
  private final AtomicBoolean sessionActivated = new AtomicBoolean(false);

  private final Object lock = new Object();

  /** Set in {@link #setSessionDetailHolder}. */
  private volatile SessionDetailHolder sessionDetailHolder;

  @GuardedBy("lock")
  private boolean enableJobPolling = true;

  @GuardedBy("lock")
  private boolean needKillJobs = false;

  @Inject
  SessionJobRunner(ClientApi clientApi, ExecMode execMode, Sleeper sleeper) {
    this.clientApi = clientApi;
    this.execMode = execMode;
    this.sleeper = sleeper;
  }

  public void setSessionDetailHolder(SessionDetailHolder sessionDetailHolder) {
    this.sessionDetailHolder = sessionDetailHolder;
  }

  public void runJobs(List<Object> sessionPlugins)
      throws MobileHarnessException, InterruptedException {
    List<String> allJobIds = new ArrayList<>();
    ImmutableList<Object> allJobPlugins =
        ImmutableList.<Object>builder()
            .addAll(sessionPlugins)
            .add(sessionActivationListener)
            .build();

    try {
      while (true) {
        boolean enableJobPolling;
        boolean needKillJobs;
        synchronized (lock) {
          enableJobPolling = this.enableJobPolling;
          needKillJobs = this.needKillJobs;
          this.needKillJobs = false;
        }

        if (enableJobPolling) {
          // Polls new jobs.
          ImmutableList<JobInfo> newJobs = sessionDetailHolder.pollJobs();
          allJobIds.addAll(
              newJobs.stream()
                  .map(JobInfo::locator)
                  .map(JobLocator::getId)
                  .collect(toImmutableList()));

          if (sessionActivated.get()) {
            // Sets HAS_ASSOCIATED_ALLOCATION for new jobs.
            setHasAssociatedAllocationForJobs(newJobs);
          }

          // Starts new jobs.
          for (JobInfo jobInfo : newJobs) {
            logger.atInfo().log(
                "Starting job %s of session %s",
                jobInfo.locator().getId(), sessionDetailHolder.getSessionId());
            jobInfo.properties().add(Job.CLIENT_TYPE, "olc");
            clientApi.startJob(jobInfo, execMode, allJobPlugins);
          }
        }

        if (needKillJobs) {
          killJobs(allJobIds);
        }

        // Waits until all jobs finish.
        if (allJobIds.stream().allMatch(clientApi::isJobDone)) {
          break;
        }

        sleeper.sleep(WAIT_FOR_JOB_INTERNAL);
      }
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      // Kills all jobs if an error occurs.
      logger.atWarning().log(
          "Error occurred during job running of session %s, killing all jobs",
          sessionDetailHolder.getSessionId());
      killJobs(allJobIds);
      throw e;
    }

    logger.atInfo().log("All jobs of session %s finished", sessionDetailHolder.getSessionId());
    // TODO: Adds job information to SessionOutput.
  }

  /** Stops polling new jobs and kills all running jobs. */
  public void abort() {
    synchronized (lock) {
      enableJobPolling = false;
      needKillJobs = true;
    }
  }

  private void killJobs(List<String> jobIds) {
    jobIds.forEach(clientApi::killJob);
  }

  private static void setHasAssociatedAllocationForJobs(ImmutableList<JobInfo> jobs) {
    for (JobInfo job : jobs) {
      logger.atInfo().log("Setting HAS_ASSOCIATED_ALLOCATION for job %s.", job.locator().getId());
      job.properties().add(Job.HAS_ASSOCIATED_ALLOCATION, "true");
    }
  }

  /**
   * A session-scope plugin that listens to the {@link JobFirstAllocationEvent} and sets the {@link
   * #sessionActivated} flag.
   */
  private final class SessionActivationListener {
    @Subscribe
    private void onFirstTestAllocated(JobFirstAllocationEvent event) {
      if (sessionActivated.compareAndSet(false, true)) {
        logger.atInfo().log(
            "Session [%s] activated by job [%s]'s test [%s] allocation.",
            sessionDetailHolder.getSessionId(),
            event.allocatedTest().jobInfo().locator().getId(),
            event.allocatedTest().locator().getId());
        setHasAssociatedAllocationForJobs(sessionDetailHolder.getAllJobs());
        logger.atInfo().log(
            "Finished setting HAS_ASSOCIATED_ALLOCATION for all jobs of session [%s].",
            sessionDetailHolder.getSessionId());
      }
    }
  }
}
