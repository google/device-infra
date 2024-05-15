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

package com.google.devtools.mobileharness.infra.ats.console.command;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleId;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.ParameterException;

@RunWith(JUnit4.class)
public final class RunCommandTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @ConsoleId private static final String CONSOLE_ID = "fake_console_id";

  @Bind @SystemProperties
  private static final ImmutableMap<String, String> SYSTEM_PROPERTIES = ImmutableMap.of();

  @Mock @Bind private LocalFileUtil localFileUtil;
  @Mock @Bind @Nullable @ConsoleLineReader private LineReader lineReader;
  @Mock @Bind private CommandExecutor commandExecutor;
  @Mock @Bind private ConsoleUtil consoleUtil;

  @Inject private ConsoleInfo consoleInfo;
  private CommandLine commandLine;
  private RunCommand runCommand;

  @Before
  public void setUp() {
    Injector injector = Guice.createInjector(BoundFieldModule.of(this));
    injector.injectMembers(this);

    runCommand =
        new GuiceFactory(
                Guice.createInjector(
                    BoundFieldModule.of(this), new ConsoleCommandTestModule(consoleInfo)))
            .create(RunCommand.class);
    commandLine = new CommandLine(runCommand);
  }

  @Test
  public void validateCommandParameters_retryCommandNotSupportSubplanOption() throws Exception {
    commandLine.parseArgs("retry", "--retry", "0", "--subplan", "subplan1");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Option '--subplan <subplan_name>' is not supported in retry command");
  }

  @Test
  public void validateCommandParameters_retryCommandRequiresSessionId() throws Exception {
    commandLine.parseArgs("retry", "--shard-count", "2");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Option '--retry <retry_session_id>' is required for retry command");
  }

  @Test
  public void validateCommandParameters_cannotSpecifyIncludeFilterAndModuleAtTheSameTime()
      throws Exception {
    commandLine.parseArgs("cts", "--module", "module_a", "--include-filter", "module_b");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Don't use '--include-filter' and '--module/-m' options at the same time");
  }

  @Test
  public void showHelpMessage_success() throws Exception {
    commandLine.parseArgs("cts", "--help");
    when(commandExecutor.run(any(Command.class)))
        .thenReturn(
            "'cts' configuration: Setup that allows to point to a remote config and run it.\n"
                + "\n"
                + "aaa\n"
                + "05-13 20:17:37 I/DeviceManager: Detected new device b\n"
                + "ccc\n"
                + "05-13 20:17:37 I/CommandScheduler: Received shutdown request.");
    assertThat(runCommand.showHelpMessage("cts", "/ats")).isEqualTo(ExitCode.OK);
    verify(consoleUtil)
        .printlnStdout(
            "'cts' configuration: Setup that allows to point to a remote config and run it.\n"
                + "\n"
                + "aaa\n"
                + "ccc");
  }
}
