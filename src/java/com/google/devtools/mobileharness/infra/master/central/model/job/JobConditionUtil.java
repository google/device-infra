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

package com.google.devtools.mobileharness.infra.master.central.model.job;

import static java.lang.Math.max;

import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition.JobStatus;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobConditionOrBuilder;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Utils for processing {@link JobCondition}. */
public final class JobConditionUtil {

  /** The min keep alive timeout */
  public static final Duration MIN_KEEP_ALIVE_TIMEOUT = Duration.ofMinutes(5);

  /**
   * Checks whether the job is expired.
   *
   * <ul>
   *   <li>If the job is closed, the keep alive timeout is 5 minutes.
   *   <li>Otherwise, the keep alive timeout is the max of the keep_alive_timeout_ms in the job
   *       condition and 5 minutes.
   * </ul>
   */
  public static boolean isExpired(JobCondition condition) {
    Instant timestamp =
        Instant.ofEpochMilli(
            condition.getHeartbeatTimeMs()
                + (isClosed(condition)
                    ? MIN_KEEP_ALIVE_TIMEOUT.toMillis()
                    : max(condition.getKeepAliveTimeoutMs(), MIN_KEEP_ALIVE_TIMEOUT.toMillis())));
    return timestamp.isBefore(Clock.systemUTC().instant());
  }

  /** Checks whether the job is already closed. */
  public static boolean isClosed(JobCondition condition) {
    return condition.getStatus() == JobStatus.DONE || condition.getStatus() == JobStatus.KILLED;
  }

  /** Reports the detail of the job condition. */
  public static String getReport(JobConditionOrBuilder jobCondition) {
    return String.format(
        "%s@%s",
        jobCondition.getStatus(),
        TimeUtils.toDateShortString(Instant.ofEpochMilli(jobCondition.getHeartbeatTimeMs())));
  }

  private JobConditionUtil() {}
}
