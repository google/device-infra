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

package com.google.wireless.qa.mobileharness.shared.model.job.util;

import com.google.common.collect.ImmutableMap;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.Comparator;

/**
 * Comparator for ordering TestResult objects. Order by the PASS, FAIL, TIMEOUT, ABOURT, UNKNOWN,
 * other, ERROR, INFRA_ERROR and SKIP.
 */
public class ResultComparator implements Comparator<TestResult> {
  private static final ImmutableMap<TestResult, Integer> PARSE_MAP_TEST_RESULT_TO_PRIORITY_LEVEL =
      ImmutableMap.<TestResult, Integer>builder()
          .put(TestResult.PASS, 5)
          .put(TestResult.FAIL, 4)
          .put(TestResult.TIMEOUT, 3)
          .put(TestResult.ABORT, 2)
          .put(TestResult.UNKNOWN, 1)
          .put(TestResult.ERROR, -1)
          .put(TestResult.INFRA_ERROR, -2)
          .put(TestResult.SKIP, -3)
          .buildOrThrow();

  @Override
  public int compare(TestResult left, TestResult right) {
    // Compares the testinfos by testresults. The priority level of testresults can be mapping by
    // the parse map before, zero by default.
    int leftPriorityLevel = 0;
    int rightPriorityLevel = 0;
    if (PARSE_MAP_TEST_RESULT_TO_PRIORITY_LEVEL.containsKey(left)) {
      leftPriorityLevel = PARSE_MAP_TEST_RESULT_TO_PRIORITY_LEVEL.get(left);
    }
    if (PARSE_MAP_TEST_RESULT_TO_PRIORITY_LEVEL.containsKey(right)) {
      rightPriorityLevel = PARSE_MAP_TEST_RESULT_TO_PRIORITY_LEVEL.get(right);
    }
    return Integer.compare(rightPriorityLevel, leftPriorityLevel);
  }
}
