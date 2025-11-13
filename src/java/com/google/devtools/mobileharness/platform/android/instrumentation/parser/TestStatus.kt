package com.google.devtools.mobileharness.platform.android.instrumentation.parser

enum class TestStatus {
  // Actual test suite/case ran successfully but there were tests that failed.
  FAILED,
  // Actual test suite/case ran successfully and all the tests passed.
  PASSED,
  // Assumption failure or test was filtered intentionally.
  IGNORED,
  // There was an error executing the test. This could be because of a user
  // configuration issue, an infrastructure issue or other. This should
  // always be accompanied by a platform error that describes the error.
  ERROR,
}
