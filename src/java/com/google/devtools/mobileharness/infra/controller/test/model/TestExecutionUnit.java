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

package com.google.devtools.mobileharness.infra.controller.test.model;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.job.in.Timeout;
import com.google.devtools.mobileharness.api.model.job.out.Timing;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.TestScheduleUnit;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import java.time.Duration;
import java.time.Instant;

/**
 * Data model for a top-level test which contains the test information needed for the test executor
 * like lab server process.
 */
public class TestExecutionUnit extends TestScheduleUnit {

  private final JobExecutionUnit job;
  private final TestTimer timer = new TestTimer();

  public TestExecutionUnit(TestLocator locator, Timing timing, JobExecutionUnit job) {
    super(locator, timing);
    this.job = job;
  }

  public JobExecutionUnit job() {
    return job;
  }

  /** Timer of the test which starts when the test starts and expires when the test expires. */
  public TestTimer timer() {
    return timer;
  }

  /** The strategy to decide how to calculate the test expire time. */
  public interface TestExpireTimeCalculationStrategy {

    Instant getTestExpireTime(Instant testStartTime, Timeout timeout);
  }

  /** The timer to calculate the expire/remaining time for one test. */
  public class TestTimer implements CountDownTimer {

    private volatile TestExpireTimeCalculationStrategy testExpireTimeCalculationStrategy =
        (testStartTime, timeout) -> testStartTime.plus(timeout.testTimeout());

    /** Change the strategy to calculate the expire time. */
    public void setTestExpireTimeCalculationStrategy(
        TestExpireTimeCalculationStrategy testExpireTimeCalculationStrategy) {
      if (testExpireTimeCalculationStrategy != null) {
        this.testExpireTimeCalculationStrategy = testExpireTimeCalculationStrategy;
      }
    }

    @Override
    public Instant expireTime() throws MobileHarnessException {
      Instant testExpireTime = getTestExpireTime();
      Instant jobExpireTime = job.timer().expireTime();
      return jobExpireTime.isBefore(testExpireTime) ? jobExpireTime : testExpireTime;
    }

    @Override
    public boolean isExpired() {
      try {
        return expireTime().isBefore(timing().getClock().instant());
      } catch (MobileHarnessException e) {
        return false;
      }
    }

    @Override
    public java.time.Duration remainingTimeJava() throws MobileHarnessException {
      Instant now = timing().getClock().instant();
      Instant jobExpireTime = job.timer().expireTime();
      if (jobExpireTime.isBefore(now)) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_TIMEOUT, "Job expired. No time to run remaining test steps.");
      }
      Instant testExpireTime = getTestExpireTime();
      if (testExpireTime.isBefore(now)) {
        throw new MobileHarnessException(
            BasicErrorId.TEST_TIMEOUT, "The test expired. No time to run remaining test steps.");
      }
      return jobExpireTime.isBefore(testExpireTime)
          ? Duration.between(now, jobExpireTime)
          : Duration.between(now, testExpireTime);
    }

    private Instant getTestExpireTime() throws MobileHarnessException {
      return testExpireTimeCalculationStrategy.getTestExpireTime(
          timing()
              .getStartTime()
              .orElseThrow(
                  () ->
                      new MobileHarnessException(
                          BasicErrorId.TEST_GET_EXPIRE_TIME_ERROR_BEFORE_START,
                          "Failed to calculate the test expire time because the test has not"
                              + " started. Please set its status from NEW to any other status.")),
          job.timeout());
    }
  }
}
