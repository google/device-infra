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
import static com.google.devtools.deviceaction.common.utils.TimeUtils.isPositive;

import com.google.common.base.Stopwatch;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import java.time.Duration;

/** A utility class to get remaining time out. */
public class TimeoutMonitor implements AutoCloseable {

  private final Stopwatch stopwatch;

  private final Duration timeout;

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

  /** Gets remaining timeout if not timeout yet. */
  public Duration getRemainingTimeout() throws DeviceActionException {
    checkState(stopwatch.isRunning(), ErrorType.INFRA_ISSUE, "Stopwatch is not running.");
    Duration remain = timeout.minus(stopwatch.elapsed());
    if (!isPositive(remain)) {
      throw new DeviceActionException("TIMEOUT", ErrorType.DEPENDENCY_ISSUE, "Timeout!");
    }
    return remain;
  }

  @Override
  public void close() {
    stopwatch.stop();
  }
}
