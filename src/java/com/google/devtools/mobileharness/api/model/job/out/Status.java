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

package com.google.devtools.mobileharness.api.model.job.out;

import com.google.devtools.mobileharness.api.model.proto.Test.TestStatus;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Status of a job/test. */
public class Status {
  // TODO: Rename TestStatus proto to Status.
  /** Status of this job/test. */
  private volatile TestStatus status = TestStatus.NEW;

  /** The logger of this job/test. */
  @Nullable private final Logger logger;

  /** The time records of the job/test. */
  private final TouchableTiming timing;

  /** Creates the status of a job/test. */
  public Status(@Nullable Logger logger, TouchableTiming timing) {
    this.logger = logger;
    this.timing = timing;
  }

  /** Updates the status with the given value. */
  @CanIgnoreReturnValue
  public synchronized Status set(TestStatus status) {
    if (this.status == status) {
      return this;
    }
    if (status == TestStatus.NEW) {
      logger.warning("Prevent overwriting the status back to NEW");
      return this;
    }

    if (logger != null) {
      logger.info("Status " + this.status + " -> " + status);
    }
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
