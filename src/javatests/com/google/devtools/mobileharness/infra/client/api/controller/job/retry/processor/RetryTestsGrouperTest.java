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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.RetryTestsGrouper.GroupedTests;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor.RetryTestsGrouper.ShardTestRuns;
import com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy.FlakyTestRetryConstants;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit test for {@link RetryTestsGrouper}. */
@RunWith(JUnit4.class)
public final class RetryTestsGrouperTest {

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_emptyList_returnsEmptyMap() {
    assertThat(
            RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(createMockJobInfo())
                .testsByShardIndex())
        .isEmpty();
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_singlePassTest_groupsCorrectly() {
    TestInfo test =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(createMockJobInfo(test));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, test)));
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_unorderedInput_returnsSortedByShardIndex() {
    TestInfo shard0 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);
    TestInfo shard1 =
        createMockTestRun(
            /* shardIndex= */ "1",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(createMockJobInfo(shard1, shard0));

    assertThat(result.testsByShardIndex().keySet()).containsExactly(0, 1).inOrder();
  }

  @Test
  public void
      groupTestsByShardAndFlakyAttemptIndex_unorderedFlakyAttemptIndex_returnsSortedByFlakyAttemptIndex() {
    TestInfo flakyAttempt0 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.FAIL);
    TestInfo flakyAttempt1 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(
            createMockJobInfo(flakyAttempt1, flakyAttempt0));

    assertThat(result.testsByShardIndex().get(0).testsByFlakyAttemptIndex().keySet())
        .containsExactly(0, 1)
        .inOrder();
  }

  @Test
  public void
      groupTestsByShardAndFlakyAttemptIndex_multipleShardsAndFlakyAttempts_groupsCorrectly() {
    // Shard 0 flaky attempt 0 (fail) and flaky attempt 1 (pass)
    TestInfo shard0FlakyAttempt0 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.FAIL);
    TestInfo shard0FlakyAttempt1 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 1,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    // Shard 1 attempt 0 (pass)
    TestInfo shard1FlakyAttempt0 =
        createMockTestRun(
            /* shardIndex= */ "1",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(
            createMockJobInfo(shard0FlakyAttempt1, shard0FlakyAttempt0, shard1FlakyAttempt0));

    assertThat(result.testsByShardIndex())
        .containsExactly(
            0, new ShardTestRuns(ImmutableMap.of(0, shard0FlakyAttempt0, 1, shard0FlakyAttempt1)),
            1, new ShardTestRuns(ImmutableMap.of(0, shard1FlakyAttempt0)))
        .inOrder();
  }

  @Test
  public void
      groupTestsByShardAndFlakyAttemptIndex_mixOfErrorAndPassOrFailRuns_picksLastPassOrFailRun() {
    TestInfo attempt0 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.ERROR);
    TestInfo attempt1 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 1,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(
            createMockJobInfo(attempt0, attempt1));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, attempt1)));
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_noPassOrFailRuns_returnsLastRun() {
    TestInfo attempt0 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.ERROR);
    TestInfo attempt1 =
        createMockTestRun(
            /* shardIndex= */ "0",
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 1,
            TestResult.TIMEOUT);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(
            createMockJobInfo(attempt0, attempt1));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, attempt1)));
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_noShardIndexProperty_defaultsToShardZero() {
    TestInfo testWithoutShardIndex =
        createMockTestRun(
            /* shardIndex= */ null,
            /* flakyAttemptIndex= */ 0,
            /* errorAttemptIndex= */ 0,
            TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(
            createMockJobInfo(testWithoutShardIndex));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, testWithoutShardIndex)));
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_nonNumericAttemptIndex_defaultsToZero() {
    TestInfo testInfo = mock(TestInfo.class, Mockito.RETURNS_DEEP_STUBS);
    Properties properties = new Properties(new Timing());
    properties.add(PropertyName.Test.SHARD_INDEX, "0");
    properties.add(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX, "invalid_num");
    properties.add(FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, "not_a_number");
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.resultWithCause().get().type()).thenReturn(TestResult.PASS);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(createMockJobInfo(testInfo));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, testInfo)));
  }

  @Test
  public void groupTestsByShardAndFlakyAttemptIndex_nullResultWithCause_treatedAsNotPassOrFail() {
    TestInfo testInfo = mock(TestInfo.class, Mockito.RETURNS_DEEP_STUBS);
    Properties properties = new Properties(new Timing());
    properties.add(PropertyName.Test.SHARD_INDEX, "0");
    properties.add(FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX, "0");
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.resultWithCause()).thenReturn(null);

    GroupedTests result =
        RetryTestsGrouper.groupTestsByShardAndFlakyAttemptIndex(createMockJobInfo(testInfo));

    assertThat(result.testsByShardIndex())
        .containsExactly(0, new ShardTestRuns(ImmutableMap.of(0, testInfo)));
  }

  private static JobInfo createMockJobInfo(TestInfo... tests) {
    JobInfo jobInfo = mock(JobInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(jobInfo.tests().getAll().values()).thenReturn(ImmutableList.copyOf(tests));
    return jobInfo;
  }

  private static TestInfo createMockTestRun(
      String shardIndex, int flakyAttemptIndex, int errorAttemptIndex, TestResult result) {
    TestInfo testInfo = mock(TestInfo.class, Mockito.RETURNS_DEEP_STUBS);

    Properties properties = new Properties(new Timing());
    if (shardIndex != null) {
      properties.add(PropertyName.Test.SHARD_INDEX, shardIndex);
    }
    properties.add(
        FlakyTestRetryConstants.TEST_PROP_FLAKY_ATTEMPT_INDEX, String.valueOf(flakyAttemptIndex));
    properties.add(
        FlakyTestRetryConstants.TEST_PROP_ERROR_ATTEMPT_INDEX, String.valueOf(errorAttemptIndex));
    when(testInfo.properties()).thenReturn(properties);

    when(testInfo.resultWithCause().get().type()).thenReturn(result);

    return testInfo;
  }
}
