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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Job;
import java.time.Duration;

/** Timeout setting of a job. */
@AutoValue
public abstract class Timeout {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final Duration MAX_JOB_TIMEOUT;

  public static final Duration MAX_TEST_TIMEOUT;

  static {
    MAX_JOB_TIMEOUT = initializeMaxJobTimeout();
    MAX_TEST_TIMEOUT = initializeMaxTestTimeout();
    logger.atInfo().log(
        "Max job timeout is set as %s, max test timeout is set as %s",
        MAX_JOB_TIMEOUT, MAX_TEST_TIMEOUT);
  }

  @VisibleForTesting
  static Duration initializeMaxJobTimeout() {
    return getMaxJobTimeout(true);
  }

  @VisibleForTesting
  static Duration initializeMaxTestTimeout() {
    return getMaxTestTimeout(true);
  }

  public static Duration getMaxJobTimeout(boolean supportLongevityTest) {
    return supportLongevityTest ? Duration.ofDays(10) : Duration.ofDays(1);
  }

  public static Duration getMaxTestTimeout(boolean supportLongevityTest) {
    return supportLongevityTest ? Duration.ofDays(5) : Duration.ofHours(12);
  }

  @VisibleForTesting static final Duration DEFAULT_JOB_TIMEOUT = Duration.ofHours(1);
  @VisibleForTesting static final Duration DEFAULT_TEST_TIMEOUT = Duration.ofMinutes(5);
  @VisibleForTesting static final Duration DEFAULT_START_TIMEOUT = Duration.ofMinutes(5);

  @VisibleForTesting static final Duration MIN_JOB_TIMEOUT = Duration.ofMinutes(5);
  @VisibleForTesting static final Duration MIN_TEST_TIMEOUT = Duration.ofMinutes(1);
  @VisibleForTesting static final Duration MIN_START_TIMEOUT = Duration.ofSeconds(4);

  /** Min timeout difference between job timeout and test timeout. */
  @VisibleForTesting static final Duration MIN_JOB_TEST_TIMEOUT_DIFF = Duration.ofMinutes(1);

  /* Max execution time of a job. */
  public abstract Duration jobTimeout();

  /* Max execution time of a single test. */
  public abstract Duration testTimeout();

  /** Max wait time for the first device allocation after the job is started. */
  public abstract Duration startTimeout();

  public static Timeout.Builder newBuilder() {
    return new AutoValue_Timeout.Builder()
        .setJobTimeout(DEFAULT_JOB_TIMEOUT)
        .setTestTimeout(DEFAULT_TEST_TIMEOUT)
        .setStartTimeout(DEFAULT_START_TIMEOUT);
  }

  public static Timeout getDefaultInstance() {
    return new AutoValue_Timeout.Builder()
        .setJobTimeout(DEFAULT_JOB_TIMEOUT)
        .setTestTimeout(DEFAULT_TEST_TIMEOUT)
        .setStartTimeout(DEFAULT_START_TIMEOUT)
        .autoBuild();
  }

  public static Timeout fromProto(Job.Timeout proto) {
    Timeout.Builder builder = newBuilder();
    if (proto.getJobTimeoutMs() > 0) {
      builder.setJobTimeout(Duration.ofMillis(proto.getJobTimeoutMs()));
    }
    if (proto.getTestTimeoutMs() > 0) {
      builder.setTestTimeout(Duration.ofMillis(proto.getTestTimeoutMs()));
    }
    if (proto.getStartTimeoutMs() > 0) {
      builder.setStartTimeout(Duration.ofMillis(proto.getStartTimeoutMs()));
    }
    return builder.build();
  }

  public Job.Timeout toProto() {
    return Job.Timeout.newBuilder()
        .setJobTimeoutMs(jobTimeout().toMillis())
        .setTestTimeoutMs(testTimeout().toMillis())
        .setStartTimeoutMs(startTimeout().toMillis())
        .build();
  }

  /** Builder for creating {@link Timeout} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Optional. If not set, will uses {@link #DEFAULT_JOB_TIMEOUT}. */
    public abstract Builder setJobTimeout(Duration jobTimeout);

    /** Optional. If not set, will uses {@link #DEFAULT_TEST_TIMEOUT}. */
    public abstract Builder setTestTimeout(Duration testTimeout);

    /** Optional. If not set, will uses {@link #DEFAULT_START_TIMEOUT}. */
    public abstract Builder setStartTimeout(Duration startTimeout);

    abstract Duration jobTimeout();

    abstract Duration testTimeout();

    abstract Duration startTimeout();

    abstract Timeout autoBuild();

    public Timeout build() {
      if (jobTimeout().compareTo(MIN_JOB_TIMEOUT) < 0) {
        logger.atWarning().log(
            "Job timeout %s is less than lower bound. Increase it to %s.",
            jobTimeout(), MIN_JOB_TIMEOUT);
        setJobTimeout(MIN_JOB_TIMEOUT);
      } else if (jobTimeout().compareTo(MAX_JOB_TIMEOUT) > 0) {
        logger.atWarning().log(
            "Job timeout %s is greater than upper bound. Reduce it to %s.",
            jobTimeout(), MAX_JOB_TIMEOUT);
        setJobTimeout(MAX_JOB_TIMEOUT);
      }

      if (testTimeout().compareTo(MIN_TEST_TIMEOUT) < 0) {
        logger.atWarning().log(
            "Test timeout %s is less than lower bound. Increase it to %s.",
            testTimeout(), MIN_TEST_TIMEOUT);
        setTestTimeout(MIN_TEST_TIMEOUT);
      } else {
        Duration upperBound = jobTimeout().minus(MIN_JOB_TEST_TIMEOUT_DIFF);
        if (upperBound.compareTo(MAX_TEST_TIMEOUT) > 0) {
          upperBound = MAX_TEST_TIMEOUT;
        }
        if (testTimeout().compareTo(upperBound) > 0) {
          logger.atWarning().log(
              "Test timeout %s is "
                  + (upperBound.equals(MAX_TEST_TIMEOUT)
                      ? "greater than upper bound"
                      : "greater than or too close to job timeout")
                  + ". Reduce it to %s.",
              testTimeout(),
              upperBound);
          setTestTimeout(upperBound);
        }
      }

      if (startTimeout().compareTo(MIN_START_TIMEOUT) < 0) {
        logger.atWarning().log(
            "Start timeout %s is less than lower bound. Increase it to %s.",
            startTimeout(), MIN_START_TIMEOUT);
        setStartTimeout(MIN_START_TIMEOUT);
      } else if (startTimeout().compareTo(jobTimeout()) > 0) {
        logger.atWarning().log(
            "Start timeout %s is greater than the job timeout setting. Reduce it to %s.",
            startTimeout(), jobTimeout());
        setStartTimeout(jobTimeout());
      }

      return autoBuild();
    }
  }
}
