package com.google.devtools.mobileharness.platform.android.instrumentation.parser

import com.google.protobuf.Timestamp

/**
 * Class for storing the start and end time for tests.
 *
 * The sum of the two times in the TimeStamp is the time since epoch.
 */
data class TestTimingData(
  /** Milliseconds from epoch when test started. */
  val startTime: Long,
  /** Milliseconds from epoch when test ended. */
  val endTime: Long,
) {
  /** Milliseconds from epoch when test started, converted to [Timestamp]. */
  val startTimeToProto: Timestamp
    get() = timeToProto(startTime)

  /** Milliseconds from epoch when test ended, converted to [Timestamp]. */
  val endTimeToProto: Timestamp
    get() = timeToProto(endTime)

  private companion object {
    /**
     * Converts milliseconds into a [Timestamp] proto.
     *
     * @param millis the number of milliseconds to convert
     * @return a [Timestamp] proto, where the sum of seconds (if converted to nanos) and nanos is
     *   (about) the total number of nanos since epoch
     */
    fun timeToProto(millis: Long): Timestamp {
      // The sum of the two times in the [Timestamp] is the time since epoch.
      return with(Timestamp.newBuilder()) {
        // 1 second is 1000 milliseconds
        seconds = millis / 1000
        // 1 millisecond is 1000000 nanoseconds
        nanos = ((millis % 1000) * 1000000).toInt()
        build()
      }
    }
  }
}
