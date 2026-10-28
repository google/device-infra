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

import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

/** More utilities for {@link Throwable}. */
public class MoreThrowables {

  /** Returns a formatted string of stack traces of all threads of the current process. */
  public static String formatStackTraces() {
    Thread currentThread = Thread.currentThread();
    return Thread.getAllStackTraces().entrySet().stream()
        .filter(threadEntry -> !threadEntry.getKey().equals(currentThread))
        .sorted(comparing(threadEntry -> threadEntry.getKey().getName()))
        .map(MoreThrowables::formatThread)
        .collect(joining("\n\n"));
  }

  private static String formatThread(Entry<Thread, StackTraceElement[]> threadEntry) {
    StringBuilder result = new StringBuilder();
    result.append(threadEntry.getKey());
    for (StackTraceElement stackTraceElement : threadEntry.getValue()) {
      result.append("\n\t");
      result.append(stackTraceElement);
    }
    return result.toString();
  }

  /**
   * Equivalent to {@linkplain #shortDebugString(Throwable, int) shortDebugString(e, 0)} (print
   * until the last cause).
   *
   * <p>Returns a string containing {@link Throwable#getMessage()} of the given exception and all of
   * its causes (if any). The delimiter string is "{@code , cause: }". For example, "{@code
   * message_of_e, cause: message_of_cause_of_e, cause: message_of_cause_of_cause_of_e}".
   *
   * <p>Note that the purpose of this method is "printing the content of an exception but making the
   * result string NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * Throwables.getStackTraceAsString(e)} instead.
   *
   * @see #shortDebugString(Throwable, int)
   */
  public static String shortDebugString(Throwable e) {
    return shortDebugString(e, /* maxLength= */ 0);
  }

  /**
   * Returns a string containing {@link Throwable#getMessage()} of the given exception and all of
   * its causes (if any). The delimiter string is "{@code , cause: }". For example, "{@code
   * message_of_e, cause: message_of_cause_of_e, cause: message_of_cause_of_cause_of_e}".
   *
   * <p>Note that the purpose of this method is "printing the content of an exception but making the
   * result string NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * Throwables.getStackTraceAsString(e)} instead.
   *
   * @param maxLength the max length of the printed message chain. For example, 2 means printing the
   *     message of the exception and the message of the cause of the exception (if any). 0 or
   *     negative for "no max length" (print until the last cause)
   */
  public static String shortDebugString(Throwable e, int maxLength) {
    StringBuilder result = new StringBuilder();
    boolean checkLength = maxLength > 0;
    while (true) {
      result.append(e.getMessage());
      if (checkLength) {
        maxLength--;
        if (maxLength == 0) {
          break;
        }
      }
      e = e.getCause();
      if (e == null) {
        break;
      }
      result.append(", cause: ");
    }
    return result.toString();
  }

  /**
   * Returns a short debug string representing the stack trace of the current thread. For example,
   * if {@code foo()} calls {@code goo()} and {@code goo()} calls this method, then the method will
   * return a string like:
   *
   * <pre>
   * {@code "[com.google.Goo.goo(Goo.java:10)  <-- com.google.Foo.foo(Foo.java:20)]"}
   * </pre>
   *
   * <p>Note that the purpose of this method is "printing a stack trace but making the result string
   * NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * logger.atInfo().withStackTrace().log()} instead.
   *
   * @param maxLength the max length of the stack trace. For example, 2 means printing the first two
   *     layers of the stack trace. 0 or negative for "no max length" (print the full stack trace)
   */
  public static String shortDebugCurrentStackTrace(long maxLength) {
    // 2L to remove the stack trace of this method.
    return shortDebugCurrentStackTrace(maxLength, /* skip= */ 1L);
  }

  /**
   * Returns a short debug string representing the stack trace of the current thread. For example,
   * if {@code foo()} calls {@code goo()} and {@code goo()} calls this method, then the method will
   * return a string like:
   *
   * <pre>
   * {@code "[com.google.Goo.goo(Goo.java:10)  <-- com.google.Foo.foo(Foo.java:20)]"}
   * </pre>
   *
   * <p>Note that the purpose of this method is "printing a stack trace but making the result string
   * NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * logger.atInfo().withStackTrace().log()} instead.
   *
   * @param maxLength the max length of the stack trace. For example, 2 means printing the first two
   *     layers of the stack trace. 0 or negative for "no max length" (print the full stack trace)
   * @param skip the number of layers to remove from the top. For example, if foo() calls goo(),
   *     goo() calls hoo(), and hoo() calls this method, if {@code skip} is {@code 1L}, the result
   *     will be like "{@code [com.google.Goo.goo(Goo.java:10) <--
   *     com.google.Foo.foo(Foo.java:20)]}"
   */
  public static String shortDebugCurrentStackTrace(long maxLength, long skip) {
    // 2L to remove the stack traces of Thread#getStackTrace() and this method.
    return shortDebugStackTrace(
        stream(Thread.currentThread().getStackTrace()).skip(2L + skip), maxLength);
  }

  /**
   * Returns a short debug string representing the stack trace of the given thread. For example,
   *
   * <pre>
   * {@code "[com.google.Goo.goo(Goo.java:10)  <-- com.google.Foo.foo(Foo.java:20)]"}
   * </pre>
   *
   * <p>Note that the purpose of this method is "printing a stack trace but making the result string
   * NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * logger.atInfo().withStackTrace().log()} instead.
   *
   * @param maxLength the max length of the stack trace. For example, 2 means printing the first two
   *     layers of the stack trace. 0 or negative for "no max length" (print the full stack trace)
   */
  public static String shortDebugStackTrace(Thread thread, long maxLength) {
    return shortDebugStackTrace(stream(thread.getStackTrace()), maxLength);
  }

  /**
   * Returns a short debug string representing the stack trace of the given exception. For example,
   *
   * <pre>
   * {@code "[com.google.Goo.goo(Goo.java:10)  <-- com.google.Foo.foo(Foo.java:20)]"}
   * </pre>
   *
   * <p>Note that the purpose of this method is "printing a stack trace but making the result string
   * NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * logger.atInfo().withStackTrace().log()} instead.
   *
   * @param maxLength the max length of the stack trace. For example, 2 means printing the first two
   *     layers of the stack trace. 0 or negative for "no max length" (print the full stack trace)
   */
  public static String shortDebugStackTrace(Throwable throwable, long maxLength) {
    return shortDebugStackTrace(stream(throwable.getStackTrace()), maxLength);
  }

  private static String shortDebugStackTrace(Stream<StackTraceElement> stackTrace, long maxLength) {
    return stackTrace
        .limit(maxLength <= 0 ? Long.MAX_VALUE : maxLength)
        .map(StackTraceElement::toString)
        .collect(joining("  <--  ", "[", "]"));
  }

  /** Checks if the given exception is a thread interruption. */
  public static boolean isInterruption(Throwable e) {
    return e instanceof InterruptedException
        || e instanceof ClosedByInterruptException
        || e instanceof InterruptedIOException;
  }

  /**
   * Adds the given exceptions to the suppressed exceptions of the given throwable in order.
   *
   * <p>Same as {@link #addSuppressed(Throwable, List)}.
   *
   * @return the skipped exceptions in order
   */
  @CanIgnoreReturnValue
  public static ImmutableList<Throwable> addSuppressed(
      Throwable throwable, Throwable... suppressedExceptions) {
    return addSuppressed(throwable, Arrays.asList(suppressedExceptions));
  }

  /**
   * Adds the given exceptions to the suppressed exceptions of the given throwable in order.
   *
   * <p>It is the same as calling {@link Throwable#addSuppressed(Throwable)} for each given
   * exception, except that an exception which would create a reference cycle is skipped, i.e., an
   * exception from which the given throwable is reachable via causes and suppressed exceptions
   * (including the given throwable itself). Reference cycles of throwables may cause infinite
   * recursion in code which traverses causes and suppressed exceptions of a throwable.
   *
   * <p>The skipped exceptions are returned so that the caller can handle them (e.g., log them),
   * since otherwise their information (e.g., messages and stack traces) is dropped silently.
   *
   * @return the skipped exceptions in order
   * @throws NullPointerException if the given throwable or any of the given exceptions is null, in
   *     which case none of the given exceptions is added
   */
  @CanIgnoreReturnValue
  public static ImmutableList<Throwable> addSuppressed(
      Throwable throwable, List<? extends Throwable> suppressedExceptions) {
    requireNonNull(throwable);
    // Checks null exceptions before adding any exception, to avoid adding only part of them.
    for (Throwable suppressedException : suppressedExceptions) {
      requireNonNull(suppressedException, "Cannot suppress a null exception.");
    }
    // Throwables from which the given throwable is known to be unreachable. It is shared among all
    // the given exceptions to avoid traversing the same throwables repeatedly, and it is still
    // valid after an exception is added, because the newly added reference starts from the given
    // throwable itself.
    Set<Throwable> unreachableThrowables = Sets.newIdentityHashSet();
    ImmutableList.Builder<Throwable> skippedExceptions = ImmutableList.builder();
    for (Throwable suppressedException : suppressedExceptions) {
      if (isReachable(throwable, suppressedException, unreachableThrowables)) {
        skippedExceptions.add(suppressedException);
      } else {
        throwable.addSuppressed(suppressedException);
      }
    }
    return skippedExceptions.build();
  }

  /**
   * Returns whether the target is reachable from the source (including the source itself) via
   * causes and suppressed exceptions.
   *
   * @param unreachableThrowables throwables from which the target is known to be unreachable, which
   *     will be skipped during the traversal, and will be updated if the target is unreachable
   */
  @SuppressWarnings("ReferenceEquality") // Throwables are intentionally compared by identity.
  private static boolean isReachable(
      Throwable target, Throwable source, Set<Throwable> unreachableThrowables) {
    Set<Throwable> visited = Sets.newIdentityHashSet();
    Deque<Throwable> stack = new ArrayDeque<>();
    stack.push(source);
    while (!stack.isEmpty()) {
      Throwable current = stack.pop();
      if (current == target) {
        return true;
      }
      if (unreachableThrowables.contains(current) || !visited.add(current)) {
        continue;
      }
      Throwable cause = current.getCause();
      if (cause != null) {
        stack.push(cause);
      }
      for (Throwable suppressed : current.getSuppressed()) {
        stack.push(suppressed);
      }
    }
    // The target is unreachable from all visited throwables.
    unreachableThrowables.addAll(visited);
    return false;
  }

  /**
   * Runs the given runnable and suppresses any thrown exception into the primary exception.
   *
   * <p>If the thrown exception is a thread interruption, it also restores the thread's interrupted
   * status.
   */
  public static void runAndSuppressException(
      Throwable primaryException, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      if (isInterruption(t)) {
        Thread.currentThread().interrupt();
      }
      if (primaryException != null) {
        primaryException.addSuppressed(t);
      } else {
        if (t instanceof RuntimeException) {
          throw (RuntimeException) t;
        } else if (t instanceof Error) {
          throw (Error) t;
        } else {
          throw new RuntimeException(t);
        }
      }
    }
  }

  private MoreThrowables() {}
}
