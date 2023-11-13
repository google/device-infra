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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestManager;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobFileUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobOrTestFileUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.TestFileUnit;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveResult;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver.ResolveSource;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * For managing the job and test entities, including job/test execution units and lab-specific
 * job/test infos like test-client-rpc-status and job-lab-copied-file-list.
 */
@Singleton
public class JobManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Lab-side job execution information. */
  private static class JobLabExecutionUnit {

    /** Lab-side test execution information. */
    private static class TestLabExecutionUnit {

      /**
       * Set by incoming RPC ExecTestService.CloseTest() from client by the end of its
       * postRunTest(), which indicates there won't be any further RPCs for this test from client
       * side, and we are safe to consider this test is actually DONE.
       */
      @GuardedBy("this")
      private boolean clientPostRunDone;

      private final ProxyTestRunner testRunner;

      @GuardedBy("this")
      private final Set<TestFileUnit> testFileUnits = new HashSet<>();

      @Nullable private volatile ListenableFuture<List<ResolveResult>> resolveAllJobFilesFuture;

      private TestLabExecutionUnit(ProxyTestRunner testRunner) {
        this.testRunner = testRunner;
      }

      private synchronized void markClientPostRunDone() {
        clientPostRunDone = true;
      }

      private synchronized boolean isClientPostRunDone() {
        // TODO: Remove this line.
        if (!clientPostRunDone) {
          logger.atWarning().log(
              "The test %s does not finish ClientPostRun.",
              testRunner.getTestExecutionUnit().locator().id());
        }
        return clientPostRunDone;
      }

      private synchronized void notifyTestFile(TestFileUnit testFileUnit) {
        if (testFileUnits.contains(testFileUnit)) {
          logger.atInfo().log("Skip duplicated test file notification [%s]", testFileUnit);
        } else {
          testRunner.notifyJobOrTestFile(
              JobOrTestFileUnit.newBuilder().setTestFileUnit(testFileUnit).build());
          testFileUnits.add(testFileUnit);
        }
      }

      private void notifyJobFile(JobFileUnit jobFileUnit) {
        testRunner.notifyJobOrTestFile(
            JobOrTestFileUnit.newBuilder().setJobFileUnit(jobFileUnit).build());
      }

      private TestExecutionUnit getTestExecutionUnit() {
        return testRunner.getTestExecutionUnit();
      }
    }

    private final JobExecutionUnit jobExecutionUnit;

    @GuardedBy("this")
    private final Map<String, TestLabExecutionUnit> tests = new HashMap<>();

    /** Paths of files received from client and copied from shared directory to job directory. */
    @GuardedBy("this")
    private final Set<String> copiedFilePaths = new HashSet<>();

    @GuardedBy("this")
    private final Set<JobFileUnit> jobFileUnits = new HashSet<>();

    @GuardedBy("this")
    private final boolean disableMasterSyncing;

    @GuardedBy("this")
    private final Map<ResolveSource, ListenableFuture<ResolveResult>> resolveFileFutures =
        new HashMap<>();

    // The job has been closed from job manager and should not do further operation on it.
    @GuardedBy("this")
    private boolean closed;

    private JobLabExecutionUnit(JobExecutionUnit jobExecutionUnit, boolean disableMasterSyncing) {
      this.jobExecutionUnit = jobExecutionUnit;
      this.disableMasterSyncing = disableMasterSyncing;
    }

    private JobExecutionUnit getJobExecutionUnit() {
      return jobExecutionUnit;
    }

    private synchronized Optional<TestExecutionUnit> getTestExecutionUnit(String testId) {
      TestLabExecutionUnit test = tests.get(testId);
      return test == null ? Optional.empty() : Optional.of(test.getTestExecutionUnit());
    }

    private synchronized Optional<TestLabExecutionUnit> getTestLabExecutionUnit(String testId) {
      return Optional.ofNullable(tests.get(testId));
    }

    private synchronized Map<String, TestExecutionUnit> getAllTestExecutionUnits() {
      return tests.entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getTestExecutionUnit()));
    }

    private synchronized void addTest(String testId, ProxyTestRunner testRunner) {
      TestLabExecutionUnit test = new TestLabExecutionUnit(testRunner);
      TestLabExecutionUnit previous = tests.putIfAbsent(testId, test);
      if (previous == null) {
        jobFileUnits.forEach(test::notifyJobFile);
      }
    }

    private synchronized void markTestClientPostRunDone(String testId) {
      TestLabExecutionUnit test = tests.get(testId);
      if (test != null) {
        test.markClientPostRunDone();
      }
    }

    private synchronized boolean isAllTestClientPostRunDone() {
      return tests.values().stream().allMatch(TestLabExecutionUnit::isClientPostRunDone);
    }

    private synchronized void markFileCopied(String path) {
      copiedFilePaths.add(path);
    }

    private synchronized boolean hasFileCopied(String path) {
      return copiedFilePaths.contains(path);
    }

    private synchronized void notifyJobFile(JobFileUnit jobFileUnit) {
      if (jobFileUnits.contains(jobFileUnit)) {
        logger.atInfo().log("Skip duplicated job file notification [%s]", jobFileUnit);
      } else {
        tests.values().forEach(test -> test.notifyJobFile(jobFileUnit));
        jobFileUnits.add(jobFileUnit);
      }
    }

    private synchronized void notifyTestFile(TestFileUnit testFileUnit)
        throws MobileHarnessException {
      String testId = testFileUnit.getTestLocator().getId();
      TestLabExecutionUnit test = tests.get(testId);
      MobileHarnessExceptions.check(
          test != null,
          InfraErrorId.LAB_JM_TEST_NOT_FOUND,
          () -> String.format("Test %s does not exist", testId));
      test.notifyTestFile(testFileUnit);
    }

    private synchronized boolean isDisableMasterSyncing() {
      return this.disableMasterSyncing;
    }

    private synchronized ListenableFuture<ResolveResult> startResolveFile(
        ResolveSource resolveSource,
        Function<ResolveSource, ListenableFuture<ResolveResult>> resolveFileOperation)
        throws MobileHarnessException {
      if (!closed) {
        return resolveFileFutures.computeIfAbsent(resolveSource, resolveFileOperation);
      }
      throw new MobileHarnessException(
          InfraErrorId.LAB_JM_ADD_RESOLVE_FILE_FUTURE_TO_CLOSED_JOB_ERROR,
          String.format(
              "Should not add resolve file future to the closed job %s.",
              jobExecutionUnit.locator().id()));
    }

    @SuppressWarnings("Interruption")
    private synchronized void close() {
      closed = true;
      resolveFileFutures.values().forEach(future -> future.cancel(true));
    }
  }

  /**
   * &lt;job ID, JobLabExecutionUnit&gt; mapping which contains all the job and test execution
   * information.
   */
  private final Map<String, JobLabExecutionUnit> jobs = new ConcurrentHashMap<>();

  private final TestManager<?> testManager;
  private final LocalFileUtil fileUtil;

  @Inject
  public JobManager(TestManager<?> testManager) {
    this(testManager, new LocalFileUtil());
  }

  @VisibleForTesting
  JobManager(TestManager<?> testManager, LocalFileUtil fileUtil) {
    this.testManager = testManager;
    this.fileUtil = fileUtil;
  }

  /**
   * Adds a new job to the manager. After a new job is added, no effect if this method is invoked
   * again with another job with the same ID.
   *
   * @param job the new job to be added
   * @param disableMasterSyncing whether the job is not synced to master and not checked for
   *     expiration from master
   * @return the old job if exists or the new job
   */
  @CanIgnoreReturnValue
  public JobExecutionUnit addJobIfAbsent(JobExecutionUnit job, boolean disableMasterSyncing) {
    String jobId = job.locator().id();
    JobLabExecutionUnit oldJob =
        jobs.putIfAbsent(jobId, new JobLabExecutionUnit(job, disableMasterSyncing));
    return oldJob == null ? job : oldJob.getJobExecutionUnit();
  }

  /** Gets a &lt;job ID, JobExecutionUnit&gt; map. */
  public Map<String, JobExecutionUnit> getJobs() {
    return jobs.entrySet().stream()
        .collect(
            ImmutableMap.toImmutableMap(Entry::getKey, e -> e.getValue().getJobExecutionUnit()));
  }

  /**
   * Returns the {@link JobExecutionUnit} according to the job id.
   *
   * @throws MobileHarnessException if the job is not found
   */
  public JobExecutionUnit getJob(String jobId) throws MobileHarnessException {
    return getJobLabExecutionUnit(jobId).getJobExecutionUnit();
  }

  /** Returns a &lt;test ID, TestExecutionUnit&gt; map. */
  public Map<String, TestExecutionUnit> getTests() {
    return jobs.values().stream()
        .flatMap(job -> job.getAllTestExecutionUnits().entrySet().stream())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  /**
   * @return the {@link TestExecutionUnit} according to the job id and the test id
   * @throws MobileHarnessException if the test/job is not found
   */
  public TestExecutionUnit getTest(String jobId, String testId) throws MobileHarnessException {
    return getJobLabExecutionUnit(jobId)
        .getTestExecutionUnit(testId)
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.LAB_JM_TEST_NOT_FOUND,
                    String.format("Test %s does not exist", testId)));
  }

  /** Adds a test to a job. */
  public void addTestIfAbsent(ProxyTestRunner testRunner) throws MobileHarnessException {
    TestLocator testLocator = testRunner.getTestExecutionUnit().locator();
    getJobLabExecutionUnit(testLocator.jobLocator().id()).addTest(testLocator.id(), testRunner);
  }

  /**
   * Indicates there won't be any further RPCs for this test from client side, and we are safe to
   * consider this test is actually DONE for lameduck mode.
   */
  public void markTestClientPostRunDone(String jobId, String testId) throws MobileHarnessException {
    getJobLabExecutionUnit(jobId).markTestClientPostRunDone(testId);
  }

  /** Returns whether all tests of all jobs have finished client-side execution. */
  public boolean isAllTestClientPostRunDone() {
    return jobs.values().stream().allMatch(JobLabExecutionUnit::isAllTestClientPostRunDone);
  }

  /**
   * Returns whether the job will not be synced to MH master, and will not be checked for expiration
   * from master.
   */
  public boolean isJobDisableMasterSyncing(String jobId) throws MobileHarnessException {
    return getJobLabExecutionUnit(jobId).isDisableMasterSyncing();
  }

  /** Marks that a job file is copied to the given job path. */
  void markJobCopyFile(String jobId, String filePath) throws MobileHarnessException {
    getJobLabExecutionUnit(jobId).markFileCopied(filePath);
  }

  /**
   * @return if the job has copied the file
   * @throws MobileHarnessException if the job does not exist
   */
  boolean isJobFileCopied(String jobId, String filePath) throws MobileHarnessException {
    return getJobLabExecutionUnit(jobId).hasFileCopied(filePath);
  }

  void notifyTestFile(TestFileUnit testFileUnit) throws MobileHarnessException {
    getJobLabExecutionUnit(testFileUnit.getTestLocator().getJobLocator().getId())
        .notifyTestFile(testFileUnit);
  }

  void notifyJobFile(JobFileUnit jobFileUnit) throws MobileHarnessException {
    getJobLabExecutionUnit(jobFileUnit.getJobLocator().getId()).notifyJobFile(jobFileUnit);
  }

  /**
   * Start to resolve job files. It returns immediately and doesn't wait for resolve finished.
   *
   * <p>The resolve file operation will be added in the job level cache. So the same file of
   * different tests in same job only need to be downloaded once.
   */
  public void startResolveJobFiles(
      TestLocator testLocator,
      List<ResolveSource> resolveSources,
      Function<ResolveSource, ListenableFuture<ResolveResult>> resolveFileOperation)
      throws MobileHarnessException {
    List<ListenableFuture<ResolveResult>> resolveJobFileFutures = new ArrayList<>();
    JobLabExecutionUnit jobLabExecutionUnit = getJobLabExecutionUnit(testLocator.jobLocator().id());
    for (ResolveSource resolveSource : resolveSources) {
      ListenableFuture<ResolveResult> resolveJobFileFuture =
          jobLabExecutionUnit.startResolveFile(resolveSource, resolveFileOperation);
      Futures.addCallback(
          resolveJobFileFuture,
          new ResolveJobFileFutureCallback(testLocator),
          MoreExecutors.directExecutor());
      resolveJobFileFutures.add(resolveJobFileFuture);
    }
    getJobLabExecutionUnit(testLocator.jobLocator().id())
        .getTestLabExecutionUnit(testLocator.id())
        .ifPresent(
            testLabExecutionUnit ->
                testLabExecutionUnit.resolveAllJobFilesFuture =
                    Futures.allAsList(resolveJobFileFutures));
  }

  /** The callback to process the completed future of ResolveJobFile. */
  private class ResolveJobFileFutureCallback implements FutureCallback<ResolveResult> {

    private final TestLocator testLocator;

    private ResolveJobFileFutureCallback(TestLocator testLocator) {
      this.testLocator = testLocator;
    }

    @Override
    public void onSuccess(ResolveResult resolveResult) {
      try {
        for (String path : resolveResult.paths()) {
          notifyJobFile(
              JobFileUnit.newBuilder()
                  .setJobLocator(testLocator.jobLocator().toProto())
                  .setTag(resolveResult.resolveSource().tag())
                  .setOriginalPath(resolveResult.resolveSource().path())
                  .setLocalPath(path)
                  .build());
        }
      } catch (MobileHarnessException e) {
        logger.atSevere().withCause(e).log(
            "Failed to notify file %s being resolved for job %s.",
            resolveResult.resolveSource().path(), testLocator.jobLocator().id());
      }
    }

    @Override
    public void onFailure(Throwable t) {
      logger.atSevere().withCause(t).log(
          "Failed to resolve file for job %s.", testLocator.jobLocator().id());
    }
  }

  public Optional<ListenableFuture<List<ResolveResult>>> getResolveJobFilesFuture(
      String jobId, String testId) throws MobileHarnessException {
    return getJobLabExecutionUnit(jobId)
        .getTestLabExecutionUnit(testId)
        .map(
            testLabExecutionUnit ->
                Optional.ofNullable(testLabExecutionUnit.resolveAllJobFilesFuture))
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.LAB_JM_TEST_NOT_FOUND,
                    String.format("Test %s does not exist", testId)));
  }

  /**
   * Removes the job from the manager.
   *
   * @param jobId ID of the job
   * @throws MobileHarnessException if the job does not exist
   */
  public void removeJob(String jobId) throws MobileHarnessException, InterruptedException {
    ImmutableList<String> testIds = testManager.getAllTests(jobId);
    logger.atInfo().log("Kill tests when removing job %s: %s", jobId, testIds);
    for (String testId : testIds) {
      testManager.killAndRemoveTest(testId);
    }

    logger.atInfo().log("Remove job %s", jobId);
    JobLabExecutionUnit job = jobs.remove(jobId);
    MobileHarnessExceptions.check(
        job != null,
        InfraErrorId.LAB_JM_JOB_NOT_FOUND,
        () -> String.format("Job %s does not exist", jobId));
    job.close();

    Dirs jobDirs = job.getJobExecutionUnit().dirs();

    if (jobDirs.hasGenFileDir()) {
      Duration jobGenFileExpiredTime = Flags.instance().jobGenFileExpiredTime.getNonNull();
      if (!jobGenFileExpiredTime.isNegative() && !jobGenFileExpiredTime.isZero()) {
        logger.atInfo().log(
            "Skip gen file cleanup for job %s, gen file dir: %s. It will be cleaned up later by"
                + " FileCleaner after %s.",
            jobId, jobDirs.genFileDir(), jobGenFileExpiredTime);
      } else {
        logger.atInfo().log("Remove GEN_FILE dir of the removed job %s", jobId);
        try {
          String genFileDir = jobDirs.genFileDir();
          fileUtil.grantFileOrDirFullAccess(genFileDir);
          fileUtil.removeFileOrDir(genFileDir);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to remove files for job %s, details:\n%s", jobId, e.getMessage());
        }
      }
    }

    if (jobDirs.hasTmpFileDir()) {
      logger.atInfo().log("Remove TMP_FILE dir of the removed job %s", jobId);
      try {
        String tmpFileDir = jobDirs.tmpFileDir();
        fileUtil.grantFileOrDirFullAccessRecursively(tmpFileDir);
        fileUtil.removeFileOrDir(tmpFileDir);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to remove temporary files for job %s, details:\n%s", jobId, e.getMessage());
      }
    }

    if (jobDirs.hasRunFileDir()) {
      logger.atInfo().log("Remove RUN_FILE dir of the removed job %s", jobId);
      try {
        String runFileDir = jobDirs.runFileDir();
        fileUtil.grantFileOrDirFullAccessRecursively(runFileDir);
        fileUtil.removeFileOrDir(runFileDir);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to remove run files for job %s, details:\n%s", jobId, e.getMessage());
      }
    }
    logger.atInfo().log("Job %s removed", jobId);
  }

  private JobLabExecutionUnit getJobLabExecutionUnit(String jobId) throws MobileHarnessException {
    JobLabExecutionUnit job = jobs.get(jobId);
    MobileHarnessExceptions.check(
        job != null,
        InfraErrorId.LAB_JM_JOB_NOT_FOUND,
        () -> String.format("Job %s does not exist", jobId));
    return job;
  }
}
