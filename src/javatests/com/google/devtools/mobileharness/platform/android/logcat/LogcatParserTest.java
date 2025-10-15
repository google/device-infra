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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.Level;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogcatParserTest {

  private static final int YEAR = LocalDate.now(ZoneId.systemDefault()).getYear();

  @Test
  public void testParse_validLine_infoLevel() {
    String line = "10-13 12:54:02.123  1234  5678 I ActivityManager: Some message";
    LogcatLine expected =
        new LogcatLine(
            LocalDateTime.of(YEAR, 10, 13, 12, 54, 2, 123_000_000),
            Level.INFO,
            "ActivityManager",
            1234,
            5678,
            "Some message");

    assertThat(LogcatParser.parse(line)).hasValue(expected);
  }

  @Test
  public void testParse_validLine_warningLevel_shortTag() {
    // Tags shorter than 8 chars are padded with space up to 8 chars.
    // 'chatty' is 6 chars, padded with 2 spaces -> 'chatty  '.
    String line = "01-18 11:21:02.083  1333  1333 W chatty  : uid=1000(system) expire 1 line";
    LogcatLine expected =
        new LogcatLine(
            LocalDateTime.of(YEAR, 1, 18, 11, 21, 2, 83_000_000),
            Level.WARNING,
            "chatty",
            1333,
            1333,
            "uid=1000(system) expire 1 line");

    assertThat(LogcatParser.parse(line)).hasValue(expected);
  }

  @Test
  public void testParse_validLine_allLevels() {
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 V TestTag : message").get().level())
        .isEqualTo(Level.VERBOSE);
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 D TestTag : message").get().level())
        .isEqualTo(Level.DEBUG);
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 I TestTag : message").get().level())
        .isEqualTo(Level.INFO);
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 W TestTag : message").get().level())
        .isEqualTo(Level.WARNING);
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 E TestTag : message").get().level())
        .isEqualTo(Level.ERROR);
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 F TestTag : message").get().level())
        .isEqualTo(Level.FATAL);
  }

  @Test
  public void testParse_validLine_silentLevel() {
    // 'S' level is parsed, but mapped to LEVEL_UNSPECIFIED by Level.from()
    assertThat(
            LogcatParser.parse("10-13 12:54:02.123  1234  5678 S TestTag : message").get().level())
        .isEqualTo(Level.LEVEL_UNSPECIFIED);
  }

  @Test
  public void testParse_invalidLine_returnsNull() {
    assertThat(LogcatParser.parse("This is not a valid logcat line")).isEmpty();
    assertThat(LogcatParser.parse("10-13 12:54:02.123  1234  I Tag: Msg")).isEmpty(); // missing tid
  }

  @Test
  public void testParse_lineWithEmptyMessage() {
    String line = "10-13 12:54:02.123  1234  5678 I TestTag : ";
    LogcatLine expected =
        new LogcatLine(
            LocalDateTime.of(YEAR, 10, 13, 12, 54, 2, 123_000_000),
            Level.INFO,
            "TestTag",
            1234,
            5678,
            "");

    assertThat(LogcatParser.parse(line)).hasValue(expected);
  }

  @Test
  public void testParse_tagWithSpaces() {
    String line = "10-13 12:54:02.123  1234  5678 I My Tag  : message with spaces";
    LogcatLine expected =
        new LogcatLine(
            LocalDateTime.of(YEAR, 10, 13, 12, 54, 2, 123_000_000),
            Level.INFO,
            "My Tag",
            1234,
            5678,
            "message with spaces");

    assertThat(LogcatParser.parse(line)).hasValue(expected);
  }
}
