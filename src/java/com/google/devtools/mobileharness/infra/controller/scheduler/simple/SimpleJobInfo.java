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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
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
   * @throws MobileHarnessException if there is already a test with the same id in this job
   */
  @CanIgnoreReturnValue
  public SimpleJobInfo addTest(TestLocator test) throws MobileHarnessException {
    String testId = test.getId();
    if (tests.putIfAbsent(testId, test) != null) {
      throw new MobileHarnessException(
          ErrorCode.TEST_DUPLICATED,
          "Test " + testId + " already exists in job " + jobUnit.locator());
    }
    return this;
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
