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

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Clock;
import java.time.Instant;

/** Timing that supports setting and getting last modified time of job/test. */
public class TouchableTiming extends Timing implements Cloneable {

  /** The time when the job/test is last modified. */
  private volatile Instant modifyTime;

  /** Creates the time records of the job/test. */
  public TouchableTiming() {
    this(Clock.systemUTC());
  }

  /** Creates the time records of the job/test, with the given job/test create time. */
  public TouchableTiming(Instant createTime) {
    this(Clock.systemUTC(), createTime);
  }

  /** Creates the time records of the job/test. */
  public TouchableTiming(Clock clock) {
    this(clock, clock.instant());
  }

  /** Creates the time records of the job/test. */
  public TouchableTiming(Clock clock, Instant createTime) {
    super(clock, createTime);
    this.modifyTime = getCreateTime();
  }

  private TouchableTiming(TouchableTiming other) {
    super(other);
    this.modifyTime = other.modifyTime;
  }

  /** Returns the time of the last modification. */
  public Instant getModifyTime() {
    return modifyTime;
  }

  /**
   * If start time is null, records current time as start time.
   *
   * @return whether the start time is updated from null
   */
  @Override
  public boolean start() {
    boolean updated = super.start();
    if (updated) {
      getStartTime().ifPresent(startTime -> modifyTime = startTime);
    }
    return updated;
  }

  /**
   * If end time is null, records current time as end time.
   *
   * @return whether the end time is updated from null
   */
  @Override
  public boolean end() {
    boolean updated = super.end();
    if (updated) {
      getEndTime().ifPresent(endTime -> modifyTime = endTime);
    }
    return updated;
  }

  /**
   * Reset the start time to null.
   *
   * @return whether the start time is cleared
   */
  @Override
  public boolean reset() {
    boolean cleared = super.reset();
    if (cleared) {
      modifyTime = getClock().instant();
    }
    return cleared;
  }

  /**
   * Updates the modify time to now.
   *
   * @return the modified time after the update, which is the current time
   */
  public Instant touch() {
    modifyTime = getClock().instant();
    return modifyTime;
  }

  /** Sets the last modification time. */
  public void setModifyTime(Instant modifyTime) {
    this.modifyTime = checkNotNull(modifyTime);
  }

  @Override
  public TouchableTiming clone() {
    return new TouchableTiming(this);
  }
}
