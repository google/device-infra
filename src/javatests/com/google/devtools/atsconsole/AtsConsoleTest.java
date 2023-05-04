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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.deviceinfra.shared.util.port.PortProber;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import javax.inject.Inject;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsConsoleTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private LineReader lineReader;
  @Mock private History history;

  private ByteArrayOutputStream consoleOutOutputStream;
  private PrintStream consoleOutPrintStream;
  private ByteArrayOutputStream consoleErrOutputStream;
  private PrintStream consoleErrPrintStream;

  @Inject private AtsConsole atsConsole;

  @Before
  public void setUp() throws Exception {
    int olcServerPort = PortProber.pickUnusedPort();
    String publicDirPath = tmpFolder.newFolder("public_dir").toString();

    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "olc_server_port",
            Integer.toString(olcServerPort),
            "public_dir",
            publicDirPath,
            "detect_adb_device",
            "false",
            "enable_ats_console_olc_server",
            "true");
    ImmutableList<String> deviceInfraServiceFlags =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    Flags.parse(deviceInfraServiceFlags.toArray(new String[0]));

    Path olcServerBinary =
        Path.of(
            RunfilesUtil.getRunfilesLocation(
                "java/com/google/devtools/atsconsole/controller/olcserver/AtsOlcServer_deploy.jar"));

    consoleOutOutputStream = new ByteArrayOutputStream();
    consoleOutPrintStream = new PrintStream(consoleOutOutputStream, false, UTF_8);

    consoleErrOutputStream = new ByteArrayOutputStream();
    consoleErrPrintStream = new PrintStream(consoleErrOutputStream, false, UTF_8);

    when(lineReader.getHistory()).thenReturn(history);

    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                deviceInfraServiceFlags,
                ImmutableList.of(),
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream,
                () -> olcServerBinary));
    injector.injectMembers(this);
    atsConsole.injector = injector;
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();

    System.out.println("STDOUT:");
    System.out.println(consoleOutOutputStream.toString(UTF_8));
    System.out.println("STDERR:");
    System.out.println(consoleErrOutputStream.toString(UTF_8));
  }

  @Test
  public void exitConsole() throws Exception {
    when(lineReader.readLine(anyString())).thenReturn("exit");

    atsConsole.call();

    assertThat(consoleOutOutputStream.toString(UTF_8)).isEmpty();
  }

  @Test
  public void startsConsoleWithHelp_exitConsoleAfterCommandExecution() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                ImmutableList.of(),
                ImmutableList.of("help"),
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream,
                () -> Path.of("")));
    injector.injectMembers(this);
    atsConsole.injector = injector;

    atsConsole.call();

    assertThat(consoleOutOutputStream.toString(UTF_8))
        .startsWith("Using commandline arguments as starting command: [help]\n");

    verify(history).add("help");
  }

  @Test
  public void runCtsv_enableAtsConsoleOlcServer() throws Exception {
    when(lineReader.readLine(anyString())).thenReturn("run -s abc cts-v").thenReturn("exit");

    atsConsole.call();

    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(15L));

    assertThat(consoleErrOutputStream.toString(UTF_8))
        .isEqualTo("Error: Unimplemented AtsSessionPlugin\n");
  }
}
