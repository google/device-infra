package com.google.devtools.mobileharness.platform.android.testrunner

import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * {@code @RepeatedTest} is an annotation used to indicate that the annotated method is a test
 * template method that should be repeated a {@linkplain #repetitions given number of times}.
 */
@Retention(RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RepeatedTest(
  /** The number of repetitions. Must be greater than zero. */
  val repetitions: UInt
)
