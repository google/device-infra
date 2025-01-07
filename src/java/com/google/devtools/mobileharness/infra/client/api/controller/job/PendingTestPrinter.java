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

package com.google.devtools.mobileharness.infra.client.api.controller.job;

import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

class PendingTestPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval for checking the tests which are waiting for allocating devices. */
  private static final Duration CHECK_NEW_TESTS_INTERVAL = Duration.ofSeconds(30L);

  private final Clock clock;
  private final JobInfo jobInfo;

  private Instant nextCheckNewTestTime;

  PendingTestPrinter(Clock clock, JobInfo jobInfo) {
    this.clock = clock;
    this.jobInfo = jobInfo;
  }

  void initialize() {
    nextCheckNewTestTime = clock.instant();
  }

  void tryPrintPendingTests() {
    if (nextCheckNewTestTime.isBefore(clock.instant())) {
      int newTestCount = jobInfo.tests().getNewTestCount();
      if (newTestCount > 0) {
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("%s tests waiting for device allocation", newTestCount);
      }
      int suspendedTestCount = jobInfo.tests().getSuspendedTestCount();
      if (suspendedTestCount > 0) {
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("%s tests suspended due to quota issues.", suspendedTestCount);
      }
      nextCheckNewTestTime = nextCheckNewTestTime.plus(CHECK_NEW_TESTS_INTERVAL);
    }
  }
}
