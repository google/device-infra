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

package com.google.devtools.deviceaction.common.utils;

import static com.google.devtools.deviceaction.common.utils.Conditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A utility class to monitor the timeout. */
public class TimeoutMonitor implements AutoCloseable {

  private final Stopwatch stopwatch;

  private final Duration timeout;

  private final AtomicReference<Duration> totalElapsed = new AtomicReference<>(Duration.ZERO);
  private final AtomicInteger checkedTime = new AtomicInteger(0);

  private TimeoutMonitor(Stopwatch stopwatch, Duration timeout) {
    this.stopwatch = stopwatch;
    this.timeout = timeout;
  }

  public static TimeoutMonitor createAndStart(Duration timeout) {
    TimeoutMonitor timeoutMonitor = new TimeoutMonitor(Stopwatch.createUnstarted(), timeout);
    timeoutMonitor.start();
    return timeoutMonitor;
  }

  private void start() {
    stopwatch.start();
  }

  /**
   * Gets elapsed time since the last check and updates the total elapsed and checked time.
   *
   * <p>The diff from {@link TimeoutMonitor#getElapsedSinceLastCheck()} is that it doesn't throw
   * exception if timeout.
   */
  public Duration getElapsedSinceLastCheckSafely() throws DeviceActionException {
    checkState(stopwatch.isRunning(), ErrorType.INFRA_ISSUE, "Stopwatch is not running.");
    Duration elapsed = stopwatch.elapsed();
    Duration previous = totalElapsed.getAndSet(elapsed);
    checkedTime.getAndIncrement();
    return elapsed.minus(previous);
  }

  /**
   * Gets elapsed time since the last check and updates the last check time.
   *
   * @throws DeviceActionException if timeout.
   */
  public Duration getElapsedSinceLastCheck() throws DeviceActionException {
    Duration ret = getElapsedSinceLastCheckSafely();
    if (totalElapsed.get().compareTo(timeout) > 0) {
      throw new DeviceActionException("TIMEOUT", ErrorType.DEPENDENCY_ISSUE, "Timeout!");
    }
    return ret;
  }

  /** Gets remaining timeout if not timeout yet. */
  public Duration getRemainingTimeout() throws DeviceActionException {
    Duration unused = getElapsedSinceLastCheck();
    return timeout.minus(totalElapsed.get());
  }

  @Override
  public void close() {
    stopwatch.stop();
  }

  @VisibleForTesting
  int getCheckedNum() {
    return checkedTime.get();
  }
}
