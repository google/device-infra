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

package com.google.devtools.atsconsole;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import java.time.Duration;
import java.util.Arrays;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsConsoleTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LineReader lineReader;
  @Mock private History history;
  @Mock private Sleeper sleeper;
  @Mock private ConsoleUtil consoleUtil;

  private AtsConsole atsConsole;

  @Before
  public void setUp() {
    atsConsole = new AtsConsole(lineReader, sleeper, consoleUtil);
    doCallRealMethod().when(consoleUtil).printLine(anyString());
    when(lineReader.getHistory()).thenReturn(history);
  }

  @Test
  public void exitConsole() throws Exception {
    atsConsole.setName("exitConsole");
    when(lineReader.readLine(anyString())).thenReturn("exit");

    atsConsole.start();
    // join has a timeout otherwise it may hang forever.
    atsConsole.join(30000);

    assertThat(atsConsole.isAlive()).isFalse();
    verify(sleeper).sleep(Duration.ofMillis(100));
    verify(lineReader, times(1)).readLine(anyString());
  }

  @Test
  public void startsConsoleWithHelp_exitConsoleAfterCommandExecution() throws Exception {
    atsConsole.setName("startsConsoleWithHelp_exitConsoleAfterCommandExecution");
    atsConsole.setArgs(Arrays.asList("--help"));

    atsConsole.start();
    // join has a timeout otherwise it may hang forever.
    atsConsole.join(2000);

    assertThat(atsConsole.isAlive()).isFalse();
    verify(sleeper).sleep(Duration.ofMillis(100));
    verify(lineReader, never()).readLine(anyString());
    verify(history).add("--help");
  }
}
