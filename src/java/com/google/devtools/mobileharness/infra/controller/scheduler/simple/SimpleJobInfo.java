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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple;

import com.google.common.collect.ImmutableMap;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Job data model for {@link SimpleScheduler} only. */
class SimpleJobInfo {

  /** Basic lab information. */
  private final JobScheduleUnit jobUnit;

  /** {TestId, TestLocator} mapping of the tests in this job. */
  private final ConcurrentHashMap<String, TestLocator> tests = new ConcurrentHashMap<>();

  SimpleJobInfo(JobScheduleUnit jobUnit) {
    this.jobUnit = jobUnit;
  }

  /** Gets the job information related to scheduling. */
  public JobScheduleUnit getScheduleUnit() {
    return jobUnit;
  }

  /**
   * Adds the given test to the job.
   *
   * @return true if the test is added, or false if the test has already been added
   */
  public boolean addTest(TestLocator test) {
    String testId = test.getId();
    TestLocator finalizedTestLocator = new TestLocator(testId, test.getName(), jobUnit.locator());
    return tests.putIfAbsent(testId, finalizedTestLocator) == null;
  }

  /** Check whether the test exists in this job. */
  public boolean containsTest(String testId) {
    return tests.containsKey(testId);
  }

  /** Removes the test from the job. */
  @Nullable
  public TestLocator removeTest(String testId) {
    return tests.remove(testId);
  }

  /** Gets the {TestId, TestLocator} mapping of all the tests. */
  public Map<String, TestLocator> getTests() {
    return ImmutableMap.copyOf(tests);
  }
}
