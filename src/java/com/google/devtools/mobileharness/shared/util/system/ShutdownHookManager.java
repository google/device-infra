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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/**
 * A manager for executing multiple submitted <b>shutdown tasks</b> in one Java {@linkplain
 * Runtime#addShutdownHook(Thread) shutdown hook}, to make them sequential based on a defined
 * priority, instead of concurrent and in unpredictable order (the behavior of Java shutdown hooks).
 *
 * <p>Additionally, this manager provides {@link #shutdown()} to manually trigger submitted shutdown
 * tasks before the JVM begins the shutdown phase, to ensure system modules like {@code LogManager}
 * are still available when executing shutdown tasks. The submitted shutdown tasks are guaranteed to
 * run exactly once, no matter triggered manually or by the JVM.
 *
 * <p>Exceptions in one shutdown task do not stop subsequent tasks. If the thread is interrupted,
 * the manager will attempt to finish remaining shutdown tasks (setting the interrupt flag).
 *
 * <p>Note that one shutdown task may block subsequent tasks.
 *
 * <p>This class is thread-safe.
 */
public class ShutdownHookManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** A shutdown task. */
  public interface Task {

    void run() throws Exception;
  }

  /** The handle of a submitted shutdown task. */
  public interface Handle {

    /** Removes the submitted shutdown task from the manager. */
    void remove();
  }

  /**
   * Priority of a shutdown task to determine execution order of submitted shutdown tasks.
   *
   * <p>Lower integer values execute EARLIER (e.g., 100 runs before 200, and -100 runs before 0).
   * Shutdown tasks with the same priority value are executed in their submission order.
   *
   * <p>You can use the predefined constants (like {@link #TRAFFIC}), derive values relative to them
   * ({@link #earlier(int)}), or simply create a custom priority directly (e.g., {@code new
   * Priority(150)}).
   *
   * <p>Standard Priorities:
   *
   * <table border="1">
   * <tr><th>Constant</th><th>Value</th><th>Description</th></tr>
   * <tr><td>{@link #HIGHEST}</td><td>MIN_VALUE</td><td>Urgent signals / health checks</td></tr>
   * <tr><td>{@link #TRAFFIC}</td><td>100</td><td>Stop external inputs (ports)</td></tr>
   * <tr><td>{@link #WORKERS}</td><td>200</td><td>Stop application logic</td></tr>
   * <tr><td>{@link #RESOURCES}</td><td>300</td><td>(Default) Release infrastructure (DB, thread pools)</td></tr>
   * <tr><td>{@link #LOWEST}</td><td>MAX_VALUE</td><td>Final audit / process termination</td></tr>
   * </table>
   */
  public record Priority(int value) implements Comparable<Priority> {

    /**
     * The first tasks to run. Use this for setting global shutdown flags so load balancers stop
     * routing traffic immediately.
     */
    public static final Priority HIGHEST = new Priority(Integer.MIN_VALUE);

    /**
     * Stops external inputs. Use this to close HTTP ports, pause Kafka consumers, or stop listening
     * to queues. This ensures no new requests enter the system while existing ones are processing.
     */
    public static final Priority TRAFFIC = new Priority(100);

    /** Stops application logic. */
    public static final Priority WORKERS = new Priority(200);

    /**
     * Releases infrastructure. Use this to close JDBC connections, close files, or shut down thread
     * pools.
     *
     * <p><b>Warning:</b> Do not use this for application logic. If the DB closes while your workers
     * are still finishing tasks, data loss may occur.
     *
     * <p>This is the default priority if none is specified.
     */
    public static final Priority RESOURCES = new Priority(300);

    /**
     * The last tasks to run. Use this for final logging, releasing PID files, or simple metrics
     * flushing.
     */
    public static final Priority LOWEST = new Priority(Integer.MAX_VALUE);

    /** The default priority for general cleanup shutdown tasks. */
    public static final Priority DEFAULT = RESOURCES;

    @Override
    public int compareTo(Priority other) {
      return Integer.compare(value, other.value);
    }

    /**
     * Returns a priority whose value is the value of this priority MINUS the given delta, which
     * means a task with the new priority will run EARLIER.
     */
    public Priority earlier(int delta) {
      return new Priority(value - delta);
    }

    /**
     * Returns a priority whose value is the value of this priority PLUS the given delta, which
     * means a task with the new priority will run LATER.
     */
    public Priority later(int delta) {
      return new Priority(value + delta);
    }
  }

  private static class Holder {
    private static final ShutdownHookManager INSTANCE = new ShutdownHookManager(/* enable= */ true);
  }

  public static ShutdownHookManager getInstance() {
    return Holder.INSTANCE;
  }

  @GuardedBy("hooks")
  private final Set<Hook> hooks = new LinkedHashSet<>();

  /** Whether {@link #shutdown()} or the Java shutdown hook has been called. */
  @GuardedBy("hooks")
  private boolean hasShutdown;

  @VisibleForTesting
  ShutdownHookManager(boolean enable) {
    if (enable) {
      Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook-manager"));
    }
  }

  /** See {@link #addShutdownHook(Task, String, Priority)}. */
  @CanIgnoreReturnValue
  public Handle addShutdownHook(Task task, String name) {
    return addShutdownHook(task, name, Priority.DEFAULT);
  }

  /**
   * Submits a shutdown task to be executed when the application stops. In detail, when the JVM runs
   * shutdown hooks, or when {@link #shutdown()} is called (the one which happens first).
   *
   * <p>If shutdown has already begun (either manually via {@link #shutdown()} or by the JVM), newly
   * submitted tasks will be ignored and a warning will be logged.
   */
  @CanIgnoreReturnValue
  public Handle addShutdownHook(Task task, String name, Priority priority) {
    Hook hook = new Hook(checkNotNull(task), checkNotNull(name), checkNotNull(priority));
    synchronized (hooks) {
      if (hasShutdown) {
        logger.atWarning().log(
            "Shutdown task [%s] cannot be added because shutdown() has been called", name);
      } else {
        hooks.add(hook);
      }
    }
    return hook;
  }

  /**
   * Explicitly triggers the execution of submitted shutdown tasks. The submitted shutdown tasks are
   * guaranteed to run exactly once, no matter triggered by this method or by the JVM.
   */
  public void shutdown() {
    // Gets a snapshot of submitted shutdown tasks and sorts them.
    List<Hook> sortedHooks;
    synchronized (hooks) {
      if (hasShutdown) {
        return;
      }
      hasShutdown = true;
      sortedHooks = copyAndSortHooks();
    }

    // Executes all shutdown tasks sequentially.
    for (Hook hook : sortedHooks) {
      logger.atInfo().log("Starting shutdown task [%s]", hook.name);
      try {
        hook.task.run();
        logger.atInfo().log("Finished shutdown task [%s]", hook.name);
      } catch (Exception | Error e) {
        if (isInterruption(e)) {
          logger.atInfo().log("Shutdown task [%s] is interrupted", hook.name);
          Thread.currentThread().interrupt();
        } else {
          logger.atWarning().withCause(e).log("Shutdown task [%s] throws an exception", hook.name);
        }
      }
    }
  }

  /** Sorts shutdown tasks by #1 priority (lower value first) and #2 submission order. */
  @GuardedBy("hooks")
  private List<Hook> copyAndSortHooks() {
    List<Hook> result = new ArrayList<>(hooks);
    result.sort(Comparator.comparing(hook -> hook.priority));
    return result;
  }

  private static boolean isInterruption(Throwable e) {
    return e instanceof InterruptedException
        || e instanceof ClosedByInterruptException
        || e instanceof InterruptedIOException;
  }

  /**
   * A shutdown hook.
   *
   * @implSpec do not implement {@link #equals(Object)} or {@link #hashCode()} because identity
   *     equality is necessary for {@link Handle#remove()}.
   */
  private class Hook implements Handle {
    private final Task task;
    private final String name;
    private final Priority priority;

    private Hook(Task task, String name, Priority priority) {
      this.task = task;
      this.name = name;
      this.priority = priority;
    }

    @Override
    public void remove() {
      synchronized (hooks) {
        hooks.remove(this);
      }
    }
  }
}
