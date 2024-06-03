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

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Branch of {@code com.google.common.util.concurrent.Callables} since the GitHub version does not
 * have some methods.
 */
public final class Callables {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Executes all {@link MobileHarnessCallable}s in the given list sequentially. All callables will
   * be executed regardless of any {@link Throwable}s thrown by previous callables.
   *
   * <p>This method will throw the first exception, and add the following exceptions as its
   * suppressed exceptions, if any.
   *
   * <p>Before executing each callable, the interrupted status of the current thread will be saved
   * and cleared.
   *
   * <p>This method can be used for simplifying code of executing multiple cleanup tasks in finally
   * blocks. For example, from
   *
   * <pre>{@code
   * try {
   *   try {
   *     try {
   *       foo();
   *     } finally {
   *       cleanupTask1();
   *     }
   *   } finally {
   *     cleanupTask2();
   *   }
   * } finally {
   *   cleanupTask3();
   * }
   * }</pre>
   *
   * or
   *
   * <pre>{@code
   * try {
   *   foo();
   * } finally {
   *   try {
   *     cleanupTask1();
   *   } finally {
   *     try {
   *       cleanupTask2();
   *     } finally {
   *       cleanupTask3();
   *     }
   *   }
   * }
   * }</pre>
   *
   * to
   *
   * <pre>{@code
   * try {
   *   foo();
   * } finally {
   *   Callables.callAll(this::cleanupTask1, this::cleanupTask2, this::cleanupTask3);
   * }
   * }</pre>
   */
  public static void callAll(MobileHarnessCallable<?>... callables)
      throws MobileHarnessException, InterruptedException {
    boolean interrupted = false;
    List<Throwable> errors = new ArrayList<>();

    // Calls each callable.
    for (MobileHarnessCallable<?> callable : callables) {
      interrupted |= Thread.interrupted();
      try {
        Object result = callable.call();
        if (result != null) {
          logger.atInfo().log("Callable [%s] returned [%s]", callable, result);
        }
      } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
        errors.add(e);
        if (isInterruptedException(e)) {
          interrupted = true;
        }
      }
    }

    // Gets the error to throw if any.
    Throwable error;
    if (errors.isEmpty()) {
      error = null;
    } else {
      error = errors.get(0);
      errors.stream().skip(1L).forEach(error::addSuppressed);
    }
    if (interrupted && !isInterruptedException(error)) {
      Thread.currentThread().interrupt();
    }

    // Throws the error if any.
    if (error == null) {
      return;
    }
    Throwables.throwIfInstanceOf(error, MobileHarnessException.class);
    Throwables.throwIfInstanceOf(error, InterruptedException.class);
    Throwables.throwIfUnchecked(error);
  }

  private static boolean isInterruptedException(Throwable e) {
    return e instanceof InterruptedException;
  }

  /**
   * Wraps the given callable such that for the duration of {@link Callable#call} the thread that is
   * running will have the given name.
   *
   * @param callable the callable to wrap
   * @param nameSupplier the supplier of thread names, {@link Supplier#get get} will be called once
   *     for each invocation of the wrapped callable.
   */
  public static <T> Callable<T> threadRenaming(
      Callable<T> callable, Supplier<String> nameSupplier) {
    checkNotNull(nameSupplier);
    checkNotNull(callable);
    return () -> {
      try (NonThrowingAutoCloseable ignored = threadRenaming(nameSupplier.get())) {
        return callable.call();
      }
    };
  }

  /**
   * Wraps the given runnable such that for the duration of {@link Runnable#run} the thread that is
   * running will have the given name.
   *
   * @param runnable the runnable to wrap
   * @param nameSupplier the supplier of thread names, {@link Supplier#get get} will be called once
   *     for each invocation of the wrapped runnable.
   */
  public static Runnable threadRenaming(Runnable runnable, Supplier<String> nameSupplier) {
    checkNotNull(nameSupplier);
    checkNotNull(runnable);
    return () -> {
      try (NonThrowingAutoCloseable ignored = threadRenaming(nameSupplier.get())) {
        runnable.run();
      }
    };
  }

  /**
   * Wraps the given future callback such that for the duration of {@link FutureCallback#onSuccess}
   * or {@link FutureCallback#onFailure} the thread that is running will have the given name.
   *
   * @param futureCallback the future callback to wrap
   * @param nameSupplier the supplier of thread names, {@link Supplier#get get} will be called once
   *     for each invocation of the wrapped future callback.
   */
  public static <T> FutureCallback<T> threadRenaming(
      FutureCallback<T> futureCallback, Supplier<String> nameSupplier) {
    checkNotNull(nameSupplier);
    checkNotNull(futureCallback);
    return new ThreadRenamingFutureCallback<>(futureCallback, nameSupplier);
  }

  /**
   * Sets the thread name for a code block in the current thread, and restores the original thread
   * name when leaving the block (when the returned closeable is closed).
   *
   * <p>Example:
   *
   * <pre>{@code try (NonThrowingAutoCloseable ignored = threadRenaming("new-thread-name")) {
   *   doSomething(); // with the thread name "new-thread-name"
   * }}</pre>
   */
  public static NonThrowingAutoCloseable threadRenaming(String threadName) {
    return new ThreadRenamer(threadName);
  }

  private static class ThreadRenamingFutureCallback<V> implements FutureCallback<V> {

    private final FutureCallback<V> futureCallback;
    private final Supplier<String> nameSupplier;

    private ThreadRenamingFutureCallback(
        FutureCallback<V> futureCallback, Supplier<String> nameSupplier) {
      this.futureCallback = futureCallback;
      this.nameSupplier = nameSupplier;
    }

    @Override
    public void onSuccess(V result) {
      try (NonThrowingAutoCloseable ignored = threadRenaming(nameSupplier.get())) {
        futureCallback.onSuccess(result);
      }
    }

    @Override
    public void onFailure(Throwable t) {
      try (NonThrowingAutoCloseable ignored = threadRenaming(nameSupplier.get())) {
        futureCallback.onFailure(t);
      }
    }
  }

  private static class ThreadRenamer implements NonThrowingAutoCloseable {

    private final Thread thread;
    private final String oldThreadName;
    private final boolean restoreThreadName;

    private ThreadRenamer(String threadName) {
      this.thread = Thread.currentThread();
      this.oldThreadName = thread.getName();
      this.restoreThreadName = trySetName(threadName, thread);
    }

    @Override
    public void close() {
      if (restoreThreadName) {
        trySetName(oldThreadName, thread);
      }
    }
  }

  @CanIgnoreReturnValue
  private static boolean trySetName(String threadName, Thread currentThread) {
    try {
      currentThread.setName(threadName);
      return true;
    } catch (SecurityException e) {
      return false;
    }
  }

  private Callables() {}
}
