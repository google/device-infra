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

import com.google.auto.value.AutoValue;
import com.google.protobuf.Timestamp;

/**
 * Class for storing the start and end time for tests.
 *
 * <p>The sum of the two times in the TimeStamp is the time since epoch.
 */
@AutoValue
public abstract class TestTimingData {

  /** Milliseconds from epoch when test started. */
  public abstract long startTime();

  /** Milliseconds from epoch when test ended. */
  public abstract long endTime();

  public static TestTimingData create(long startTime, long endTime) {
    return new AutoValue_TestTimingData(startTime, endTime);
  }

  /** Milliseconds from epoch when test started, converted to {@link Timestamp}. */
  public Timestamp startTimeToProto() {
    return timeToProto(startTime());
  }

  /** Milliseconds from epoch when test ended, converted to {@link Timestamp}. */
  public Timestamp endTimeToProto() {
    return timeToProto(endTime());
  }

  /**
   * Converts milliseconds into a {@link Timestamp} proto.
   *
   * @param millis the number of milliseconds to convert
   * @return a {@link Timestamp} proto, where the sum of seconds (if converted to nanos) and nanos
   *     is (about) the total number of nanos since epoch
   */
  private static Timestamp timeToProto(long millis) {
    // The sum of the two times in the [Timestamp] is the time since epoch.
    return Timestamp.newBuilder()
        // 1 second is 1000 milliseconds
        .setSeconds(millis / 1000)
        // 1 millisecond is 1000000 nanoseconds
        .setNanos((int) ((millis % 1000) * 1000000))
        .build();
  }
}
