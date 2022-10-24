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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;

/** Status of a job/test. */
public class Status {
  // TODO: Rename TestStatus proto to Status.
  /** Status of this job/test. */
  private volatile TestStatus status = TestStatus.NEW;

  /** The logger of this job/test. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The time records of the job/test. */
  private final Timing timing;

  /** Creates the status of a job/test. */
  public Status(Timing timing) {
    this.timing = timing;
  }

  /**
   * Creates the status of a job/test by the given {@link TestStatus}. Note: please don't make this
   * public at any time.
   */
  Status(Timing timing, TestStatus testStatus) {
    this.timing = timing;
    this.status = testStatus;
  }

  /** Updates the status with the given value. */
  @CanIgnoreReturnValue
  public synchronized Status set(TestStatus status) {
    if (this.status == status) {
      return this;
    }
    if (status == TestStatus.NEW && this.status != TestStatus.SUSPENDED) {
      logger.atWarning().log(
          "Prevent overwriting the status back to NEW if the old status is not SUSPENDED");
      return this;
    }

    logger.atInfo().log("Status %s -> %s", this.status, status);
    // If the job/test just gets started, records the start time.
    if (this.status == TestStatus.NEW && timing.start()) {
      // The timing has been updated. Skips.
    } else {
      timing.touch();
    }
    this.status = status;
    return this;
  }

  /** Gets the current status. */
  public TestStatus get() {
    return status;
  }
}
