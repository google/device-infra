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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Concurrency utility for running tasks in parallel. */
public class ConcurrencyUtil {

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
   *
   * <p>If one sub task fails or the current thread is interrupted, all sub tasks will be cancelled,
   * interrupted, and waited until completion before this method returns.
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

      // Casts the error to MH exception.
      return MobileHarnessExceptions.rethrow(
          e.getCause(), BasicErrorId.UNEXPECTED_NON_MH_CHECKED_EXCEPTION_FROM_SUB_TASK);
    } catch (InterruptedException e) {
      // Interrupts all sub tasks and waits for them to finish if the current thread is interrupted.
      combinedFuture.cancel(/* mayInterruptIfRunning= */ false);
      cancelAndAwaitAllTasks(executions);
      throw e;
    }
  }

  private static void cancelAndAwaitAllTasks(List<? extends TaskExecution<?>> executions) {
    executions.forEach(TaskExecution::cancelAndInterrupt);
    boolean interrupted = false;
    for (TaskExecution<?> execution : executions) {
      while (true) {
        try {
          execution.awaitFinish();
          break;
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

  private static class TaskExecution<V> {
    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean started = false;

    @GuardedBy("lock")
    private boolean finished = false;

    @GuardedBy("lock")
    @Nullable
    private Thread runningThread = null;

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

    private void awaitFinish() throws InterruptedException {
      synchronized (lock) {
        while (!finished) {
          lock.wait();
        }
      }
    }
  }

  private ConcurrencyUtil() {}
}
