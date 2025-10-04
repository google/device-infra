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

import java.time.Instant;
import java.util.function.Supplier;

/** Keeps track of when Instrumentation tests were run. */
public interface TestTimeTracker {
  /**
   * Returns a {@link TestTimingData} instance with start and end times represented by {@link
   * Timestamp} protos.
   */
  TestTimingData getTestTimingData();

  /** Call when a test has started. Sets the start time in the tracker to now. */
  void testStart();

  /** Call when a test has finished. Sets the end time in the tracker to now. */
  void testEnd();

  static TestTimeTracker create() {
    return new TestTimeTrackerImpl(Instant::now);
  }

  static TestTimeTracker create(Supplier<Instant> now) {
    return new TestTimeTrackerImpl(now);
  }
}
