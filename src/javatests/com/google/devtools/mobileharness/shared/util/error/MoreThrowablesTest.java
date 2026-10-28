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
import com.google.common.collect.ImmutableList;
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

  @Test
  public void addSuppressed_addsExceptionsInOrder() {
    Exception primary = new Exception("Primary");
    Exception suppressed1 = new Exception("Suppressed 1");
    Exception suppressed2 = new Exception("Suppressed 2");
    Exception suppressed3 = new Exception("Suppressed 3");

    assertThat(MoreThrowables.addSuppressed(primary, suppressed1)).isEmpty();
    assertThat(MoreThrowables.addSuppressed(primary, suppressed2, suppressed3)).isEmpty();

    assertThat(primary.getSuppressed())
        .asList()
        .containsExactly(suppressed1, suppressed2, suppressed3)
        .inOrder();
  }

  @Test
  public void addSuppressed_list_addsExceptionsInOrder() {
    Exception primary = new Exception("Primary");
    Exception suppressed1 = new Exception("Suppressed 1");
    Exception suppressed2 = new Exception("Suppressed 2");

    assertThat(MoreThrowables.addSuppressed(primary, ImmutableList.of(suppressed1, suppressed2)))
        .isEmpty();

    assertThat(primary.getSuppressed())
        .asList()
        .containsExactly(suppressed1, suppressed2)
        .inOrder();
  }

  @Test
  public void addSuppressed_skipsExceptionsWhichWouldCreateReferenceCycles() {
    Exception primary = new Exception("Primary");
    // The primary exception is the cause.
    Exception cycle1 = new Exception("Cycle 1", primary);
    // The primary exception is a suppressed exception.
    Exception cycle2 = new Exception("Cycle 2");
    cycle2.addSuppressed(primary);
    // The primary exception is reachable indirectly.
    Exception cycle3 = new Exception("Cycle 3", new Exception("Cause of cycle 3", cycle2));
    Exception suppressed = new Exception("Suppressed");

    assertThat(MoreThrowables.addSuppressed(primary, primary, cycle1, suppressed, cycle2, cycle3))
        .containsExactly(primary, cycle1, cycle2, cycle3)
        .inOrder();

    assertThat(primary.getSuppressed()).asList().containsExactly(suppressed);
  }

  @Test
  public void addSuppressed_addsExceptionsSharingReferencesWithoutCycles() {
    Exception cause = new Exception("Cause");
    Exception primary = new Exception("Primary", cause);
    Exception suppressed1 = new Exception("Suppressed 1", cause);
    Exception suppressed2 = new Exception("Suppressed 2", suppressed1);

    assertThat(MoreThrowables.addSuppressed(primary, cause, suppressed1, suppressed2)).isEmpty();

    assertThat(primary.getSuppressed())
        .asList()
        .containsExactly(cause, suppressed1, suppressed2)
        .inOrder();
  }

  @Test
  public void addSuppressed_existingReferenceCycle_terminates() {
    Exception cycle1 = new Exception("Cycle 1");
    Exception cycle2 = new Exception("Cycle 2");
    cycle1.addSuppressed(cycle2);
    cycle2.addSuppressed(cycle1);
    Exception primary = new Exception("Primary");

    assertThat(MoreThrowables.addSuppressed(primary, cycle1)).isEmpty();
    assertThat(MoreThrowables.addSuppressed(cycle1, primary)).containsExactly(primary);

    assertThat(primary.getSuppressed()).asList().containsExactly(cycle1);
    assertThat(cycle1.getSuppressed()).asList().containsExactly(cycle2);
  }

  @Test
  public void addSuppressed_nullException_throwsNullPointerExceptionWithoutAddingAny() {
    Exception primary = new Exception("Primary");
    Exception suppressed = new Exception("Suppressed");

    assertThrows(
        NullPointerException.class, () -> MoreThrowables.addSuppressed(primary, suppressed, null));

    assertThat(primary.getSuppressed()).isEmpty();
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
