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

import static java.util.Collections.min;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.model.job.util.ResultComparator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** A set of tests that belongs to the same job. */
public class TestInfos {

  /** The job that these tests belongs to. */
  private final JobInfo jobInfo;

  /** The parent test. Null when these are top-level tests. */
  @Nullable public final TestInfo parentTest;

  /** All the tests in this job group by test name. */
  private final ListMultimap<String, TestInfo> testsByName = LinkedListMultimap.create();

  /** All the tests in this job group indexed by test id. */
  private final Map<String, TestInfo> testsById = new HashMap<>();

  /**
   * Creates a test set. Only visible for this package. API users shouldn't directly create an
   * instance of this.
   */
  TestInfos(JobInfo jobInfo) {
    this(jobInfo, null /*parentTest*/);
  }

  /**
   * Creates sub test set. Only visible for this package. API users shouldn't directly create an
   * instance of this.
   */
  TestInfos(JobInfo jobInfo, @Nullable TestInfo parentTest) {
    this.jobInfo = jobInfo;
    this.parentTest = parentTest;
  }

  /**
   * Creates a new {@code TestInfo} with the given ID and name to the job.
   *
   * @throws MobileHarnessException if adds a test with exist id in this job
   */
  public synchronized TestInfo add(String testId, String testName) throws MobileHarnessException {
    return add(testId, testName, null);
  }

  /**
   * Creates a new {@code TestInfo} with the given ID, name, and timing to the job.
   *
   * @throws MobileHarnessException if adds a test with exist id in this job
   */
  public synchronized TestInfo add(String testId, String testName, @Nullable Timing testTiming)
      throws MobileHarnessException {
    TestInfo testInfo =
        TestInfo.newBuilder()
            .setId(testId)
            .setName(testName)
            .setJobInfo(jobInfo)
            .setParentTest(parentTest)
            .setTiming(testTiming)
            .build();
    add(testInfo);
    return testInfo;
  }

  /**
   * Creates a new {@code TestInfo} with the given ID, name, timing to the job and fileUtil.
   *
   * @throws MobileHarnessException if adds a test with exist id in this job
   */
  public synchronized TestInfo add(
      String testId,
      String testName,
      @Nullable Timing testTiming,
      @Nullable LocalFileUtil localFileUtil)
      throws MobileHarnessException {
    TestInfo testInfo =
        TestInfo.newBuilder()
            .setId(testId)
            .setName(testName)
            .setJobInfo(jobInfo)
            .setParentTest(parentTest)
            .setTiming(testTiming)
            .setFileUtil(localFileUtil)
            .build();
    add(testInfo);
    return testInfo;
  }

  /**
   * Creates a new {@code TestInfo} with the given name to the job.
   *
   * @throws MobileHarnessException if adds a test with exist id in this job
   */
  public synchronized TestInfo add(String testName) throws MobileHarnessException {
    TestInfo testInfo =
        TestInfo.newBuilder()
            .setName(testName)
            .setJobInfo(jobInfo)
            .setParentTest(parentTest)
            .build();
    add(testInfo);
    return testInfo;
  }

  /**
   * Adds the given {@code TestInfo} to the job.
   *
   * @throws MobileHarnessException if there is already a test with the same id in this job
   */
  // TODO: Change this method to private after deleting JobInfo.addTest(TestInfo).
  @CanIgnoreReturnValue
  synchronized TestInfos add(TestInfo testInfo) throws MobileHarnessException {
    TestLocator testLocator = testInfo.locator();
    String testId = testLocator.getId();
    if (testsById.containsKey(testId)) {
      throw new MobileHarnessException(
          BasicErrorId.TEST_ADD_TEST_WITH_DUPLICATED_ID, "Test " + testId + " already exists");
    }

    testsById.put(testId, testInfo);
    testsByName.put(testLocator.getName(), testInfo);
    jobInfo.timing().touch();
    return this;
  }

  /**
   * Creates new {@code TestInfo}s with the given names to the job.
   *
   * @throws MobileHarnessException if there is already a test with one of the given name in this
   *     job and the test is not finished
   */
  @CanIgnoreReturnValue
  public TestInfos addAll(Collection<String> testNames) throws MobileHarnessException {
    for (String testName : testNames) {
      add(testName);
    }
    return this;
  }

  /**
   * Adds a list of tests quietly, it helps recover a JobInfo/TestInfo. Note: please don't make this
   * public at any time.
   */
  @CanIgnoreReturnValue
  synchronized TestInfos addAll(List<TestInfo> testInfos) {
    testInfos.forEach(
        testInfo -> {
          TestLocator testLocator = testInfo.locator();
          String testId = testLocator.getId();
          testsById.put(testId, testInfo);
          testsByName.put(testLocator.getName(), testInfo);
        });
    return this;
  }

  /**
   * Removes a test which is not {@link TestStatus#RUNNING} from job. No effect if the test doesn't
   * exist.
   *
   * @param testId id of the test
   * @return the test removed, or null if the test doesn't exist.
   * @throws MobileHarnessException if the test exists and is still {@link TestStatus#RUNNING}
   */
  @Nullable
  public synchronized TestInfo remove(String testId) throws MobileHarnessException {
    TestInfo testInfo = testsById.get(testId);
    if (testInfo != null) {
      TestStatus status = testInfo.status().get();
      if (status == TestStatus.RUNNING) {
        throw new MobileHarnessException(
            BasicErrorId.TEST_REMOVE_RUNNING_TEST_ERROR, "Can not remove a running test " + testId);
      }
      testsByName.remove(testInfo.locator().getName(), testInfo);
      testsById.remove(testId);
      jobInfo.timing().touch();
    }
    return testInfo;
  }

  /** Remove all the {@link TestInfo} of the current job. */
  public synchronized void clear() {
    testsByName.clear();
    testsById.clear();
    jobInfo.timing().touch();
  }

  /** Returns a list of {@link TestInfo} with the given test name. */
  public synchronized List<TestInfo> getByName(String testName) {
    return testsByName.get(testName);
  }

  /** Returns the {@link TestInfo} object with the given test id, or null if test not exists. */
  @Nullable
  public synchronized TestInfo getById(String testId) {
    return testsById.get(testId);
  }

  /**
   * Returns the {@link TestInfo} object with the given test id, or throws {@link
   * NullPointerException} if test not exists.
   */
  public synchronized TestInfo getByIdNonNull(String testId) {
    return Preconditions.checkNotNull(getById(testId));
  }

  /** Whether the job contains any tests. */
  public synchronized boolean isEmpty() {
    return testsById.isEmpty();
  }

  /** Gets the number of the tests. */
  public synchronized int size() {
    return testsById.size();
  }

  /** Gets the number of the tests which are new, not assigned and not suspended. */
  public synchronized int getNewTestCount() {
    int count = 0;
    for (TestInfo testInfo : testsById.values()) {
      if (testInfo.status().get() == TestStatus.NEW) {
        count++;
      }
    }
    return count;
  }

  /** Gets the number of the suspended tests, which are not assigned due to quota issues. */
  public synchronized int getSuspendedTestCount() {
    int count = 0;
    for (TestInfo testInfo : testsById.values()) {
      if (testInfo.status().get() == TestStatus.SUSPENDED) {
        count++;
      }
    }
    return count;
  }

  /** Returns whether all the tests in this job are finished. */
  public synchronized boolean allDone() {
    for (TestInfo testInfo : testsById.values()) {
      if (testInfo.status().get() != TestStatus.DONE) {
        return false;
      }
    }
    return true;
  }

  /** Returns all the {@link TestInfo} of the current job. */
  public synchronized ListMultimap<String, TestInfo> getAll() {
    return LinkedListMultimap.create(testsByName);
  }

  /**
   * Returns the only {@linkplain TestInfo test} of the current job.
   *
   * @throws java.util.NoSuchElementException if the job has no test
   * @throws IllegalStateException if the job has more than one tests
   */
  public synchronized TestInfo getOnly() {
    return Iterables.getOnlyElement(testsById.values());
  }

  /**
   * Gets the finalize test results depends on retry level.
   *
   * <ul>
   *   <li>If the retry level is ERROR(default)/FAIL, only keeps the best result for each test and
   *       ignores other attempts. So each test only has one test result. When there are different
   *       attempts with the same result, use the latter one.
   *   <li>If the repeat_runs is set or retry level is ALL. All attempts of each test are keeped.
   * </ul>
   */
  public synchronized ListMultimap<String, TestInfo> getFinalized() {
    Retry retry = jobInfo.setting().getRetry();

    if (retry.getRetryLevel().equals(Retry.Level.ALL)) {
      return getAll();
    }
    ListMultimap<String, TestInfo> finalizedTests = LinkedListMultimap.create();
    for (String testName : testsByName.keySet()) {
      List<TestInfo> tests = testsByName.get(testName);
      // if reapeat_runs is specified, each repeat needs to be shown.
      ListMultimap<Integer, TestInfo> testsByRepeatIndex = getTestsByRepeatIndex(tests);
      for (Integer repeatIndex : testsByRepeatIndex.keySet()) {
        finalizedTests.put(testName, getBestResult(testsByRepeatIndex.get(repeatIndex)));
      }
    }
    return finalizedTests;
  }

  private ListMultimap<Integer, TestInfo> getTestsByRepeatIndex(List<TestInfo> tests) {
    ListMultimap<Integer, TestInfo> testsByRepeatIndex = LinkedListMultimap.create();
    for (TestInfo test : tests) {
      if (!test.properties().has(Ascii.toLowerCase(Test.REPEAT_INDEX.name()))) {
        // should not have this case, but need to handle it if somewhere forgets to set the property
        testsByRepeatIndex.put(Integer.valueOf(1), test);
      } else {
        try {
          testsByRepeatIndex.put(
              Integer.valueOf(test.properties().get(Ascii.toLowerCase(Test.REPEAT_INDEX.name()))),
              test);
        } catch (NumberFormatException e) {
          // Do nothing.
        }
      }
    }
    return testsByRepeatIndex;
  }

  /*
   * Returns the best result from all the attempts.
   */
  private TestInfo getBestResult(List<TestInfo> attempts) {
    return min(
        attempts,
        new Comparator<TestInfo>() {
          private final ResultComparator resultComparator = new ResultComparator();

          @Override
          public int compare(TestInfo left, TestInfo right) {
            TestResult leftResult = left.result().get();
            TestResult rightResult = right.result().get();
            int resultComparism = resultComparator.compare(leftResult, rightResult);
            if (resultComparism != 0) {
              return resultComparism;
            } else {
              Instant leftStartTime =
                  left.timing().getStartTime() == null
                      ? Instant.EPOCH
                      : left.timing().getStartTime();
              Instant rightStartTime =
                  right.timing().getStartTime() == null
                      ? Instant.EPOCH
                      : right.timing().getStartTime();
              return rightStartTime.compareTo(leftStartTime); // Returns the late one.
            }
          }
        });
  }
}
