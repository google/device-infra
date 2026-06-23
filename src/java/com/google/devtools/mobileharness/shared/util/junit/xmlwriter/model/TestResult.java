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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Result of executing a test suite or test case. */
public final class TestResult {
  private final String name;
  private final String className;
  private final Map<String, String> properties;
  private final List<String> failures;
  private final List<String> skippedMessages;
  private final long runTime;
  private final int numTests;
  private final int numFailures;
  private final int numSkips;
  private final List<TestResult> childResults;
  private final Optional<LocalDateTime> timestamp;

  private TestResult(Builder builder) {
    name = checkNotNull(builder.name, "name not set");
    className = checkNotNull(builder.className, "className not set");
    properties = checkNotNull(builder.properties, "properties not set");
    failures = checkNotNull(builder.failures, "failures not set");
    skippedMessages = checkNotNull(builder.skippedMessages, "skippedMessages not set");
    numTests = checkNotNull(builder.numTests, "numTests not set");
    numFailures = checkNotNull(builder.numFailures, "numFailures not set");
    numSkips = checkNotNull(builder.numSkips, "numSkips not set");
    childResults = checkNotNull(builder.childResults, "childResults not set");
    runTime = builder.runTime;
    timestamp = builder.timestamp;
  }

  public String getName() {
    return name;
  }

  public String getClassName() {
    return className;
  }

  Map<String, String> getProperties() {
    return properties;
  }

  List<String> getFailures() {
    return failures;
  }

  List<String> getSkippedMessages() {
    return skippedMessages;
  }

  public long getRunTime() {
    return runTime;
  }

  public Optional<LocalDateTime> getTimestamp() {
    return timestamp;
  }

  public int getNumTests() {
    return numTests;
  }

  public int getNumFailures() {
    return numFailures;
  }

  public int getNumSkips() {
    return numSkips;
  }

  public List<TestResult> getChildResults() {
    return childResults;
  }

  public List<String> getAllFailureMessages() {
    List<String> messages = new ArrayList<>();
    getAllFailureMessagesRecursively(messages);

    return messages;
  }

  private void getAllFailureMessagesRecursively(List<String> messages) {
    messages.addAll(failures);

    for (TestResult childResult : childResults) {
      childResult.getAllFailureMessagesRecursively(messages);
    }
  }

  private static <T> T checkNotNull(T reference, String errorMessage) {
    if (reference == null) {
      throw new NullPointerException(errorMessage);
    }
    return reference;
  }

  static final class Builder {
    private String name = null;
    private String className = null;
    private Map<String, String> properties = null;
    private List<String> failures = null;
    private List<String> skippedMessages = null;
    private long runTime = Long.MIN_VALUE;
    private Integer numTests = null;
    private Integer numFailures = null;
    private Integer numSkips = null;
    private List<TestResult> childResults = null;
    private Optional<LocalDateTime> timestamp = Optional.empty();

    Builder() {}

    Builder name(String name) {
      this.name = checkNullToNotNull(this.name, name, "name");
      return this;
    }

    Builder className(String className) {
      this.className = checkNullToNotNull(this.className, className, "className");
      return this;
    }

    Builder properties(Map<String, String> properties) {
      this.properties = checkNullToNotNull(this.properties, properties, "properties");
      return this;
    }

    Builder failures(List<String> failures) {
      this.failures = checkNullToNotNull(this.failures, failures, "failures");
      return this;
    }

    Builder skippedMessages(List<String> messages) {
      this.skippedMessages = checkNullToNotNull(this.skippedMessages, messages, "skippedMessages");
      return this;
    }

    Builder runTime(long runTime) {
      if (this.runTime != Long.MIN_VALUE) {
        throw new IllegalStateException("runTime already set");
      }
      this.runTime = runTime;
      return this;
    }

    Builder timestamp(Optional<LocalDateTime> timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    Builder numTests(int numTests) {
      this.numTests = checkNullToNotNull(this.numTests, numTests, "numTests");
      return this;
    }

    Builder numFailures(int numFailures) {
      this.numFailures = checkNullToNotNull(this.numFailures, numFailures, "numFailures");
      return this;
    }

    Builder numSkips(int numSkips) {
      this.numSkips = checkNullToNotNull(this.numSkips, numSkips, "numSkips");
      return this;
    }

    Builder childResults(List<TestResult> childResults) {
      this.childResults = checkNullToNotNull(this.childResults, childResults, "childResults");
      return this;
    }

    TestResult build() {
      return new TestResult(this);
    }

    private static <T> T checkNullToNotNull(T currValue, T newValue, String desc) {
      if (currValue != null) {
        throw new IllegalStateException(desc + " already set");
      }
      return checkNotNull(newValue, desc + " is null");
    }
  }
}
