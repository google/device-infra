package com.google.devtools.mobileharness.platform.android.testrunner

/**
 * {@code RepetitionInfo} can be used to inject information about the current repetition of a
 * repeated test annotated with {@code @RepeatedTest}.
 */
data class RepetitionInfo(
  /** The current repetition of the repeated test method. */
  @get:JvmName("currentRepetition") val currentRepetition: UInt,
  /** The total number of repetition of the repeated test method. */
  @get:JvmName("totalRepetitions") val totalRepetitions: UInt
)
