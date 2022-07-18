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

package com.google.devtools.deviceinfra.shared.util.command;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.deviceinfra.shared.util.command.Timeout.deadline;
import static com.google.devtools.deviceinfra.shared.util.command.Timeout.fixed;

import com.google.common.time.TimeSource;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TimeoutTest {

  private static final Instant INSTANT = TimeSource.system().now();

  @Test
  public void timeout() {
    assertThat(fixed(Duration.ofMillis(2L)).getPeriod().orElseThrow(AssertionError::new))
        .isEqualTo(Duration.ofMillis(2L));
    assertThat(fixed(Duration.ofMillis(2L)).getPeriod().orElseThrow(AssertionError::new))
        .isEqualTo(Duration.ofMillis(2L));

    assertThat(deadline(INSTANT).getDeadline().orElseThrow(AssertionError::new)).isEqualTo(INSTANT);
    assertThat(
            deadline(Instant.parse("2007-12-03T10:15:30.00Z"))
                .getDeadline()
                .orElseThrow(AssertionError::new))
        .isEqualTo(Instant.parse("2007-12-03T10:15:30.00Z"));

    assertThat(
            deadline(INSTANT)
                .withFixed(Duration.ofMillis(2L))
                .getPeriod()
                .orElseThrow(AssertionError::new))
        .isEqualTo(Duration.ofMillis(2L));

    assertThat(
            fixed(Duration.ofMillis(2L))
                .withDeadline(INSTANT)
                .getDeadline()
                .orElseThrow(AssertionError::new))
        .isEqualTo(INSTANT);
    assertThat(
            fixed(Duration.ofMillis(2L))
                .withDeadline(Instant.parse("2007-12-03T10:15:30.00Z"))
                .getDeadline()
                .orElseThrow(AssertionError::new))
        .isEqualTo(Instant.parse("2007-12-03T10:15:30.00Z"));

    assertThat(
            Timeout.of(Duration.ofMillis(2L), INSTANT).getPeriod().orElseThrow(AssertionError::new))
        .isEqualTo(Duration.ofMillis(2L));
    assertThat(
            Timeout.of(Duration.ofMillis(2L), INSTANT)
                .getDeadline()
                .orElseThrow(AssertionError::new))
        .isEqualTo(INSTANT);
    assertThat(
            Timeout.of(Duration.ofMillis(2L), Instant.parse("2007-12-03T10:15:30.00Z"))
                .getDeadline()
                .orElseThrow(AssertionError::new))
        .isEqualTo(Instant.parse("2007-12-03T10:15:30.00Z"));
  }

  @Test
  public void getRemainingTest() {
    assertThat(fixed(Duration.ofMillis(2L)).getRemainingTime()).isEqualTo(Duration.ofMillis(2L));
    assertThat(
            deadline(TimeSource.system().now().plus(Duration.ofMinutes(1L)))
                .getRemainingTime()
                .compareTo(Duration.ofMinutes(1L)))
        .isAtMost(0);
    assertThat(
            fixed(Duration.ofSeconds(3L))
                .withDeadline(TimeSource.system().now().plusSeconds(4L))
                .getRemainingTime())
        .isEqualTo(Duration.ofSeconds(3L));
  }
}
