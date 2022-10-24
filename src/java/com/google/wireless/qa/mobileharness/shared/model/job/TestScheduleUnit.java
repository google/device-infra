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

package com.google.wireless.qa.mobileharness.shared.model.job;

import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Schedule unit of a Mobile Harness test. It contains the test information needed for the
 * scheduler.
 */
public class TestScheduleUnit implements Cloneable {
  /** Locator of this test. */
  private final TestLocator locator;

  /** Time records of the test. */
  private final Timing timing;

  /** Creates a schedule unit of a Mobile Harness test. */
  public TestScheduleUnit(TestLocator locator) {
    this(locator, new Timing());
  }

  /** Creates a schedule unit of a Mobile Harness test. */
  public TestScheduleUnit(TestLocator locator, Timing timing) {
    this.locator = locator;
    this.timing = timing;
  }

  protected TestScheduleUnit(TestScheduleUnit other) {
    this.locator = other.locator;
    this.timing = (Timing) other.timing.clone();
  }

  @Override
  public Object clone() {
    return new TestScheduleUnit(this);
  }

  /** Gets the test locator. */
  public TestLocator locator() {
    return locator;
  }

  /** Time records of the test. */
  public Timing timing() {
    return timing;
  }
}
