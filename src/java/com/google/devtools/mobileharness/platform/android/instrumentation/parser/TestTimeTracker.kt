package com.google.devtools.mobileharness.platform.android.instrumentation.parser

import com.google.common.flogger.FluentLogger
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps track of when Instrumentation tests were run. */
interface TestTimeTracker {
  /**
   * Returns a [TestTimingData] instance with start and end times represented by [Timestamp] protos.
   */
  val testTimingData: TestTimingData

  /** Call when a test has started. Sets the start time in the tracker to now. */
  fun testStart()

  /** Call when a test has finished. Sets the end time in the tracker to now. */
  fun testEnd()
}

internal class TestTimeTrackerImpl(private val now: () -> Instant) : TestTimeTracker {
  private var hasStarted = AtomicBoolean(false)
  private var hasEnded = AtomicBoolean(false)
  private var startTime = -1L
  private var endTime = -1L
  override val testTimingData: TestTimingData
    get() {
      require(hasStarted.get()) {
        "Called TestTimeTracker.testTimingData before TestTimeTracker.testStart()"
      }
      require(hasEnded.get()) {
        "Called TestTimeTracker.testTimingData before TestTimeTracker.testEnd()"
      }
      return TestTimingData(startTime = startTime, endTime = endTime)
    }

  override fun testStart() {
    require(!hasStarted.get()) { "Called TestTimeTracker.testStart() twice" }
    startTime = now().toEpochMilli()
    hasStarted.set(true)
  }

  override fun testEnd() {
    if (!hasStarted.get()) {
      logger
        .atWarning()
        .log(
          """
          |TestTimeTracker.testEnd() was called before TestTimeTracker.testStart(). The test may not
          |have run. Check the test logs for details."""
        )
      testStart()
    }
    require(!hasEnded.get()) { "Called TestTimeTracker.testEnd() twice" }
    endTime = now().toEpochMilli()
    hasEnded.set(true)
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}

/** Factory function for the default implementation of [TestTimeTracker]. */
@Suppress("FunctionName")
fun TestTimeTracker(now: () -> Instant = { Instant.now() }): TestTimeTracker =
  TestTimeTrackerImpl(now)
