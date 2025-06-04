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

package com.google.devtools.mobileharness.infra.ats.console.util.console;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Duration;
import java.util.logging.Level;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class InterruptibleLineReaderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock @ConsoleLineReader private LineReader lineReader;
  @Bind @Mock private ConsoleUtil consoleUtil;
  @Bind @Mock private CommandHelper commandHelper;

  private final ListeningScheduledExecutorService scheduledThreadPool =
      ThreadPools.createStandardScheduledThreadPool("testing-scheduled-thread-pool", 1);

  @Inject private InterruptibleLineReader interruptibleLineReader;

  @Before
  public void setUp() throws Exception {
    when(commandHelper.getXtsType()).thenReturn("cts");
    when(lineReader.readLine(anyString()))
        .then(
            invocation -> {
              try {
                Thread.currentThread().join();
              } catch (InterruptedException e) {
                throw new UserInterruptException("Interrupted");
              }
              throw new AssertionError("Should not reach here");
            });

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void interrupt_notRunning() {
    interruptibleLineReader.interrupt();

    assertThat(interruptibleLineReader.readLine()).isEmpty();
    verify(consoleUtil).printlnStderr("Stop line reader");
  }

  @Test
  public void interrupt_running() {
    logFailure(
        scheduledThreadPool.schedule(
            () -> interruptibleLineReader.interrupt(), Duration.ofSeconds(2L)),
        Level.WARNING,
        "Fatal error when interrupting line reader");

    assertThat(interruptibleLineReader.readLine()).isEmpty();

    verify(consoleUtil).printlnStderr("Interrupt line reader");
    verify(consoleUtil).printlnStderr("Interrupted by the user.");
  }
}
