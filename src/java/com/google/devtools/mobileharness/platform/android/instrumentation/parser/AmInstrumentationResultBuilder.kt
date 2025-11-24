package com.google.devtools.mobileharness.platform.android.instrumentation.parser

import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.Error
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestCase
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult as ProtoTestResult
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus as ProtoTestStatus
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteMetaData
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult
import com.google.devtools.mobileharness.shared.util.time.kotlin.toProtoTimestamp

/** A [AmInstrumentationListener] that builds a [TestSuiteResult] from instrumentation events. */
class AmInstrumentationResultBuilder(private val testSuiteResultBuilder: TestSuiteResult.Builder) :
  AmInstrumentationListener {

  override fun instrumentationStarted(testCount: Int) {
    testSuiteResultBuilder.setTestSuiteMetaData(
      TestSuiteMetaData.newBuilder().setScheduledTestCaseCount(testCount)
    )
  }

  override fun testStarted(testIdentifier: TestIdentifier) {}

  override fun testEnded(testResult: TestResult) {
    val resultBuilder =
      ProtoTestResult.newBuilder()
        .setTestCase(
          TestCase.newBuilder()
            .setTestClass(testResult.testIdentifier.testClass)
            .setTestPackage(testResult.testIdentifier.testPackage)
            .setTestMethod(testResult.testIdentifier.testMethod)
            .setStartTime(testResult.startTime.toProtoTimestamp())
            .setEndTime(testResult.endTime.toProtoTimestamp())
        )
        .setTestStatus(
          when (testResult.status) {
            TestStatus.PASSED -> ProtoTestStatus.PASSED
            TestStatus.FAILED -> ProtoTestStatus.FAILED
            TestStatus.IGNORED -> ProtoTestStatus.IGNORED
            TestStatus.ERROR -> ProtoTestStatus.ERROR
          }
        )
    testResult.stackTrace?.let {
      resultBuilder.setError(
        Error.newBuilder()
          .setErrorMessage(it.lineSequence().first())
          .setErrorType(it.substringAfter("Caused by: ").substringBefore(":"))
          .setStackTrace(it)
      )
    }
    testSuiteResultBuilder.addTestResult(resultBuilder.build())
  }

  override fun instrumentationFailed(errorMessage: String) {
    testSuiteResultBuilder.testStatus = ProtoTestStatus.FAILED
  }

  override fun instrumentationEnded(instrumentationResult: InstrumentationResult) {
    testSuiteResultBuilder.testStatus =
      if (instrumentationResult.success) {
        ProtoTestStatus.PASSED
      } else {
        ProtoTestStatus.FAILED
      }
  }
}
