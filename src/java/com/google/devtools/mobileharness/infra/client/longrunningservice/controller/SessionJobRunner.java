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
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.mode.local.LocalMode;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Runner for running OmniLab jobs of a session. */
public class SessionJobRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of checking job status. */
  private static final Duration WAIT_FOR_JOB_INTERNAL = Duration.ofSeconds(2L);

  private final ClientApi clientApi;
  private final LocalMode localMode;
  private final Sleeper sleeper;

  @Inject
  SessionJobRunner(ClientApi clientApi, LocalMode localMode, Sleeper sleeper) {
    this.clientApi = clientApi;
    this.localMode = localMode;
    this.sleeper = sleeper;
  }

  public void runJobs(SessionDetailHolder sessionDetailHolder, List<Object> sessionPlugins)
      throws MobileHarnessException, InterruptedException {
    List<String> allJobIds = new ArrayList<>();

    try {
      while (true) {
        // Polls new jobs.
        ImmutableList<JobInfo> newJobs = sessionDetailHolder.pollJobs();
        allJobIds.addAll(
            newJobs.stream()
                .map(JobInfo::locator)
                .map(JobLocator::getId)
                .collect(toImmutableList()));

        // Starts new jobs.
        for (JobInfo jobInfo : newJobs) {
          logger.atInfo().log(
              "Starting job %s of session %s",
              jobInfo.locator().getId(), sessionDetailHolder.getSessionId());
          clientApi.startJob(jobInfo, localMode, sessionPlugins);
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

  private void killJobs(List<String> jobIds) {
    jobIds.forEach(clientApi::killJob);
  }
}
