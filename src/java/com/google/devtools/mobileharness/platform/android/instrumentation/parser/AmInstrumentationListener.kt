package com.google.devtools.mobileharness.platform.android.instrumentation.parser
/**
 * Receives events during instrumentation runs.
 *
 * The order of calls is defined below. Calls in square brackets are optional.
 * * `instrumentationStarted`
 * * Zero or more of:
 * ```
 *    * `testStarted`
 *    * `testEnded`
 * ```
 * * `[instrumentationFailed]`
 * * `instrumentationEnded`
 */
interface AmInstrumentationListener {
  /**
   * Reports the start of an instrumentation.
   *
   * @param testCount Total number of tests in the instrumentation. For custom, non-test
   *
   * ```
   *     instrumentations the count is 0.
   * ```
   */
  fun instrumentationStarted(testCount: Int)

  /**
   * Reports the execution start of a test case.
   *
   * @param testIdentifier Identifies the started test case.
   */
  fun testStarted(testIdentifier: TestIdentifier)

  /**
   * Reports the execution end of a test case.
   *
   * @param testResult End result of the test case.
   */
  fun testEnded(testResult: TestResult)

  /**
   * Reports an instrumentation failed to complete due to a fatal error.
   *
   * @param errorMessage Describes the reason for the fatal error.
   */
  fun instrumentationFailed(errorMessage: String)

  /**
   * Reports the end of an instrumentation.
   *
   * @param instrumentationResult [InstrumentationResult] reported at the end of an
   *
   * ```
   *     instrumentation run.
   * ```
   */
  fun instrumentationEnded(instrumentationResult: InstrumentationResult)
}
