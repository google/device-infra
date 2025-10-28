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

package com.google.devtools.mobileharness.infra.master.central.model.job;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.TestScheduleUnit;
import com.google.devtools.mobileharness.infra.master.central.proto.Test.TestCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Test.TestProfile;

/** Data access object of a test. */
@AutoValue
public abstract class TestDao {

  public static TestDao create(TestLocator locator, TestProfile profile, TestCondition condition) {
    return new AutoValue_TestDao(locator, profile, condition);
  }

  public abstract TestLocator locator();

  public abstract TestProfile profile();

  public abstract TestCondition condition();

  public TestScheduleUnit toScheduleUnit() {
    return new TestScheduleUnit(locator());
  }
}
