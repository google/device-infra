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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.util.AddFileHandler;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobFileUnit;
import com.google.devtools.mobileharness.infra.lab.proto.File.TestFileUnit;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.comm.filetransfer.FileCallback;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

/**
 * Socket file receiver callback for classifying job/test files and adding to job/test accordingly.
 */
public class FileClassifier implements FileCallback {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final JobManager jobManager;

  private final LocalFileUtil fileUtil;

  /** Locks for job files copy threads. The key is target file path. */
  private final ConcurrentHashMap<String, CountDownLatch> copyingLocks = new ConcurrentHashMap<>();

  public FileClassifier(JobManager jobManager) {
    this.jobManager = jobManager;
    this.fileUtil = new LocalFileUtil();
  }

  @VisibleForTesting
  static class CopyFileHandler extends AddFileHandler {
    // TODO: Investigates whether it is safe to create hard links for all run files.
    /** Suffixes of run files that are safe to create hard links and executed with the links. */
    private static final ImmutableSet<String> LINKABLE_FILE_SUFFIX =
        ImmutableSet.of("apk", "gz", "img", "jar", "par", "tar", "zip");

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final LocalFileUtil fileUtil;
    private final String targetFileOrDirPath;

    @VisibleForTesting
    CopyFileHandler(String targetFileOrDirPath, LocalFileUtil fileUtil) {
      super();
      this.targetFileOrDirPath = checkNotNull(targetFileOrDirPath);
      this.fileUtil = checkNotNull(fileUtil);
    }

    @Override
    public String getTargetFileOrDirPath(String originalFileOrDirPath) {
      return targetFileOrDirPath;
    }

    @Override
    public void handleFileOrDir(String tag, String originalFileOrDirPath)
        throws MobileHarnessException, InterruptedException {
      // Creates parent dir of the target file/dir.
      File targetFileOrDir = new File(targetFileOrDirPath);
      String targetFileOrDirPathParent = targetFileOrDir.getParent();
      logger.atFine().log("Prepare dir %s", targetFileOrDirPathParent);
      fileUtil.prepareDir(targetFileOrDirPathParent);

      // Copies/links the source file/dir to the job specific run-file dir.
      if (LINKABLE_FILE_SUFFIX.contains(
          Ascii.toLowerCase(Files.getFileExtension(targetFileOrDirPath)))) {
        logger.atFine().log(
            "Hard link file from %s to %s", originalFileOrDirPath, targetFileOrDirPath);
        fileUtil.hardLinkFile(originalFileOrDirPath, targetFileOrDirPath);
      } else {
        logger.atFine().log(
            "Copy file/dir from %s to %s", originalFileOrDirPath, targetFileOrDirPath);
        if (fileUtil.getFileOrDir(originalFileOrDirPath).isDirectory()
            && targetFileOrDir.exists()) {
          fileUtil.prepareDir(targetFileOrDirPath);
          for (File srcFile : fileUtil.listFilesOrDirs(originalFileOrDirPath)) {
            fileUtil.copyFileOrDir(srcFile.getPath(), targetFileOrDirPath);
          }
        } else {
          fileUtil.copyFileOrDir(originalFileOrDirPath, targetFileOrDirPath);
        }
      }
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof CopyFileHandler
          && ((CopyFileHandler) object).targetFileOrDirPath.equals(targetFileOrDirPath);
    }

    @Override
    public int hashCode() {
      return targetFileOrDirPath.hashCode();
    }
  }

  @Override
  public void onReceived(
      String fileId, String tag, String path, String originalPath, @Nullable String checksum)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log(
        "Receive lab file [fileId=%s, tag=%s, path=%s, originalPath=%s, checksum=%s]",
        fileId, tag, path, originalPath, checksum);
    TestLocator testLocator = TestLocator.tryParseString(fileId);
    if (testLocator != null) {
      TestExecutionUnit test =
          jobManager.getTest(testLocator.getJobLocator().getId(), testLocator.getId());
      String relativePath =
          fileUtil.isLocalFileOrDir(originalPath)
              ? originalPath
              : fileUtil.escapeFilePath(originalPath.replaceAll("::", "/"));
      String targetFileOrDirPath =
          PathUtil.join(test.job().dirs().runFileDir(), testLocator.getId(), relativePath);
      copyFileToJobDir(testLocator.getJobLocator().getId(), tag, path, targetFileOrDirPath);

      TestFileUnit.Builder testFileUnit =
          TestFileUnit.newBuilder()
              .setTestLocator(testLocator.toNewTestLocator().toProto())
              .setTag(tag)
              .setLocalPath(targetFileOrDirPath)
              .setOriginalPath(originalPath);
      if (checksum != null) {
        testFileUnit.setChecksum(checksum);
      }
      jobManager.notifyTestFile(testFileUnit.build());
    } else {
      JobLocator jobLocator = JobLocator.parseString(fileId);
      JobExecutionUnit job = jobManager.getJob(jobLocator.getId());
      String relativePath =
          fileUtil.isLocalFileOrDir(originalPath)
              ? originalPath
              : fileUtil.escapeFilePath(originalPath.replaceAll("::", "/"));
      String targetFileOrDirPath = PathUtil.join(job.dirs().runFileDir(), relativePath);
      copyFileToJobDir(jobLocator.getId(), tag, path, targetFileOrDirPath);
      JobFileUnit.Builder jobFileUnit =
          JobFileUnit.newBuilder()
              .setJobLocator(jobLocator.toNewJobLocator().toProto())
              .setTag(tag)
              .setLocalPath(targetFileOrDirPath)
              .setOriginalPath(originalPath);
      if (checksum != null) {
        jobFileUnit.setChecksum(checksum);
      }
      jobManager.notifyJobFile(jobFileUnit.build());
    }
  }

  private void copyFileToJobDir(String jobId, String tag, String path, String targetFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    while (true) {
      CountDownLatch running = copyingLocks.putIfAbsent(targetFileOrDirPath, latch);
      if (running == null) {
        // Acquired.
        break;
      }
      running.await();
    }
    try {
      if (!jobManager.isJobFileCopied(jobId, targetFileOrDirPath)) {
        logger.atFine().log(
            "Copying job file from shared dir [jobId=%s, tag=%s, path=%s, targetPath=%s]",
            jobId, tag, path, targetFileOrDirPath);
        CopyFileHandler copyFileHandler = new CopyFileHandler(targetFileOrDirPath, fileUtil);
        copyFileHandler.handleFileOrDir(tag, path);
        jobManager.markJobCopyFile(jobId, targetFileOrDirPath);
      } else {
        logger.atFine().log(
            "Skip copying job file from shared dir [jobId=%s, tag=%s, path=%s, targetPath=%s]",
            jobId, tag, path, targetFileOrDirPath);
      }
    } finally {
      copyingLocks.remove(targetFileOrDirPath);
      latch.countDown();
    }
  }
}
