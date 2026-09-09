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
import com.google.common.collect.ImmutableList;

/** A single {@code <testsuite>} element parsed from a JUnit (Ant) XML test result file. */
@AutoValue
public abstract class JunitXmlTestSuite {

  /**
   * Value of the {@code name} attribute.
   *
   * <p>This is empty in the JUnit XML generated for iOS XCTest runs, in which case the test target
   * can only be recovered from the {@code classname} of the individual test cases.
   */
  public abstract String name();

  public abstract ImmutableList<JunitXmlTestCase> testCases();

  public static JunitXmlTestSuite create(String name, ImmutableList<JunitXmlTestCase> testCases) {
    return new AutoValue_JunitXmlTestSuite(name, testCases);
  }
}
