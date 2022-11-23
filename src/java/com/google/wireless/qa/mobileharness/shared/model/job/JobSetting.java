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

package com.google.wireless.qa.mobileharness.shared.model.job;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Dirs;
import com.google.devtools.mobileharness.api.model.proto.Job;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.api.model.proto.Job.Repeat;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Immutable settings of a job. */
public class JobSetting {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Timeout DEFAULT_TIMEOUT =
      Timeout.newBuilder()
          .setJobTimeoutMs(
              com.google.devtools.mobileharness.api.model.job.in.Timeout.getDefaultInstance()
                  .jobTimeout()
                  .toMillis())
          .setTestTimeoutMs(
              com.google.devtools.mobileharness.api.model.job.in.Timeout.getDefaultInstance()
                  .testTimeout()
                  .toMillis())
          .setStartTimeoutMs(
              com.google.devtools.mobileharness.api.model.job.in.Timeout.getDefaultInstance()
                  .startTimeout()
                  .toMillis())
          .build();

  private static final int DEFAULT_RETRY_TEST_ATTEMPTS = 2;

  public static Timeout getDefaultTimeout() {
    return DEFAULT_TIMEOUT;
  }

  public static Retry getDefaultRetryInstance() {
    return Retry.newBuilder()
        .setTestAttempts(DEFAULT_RETRY_TEST_ATTEMPTS)
        .setRetryLevel(Retry.Level.ERROR)
        .build();
  }

  /** Min timeout setting for a job. */
  public static final Duration MIN_JOB_TIMEOUT = Duration.ofMinutes(5);

  /** Min timeout setting for starting a job. */
  public static final Duration MIN_START_TIMEOUT = Duration.ofSeconds(4);

  /** Min timeout setting for a test. */
  public static final Duration MIN_TEST_TIMEOUT = Duration.ofMinutes(1);

  /** Max timeout setting for a job. */
  public static final Duration MAX_JOB_TIMEOUT =
      com.google.devtools.mobileharness.api.model.job.in.Timeout.MAX_JOB_TIMEOUT;

  /**
   * @deprecated java/com/google/sitespeed/pagespeed/speedindex/MobileHarnessClient.java still uses
   *     it.
   */
  @Deprecated public static final long MAX_JOB_TIMEOUT_MS = MAX_JOB_TIMEOUT.toMillis();

  /** Max timeout setting for a test. */
  public static final Duration MAX_TEST_TIMEOUT =
      com.google.devtools.mobileharness.api.model.job.in.Timeout.MAX_TEST_TIMEOUT;

  /** Min timeout difference between job timeout and test timeout. */
  public static final Duration MIN_JOB_TEST_TIMEOUT_DIFF = Duration.ofMinutes(1);

  /** Job timeout setting. */
  private final Timeout timeout;

  /** Job timeout setting. */
  private final com.google.devtools.mobileharness.api.model.job.in.Timeout newTimeout;

  /** Job retry setting. */
  private final Retry retry;

  /** Job priority. */
  private final Priority priority;

  /** Job priority. */
  private final Job.Priority newPriority;

  /** Allocation exit strategy. */
  private final AllocationExitStrategy allocationExitStrategy;

  private final Dirs newDirs;

  /** Job repeat run setting. */
  private final Repeat repeat;

  /** Builder for building a JobSetting instance. */
  public static class Builder {
    private Timeout timeout;
    private Retry retry;
    private Priority priority;
    private AllocationExitStrategy allocationExitStrategy;
    private String genFileDirPath;
    private String tmpFileDirPath;
    private String runFileDirPath;
    private String remoteFileDirPath;
    private boolean hasTestSubdirs = true;
    private LocalFileUtil localFileUtil;
    private Repeat repeat;

    private Builder() {}

    public JobSetting build() {
      return new JobSetting(this);
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setTimeout(Timeout timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setRetry(Retry retry) {
      this.retry = retry;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setRepeat(Repeat repeat) {
      this.repeat = repeat;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setGenFileDir(String dirPath) {
      genFileDirPath = dirPath;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setTmpFileDir(String dirPath) {
      tmpFileDirPath = dirPath;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setRunFileDir(String dirPath) {
      runFileDirPath = dirPath;
      return this;
    }

    /** Optional, true by default. */
    @CanIgnoreReturnValue
    public Builder setHasTestSubdirs(boolean hasTestSubdirs) {
      this.hasTestSubdirs = hasTestSubdirs;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setRemoteFileDir(String dirPath) {
      this.remoteFileDirPath = dirPath;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setLocalFileUtil(LocalFileUtil localFileUtil) {
      this.localFileUtil = localFileUtil;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAllocationExitStrategy(AllocationExitStrategy allocationExitStrategy) {
      this.allocationExitStrategy = allocationExitStrategy;
      return this;
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  private JobSetting(Builder builder) {
    // Finalizes the dirs.
    String genFileDirPath;
    if (builder.genFileDirPath == null) {
      genFileDirPath = getDefaultTmpDir() + "/mh_gen_" + UUID.randomUUID();
    } else {
      genFileDirPath = builder.genFileDirPath;
    }
    String tmpFileDirPath;
    if (builder.tmpFileDirPath == null) {
      tmpFileDirPath = getDefaultTmpDir() + "/mh_tmp_" + UUID.randomUUID();
    } else {
      tmpFileDirPath = builder.tmpFileDirPath;
    }
    String runFileDirPath;
    if (builder.runFileDirPath == null) {
      runFileDirPath = getDefaultTmpDir() + "/mh_run_" + UUID.randomUUID();
    } else {
      runFileDirPath = builder.runFileDirPath;
    }
    newDirs =
        new Dirs(
            genFileDirPath,
            tmpFileDirPath,
            runFileDirPath,
            builder.remoteFileDirPath,
            builder.hasTestSubdirs,
            builder.localFileUtil == null ? new LocalFileUtil() : builder.localFileUtil);

    // Finalizes priority setting.
    if (builder.priority == null) {
      priority = Priority.DEFAULT;
    } else {
      priority = builder.priority;
    }
    newPriority = Job.Priority.valueOf(priority.name());
    allocationExitStrategy =
        builder.allocationExitStrategy == null
            ? AllocationExitStrategy.NORMAL
            : builder.allocationExitStrategy;

    // Finalizes timeout setting.
    com.google.devtools.mobileharness.api.model.job.in.Timeout.Builder timeoutBuilder =
        com.google.devtools.mobileharness.api.model.job.in.Timeout.newBuilder();
    if (builder.timeout != null) {
      timeoutBuilder.setJobTimeout(Duration.ofMillis(builder.timeout.getJobTimeoutMs()));
      timeoutBuilder.setTestTimeout(Duration.ofMillis(builder.timeout.getTestTimeoutMs()));
      timeoutBuilder.setStartTimeout(Duration.ofMillis(builder.timeout.getStartTimeoutMs()));
    }
    this.newTimeout = timeoutBuilder.build();
    this.timeout =
        Timeout.newBuilder()
            .setJobTimeoutMs(newTimeout.jobTimeout().toMillis())
            .setTestTimeoutMs(newTimeout.testTimeout().toMillis())
            .setStartTimeoutMs(newTimeout.startTimeout().toMillis())
            .build();

    // Finalizes retry setting.
    if (builder.retry == null) {
      retry = getDefaultRetryInstance();
    } else {
      Retry.Builder retryBuilder = builder.retry.toBuilder();
      if (retryBuilder.getTestAttempts() <= 0) {
        logger.atWarning().log(
            "Test attempts %d <= 0. Set it to default value %d.",
            retryBuilder.getTestAttempts(), getDefaultRetryInstance().getTestAttempts());
        retryBuilder.setTestAttempts(getDefaultRetryInstance().getTestAttempts());
      }
      retry = retryBuilder.build();
    }
    // Finalizes repeat setting.
    if (builder.repeat != null) {
      repeat = builder.repeat;
    } else {
      repeat = Repeat.getDefaultInstance();
    }
  }

  /** Gets the job priority setting. */
  public Priority getPriority() {
    return priority;
  }

  /** Gets the job priority setting. */
  public Job.Priority getNewPriority() {
    return newPriority;
  }

  public AllocationExitStrategy getAllocationExitStrategy() {
    return allocationExitStrategy;
  }

  /** Gets the job timeout setting. */
  public Timeout getTimeout() {
    return timeout;
  }

  /** Gets the job timeout setting. */
  public com.google.devtools.mobileharness.api.model.job.in.Timeout getNewTimeout() {
    return newTimeout;
  }

  /** Gets the job retry setting. */
  public Retry getRetry() {
    return retry;
  }

  public Repeat getRepeat() {
    return repeat;
  }

  /** Returns true if the {@code genFileDirPath} has been created. */
  public boolean hasGenFileDir() {
    return dirs().hasGenFileDir();
  }

  /**
   * Gets the path of the directory that contains all and only the generated files of this job. Will
   * create this directory if it doesn't exist.
   */
  public String getGenFileDir() throws MobileHarnessException {
    return dirs().genFileDir();
  }

  /** Returns true if the {@code tmpFileDirPath} has been created. */
  public boolean hasTmpFileDir() {
    return dirs().hasTmpFileDir();
  }

  /**
   * Gets the path of the directory that contains all and temp files of this job. Will create this
   * directory if it doesn't exist.
   */
  public String getTmpFileDir() throws MobileHarnessException {
    return dirs().tmpFileDir();
  }

  /** Returns true if the {@code runFileDirPath} has been created. */
  public boolean hasRunFileDir() {
    return dirs().hasRunFileDir();
  }

  /**
   * Gets the path of the directory that contains all and only the run files of this job. Will
   * create this directory if it doesn't exist.
   */
  public String getRunFileDir() throws MobileHarnessException {
    return dirs().runFileDir();
  }

  /** Whether to create independent gen/tmp subdirs for each test under the job gen/tmp dirs. */
  public boolean hasTestSubdirs() {
    return dirs().hasTestSubdirs();
  }

  /** Gets the remote file root dir. */
  public Optional<String> getRemoteFileDir() {
    return dirs().remoteFileDir();
  }

  /** Returns the job directory settings. */
  public Dirs dirs() {
    return newDirs;
  }

  private static String getDefaultTmpDir() {
    // https://bazel.build/reference/test-encyclopedia#test-interaction-filesystem
    return System.getenv("TEST_TMPDIR");
  }
}
