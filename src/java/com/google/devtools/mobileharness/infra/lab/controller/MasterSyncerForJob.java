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

package com.google.devtools.mobileharness.infra.lab.controller;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStateChecker;
import com.google.devtools.mobileharness.infra.controller.test.event.TestExecutionEndedEvent;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.helper.JobSyncHelper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sync job information to MH master. */
public class MasterSyncerForJob implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of regular synchronization with master server. */
  private static final Duration SYNC_INTERVAL = Duration.ofSeconds(10);

  /** The extra time of expiring job. */
  @VisibleForTesting static final Duration EXTRA_TIME_FOR_EXPIRING_JOB = Duration.ofMinutes(1L);

  /** For talking to Master V5 JobSyncService. */
  private final JobSyncHelper jobSyncHelper;

  /** Job manager where to get all the job/test information. */
  private final JobManager jobManager;

  /** Checker to check whether the device is dirty. */
  private final DeviceStateChecker deviceStateChecker;

  private final Map<String, Instant> expiringJobs;
  private final Clock clock;

  private final NetUtil netUtil = new NetUtil();

  private final AtomicBoolean inDrainingMode = new AtomicBoolean(false);

  /** Sync local Lab Job information to MH master. */
  public MasterSyncerForJob(
      JobManager jobManager, JobSyncHelper jobSyncHelper, DeviceStateChecker deviceStateChecker) {
    this(
        jobManager,
        jobSyncHelper,
        deviceStateChecker,
        new ConcurrentHashMap<>(),
        Clock.systemUTC());
  }

  @VisibleForTesting
  MasterSyncerForJob(
      JobManager jobManager,
      JobSyncHelper jobSyncHelper,
      DeviceStateChecker deviceStateChecker,
      Map<String, Instant> expiringJobs,
      Clock clock) {
    this.jobManager = jobManager;
    this.jobSyncHelper = jobSyncHelper;
    this.deviceStateChecker = deviceStateChecker;
    this.expiringJobs = expiringJobs;
    this.clock = clock;
  }

  @Override
  public void run() {
    logger.atInfo().log("Start running");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(SYNC_INTERVAL.toMillis());
        checkExpiredJobs();
      } catch (InterruptedException e) {
        logger.atWarning().log("Interrupted: %s", e.getMessage());
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        // Catches all exception to make sure the DeviceManager thread won't be stopped. Otherwise,
        // no device can be detected.
        logger.atSevere().withCause(e).log("FATAL ERROR");
      }
    }
    logger.atSevere().log("Stopped!");
  }

  /** Closes the test in master when test is finished and marked as DONE. */
  @Subscribe
  public void onPostTest(TestExecutionEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    boolean deviceDirty = event.needReboot();
    String deviceId = event.getAllocation().getDevice().id();

    String jobId = event.getAllocation().getTest().jobLocator().id();

    if (isJobDisableMasterSyncing(jobId)) {
      logger.atInfo().log(
          "Skip test %s because job %s does not sync with master",
          event.getAllocation().getTest().id(), jobId);
      return;
    }

    try {
      deviceDirty |= deviceStateChecker.isDirty(deviceId) || inDrainingMode.get();
    } finally {
      logger.atInfo().log("Release device %s in master, DeviceDirty=%s", deviceId, deviceDirty);
      jobSyncHelper.closeTest(
          event.getAllocation().getTest(),
          event.getTestResult(),
          DeviceLocator.of(
              deviceId,
              LabLocator.of(
                  netUtil.getUniqueHostIpOrEmpty().orElse(""), netUtil.getLocalHostName())),
          deviceDirty);
    }
  }

  /** Checks and removes expired jobs. */
  @VisibleForTesting
  void checkExpiredJobs() throws InterruptedException {

    // Filters out the jobs that need to be synced with master and checked for expiration by master.
    ImmutableMap<String, JobExecutionUnit> jobs = getSyncingJobs();

    if (jobs.isEmpty()) {
      logger.atInfo().atMostEvery(2, MINUTES).log("No job");
      return;
    }
    // Gets the alive job ids from master.
    Set<String> aliveJobsInMaster = null;
    try {
      aliveJobsInMaster = new HashSet<>(jobSyncHelper.getAliveJobs(jobs.keySet()));

    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get the alive job ids from master");
    }
    for (JobExecutionUnit job : jobs.values()) {
      String jobId = job.locator().id();
      if (expiringJobs.containsKey(jobId)) {
        continue;
      }
      if (aliveJobsInMaster != null && !aliveJobsInMaster.contains(jobId)) {
        Instant instant = clock.instant().plus(EXTRA_TIME_FOR_EXPIRING_JOB);
        expiringJobs.put(jobId, instant);
        logger.atInfo().log(
            "Job %s is dead in master server. Added it into expiring jobs list and will remove it "
                + "after %s.",
            jobId, EXTRA_TIME_FOR_EXPIRING_JOB);
      } else if (job.timer().isExpired()) {
        expiringJobs.put(jobId, clock.instant().plus(EXTRA_TIME_FOR_EXPIRING_JOB));
        logger.atInfo().log(
            "Job %s is timeout in lab server. Added it into expiring jobs list and will remove it "
                + "after %s.",
            jobId, EXTRA_TIME_FOR_EXPIRING_JOB);
      }
    }

    // Adds the job to expired job list if the job was expired before
    // {@code EXTRA_TIME_FOR_EXPIRING_JOB}.
    List<String> expiredJobIds = Lists.newLinkedList();
    for (Entry<String, Instant> expiringEntry : expiringJobs.entrySet()) {
      if (clock.instant().isAfter(expiringEntry.getValue())) {
        expiredJobIds.add(expiringEntry.getKey());
      }
    }
    // Removes expired jobs from lab server.
    for (String expiredJobId : expiredJobIds) {
      try {
        jobManager.removeJob(expiredJobId);
        expiringJobs.remove(expiredJobId);
        logger.atInfo().log("Removed the expired job %s in lab server.", expiredJobId);
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to remove expired job %s.", expiredJobId);
      }
    }
  }

  /** Flag to notify JobSyncer in draining mode. */
  public void enableDrainingMode() {
    inDrainingMode.set(true);
  }

  private ImmutableMap<String, JobExecutionUnit> getSyncingJobs() {
    var jobsByDisableMasterSyncing =
        jobManager.getJobs().entrySet().stream()
            .collect(
                partitioningBy(
                    entry -> isJobDisableMasterSyncing(entry.getKey()),
                    toImmutableMap(Entry::getKey, Entry::getValue)));

    // log down the skipped jobs due to disableMasterSyncing
    if (!jobsByDisableMasterSyncing.get(true).isEmpty()) {
      logger.atInfo().atMostEvery(2, MINUTES).log(
          "Skip checking jobs: %s",
          String.join(",", jobsByDisableMasterSyncing.get(true).keySet()));
    }

    // These jobs are needed to sync to master and check for expiration
    return jobsByDisableMasterSyncing.get(false);
  }

  private boolean isJobDisableMasterSyncing(String jobId) {
    try {
      return jobManager.isJobDisableMasterSyncing(jobId);
    } catch (com.google.devtools.mobileharness.api.model.error.MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Job manager failed to get disableMasterSyncing for job id %s", jobId);
      return false;
    }
  }
}
