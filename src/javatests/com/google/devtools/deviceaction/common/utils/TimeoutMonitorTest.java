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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TimeoutMonitorTest {

  @Test
  public void createAndStart_success() throws Exception {
    try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofMillis(300))) {
      // The compare precision is < 100 millis.
      Thread.sleep(/* millis= */ 101);
      assertThat(monitor.getElapsedSinceLastCheck().toMillis()).isGreaterThan(100);
      assertThat(monitor.getRemainingTimeout().toMillis()).isLessThan(200);
      assertThat(monitor.getCheckedNum()).isEqualTo(2);
      Thread.sleep(/* millis= */ 101);
      assertThat(monitor.getElapsedSinceLastCheck().toMillis()).isGreaterThan(100);
      assertThat(monitor.getRemainingTimeout().toMillis()).isLessThan(100);
      assertThat(monitor.getCheckedNum()).isEqualTo(4);
    }
  }

  @Test
  public void getRemainingTimeout_throwExceptionAfterTimeout() {
    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> {
              try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofMillis(100))) {
                Thread.sleep(/* millis= */ 200);
                monitor.getRemainingTimeout();
              }
            });
    assertThat(t.getErrorId().name()).isEqualTo("TIMEOUT");
  }

  @Test
  public void getElapsedSinceLastCheck_throwExceptionAfterTimeout() {
    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> {
              try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofMillis(100))) {
                Thread.sleep(/* millis= */ 200);
                monitor.getElapsedSinceLastCheck();
              }
            });
    assertThat(t.getErrorId().name()).isEqualTo("TIMEOUT");
  }

  @Test
  public void getElapsedSinceLastCheckSafely_noExceptionAfterTimeout() throws Exception {
    try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofMillis(100))) {
      Thread.sleep(/* millis= */ 200);
      Duration unused = monitor.getElapsedSinceLastCheckSafely();
    }
  }
}
