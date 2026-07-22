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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.FlakyTestRetryConstants;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Utility for grouping test runs. */
public final class RetryTestsGrouper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private RetryTestsGrouper() {}

  /**
   * Grouped test runs for a single shard by flaky attempt index.
   *
   * @param testsByFlakyAttemptIndex map of flaky attempt index to test run
   */
  public record ShardTestRuns(ImmutableMap<Integer, TestInfo> testsByFlakyAttemptIndex) {}

  /**
   * Grouped test runs by shard index then by flaky attempt index.
   *
   * @param testsByShardIndex map of shard index to tests grouped by flaky attempt index for that
   *     shard
   */
  public record GroupedTests(ImmutableMap<Integer, ShardTestRuns> testsByShardIndex) {}

  /**
   * Groups test runs by shard index then by flaky attempt index.
   *
   * <p>If a shard has equal to or more than one pass/fail test run, test runs excluding pass/fail
   * runs are discarded in the result. If none of the test runs for a shard is pass or fail, returns
   * the last run for that shard.
   */
  public static GroupedTests groupTestsByShardAndFlakyAttemptIndex(JobInfo jobInfo) {
    ImmutableMap.Builder<Integer, ShardTestRuns> groupedTestsByShard = ImmutableMap.builder();

    Map<Integer, ImmutableList<TestInfo>> shardIndexToTestInfoList =
        jobInfo.tests().getAll().values().stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.properties().getInt(Test.SHARD_INDEX).orElse(0),
                    TreeMap::new,
                    toImmutableList()));

    for (Integer shardIndex : shardIndexToTestInfoList.keySet()) {
      ImmutableList<TestInfo> testRunsForShard = shardIndexToTestInfoList.get(shardIndex);
      logger.atInfo().log("Found %s test runs for shard %d", testRunsForShard.size(), shardIndex);
      boolean hasPassOrFail = testRunsForShard.stream().anyMatch(RetryTestsGrouper::isPassOrFail);

      ImmutableMap.Builder<Integer, TestInfo> flakyAttemptIndexToUserVisibleTest =
          ImmutableMap.builder();

      if (hasPassOrFail) {
        Map<Integer, ImmutableList<TestInfo>> flakyAttemptIndexToTestInfoList =
            testRunsForShard.stream()
                .collect(
                    Collectors.groupingBy(
                        t ->
                            getAttemptIndexProperty(
                                t, FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX),
                        TreeMap::new,
                        toImmutableList()));

        for (Entry<Integer, ImmutableList<TestInfo>> flakyAttemptIndexToTestInfoListEntry :
            flakyAttemptIndexToTestInfoList.entrySet()) {
          int flakyAttemptIndex = flakyAttemptIndexToTestInfoListEntry.getKey();
          ImmutableList<TestInfo> testsFromSameFlakyAttempt =
              flakyAttemptIndexToTestInfoListEntry.getValue();
          findLastPassOrFailRun(testsFromSameFlakyAttempt)
              .ifPresent(
                  passOrFailTest ->
                      flakyAttemptIndexToUserVisibleTest.put(flakyAttemptIndex, passOrFailTest));
        }
      } else {
        findLastRun(testRunsForShard)
            .ifPresent(
                lastRun ->
                    flakyAttemptIndexToUserVisibleTest.put(
                        getAttemptIndexProperty(
                            lastRun, FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX),
                        lastRun));
      }
      groupedTestsByShard.put(
          shardIndex, new ShardTestRuns(flakyAttemptIndexToUserVisibleTest.buildOrThrow()));
    }

    return new GroupedTests(groupedTestsByShard.buildOrThrow());
  }

  private static Optional<TestInfo> findLastPassOrFailRun(List<TestInfo> attempts) {
    return attempts.stream()
        .filter(RetryTestsGrouper::isPassOrFail)
        .max(
            Comparator.comparingInt(
                t ->
                    getAttemptIndexProperty(
                        t, FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX)));
  }

  private static Optional<TestInfo> findLastRun(List<TestInfo> attempts) {
    return attempts.stream()
        .max(
            Comparator.comparingInt(
                    (TestInfo t) ->
                        getAttemptIndexProperty(
                            t, FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX))
                .thenComparingInt(
                    t ->
                        getAttemptIndexProperty(
                            t, FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX)));
  }

  private static boolean isPassOrFail(TestInfo testInfo) {
    if (testInfo.resultWithCause() == null || testInfo.resultWithCause().get() == null) {
      return false;
    }
    TestResult type = testInfo.resultWithCause().get().type();
    return type == TestResult.PASS || type == TestResult.FAIL;
  }

  private static int getAttemptIndexProperty(TestInfo testInfo, String propertyName) {
    String value = testInfo.properties().get(propertyName);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
