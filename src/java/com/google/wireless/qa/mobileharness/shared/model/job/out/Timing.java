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

import com.google.common.annotations.Beta;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import java.time.Clock;
import java.time.Instant;
import javax.annotation.Nullable;

/** The time records of the job/test. */
public class Timing implements Cloneable {

  private final TouchableTiming newTiming;

  /** Creates the time records of the job/test. */
  public Timing() {
    this.newTiming = new TouchableTiming();
  }

  /** Creates the time records of the job/test, with the given job/test create time. */
  public Timing(Instant createTime) {
    this.newTiming = new TouchableTiming(createTime);
  }

  /** Creates the time records of the job/test. */
  public Timing(Clock clock) {
    this.newTiming = new TouchableTiming(clock);
  }

  /** Creates the time records of the job/test. */
  public Timing(Clock clock, Instant createTime) {
    this.newTiming = new TouchableTiming(clock, createTime);
  }

  /**
   * Creates the time records of the job/test by the given {@link TouchableTiming}. Note: please
   * don't make this public at any time.
   */
  Timing(TouchableTiming newTiming) {
    this.newTiming = newTiming;
  }

  private Timing(Timing other) {
    this.newTiming = other.newTiming.clone();
  }

  @Override
  public Object clone() {
    return new Timing(this);
  }

  /**
   * @return the new data model which has the same backend of this object.
   */
  @Beta
  public TouchableTiming toNewTiming() {
    return newTiming;
  }

  /** Returns the time when the instance is created. */
  public Instant getCreateTime() {
    return newTiming.getCreateTime();
  }

  /** Returns the start time. Or null if it is not started. */
  @Nullable
  public Instant getStartTime() {
    return newTiming.getStartTime().orElse(null);
  }

  /**
   * @return the start time
   */
  public Instant getStartTimeNonNull() {
    return newTiming.getStartTime().orElseThrow(() -> new IllegalStateException("Not started"));
  }

  /** Returns the time of the last modification. */
  public Instant getModifyTime() {
    return newTiming.getModifyTime();
  }

  /** Returns the end time. Or null if it is not ended. */
  @Nullable
  public Instant getEndTime() {
    return newTiming.getEndTime().orElse(null);
  }

  /**
   * If start time is null, records current time as start time.
   *
   * @return whether the start time is updated from null
   */
  public boolean start() {
    return newTiming.start();
  }

  /**
   * If start time is null, records the given time as start time.
   *
   * @return whether the start time is updated from null
   */
  public boolean start(Instant startTime) {
    return newTiming.start(startTime);
  }

  /**
   * If end time is null, records current time as end time.
   *
   * @return whether the end time is updated from null
   */
  public boolean end() {
    return newTiming.end();
  }

  /**
   * If end time is null, records the given time as end time.
   *
   * @return whether the end time is updated from null
   */
  public boolean end(Instant endTime) {
    return newTiming.end(endTime);
  }

  /**
   * Reset the start time to null.
   *
   * @return whether the start time is cleared
   */
  public boolean reset() {
    return newTiming.reset();
  }

  /**
   * Updates the modify time to now.
   *
   * @return the modified time after the update, which is the current time
   */
  public Instant touch() {
    return newTiming.touch();
  }

  /** Sets the last modification time. */
  public void setModifyTime(Instant modifyTime) {
    newTiming.setModifyTime(modifyTime);
  }

  /** Returns the clock used by this instance. */
  public Clock getClock() {
    return newTiming.getClock();
  }
}
