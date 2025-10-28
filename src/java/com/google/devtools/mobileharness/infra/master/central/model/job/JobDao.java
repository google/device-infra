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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobProfile;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Data access object of a job. */
@AutoValue
public abstract class JobDao {
  /** {TestId, TestDao} mapping of the tests in this job. */
  private final Map<String, TestDao> testsById = new HashMap<>();

  private final ListMultimap<String, TestDao> testsByName = LinkedListMultimap.create();

  public static JobDao create(
      JobLocator locator, JobProfile jobProfile, JobCondition jobCondition) {
    return new AutoValue_JobDao(locator, jobProfile, jobCondition);
  }

  public abstract JobLocator locator();

  public abstract JobProfile profile();

  public abstract JobCondition condition();

  /**
   * Adds the test to the job only if the test does not exist in job.
   *
   * @return the previous {@link TestDao} associated with test ID, or empty if there was no test
   *     with the given ID previously.
   */
  @CanIgnoreReturnValue
  public Optional<TestDao> addTest(TestDao test) {
    testsByName.put(test.locator().name(), test);
    return Optional.ofNullable(testsById.put(test.locator().id(), test));
  }

  /**
   * Removes the test from job.
   *
   * @return the previous {@link TestDao} associated with test ID, or empty if there was no test
   *     with the given ID.
   */
  public Optional<TestDao> removeTest(String testId) {
    TestDao testDao = testsById.get(testId);
    if (testDao != null) {
      testsByName.remove(testDao.locator().name(), testDao);
    }
    return Optional.ofNullable(testsById.remove(testId));
  }

  /** Returns all the tests in this job grouped by name. */
  public ListMultimap<String, TestDao> getTestsByName() {
    return testsByName;
  }

  /** Returns all the tests in this job. */
  public ImmutableSet<TestDao> getTests() {
    return ImmutableSet.copyOf(testsById.values());
  }

  /**
   * Gets the {@link TestDao} associated with test ID, or emtpy if there was no test with the given
   * ID.
   */
  public Optional<TestDao> getTest(String testId) {
    return Optional.ofNullable(testsById.get(testId));
  }
}
