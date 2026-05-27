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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.base.StackTraceExtractor.StackTrace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StackTraceExtractorTest {

  @Test
  public void extract_emptyOrNull_returnsEmpty() {
    assertThat(StackTraceExtractor.extract(null)).isEmpty();
    assertThat(StackTraceExtractor.extract("")).isEmpty();
  }

  @Test
  public void extract_noStackTraces_returnsEmpty() {
    String log = "Line 1\nLine 2\nLine 3";
    assertThat(StackTraceExtractor.extract(log)).isEmpty();
  }

  @Test
  public void extract_singleStackTrace_returnsOneExtracted() {
    String log =
        """
        An exception occurred:
        \tat com.google.Class.method1(File.java:10)
        \tat com.google.Class.method2(File.java:20)
        End of log.\
        """;

    ImmutableList<StackTrace> results = StackTraceExtractor.extract(log);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).frames())
        .containsExactly(
            "com.google.Class.method1(File.java:10)", "com.google.Class.method2(File.java:20)")
        .inOrder();
  }

  @Test
  public void extract_multipleStackTraces_returnsAllExtracted() {
    String log =
        """
        Error 1:
        \tat com.google.Class1.run(Class1.java:5)
        Some other info
        Error 2:
        \tat com.google.Class2.init(Class2.java:15)
        \tat com.google.Class2.start(Class2.java:25)
        """;

    ImmutableList<StackTrace> results = StackTraceExtractor.extract(log);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).frames()).containsExactly("com.google.Class1.run(Class1.java:5)");
    assertThat(results.get(1).frames())
        .containsExactly(
            "com.google.Class2.init(Class2.java:15)", "com.google.Class2.start(Class2.java:25)")
        .inOrder();
  }
}
