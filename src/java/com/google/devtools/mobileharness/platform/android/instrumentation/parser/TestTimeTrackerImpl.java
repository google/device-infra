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

package com.google.devtools.mobileharness.platform.android.instrumentation.parser;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.flogger.FluentLogger;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** The default implementation of {@link TestTimeTracker}. */
class TestTimeTrackerImpl implements TestTimeTracker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Supplier<Instant> now;
  private final AtomicBoolean hasStarted = new AtomicBoolean(false);
  private final AtomicBoolean hasEnded = new AtomicBoolean(false);

  private long startTime = -1L;
  private long endTime = -1L;

  TestTimeTrackerImpl(Supplier<Instant> now) {
    this.now = now;
  }

  @Override
  public TestTimingData getTestTimingData() {
    checkArgument(
        hasStarted.get(),
        "Called TestTimeTracker.testTimingData before TestTimeTracker.testStart()");
    checkArgument(
        hasEnded.get(), "Called TestTimeTracker.testTimingData before TestTimeTracker.testEnd()");
    return TestTimingData.create(startTime, endTime);
  }

  @Override
  public void testStart() {
    checkArgument(!hasStarted.get(), "Called TestTimeTracker.testStart() twice");
    startTime = now.get().toEpochMilli();
    hasStarted.set(true);
  }

  @Override
  public void testEnd() {
    if (!hasStarted.get()) {
      logger.atWarning().log(
          "TestTimeTracker.testEnd() was called before TestTimeTracker.testStart(). The test may"
              + " not have run. Check the test logs for details.");
      testStart();
    }
    checkArgument(!hasEnded.get(), "Called TestTimeTracker.testEnd() twice");
    endTime = now.get().toEpochMilli();
    hasEnded.set(true);
  }
}
