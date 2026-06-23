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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/** A parent node in the test suite model. */
public class TestSuiteNode extends TestNode {
  private final List<TestNode> children = new ArrayList<>();
  private final String name;
  private long runTime = -1;
  private Optional<LocalDateTime> timestamp = Optional.empty();

  public TestSuiteNode(String name) {
    this.name = name;
  }

  // VisibleForTesting
  @Override
  public List<TestNode> getChildren() {
    return Collections.unmodifiableList(children);
  }

  @Override
  public boolean isTestCase() {
    return false;
  }

  public void addTestSuite(TestSuiteNode suite) {
    children.add(suite);
  }

  public void addTestCase(TestCaseNode testCase) {
    children.add(testCase);
  }

  private int getNumTests() {
    int numTests = 0;

    for (TestNode node : children) {
      if (node.isTestCase()) {
        numTests++;
      } else if (node instanceof TestSuiteNode) {
        numTests += ((TestSuiteNode) node).getNumTests();
      }
    }

    return numTests;
  }

  /** Set the run time for the entire test suite, in milliseconds. */
  public void setRunTime(long millis) {
    this.runTime = millis;
  }

  /** Set the timestamp of the start of the entire test suite. */
  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = Optional.of(timestamp);
  }

  @Override
  public TestResult buildResult() {
    long childRunTime = 0;
    int numFailures = 0;
    int numSkips = 0;
    LinkedList<TestResult> childResults = new LinkedList<>();

    for (TestNode child : children) {
      TestResult childResult = child.getResult();
      childResults.add(childResult);
      numFailures += childResult.getNumFailures();
      numSkips += childResult.getNumSkips();
      childRunTime += childResult.getRunTime();
    }

    if (runTime == -1) {
      runTime = childRunTime;
    }

    return new TestResult.Builder()
        .name(name)
        .className("")
        .properties(Collections.<String, String>emptyMap())
        .failures(Collections.emptyList())
        .skippedMessages(Collections.emptyList())
        .runTime(runTime)
        .timestamp(timestamp)
        .numTests(getNumTests())
        .numFailures(numFailures)
        .numSkips(numSkips)
        .childResults(childResults)
        .build();
  }
}
