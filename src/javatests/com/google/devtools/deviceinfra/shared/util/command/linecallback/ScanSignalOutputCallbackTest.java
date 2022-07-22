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

package com.google.devtools.deviceinfra.shared.util.command.linecallback;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.shared.util.command.CommandProcess;
import com.google.devtools.deviceinfra.shared.util.command.LineCallback.Response;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link ScanSignalOutputCallback}. */
@RunWith(JUnit4.class)
public final class ScanSignalOutputCallbackTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Sleeper sleeper;
  @Mock private Clock clock;

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private CommandProcess commandProcess;

  private static final String SIGNAL = "signal";
  private static final boolean DEFAULT_STOP_ON_SIGNAL = true;

  private ScanSignalOutputCallback callback;

  @Before
  public void setUp() {
    callback = new ScanSignalOutputCallback(SIGNAL, DEFAULT_STOP_ON_SIGNAL, sleeper, clock);
  }

  @Test
  public void onLine_stopOnSignal() {
    String[] otherOutput = {"line 1", "line2", "line3"};

    for (String line : otherOutput) {
      assertThat(callback.onLine(line)).isEqualTo(Response.notStop());
      assertThat(callback.isSignalCaught()).isFalse();
    }

    assertThat(callback.onLine("prefix-" + SIGNAL + "-suffix")).isEqualTo(Response.stop());
    assertThat(callback.isSignalCaught()).isTrue();
  }

  @Test
  public void onLine_notStopOnSignal() {
    callback = new ScanSignalOutputCallback(SIGNAL, /* stopOnSignal= */ false, sleeper, clock);
    String[] otherOutput = {"line 1", "line2", "line3"};

    for (String line : otherOutput) {
      assertThat(callback.onLine(line)).isEqualTo(Response.notStop());
      assertThat(callback.isSignalCaught()).isFalse();
    }

    assertThat(callback.onLine("prefix-" + SIGNAL + "-suffix")).isEqualTo(Response.notStop());
    assertThat(callback.isSignalCaught()).isTrue();
  }

  @Test
  public void waitForSignal_catchSignal() throws Exception {
    Duration timeout = Duration.ofMinutes(1);
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs + 1),
            Instant.ofEpochMilli(nowMs + timeout.toMillis()));
    when(commandProcess.isAlive()).thenReturn(false);

    assertThat(callback.onLine(SIGNAL)).isEqualTo(Response.stop());
    assertThat(callback.waitForSignal(timeout, commandProcess)).isTrue();
  }

  @Test
  public void waitForSignal_timeoutBeforeCatchingSignal() throws Exception {
    Duration timeout = Duration.ofMinutes(1);
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs + 1),
            Instant.ofEpochMilli(nowMs + timeout.toMillis()));
    when(commandProcess.isAlive()).thenReturn(true);

    assertThat(callback.waitForSignal(timeout, commandProcess)).isFalse();
  }
}
