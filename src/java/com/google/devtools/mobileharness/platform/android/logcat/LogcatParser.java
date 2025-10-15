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

package com.google.devtools.mobileharness.platform.android.logcat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to parse logcat lines in the threadtime format. */
public final class LogcatParser {

  private static final Pattern LOGCAT_THREADTIME_PATTERN =
      Pattern.compile(
          // timestamp
          "^(?<month>\\d{2})-(?<day>\\d{2}) (?<time>\\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+"
              +
              // pid tid log_level
              "(?<pid>\\d+)\\s+(?<tid>\\d+)\\s+(?<level>[VDIWEFS])\\s"
              +
              // tag: message
              "(?<tag>.{8,}?):\\s(?<message>.*)$");

  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  private LogcatParser() {}

  public static Optional<LogcatLine> parse(String line) {

    Matcher matcher = LOGCAT_THREADTIME_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    LocalDate date =
        LocalDate.of(
            LocalDate.now(ZoneId.systemDefault()).getYear(),
            Integer.parseInt(matcher.group("month")),
            Integer.parseInt(matcher.group("day")));
    LocalTime time = LocalTime.parse(matcher.group("time"), TIME_FORMATTER);
    LocalDateTime dateTime = time.atDate(date);

    Level level = Level.from(matcher.group("level"));

    return Optional.of(
        new LogcatLine(
            dateTime,
            level,
            matcher.group("tag").trim(), /* Tags are padded with spaces if tag length < 8 */
            Integer.parseInt(matcher.group("pid")),
            Integer.parseInt(matcher.group("tid")),
            matcher.group("message")));
  }

  /** Log levels indicated in each logcat line. */
  public enum Level {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL,
    LEVEL_UNSPECIFIED;

    public static Level from(String level) {
      return switch (level) {
        case "V" -> VERBOSE;
        case "D" -> DEBUG;
        case "I" -> INFO;
        case "W" -> WARNING;
        case "E" -> ERROR;
        case "F" -> FATAL;
        default -> LEVEL_UNSPECIFIED;
      };
    }
  }

  /** Record class indicating individual components of the logcat message in threadtime format. */
  public record LogcatLine(
      LocalDateTime timestamp, Level level, String tag, int pid, int tid, String message) {}
}
