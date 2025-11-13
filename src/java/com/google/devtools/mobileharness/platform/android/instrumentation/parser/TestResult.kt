package com.google.devtools.mobileharness.platform.android.instrumentation.parser

import java.time.Instant

/**
 * The end result of an individual test case.
 *
 * A test result can be in one of the following final states:
 * * `status = PASSED` - The test completed successfully.
 * * `status = FAILED` - The test completed with a failure. `stackTrace` contains the stack trace
 *   where the failure occurred. E.g. this covers assertion failures or tests throwing exceptions.
 * * `status = IGNORED` - Two possible sub states:
 *     * `stackTrace` is set - The test completed with an assumption failure. E.g. JUnit `assume*()`
 *       methods.
 *     * `stackTrace` is not set - The test was ignored. E.g. JUnit `@Ignore` was present on the
 *       test method.
 * * `status = ERROR` - A fatal error occurred. E.g. the test case did not complete because the
 *   instrumentation crashed.
 */
data class TestResult(
  /** Test case this end result is for. */
  val testIdentifier: TestIdentifier,
  /** Final status of the test. One of `PASSED`, `FAILED`, `IGNORED`, or `ERROR`. */
  val status: TestStatus,
  /** Start time of the test execution. Recorded by the parser. */
  val startTime: Instant,
  /** End time of the test execution. Recorded by the parser. */
  val endTime: Instant,
  /** In case of failures (assertions, exceptions, assumptions) the stack trace of the failure. */
  val stackTrace: String? = null,
  /** Additional status information written by the instrumentation. */
  val statusBundle: Map<String, String> = mapOf(),
)
