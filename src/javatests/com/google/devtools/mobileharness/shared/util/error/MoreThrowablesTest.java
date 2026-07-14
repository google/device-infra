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

package com.google.devtools.mobileharness.shared.util.error;

import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreThrowablesTest {

  @Test
  public void isInterruption_checksCorrectTypes() {
    assertThat(MoreThrowables.isInterruption(new InterruptedException())).isTrue();
    assertThat(MoreThrowables.isInterruption(new ClosedByInterruptException())).isTrue();
    assertThat(MoreThrowables.isInterruption(new InterruptedIOException())).isTrue();

    assertThat(MoreThrowables.isInterruption(new IllegalArgumentException())).isFalse();
    assertThat(MoreThrowables.isInterruption(new Exception(new InterruptedException())))
        .isFalse(); // only top level
  }

  @Test
  public void runAndSuppressException_suppressesException() {
    Exception primary = new Exception("Primary");
    MoreThrowables.runAndSuppressException(
        primary,
        () -> {
          throw new Exception("Secondary");
        });

    assertThat(primary.getSuppressed()).hasLength(1);
    assertThat(primary.getSuppressed()[0]).hasMessageThat().contains("Secondary");
  }

  @Test
  public void runAndSuppressException_handlesInterruptedExceptionAndRestoresStatus() {
    Exception primary = new Exception("Primary");

    // Ensure thread is not interrupted initially
    Thread.interrupted();

    MoreThrowables.runAndSuppressException(
        primary,
        () -> {
          throw new InterruptedException("Interrupted");
        });

    assertThat(primary.getSuppressed()).hasLength(1);
    assertThat(primary.getSuppressed()[0]).isInstanceOf(InterruptedException.class);

    // Check that the interrupted status was restored
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  public void runAndSuppressException_doesNotSuppressWhenSuccessful() {
    Exception primary = new Exception("Primary");
    MoreThrowables.runAndSuppressException(primary, () -> {});

    assertThat(primary.getSuppressed()).isEmpty();
  }

  @Test
  public void runAndSuppressException_primaryNull_throwsOriginalUncheckedException() {
    RuntimeException expected = new RuntimeException("Unchecked");
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                MoreThrowables.runAndSuppressException(
                    null,
                    () -> {
                      throw expected;
                    }));
    assertThat(thrown).isSameInstanceAs(expected);
  }

  @Test
  public void runAndSuppressException_primaryNull_wrapsCheckedException() {
    Exception checked = new Exception("Checked");
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                MoreThrowables.runAndSuppressException(
                    null,
                    () -> {
                      throw checked;
                    }));
    assertThat(thrown.getCause()).isSameInstanceAs(checked);
  }

  @Test
  public void runAndSuppressException_primaryNull_handlesInterruptedExceptionAndRestoresStatus() {
    // Ensure thread is not interrupted initially
    Thread.interrupted();

    InterruptedException interrupted = new InterruptedException("Interrupted");
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                MoreThrowables.runAndSuppressException(
                    null,
                    () -> {
                      throw interrupted;
                    }));
    assertThat(thrown.getCause()).isSameInstanceAs(interrupted);

    // Check that the interrupted status was restored
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  public void shortDebugCurrentStackTrace() throws Exception {
    String currentStackTrace = f1();

    assertThat(
            // Splits by "<--" and removes the "[]" prefix/suffix.
            Splitter.onPattern("<--|[\\[\\]]")
                .omitEmptyStrings()
                .trimResults()
                .splitToList(currentStackTrace))
        .comparingElementsUsing(
            transforming(
                (Function<String, String>)
                    input -> {
                      // Removes the "(...)" suffix.
                      int endIndex = input.indexOf('(');
                      endIndex = endIndex == -1 ? input.length() : endIndex;
                      return input.substring(0, endIndex);
                    },
                "has a prefix of"))
        .containsExactly(
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f4",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f3",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f2",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f1",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.shortDebugCurrentStackTrace")
        .inOrder();
  }

  private String f1() {
    return f2();
  }

  private String f2() {
    return f3();
  }

  private String f3() {
    return f4();
  }

  private String f4() {
    return MoreThrowables.shortDebugCurrentStackTrace(5L);
  }
}
