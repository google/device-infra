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

package com.google.devtools.mobileharness.shared.util.comparator;

import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import java.util.Comparator;

/**
 * Creates a comparator to be used to sort test results. The priority order: INFRA_ERROR > ERROR >
 * TIMEOUT > ALLOC_ERROR > ALLOC_FAIL > ABORT > FAIL > PASS > SKIP
 */
public class TestResultComparator implements Comparator<TestResult> {
  @Override
  public int compare(TestResult left, TestResult right) {
    return getPriority(left) - getPriority(right);
  }

  private int getPriority(TestResult testResult) {
    int priority;
    switch (testResult) {
      case INFRA_ERROR:
        priority = 0;
        break;
      case ERROR:
        priority = 1;
        break;
      case TIMEOUT:
        priority = 2;
        break;
      case ALLOC_ERROR:
        priority = 3;
        break;
      case ALLOC_FAIL:
        priority = 4;
        break;
      case ABORT:
        priority = 5;
        break;
      case FAIL:
        priority = 6;
        break;
      case PASS:
        priority = 7;
        break;
      case SKIP:
        priority = 8;
        break;
      default:
        priority = 9;
        break;
    }
    return priority;
  }
}
