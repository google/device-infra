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

package com.google.devtools.mobileharness.shared.util.junit.xmlwriter.model;

import java.util.List;
import javax.annotation.Nullable;

/** A node in a test suite. */
public abstract class TestNode {
  @Nullable private TestResult result = null;

  /** Returns this node's children (test suites or tests cases). */
  // VisibleForTesting
  public abstract List<TestNode> getChildren();

  /**
   * Returns true if this node is a test case (e.g. junit4 test), false otherwise (e.g. junit4 test
   * suite).
   */
  public abstract boolean isTestCase();

  /**
   * Template-method that creates a {@link TestResult} object that represents the test outcome of
   * this node.
   */
  public abstract TestResult buildResult();

  final TestResult getResult() {
    if (result == null) {
      result = buildResult();
    }
    return result;
  }
}
