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

package com.google.devtools.mobileharness.shared.util.sharedpool;

import static java.lang.Math.max;

import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.in.Dimensions;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.time.Duration;

/** Utils for MH shared pool */
public final class SharedPoolJobUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Any tests with a timeout longer than this value is considered to have a long start timeout. */
  private static final Duration LONG_START_TIMEOUT = Duration.ofMinutes(20);

  /** Hawkeye jobs will contain this string. */
  private static final String HAWKEYE_JOB_STR = "_HAWKEYE_";

  private SharedPoolJobUtil() {}

  public static boolean isUsingSharedPool(JobScheduleUnit job) {
    // Still use dimensions here, since M&M only support one device for now
    return Value.POOL_SHARED.equals(job.dimensions().get(Name.POOL));
  }

  public static boolean isUsingSharedPool(Dimensions dimensions) {
    return Value.POOL_SHARED.equals(dimensions.get(Name.POOL).orElse(null));
  }

  /** Returns whether if {@code jobConfig} is using shared pool. */
  public static boolean isUsingSharedPool(JobConfig jobConfig) {
    return jobConfig.getDevice().getSubDeviceSpecList().stream()
        .anyMatch(
            spec ->
                Value.POOL_SHARED.equals(
                    spec.getDimensions()
                        .getContentMap()
                        .get(Ascii.toLowerCase(Name.POOL.toString()))));
  }

  /** Returns whether {@code job} is using performance pool. */
  public static boolean isUsingPerformancePool(JobScheduleUnit job) {
    return job.dimensions().get(Name.PERFORMANCE_POOL) != null;
  }

  /** Returns whether {@code jobConfig} is using performance pool. */
  public static boolean isUsingPerformancePool(JobConfig jobConfig) {
    return jobConfig.getDevice().getSubDeviceSpecList().stream()
        .anyMatch(spec -> spec.getDimensions().getContentMap().get("performance_pool") != null);
  }

  /** Returns whether {@code job} is using default performance pool. */
  public static boolean isUsingDefaultPerformancePool(JobScheduleUnit job) {
    return job.dimensions().get(Name.PERFORMANCE_POOL) != null
        && Value.DEFAULT_PERFORMANCE_POOL_SHARED.equals(
            job.dimensions().get(Name.PERFORMANCE_POOL));
  }

  /** Returns whether {@code jobConfig} is using default performance pool. */
  public static boolean isUsingDefaultPerformancePool(JobConfig jobConfig) {
    return jobConfig.getDevice().getSubDeviceSpecList().stream()
        .anyMatch(
            spec ->
                spec.getDimensions().getContentMap().get("performance_pool") != null
                    && Value.DEFAULT_PERFORMANCE_POOL_SHARED.equals(
                        spec.getDimensions().getContentMap().get("performance_pool")));
  }

  /** Returns whether {@code job} is user performance pool in shared pool. */
  public static boolean isUsingSharedPerformancePool(JobScheduleUnit job) {
    return isUsingSharedPool(job) && isUsingPerformancePool(job);
  }

  /** Returns whether {@code job} is user performance pool in shared pool. */
  public static boolean isUsingSharedPerformancePool(JobConfig job) {
    return isUsingSharedPool(job) && isUsingPerformancePool(job);
  }

  /** Returns whether {@code job} is user default performance pool in shared pool. */
  public static boolean isUsingSharedDefaultPerformancePool(JobScheduleUnit job) {
    return isUsingSharedPool(job) && isUsingDefaultPerformancePool(job);
  }

  /** Returns whether {@code job} is user default performance pool in shared pool. */
  public static boolean isUsingSharedDefaultPerformancePool(JobConfig job) {
    return isUsingSharedPool(job) && isUsingDefaultPerformancePool(job);
  }

  /** Returns whether a job is from Hawkeye. */
  public static boolean isFromHawkeye(JobLocator job) {
    return job.name().contains(HAWKEYE_JOB_STR);
  }

  /**
   * Extends start timeout if necessary. see http://b/135557356 for more details.
   *
   * @param timeout that need to be updated; it is not necessary to be consistent with {@code
   *     jobConfig};
   * @param jobConfig config of job which is used to check the necessity to rewrite {@code timeout}
   * @return updated timeout
   */
  public static Timeout maybeExtendStartTimeout(Timeout timeout, JobConfig jobConfig) {
    if (!isUsingSharedPool(jobConfig)) {
      return timeout;
    }
    return maybeExtendStartTimeout(timeout);
  }

  private static Timeout maybeExtendStartTimeout(Timeout timeout) {
    long startTimeoutMs = timeout.getStartTimeoutMs();

    long timeoutGapMs = timeout.getJobTimeoutMs() - timeout.getTestTimeoutMs();
    boolean usingDefaultValue = startTimeoutMs == Timeout.getDefaultInstance().getStartTimeoutMs();

    long finalizedStartTimeoutMs = startTimeoutMs;
    if (usingDefaultValue || startTimeoutMs >= LONG_START_TIMEOUT.toMillis()) {
      finalizedStartTimeoutMs = max(timeoutGapMs, startTimeoutMs);
    }

    if (finalizedStartTimeoutMs != startTimeoutMs) {
      logger.atInfo().log(
          "Changing start timeout from %s ms to %s ms according to go/mnm-start-timeout",
          startTimeoutMs, finalizedStartTimeoutMs);
      return timeout.toBuilder().setStartTimeoutMs(finalizedStartTimeoutMs).build();
    }
    return timeout;
  }

  /** Returns the device model. */
  private static String getDeviceModel(Dimensions dimensions) {
    return dimensions.get(Name.MODEL).orElse("unknown");
  }

  /** Returns the device version. SDK_VERSION for Android while SOFTWARE_VERSION for iOS. */
  // TODO: Consolidate device version logic for Android/iOS separately.
  private static String getDeviceVersion(Dimensions dimensions) {
    return dimensions
        .get(Name.SDK_VERSION)
        .orElse(dimensions.get(Name.SOFTWARE_VERSION).orElse("unknown"));
  }
}
