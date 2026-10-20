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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugStackTrace;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Concurrency utility for running tasks in parallel. */
public class ConcurrencyUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of the warning logs when waiting for interrupted sub tasks to finish. */
  private static final Duration WAITING_WARNING_INTERVAL = Duration.ofMinutes(5L);

  /**
   * Sub task to run in parallel by {@link #runInParallel}.
   *
   * @param <V> the result type of the task
   * @see #runInParallel
   */
  @AutoValue
  public abstract static class SubTask<V> {

    /**
     * Creates a sub task.
     *
     * @param callable the task to run
     * @param threadName the name of the thread which/when runs the task
     * @param logTagName the name of the extra FluentLogger log tag to show when running the task
     * @param logTagValue the value of the extra FluentLogger log tag to show when running the task
     * @param <V> the result type of the task
     */
    public static <V> SubTask<V> of(
        MobileHarnessCallable<V> callable,
        String threadName,
        String logTagName,
        String logTagValue) {
      return new AutoValue_ConcurrencyUtil_SubTask<>(callable, threadName, logTagName, logTagValue);
    }

    public abstract MobileHarnessCallable<V> callable();

    public abstract String threadName();

    public abstract String logTagName();

    public abstract String logTagValue();
  }

  /**
   * Runs the given sub tasks in parallel and waits until all sub tasks succeed or one fails or the
   * current thread is interrupted.
   *
   * <p>If all sub tasks succeed, the given result merger will be called to merge all results.
   *
   * <p>If one sub task fails (throws an exception), this method will interrupt all other sub tasks,
   * wait for all sub tasks to finish, and return with the exception thrown by the failed sub task.
   * Exceptions thrown by other sub tasks will be added to it as suppressed exceptions.
   *
   * <p>If one sub task fails or the current thread is interrupted, all sub tasks will be cancelled,
   * interrupted, and waited until completion before this method returns. If the current thread is
   * interrupted, exceptions thrown by sub tasks will be added to the thrown {@link
   * InterruptedException} as suppressed exceptions. A warning will be logged periodically if any
   * sub task has not finished for a long time after being interrupted.
   *
   * <p>The current trace context will be propagated to the threads of all sub tasks. A local trace
   * span will be created for each sub task and {@link SubTask#logTagName()}/{@link
   * SubTask#logTagValue()} of a sub task will be added to its FluentLogger log tags of its trace
   * context.
   *
   * <p>{@link SubTask#threadName()} will be used as each sub task's thread name.
   *
   * @param tasks the sub tasks to run in parallel
   * @param executorService the executor service to run the sub tasks and the result merger
   * @param resultMerger the result merger to merge results of all sub tasks
   * @param <V> the result type of all sub tasks
   * @return the merged result
   * @throws MobileHarnessException if one sub task fails
   * @throws InterruptedException if the current thread is interrupted
   */
  public static <V> V runInParallel(
      List<SubTask<V>> tasks,
      ListeningExecutorService executorService,
      Function<List<V>, V> resultMerger)
      throws MobileHarnessException, InterruptedException {
    // Gets the caller of this method.
    StackTraceElement caller = new Throwable().getStackTrace()[1];

    // Starts all sub tasks.
    ListeningExecutorService taskExecutor = executorService;
    ImmutableList<TaskExecution<V>> executions =
        tasks.stream()
            .map(
                task ->
                    threadRenaming(
                        () -> {
                          // Adds the log tag.
                          MobileHarnessLogTag.addTag(task.logTagName(), task.logTagValue());

                          // Runs the actual sub task.
                          return task.callable().call();
                        },
                        task::threadName))
            .map(callable -> new TaskExecution<>(callable, taskExecutor))
            .collect(toImmutableList());

    // Creates a fail-fast combined future which calls the result merger.
    ListenableFuture<V> combinedFuture =
        Futures.whenAllSucceed(
                executions.stream().map(TaskExecution::future).collect(toImmutableList()))
            .call(
                () -> {
                  List<V> results = new ArrayList<>(executions.size());
                  for (TaskExecution<V> execution : executions) {
                    results.add(Futures.getDone(execution.future()));
                  }
                  return resultMerger.apply(results);
                },
                executorService);

    // Waits until all sub task succeed or one fails or the current thread is interrupted.
    try {
      return combinedFuture.get();
    } catch (ExecutionException e) {
      // Interrupts all sub tasks and waits for them to finish if one fails.
      cancelAndAwaitAllTasks(executions);

      // Attaches failures of other sub tasks to the failure.
      addSubTaskFailuresAsSuppressed(e.getCause(), executions);

      // Casts the error to MH exception.
      return MobileHarnessExceptions.rethrow(
          e.getCause(), BasicErrorId.UNEXPECTED_NON_MH_CHECKED_EXCEPTION_FROM_SUB_TASK);
    } catch (InterruptedException e) {
      // Interrupts all sub tasks and waits for them to finish if the current thread is interrupted.
      combinedFuture.cancel(/* mayInterruptIfRunning= */ false);
      cancelAndAwaitAllTasks(executions);

      // Attaches failures of sub tasks to the interruption.
      addSubTaskFailuresAsSuppressed(e, executions);
      throw e;
    }
  }

  private static void cancelAndAwaitAllTasks(List<? extends TaskExecution<?>> executions) {
    executions.forEach(TaskExecution::cancelAndInterrupt);
    long startNanos = System.nanoTime();
    boolean interrupted = false;
    for (TaskExecution<?> execution : executions) {
      while (true) {
        try {
          if (execution.awaitFinish(WAITING_WARNING_INTERVAL)) {
            break;
          }
          // The sub task may hang (e.g., blocked by an uninterruptible operation). Keeps waiting
          // because it may still be using resources (e.g., devices), and logs a warning
          // periodically for debugging.
          logger.atWarning().log(
              "Sub task has not finished in %s after being interrupted, keep waiting. %s",
              Duration.ofNanos(System.nanoTime() - startNanos), execution.getDebugInfo());
        } catch (InterruptedException e) {
          interrupted = true;
          executions.forEach(TaskExecution::cancelAndInterrupt);
        }
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Adds failures of the given sub tasks to the given throwable as suppressed exceptions, skipping
   * the throwable itself and duplicated failures.
   */
  private static void addSubTaskFailuresAsSuppressed(
      Throwable throwable, List<? extends TaskExecution<?>> executions) {
    // TODO: Prevent reference cycles caused by addSuppressed(), which may cause
    // infinite recursion in code traversing the cause/suppressed graph (e.g., ErrorModelConverter).
    Set<Throwable> addedFailures = Sets.newIdentityHashSet();
    addedFailures.add(throwable);
    for (TaskExecution<?> execution : executions) {
      Throwable failure = execution.getFailure();
      if (failure != null && addedFailures.add(failure)) {
        throwable.addSuppressed(failure);
      }
    }
  }

  private static class TaskExecution<V> {
    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean started = false;

    @GuardedBy("lock")
    private boolean finished = false;

    @GuardedBy("lock")
    @Nullable
    private Thread runningThread = null;

    @GuardedBy("lock")
    @Nullable
    private Throwable failure = null;

    private final ListenableFuture<V> future;

    private TaskExecution(Callable<V> callable, ListeningExecutorService executorService) {
      this.future =
          executorService.submit(
              () -> {
                if (!onStart()) {
                  throw new InterruptedException("Sub task cancelled before starting");
                }
                try {
                  return callable.call();
                } catch (Exception | Error e) {
                  // Records the failure here rather than reading it from the future later, because
                  // the future may not be done yet right after onFinish() is called.
                  onFailure(e);
                  throw e;
                } finally {
                  onFinish();
                }
              });
    }

    private ListenableFuture<V> future() {
      return future;
    }

    private boolean onStart() {
      synchronized (lock) {
        if (finished) {
          return false;
        }
        started = true;
        runningThread = Thread.currentThread();
        return true;
      }
    }

    private void onFailure(Throwable failure) {
      synchronized (lock) {
        this.failure = failure;
      }
    }

    private void onFinish() {
      synchronized (lock) {
        finished = true;
        runningThread = null;
        lock.notifyAll();
      }
    }

    @SuppressWarnings("Interruption")
    private void cancelAndInterrupt() {
      synchronized (lock) {
        if (!started) {
          finished = true;
          future.cancel(/* mayInterruptIfRunning= */ false);
          lock.notifyAll();
        } else if (!finished && runningThread != null) {
          runningThread.interrupt();
        }
      }
    }

    /**
     * Waits until the sub task finishes or the given timeout elapses.
     *
     * @return whether the sub task has finished
     */
    private boolean awaitFinish(Duration timeout) throws InterruptedException {
      long deadlineNanos = System.nanoTime() + timeout.toNanos();
      synchronized (lock) {
        while (!finished) {
          long remainingNanos = deadlineNanos - System.nanoTime();
          if (remainingNanos <= 0L) {
            return false;
          }
          NANOSECONDS.timedWait(lock, remainingNanos);
        }
        return true;
      }
    }

    @Nullable
    private Throwable getFailure() {
      synchronized (lock) {
        return failure;
      }
    }

    private String getDebugInfo() {
      synchronized (lock) {
        if (runningThread == null) {
          return "The sub task has just finished.";
        }
        return String.format(
            "thread_name=[%s], stack_trace=%s",
            runningThread.getName(), shortDebugStackTrace(runningThread, /* maxLength= */ 0));
      }
    }
  }

  private ConcurrencyUtil() {}
}
