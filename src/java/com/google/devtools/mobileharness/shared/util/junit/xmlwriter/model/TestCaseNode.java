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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** A leaf in the test suite model. */
public class TestCaseNode extends TestNode {
  private final String className;
  private final String testName;
  private final Queue<String> failures = new ConcurrentLinkedQueue<>();
  private final Queue<String> skippedMessages = new ConcurrentLinkedQueue<>();
  private final Map<String, String> properties = new ConcurrentHashMap<>();
  private long runTime = 0;
  private boolean skipped = false;

  public TestCaseNode(String className, String testName) {
    this.className = className;
    this.testName = testName;
  }

  // VisibleForTesting
  @Override
  public List<TestNode> getChildren() {
    return Collections.emptyList();
  }

  public void testFailed(String failure) {
    failures.add(failure);
    skippedMessages.clear();
    skipped = false;
  }

  public void testSkipped(String message) {
    skippedMessages.add(message);
    testSkipped();
  }

  public void testSkipped() {
    failures.clear();
    skipped = true;
  }

  @Override
  public boolean isTestCase() {
    return true;
  }

  public void setRunTime(long runtime) {
    this.runTime = runtime;
  }

  public long getRuntime() {
    return runTime;
  }

  @Override
  public TestResult buildResult() {
    boolean failed = !failures.isEmpty();
    return new TestResult.Builder()
        .name(testName)
        .className(className)
        .properties(properties)
        .failures(new ArrayList<>(failures))
        .skippedMessages(new ArrayList<>(skippedMessages))
        .runTime(getRuntime())
        .numTests(1)
        .numFailures(failed ? 1 : 0)
        .numSkips(skipped ? 1 : 0)
        .childResults(Collections.emptyList())
        .build();
  }
}
