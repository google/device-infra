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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.common.annotations.Beta;

/**
 * Schedule unit of a Mobile Harness lab. It contains the lab information needed for the scheduler.
 */
@Beta
public class LabScheduleUnit implements Cloneable {
  /** Lab location. */
  private final LabLocator locator;

  public LabScheduleUnit(LabLocator locator) {
    this.locator = locator;
  }

  protected LabScheduleUnit(LabScheduleUnit other) {
    this(other.locator);
  }

  @Override
  public Object clone() {
    return new LabScheduleUnit(this);
  }

  /** Returns the location information of this lab. */
  public LabLocator locator() {
    return locator;
  }

  @Override
  public String toString() {
    return locator.toString();
  }
}
