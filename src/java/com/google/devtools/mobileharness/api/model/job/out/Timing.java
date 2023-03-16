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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** The time records of the job/test. */
public class Timing {

  /** The time when this job/test is created. */
  private final Instant createTime;

  /** The time when this job/test is started. Null if not started. */
  private final AtomicReference<Instant> startTime = new AtomicReference<>();

  /** The time when this job/test is ended. Null if not ended. */
  private final AtomicReference<Instant> endTime = new AtomicReference<>();

  /** Clock for getting the system time. */
  private final Clock clock;

  /** Creates the time records of the job/test. */
  public Timing() {
    this(Clock.systemUTC());
  }

  /** Creates the time records of the job/test, with the given job/test create time. */
  public Timing(Instant createTime) {
    this(Clock.systemUTC(), createTime);
  }

  /** Creates the time records of the job/test. */
  public Timing(Clock clock) {
    this(clock, clock.instant());
  }

  /** Creates the time records of the job/test. */
  @SuppressWarnings("GoodTime")
  public Timing(Clock clock, Instant createTime) {
    this.clock = clock;
    this.createTime = createTime;
  }

  protected Timing(Timing other) {
    this.createTime = other.createTime;
    this.startTime.set(other.startTime.get());
    this.endTime.set(other.endTime.get());
    this.clock = other.clock;
  }

  /**
   * Creates the time records by the given create and start time. Note: please don't make this
   * public at any time.
   */
  Timing(Clock clock, Instant createTime, Instant startTime) {
    this.clock = clock;
    this.createTime = createTime;
    this.startTime.set(startTime);
  }

  /** Returns the time when the instance is created. */
  public Instant getCreateTime() {
    return createTime;
  }

  /** Returns the start time. Or empty if it is not started. */
  public Optional<Instant> getStartTime() {
    return Optional.ofNullable(startTime.get());
  }

  /**
   * If start time is null, records current time as start time.
   *
   * @return whether the start time is updated from null
   */
  public boolean start() {
    return startTime.compareAndSet(/* expectedValue= */ null, clock.instant());
  }

  /**
   * If start time is null, uses the given time as start time.
   *
   * @return whether the start time is updated from null
   */
  public boolean start(Instant startTime) {
    return this.startTime.compareAndSet(/* expectedValue= */ null, startTime);
  }

  /**
   * Records current time as end time.
   *
   * @return whether the end time is updated
   */
  public boolean end() {
    endTime.set(clock.instant());
    return true;
  }

  /**
   * If end time is null, uses the given time as end time.
   *
   * @return whether the end time is updated
   */
  public boolean end(Instant endTime) {
    return this.endTime.compareAndSet(/* expectedValue= */ null, endTime);
  }

  /**
   * Returns the end time, or empty if it is not ended. The end time should be set when the client
   * test runner stops running.
   */
  public Optional<Instant> getEndTime() {
    return Optional.ofNullable(endTime.get());
  }

  /**
   * Reset the start time to null.
   *
   * @return whether the start time is cleared
   */
  public boolean reset() {
    return startTime.getAndSet(/* newValue= */ null) != null;
  }

  /** Returns the clock used by this instance. */
  @SuppressWarnings("GoodTime") // TODO: fix GoodTime violation
  public Clock getClock() {
    return clock;
  }
}
