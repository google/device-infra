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
  public void getRemainingTimeout() throws Exception {
    try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofSeconds(3))) {
      Thread.sleep(/* millis= */ 1001);
      Duration remain1 = monitor.getRemainingTimeout();
      Thread.sleep(/* millis= */ 1001);
      Duration remain2 = monitor.getRemainingTimeout();

      assertThat(remain1.compareTo(Duration.ofSeconds(2))).isLessThan(0);
      assertThat(remain2.compareTo(Duration.ofSeconds(1))).isLessThan(0);
    }
  }

  @Test
  public void createAndStart_throwExceptionIfTimeout() {
    DeviceActionException t =
        assertThrows(
            DeviceActionException.class,
            () -> {
              try (TimeoutMonitor monitor = TimeoutMonitor.createAndStart(Duration.ofSeconds(1))) {
                Thread.sleep(/* millis= */ 2000);
                monitor.getRemainingTimeout();
              }
            });
    assertThat(t.getErrorId().name()).isEqualTo("TIMEOUT");
  }
}
