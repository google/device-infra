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

package com.google.devtools.mobileharness.infra.lab.controller.util;

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.util.AddFileHandler;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobFileUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobOrTestFileUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobOrTestFileUnit.JobOrTestCase;
import com.google.devtools.mobileharness.infra.lab.proto.File.TestFileUnit;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper.FilePathVisitor;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Lab file notifier for receiving and caching job/test file receiving notification and handling
 * these files after test is kicked off.
 */
public class LabFileNotifier {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final JobSpecHelper jobSpecHelper;

  private final LocalFileUtil fileUtil;

  @GuardedBy("itself")
  private final List<JobOrTestFileUnit> fileCache = new ArrayList<>();

  @GuardedBy("fileCache")
  private boolean isTestStarted;

  /** Present if {@link #isTestStarted} is true. */
  @GuardedBy("fileCache")
  private TestInfo testInfo;

  /** Present if {@link #isTestStarted} is true. */
  @GuardedBy("fileCache")
  private Allocation allocation;

  public LabFileNotifier() {
    this(JobSpecHelper.getDefaultHelper(), new LocalFileUtil());
  }

  @VisibleForTesting
  LabFileNotifier(JobSpecHelper jobSpecHelper, LocalFileUtil fileUtil) {
    this.jobSpecHelper = jobSpecHelper;
    this.fileUtil = fileUtil;
  }

  /** Handles all cached job/test files. */
  @SuppressWarnings("GuardedBy")
  public void onTestStarting(TestInfo testInfo, Allocation allocation) {
    testInfo.log().atInfo().alsoTo(logger).log("Start to handling cached job/test files");
    synchronized (fileCache) {
      isTestStarted = true;
      this.testInfo = testInfo;
      this.allocation = allocation;
      // This access should be guarded by 'this.fileCache', which is not currently held
      fileCache.forEach(this::handleJobOrTestFile);
      // This access should be guarded by 'this.fileCache', which is not currently held
      fileCache.forEach(this::addJobOrTestFile);
      fileCache.clear();
    }
  }

  /** Handles a job/test file if the test has already started, otherwise adds it to a cache. */
  public void notifyJobOrTestFile(JobOrTestFileUnit fileUnit) {
    logger.atFine().log("Notify job/test file: %s", shortDebugString(fileUnit));
    synchronized (fileCache) {
      if (isTestStarted) {
        addJobOrTestFile(fileUnit);
        handleJobOrTestFile(fileUnit);
      } else {
        fileCache.add(fileUnit);
      }
    }
  }

  @GuardedBy("fileCache")
  @VisibleForTesting
  protected void addJobOrTestFile(JobOrTestFileUnit fileUnit) {
    testInfo
        .log()
        .at(Level.FINE)
        .alsoTo(logger)
        .log("Add job/test file: %s", shortDebugString(fileUnit));
    try {
      if (fileUnit.getJobOrTestCase().equals(JobOrTestCase.TEST_FILE_UNIT)) {
        addTestFile(fileUnit.getTestFileUnit());
      } else {
        addJobFile(fileUnit.getJobFileUnit());
      }
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.LAB_FILE_NOTIFIER_ADD_FILE_ERROR,
                  String.format("Failed to add file [%s]", fileUnit),
                  e),
              logger);
    }
  }

  @GuardedBy("fileCache")
  @VisibleForTesting
  protected void handleJobOrTestFile(JobOrTestFileUnit fileUnit) {
    testInfo
        .log()
        .at(Level.FINE)
        .alsoTo(logger)
        .log("Handle job/test file: %s", shortDebugString(fileUnit));
    try {
      if (fileUnit.getJobOrTestCase().equals(JobOrTestCase.TEST_FILE_UNIT)) {
        handleTestFile(fileUnit.getTestFileUnit());
      } else {
        handleJobFile(fileUnit.getJobFileUnit());
      }
    } catch (MobileHarnessException | InterruptedException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  e instanceof MobileHarnessException
                      ? InfraErrorId.LAB_FILE_NOTIFIER_HANDLE_FILE_ERROR
                      : InfraErrorId.LAB_FILE_NOTIFIER_HANDLE_FILE_INTERRUPTED,
                  String.format("Failed to handle file [%s]", fileUnit),
                  e),
              logger);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @GuardedBy("fileCache")
  protected void addTestFile(TestFileUnit testFileUnit) throws MobileHarnessException {
    testInfo.files().add(testFileUnit.getTag(), testFileUnit.getLocalPath());
  }

  @GuardedBy("fileCache")
  private void addJobFile(JobFileUnit jobFileUnit) throws MobileHarnessException {
    JobInfo jobInfo = testInfo.jobInfo();
    if (!jobFileUnit.getTag().startsWith(JobSpecHelper.FILE_TAG_PREFIX)
        && !jobFileUnit.getTag().startsWith(ScopedSpecs.FILE_TAG_PREFIX)) {
      jobInfo.files().add(jobFileUnit.getTag(), jobFileUnit.getLocalPath());
    }
  }

  @GuardedBy("fileCache")
  protected void handleTestFile(TestFileUnit testFileUnit) {}

  @GuardedBy("fileCache")
  private void handleJobFile(JobFileUnit jobFileUnit)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    // TODO: Deprecate AddFileHandler Interface as it is not needed.
    AddFileHandler extraFileHandler = AddFileHandler.EMPTY;
    if (jobFileUnit.getTag().startsWith(JobSpecHelper.FILE_TAG_PREFIX)) {
      jobInfo
          .protoSpec()
          .setProto(
              JobSpecHelper.forEachFiles(
                  jobInfo.protoSpec().getProto(),
                  new SpecFileReplacer(
                      jobFileUnit.getTag(),
                      jobFileUnit.getLocalPath(),
                      jobFileUnit.getOriginalPath(),
                      extraFileHandler)));
    } else if (jobFileUnit.getTag().startsWith(ScopedSpecs.FILE_TAG_PREFIX)) {
      updateScopedSpecFilesToLocalPath(jobFileUnit, jobInfo.scopedSpecs(), extraFileHandler);
      for (SubDeviceSpec subDeviceSpec : jobInfo.subDeviceSpecs().getAllSubDevices()) {
        updateScopedSpecFilesToLocalPath(
            jobFileUnit, subDeviceSpec.scopedSpecs(), extraFileHandler);
      }
    } else {
      String targetFileOrDirPath =
          extraFileHandler.getTargetFileOrDirPath(jobFileUnit.getLocalPath());
      // Actually handle the original file/dir.
      try {
        extraFileHandler.handleFileOrDir(jobFileUnit.getTag(), jobFileUnit.getLocalPath());
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_OR_TEST_FILE_HANDLER_ERROR,
            String.format(
                "Failed to execute file handler %s when processing file [%s]%s",
                extraFileHandler.getClass().getName(),
                jobFileUnit.getTag(),
                jobFileUnit.getLocalPath()),
            e);
      }
      // Makes sure the target file/dir exists after handling.
      try {
        if (fileUtil.isLocalFileOrDir(targetFileOrDirPath)) {
          fileUtil.checkFileOrDir(targetFileOrDirPath);
        }
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_OR_TEST_FILE_HANDLER_GENERATE_NO_FILE,
            String.format(
                "Failed to handle [%s]%s, the handler %s is supposed to generate %s but the file "
                    + "is not generated actually.",
                jobFileUnit.getTag(),
                jobFileUnit.getLocalPath(),
                extraFileHandler.getClass().getName(),
                targetFileOrDirPath),
            e);
      }
    }
  }

  private void updateScopedSpecFilesToLocalPath(
      JobFileUnit jobFileUnit, ScopedSpecs scopedSpecs, AddFileHandler extraFileHandler)
      throws MobileHarnessException, InterruptedException {
    scopedSpecs.addAll(
        JobSpecHelper.forEachFiles(
            scopedSpecs.toJobSpec(jobSpecHelper),
            new SpecFileReplacer(
                jobFileUnit.getTag(),
                jobFileUnit.getLocalPath(),
                jobFileUnit.getOriginalPath(),
                extraFileHandler)));
  }

  /**
   * A visitor of {@link JobSpec}. Used to replace the original path in JobSpec (from sender) with
   * the target path (in receiver).
   */
  private class SpecFileReplacer extends FilePathVisitor {

    /** Path of received file. */
    private final String path;

    /** Tag of {@link #path}. */
    private final String tag;

    /** Path in sender side. */
    private final String originalPath;

    /** Target path in server side. */
    private String targetPath = null;

    private AddFileHandler handler = null;

    private SpecFileReplacer(String tag, String path, String originalPath, AddFileHandler handler) {
      this.path = path;
      this.tag = tag;
      this.originalPath = originalPath;
      this.handler = handler;
    }

    @Nullable
    @Override
    public String handleFile(String specPath) throws InterruptedException, MobileHarnessException {
      if (!specPath.equals(originalPath)) {
        return null;
      }
      if (targetPath == null) {
        if (handler != null) {
          handler.handleFileOrDir(tag, path);
        }
        // Makes sure the target file/dir exists after handling.
        targetPath = handler.getTargetFileOrDirPath(path);
        if (fileUtil.isLocalFileOrDir(targetPath)) {
          fileUtil.checkFileOrDir(targetPath);
        }
      }
      return targetPath;
    }
  }
}
