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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import javax.inject.Inject;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.junit.After;
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

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private LineReader lineReader;
  @Mock private History history;

  private ByteArrayOutputStream consoleOutputStream;
  private PrintStream consolePrintStream;

  @Inject private AtsConsole atsConsole;

  @Before
  public void setUp() throws Exception {
    consoleOutputStream = new ByteArrayOutputStream();
    consolePrintStream = new PrintStream(consoleOutputStream, false, UTF_8);
    when(lineReader.getHistory()).thenReturn(history);

    AtsConsole.injector =
        Guice.createInjector(
            new AtsConsoleModule(
                ImmutableList.of(),
                ImmutableList.of(),
                lineReader,
                consolePrintStream,
                () -> Path.of("")));
    AtsConsole.injector.injectMembers(this);
  }

  @After
  public void tearDown() {
    AtsConsole.injector = null;

    System.out.println(consoleOutputStream.toString(UTF_8));
  }

  @Test
  public void exitConsole() throws Exception {
    when(lineReader.readLine(anyString())).thenReturn("exit");

    atsConsole.call();

    assertThat(consoleOutputStream.toString(UTF_8)).isEmpty();
  }

  @Test
  public void startsConsoleWithHelp_exitConsoleAfterCommandExecution() throws Exception {
    AtsConsole.injector =
        Guice.createInjector(
            new AtsConsoleModule(
                ImmutableList.of(),
                ImmutableList.of("--help"),
                lineReader,
                consolePrintStream,
                () -> Path.of("")));
    AtsConsole.injector.injectMembers(this);

    atsConsole.call();

    assertThat(consoleOutputStream.toString(UTF_8))
        .isEqualTo("Using commandline arguments as starting command: [--help]\n");

    verify(history).add("--help");
  }
}
