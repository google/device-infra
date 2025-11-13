package com.google.devtools.mobileharness.platform.android.instrumentation.parser

/**
 * Results reported at the end of an `am instrument` command.
 *
 * The `code`, also called Session Result Code, reported by `am instrument` is defined in AOSP
 * `frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java` as:
 * * -1: Success
 * * other: Failure
 */
data class InstrumentationResult(val code: Int? = null, val bundle: Map<String, String> = mapOf()) {
  val success
    get() = code == -1
}
