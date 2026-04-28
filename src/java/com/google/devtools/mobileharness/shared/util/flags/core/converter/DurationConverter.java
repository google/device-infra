/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.util.flags.core.converter;

import static com.google.common.base.CharMatcher.whitespace;
import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converter for {@link Duration}. */
public class DurationConverter implements ITypeConverter<Duration> {

  private static final ImmutableMap<String, Duration> ABBREVIATION_TO_DURATION =
      new ImmutableMap.Builder<String, Duration>()
          .put("d", Duration.ofDays(1))
          .put("h", Duration.ofHours(1))
          .put("m", Duration.ofMinutes(1))
          .put("s", Duration.ofSeconds(1))
          .put("ms", Duration.ofMillis(1))
          .put("us", Duration.of(1, ChronoUnit.MICROS))
          .put("ns", Duration.ofNanos(1))
          .buildOrThrow();
  private static final String UNIT_PATTERN_STRING =
      "(?x) (?<whole>[0-9]+)? (?<frac>\\.[0-9]*)? (?<unit>d|h|ms?|s|us|ns)";
  private static final Pattern UNIT_PATTERN = Pattern.compile(UNIT_PATTERN_STRING);
  private static final CharMatcher ASCII_DIGIT = CharMatcher.inRange('0', '9');

  @Override
  public Duration convert(String value) {
    checkFormat(value != null, "input value cannot be null");
    checkFormat(!value.isEmpty(), "input value cannot be empty");
    checkFormat(!value.equals("-"), "input value cannot be '-'");
    checkFormat(!value.equals("+"), "input value cannot be '+'");

    value = whitespace().trimFrom(value);

    if ("0".equals(value)) {
      return Duration.ZERO;
    }

    Duration duration = Duration.ZERO;
    boolean negative = value.startsWith("-");
    boolean explicitlyPositive = value.startsWith("+");
    int index = negative || explicitlyPositive ? 1 : 0;
    Matcher matcher = UNIT_PATTERN.matcher(value);
    while (matcher.find(index) && matcher.start() == index) {
      // Prevent strings like ".s" or "d" by requiring at least one digit.
      checkFormat(ASCII_DIGIT.matchesAnyOf(matcher.group(0)), "expected at least one digit");
      try {
        String unit = matcher.group("unit");

        long whole = Long.parseLong(firstNonNull(matcher.group("whole"), "0"));
        Duration singleUnit = ABBREVIATION_TO_DURATION.get(unit);
        checkFormat(singleUnit != null, "invalid unit (%s)", unit);
        // TODO: Consider using saturated duration math here
        duration = duration.plus(singleUnit.multipliedBy(whole));

        long nanosPerUnit = singleUnit.toNanos();
        double frac = Double.parseDouble("0" + firstNonNull(matcher.group("frac"), ""));
        duration = duration.plus(Duration.ofNanos((long) (nanosPerUnit * frac)));
      } catch (ArithmeticException e) {
        throw new TypeConversionException("duration overflow: " + e.getMessage());
      }
      index = matcher.end();
    }
    checkFormat(index >= value.length(), "could not parse entire duration: %s", value);
    if (negative) {
      duration = duration.negated();
    }
    return duration;
  }

  @FormatMethod
  private static void checkFormat(
      boolean condition, @FormatString String errorMessageFormat, Object... args) {
    if (!condition) {
      throw new TypeConversionException(String.format(errorMessageFormat, args));
    }
  }
}
