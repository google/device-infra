package com.google.devtools.mobileharness.platform.android.testrunner

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import java.lang.reflect.Method
import org.junit.Test
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/**
 * A customer runner extends {@link AndroidJUnit4ClassRunner} and supports test methods annotated
 * with {@link RepeatedTest}.
 */
class AndroidRepeatedTestRunner(testClazz: Class<*>) : AndroidJUnit4ClassRunner(testClazz) {

  override fun validateTestMethods(errorsReturned: MutableList<Throwable>) {
    testClass.getAnnotatedMethods(Test::class.java).forEach { testMethod ->
      val repeatedTestAnnotation = testMethod.method.getAnnotation(RepeatedTest::class.java)
      if (repeatedTestAnnotation == null) {
        // This replicates the validation from parent BlockJUnit4ClassRunner#validateTestMethods
        testMethod.validatePublicVoidNoArg(/* isStatic= */ false, errorsReturned)
      } else {
        testMethod.validatePublicVoid(/* isStatic= */ false, errorsReturned)
        // Below are for validation related with the annotation RepeatedTest
        errorsReturned.addAll(
          validateRepeatedTestAnnotation(testMethod.method, repeatedTestAnnotation)
        )
        errorsReturned.addAll(validateRepeatedTestMethodParameters(testMethod.method))
      }
    }
  }

  private fun validateRepeatedTestAnnotation(
    testMethod: Method,
    repeatedTestAnnotation: RepeatedTest
  ): List<Throwable> {
    val errors = mutableListOf<Throwable>()
    if (repeatedTestAnnotation.repetitions == 0U) {
      errors.add(
        IllegalArgumentException(
          "Value of annotation ${RepetitionInfo::class.java.getSimpleName()} must be greater than" +
            " zero in test method ${testMethod.name}",
        )
      )
    }
    return errors.toList()
  }

  private fun validateRepeatedTestMethodParameters(testMethod: Method): List<Throwable> {
    val methodParameterTypes = testMethod.getParameterTypes()
    val errors = mutableListOf<Throwable>()
    if (methodParameterTypes.size > 1) {
      errors.add(
        IllegalStateException(
          "Only one param with type ${RepetitionInfo::class.java.name} can be specified in" +
            " the test method ${testMethod.name}, but found ${methodParameterTypes.size} params" +
            " specified",
        )
      )
    }

    if (methodParameterTypes.any { it != RepetitionInfo::class.java }) {
      errors.add(
        IllegalStateException(
          "The parameter type(s) in method ${testMethod.name} is not expected. Only type" +
            " ${RepetitionInfo::class.java.name} is allowed",
        )
      )
    }
    return errors.toList()
  }

  override fun methodInvoker(frameworkMethod: FrameworkMethod, test: Any): Statement {
    val repeatedFrameworkMethod: RepeatedFrameworkMethod =
      frameworkMethod as RepeatedFrameworkMethod
    // If no annotation RepeatedTest specified on the test method, rely on parent's implementation
    // to invoke the method.
    if (!repeatedFrameworkMethod.isRepeatedTest() ||
        repeatedFrameworkMethod.method.getParameterTypes().size == 0
    ) {
      return super.methodInvoker(frameworkMethod, test)
    }
    val parameters =
      if (repeatedFrameworkMethod.isRepeatedTest()) listOf(repeatedFrameworkMethod.repetitionInfo)
      else listOf()

    val statement =
      object : Statement() {
        override fun evaluate() {
          frameworkMethod.invokeExplosively(test, *parameters.toTypedArray())
        }
      }
    // This replicates the logic from AndroidJUnit4ClassRunner, which supports executing a test on
    // the application's main thread or UI thread.
    if (UiThreadStatement.shouldRunOnUiThread(frameworkMethod)) {
      return UiThreadStatement(statement, true)
    }
    return statement
  }

  override fun computeTestMethods(): List<FrameworkMethod> {
    return testClass.getAnnotatedMethods(Test::class.java).flatMap {
      generateRepeatedTestMethodsIfNeeded(it)
    }
  }

  private fun generateRepeatedTestMethodsIfNeeded(
    initialMethod: FrameworkMethod
  ): List<FrameworkMethod> {
    val repeatedTestAnnotation: RepeatedTest? =
      initialMethod.method.getAnnotation(RepeatedTest::class.java)
    if (repeatedTestAnnotation == null) {
      return listOf(RepeatedFrameworkMethod(method = initialMethod.method))
    }

    val testMethods = mutableListOf<FrameworkMethod>()
    val totalRepetitions = repeatedTestAnnotation.repetitions
    for (currentRepetition in 1U..totalRepetitions) {
      testMethods.add(
        RepeatedFrameworkMethod(
          method = initialMethod.method,
          repetitionInfo =
            RepetitionInfo(
              currentRepetition = currentRepetition,
              totalRepetitions = totalRepetitions
            )
        )
      )
    }
    return testMethods.toList()
  }

  /** Implementation of a JUnit FrameworkMethod where the name is overridden for repeated test. */
  private data class RepeatedFrameworkMethod
  @JvmOverloads
  constructor(private val method: Method, val repetitionInfo: RepetitionInfo? = null) :
    FrameworkMethod(method) {

    fun isRepeatedTest(): Boolean {
      return repetitionInfo != null
    }

    override fun getName(): String {
      val name = super.getName()
      return if (isRepeatedTest()) "$name[${repetitionInfo!!.currentRepetition - 1U}]" else name
    }
  }
}
