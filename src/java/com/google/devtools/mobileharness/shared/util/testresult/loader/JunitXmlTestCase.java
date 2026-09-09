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

package com.google.devtools.mobileharness.shared.util.testresult.loader;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/** A single {@code <testcase>} element parsed from a JUnit (Ant) XML test result file. */
@AutoValue
public abstract class JunitXmlTestCase {

  /** Status of a test case, derived from the child elements of {@code <testcase>}. */
  public enum Status {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
  }

  /** Value of the {@code name} attribute, e.g. {@code testLayout}. */
  public abstract String name();

  /** Value of the {@code classname} attribute, e.g. {@code EarlGreyExampleSwiftTests.FooTest}. */
  public abstract String className();

  /** Value of the {@code time} attribute, or {@link Duration#ZERO} if absent or unparseable. */
  public abstract Duration elapsedTime();

  public abstract Status status();

  /**
   * Message attribute and body of the {@code <failure>} or {@code <error>} child element, or empty
   * if the test case has neither.
   */
  public abstract Optional<String> stackTrace();

  public static Builder builder() {
    return new AutoValue_JunitXmlTestCase.Builder()
        .setName("")
        .setClassName("")
        .setElapsedTime(Duration.ZERO)
        .setStatus(Status.PASSED);
  }

  /** Builder for {@link JunitXmlTestCase}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setClassName(String className);

    public abstract Builder setElapsedTime(Duration elapsedTime);

    public abstract Builder setStatus(Status status);

    public abstract Builder setStackTrace(String stackTrace);

    public abstract JunitXmlTestCase build();
  }
}
