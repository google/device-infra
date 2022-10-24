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

package com.google.devtools.mobileharness.infra.controller.test.util;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import com.google.devtools.mobileharness.api.model.job.out.Timing;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import java.time.Duration;
import java.time.Instant;

/** Job timer for calculating the expired time and the remaining time of a Mobile Harness job. */
@AutoValue
public abstract class JobTimer implements CountDownTimer {

  public static JobTimer create(Timing jobTiming, Timeout jobTimeout) {
    return new AutoValue_JobTimer(jobTiming, jobTimeout);
  }

  abstract Timing jobTiming();

  abstract Timeout jobTimeout();

  @Override
  public Instant expireTime() throws MobileHarnessException {
    return jobTiming()
        .getStartTime()
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    BasicErrorId.JOB_GET_EXPIRE_TIME_ERROR_BEFORE_START,
                    "Failed to calculate the job expire time because job has not started. "
                        + "Please set the job status from NEW to any other status."))
        .plus(jobTimeout().jobTimeout());
  }

  @Override
  public boolean isExpired() {
    try {
      return expireTime().isBefore(jobTiming().getClock().instant());
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  @Override
  public Duration remainingTimeJava() throws MobileHarnessException {
    Instant expireTime = expireTime();
    Instant now = jobTiming().getClock().instant();
    if (expireTime.isBefore(now)) {
      throw new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Job expired");
    }
    return Duration.between(now, expireTime);
  }
}
