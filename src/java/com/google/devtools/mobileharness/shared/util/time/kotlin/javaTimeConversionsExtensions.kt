@file:Suppress("PreferKotlinApi") // Many of these extensions are PKA replacements.

package com.google.devtools.mobileharness.shared.util.time.kotlin

import com.google.devtools.mobileharness.shared.util.time.TimeUtils
import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import java.time.Instant

/**
 * Converts a protobuf [Timestamp] to a [java.time.Instant].
 *
 * **Note:** this API normalizes the `Timestamp` before converting, so invalid timestamps (according
 * to the proto specification) **are accepted** in accordance with the
 * [robustness principle](https://en.wikipedia.org/wiki/Robustness_principle). If you want to ensure
 * the timestamp is valid before converting, please use [Timestamps.isValid].
 */
fun Timestamp.toJavaInstant(): Instant = TimeUtils.toJavaInstant(this)

/**
 * Converts a protobuf [Duration] to a [java.time.Duration].
 *
 * **Note:** this API normalizes the `Duration` before converting, so invalid durations (according
 * to the proto specification) **are accepted** in accordance with the
 * [robustness principle](https://en.wikipedia.org/wiki/Robustness_principle). If you want to ensure
 * the duration is valid before converting, please use [Durations.isValid].
 */
fun Duration.toJavaDuration(): java.time.Duration = TimeUtils.toJavaDuration(this)

/**
 * Converts a [java.time.Instant] to a protobuf [Timestamp].
 *
 * @throws IllegalArgumentException if the given [java.time.Instant] cannot legally fit into
 *   [Timestamp]. See [Timestamps.isValid].
 */
fun Instant.toProtoTimestamp(): Timestamp = TimeUtils.toProtoTimestamp(this)

/**
 * Converts a [java.time.Duration] to a protobuf [Duration].
 *
 * @throws IllegalArgumentException if the given [java.time.Duration] cannot legally fit into a
 *   [Duration]. See [Durations.isValid].
 */
fun java.time.Duration.toProtoDuration(): Duration = TimeUtils.toProtoDuration(this)
