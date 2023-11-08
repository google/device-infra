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

package com.google.devtools.mobileharness.shared.util.time;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.time.Sleeper.defaultSleeper;
import static com.google.devtools.mobileharness.shared.util.time.Sleeper.noOpSleeper;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import junit.framework.TestCase;

/** Unit tests for {@link Sleeper}. */
public class SleeperTest extends TestCase {

  public void testNegativeDuration() throws Exception {
    Duration negativeDuration = Duration.ofMillis(-1);
    assertThrows(IllegalArgumentException.class, () -> defaultSleeper().sleep(negativeDuration));
    assertThrows(IllegalArgumentException.class, () -> noOpSleeper().sleep(negativeDuration));
  }

  public void testSleepDuration() throws Exception {
    for (Duration sleepTime :
        // we don't want to test anything larger than 1 second for the "real" sleeper
        asList(Duration.ZERO, Duration.ofNanos(1), Duration.ofMillis(1), Duration.ofSeconds(1))) {
      assertSleepForDuration(sleepTime);
    }
  }

  public void testSleepDurationsMax() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            try {
              Sleeper.defaultSleeper().sleep(Duration.ofDays(1));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        };
    Future<?> result = executor.submit(runnable);
    Thread.sleep(5000); // presumably long enough for it to throw if it's going to throw
    result.cancel(true);
    assertThat(result.isCancelled()).isTrue(); // cancelled, rather than failed
  }

  private static void assertSleepForDuration(Duration sleepTime) throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    defaultSleeper().sleep(sleepTime);
    // we can't use stopwatch.elapsed() here because that method isn't available under Android
    Duration elapsed = stopwatch.elapsed();
    assertThat(elapsed).isAtLeast(sleepTime);
  }

  public void testNoOpSleepDuration() throws Exception {
    for (Duration sleepTime :
        asList(
            Duration.ZERO,
            Duration.ofNanos(1),
            Duration.ofMillis(1),
            Duration.ofSeconds(1),
            Duration.ofMinutes(1),
            Duration.ofHours(1),
            Duration.ofDays(1))) {
      assertNoSleepForDuration(sleepTime);
    }
  }

  private static void assertNoSleepForDuration(Duration sleepTime) throws InterruptedException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    noOpSleeper().sleep(sleepTime);
    // we can't use stopwatch.elapsed() here because that method isn't available under Android
    Duration elapsed = stopwatch.elapsed();
    // we could probably make this bound even tighter, but then the test might become flaky
    assertThat(elapsed).isAtMost(Duration.ofSeconds(1));
  }
}
