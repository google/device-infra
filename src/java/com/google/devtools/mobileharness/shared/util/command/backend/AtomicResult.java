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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Monitor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** Atomically stores the result of a command. */
final class AtomicResult {
  private final Command command;
  private final Runnable kill;
  private final Monitor monitor = new Monitor();

  @GuardedBy("monitor")
  private CommandResult result;

  @GuardedBy("monitor")
  private boolean failed;

  @GuardedBy("monitor")
  private final Set<ResultFuture> futures = new HashSet<>();

  private final Monitor.Guard processComplete =
      new Monitor.Guard(monitor) {
        @Override
        public boolean isSatisfied() {
          return result != null;
        }
      };

  AtomicResult(Command command, Runnable kill) {
    this.command = checkNotNull(command);
    this.kill = checkNotNull(kill);
  }

  void complete(CommandResult result) {
    checkNotNull(result, "result");
    Set<ResultFuture> futuresCopy;
    CommandFailureException ex = null;
    monitor.enter();
    try {
      checkState(this.result == null, "result already set");
      this.result = result;
      failed = !command.successCondition().test(result);
      if (failed) {
        ex = new CommandFailureException(command, result);
      }
      futuresCopy = new HashSet<>(futures);
      futures.clear();
    } finally {
      monitor.leave();
    }

    if (ex == null) {
      for (ResultFuture future : futuresCopy) {
        future.set(result);
      }
    } else {
      for (ResultFuture future : futuresCopy) {
        future.setException(ex);
      }
    }
  }

  boolean isComplete() {
    monitor.enter();
    try {
      return result != null;
    } finally {
      monitor.leave();
    }
  }

  Optional<CommandResult> get() throws CommandFailureException {
    monitor.enter();
    try {
      return Optional.ofNullable(getOrThrow());
    } finally {
      monitor.leave();
    }
  }

  CommandResult await() throws InterruptedException, CommandFailureException {
    monitor.enterWhen(processComplete);
    try {
      return getOrThrow();
    } finally {
      monitor.leave();
    }
  }

  CommandResult await(Duration timeout)
      throws InterruptedException, CommandFailureException, TimeoutException {
    if (monitor.enterWhen(processComplete, timeout.toNanos(), NANOSECONDS)) {
      try {
        return getOrThrow();
      } finally {
        monitor.leave();
      }
    }
    throw new TimeoutException(String.format("%s did not complete after %s", command, timeout));
  }

  ListenableFuture<CommandResult> future() {
    monitor.enter();
    try {
      if (failed) {
        return immediateFailedFuture(new CommandFailureException(command, result));
      } else if (result != null) {
        return immediateFuture(result);
      } else {
        ResultFuture future = new ResultFuture();
        futures.add(future);
        return future;
      }
    } finally {
      monitor.leave();
    }
  }

  @GuardedBy("monitor")
  private CommandResult getOrThrow() throws CommandFailureException {
    if (failed) {
      throw new CommandFailureException(command, result);
    }
    return result;
  }

  private class ResultFuture extends AbstractFuture<CommandResult> {
    /* Expose this method to the outer class */
    @Override
    @CanIgnoreReturnValue
    protected boolean set(CommandResult result) {
      return super.set(result);
    }

    /* Expose this method to the outer class */
    @Override
    @CanIgnoreReturnValue
    protected boolean setException(Throwable ex) {
      return super.setException(ex);
    }

    /** See documentation for {@link AbstractFuture#cancel(boolean)}. */
    @Override
    protected void afterDone() {
      if (wasInterrupted()) {
        kill.run();
      }
    }
  }
}
