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
import static org.mockito.Mockito.verify;

import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.infra.ats.console.command.alias.AliasManager;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@RunWith(JUnit4.class)
public final class AliasCommandTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConsoleUtil consoleUtil;
  @Inject private AliasManager aliasManager;

  private CommandLine commandLine;

  /**
   * A test-only root command to host the {@link AliasCommand} under test.
   *
   * <p>This is to simulate the production setup where {@link AliasCommand} is a subcommand of
   * {@link RootCommand}. Using a test-only root command avoids the need to provide dependencies for
   * all the other commands in {@link RootCommand}.
   */
  @Command(subcommands = {AliasCommand.class})
  private static class TestRootCommand {}

  @Before
  public void setUp() {
    Injector injector = Guice.createInjector(BoundFieldModule.of(this));
    injector.injectMembers(this);
    commandLine = new CommandLine(TestRootCommand.class, new GuiceFactory(injector));
  }

  @Test
  public void call_validAliasWithSingleQuotes_success() {
    int exitCode = commandLine.execute(new String[] {"alias", "usb_tests='cts -m CtsUsbTests'"});

    assertThat(exitCode).isEqualTo(ExitCode.OK);
    assertThat(aliasManager.getAlias("usb_tests")).hasValue("cts -m CtsUsbTests");
    verify(consoleUtil)
        .printlnStdout("Alias '%s' created, value: [%s].", "usb_tests", "cts -m CtsUsbTests");
  }

  @Test
  public void call_validAliasWithDoubleQuotes_success() {
    int exitCode = commandLine.execute(new String[] {"alias", "usb_tests=\"cts -m CtsUsbTests\""});

    assertThat(exitCode).isEqualTo(ExitCode.OK);
    assertThat(aliasManager.getAlias("usb_tests")).hasValue("cts -m CtsUsbTests");
    verify(consoleUtil)
        .printlnStdout("Alias '%s' created, value: [%s].", "usb_tests", "cts -m CtsUsbTests");
  }

  @Test
  public void call_validAliasWithNoQuotes_success() {
    int exitCode = commandLine.execute(new String[] {"alias", "usb_tests=cts -m CtsUsbTests"});

    assertThat(exitCode).isEqualTo(ExitCode.OK);
    assertThat(aliasManager.getAlias("usb_tests")).hasValue("cts -m CtsUsbTests");
    verify(consoleUtil)
        .printlnStdout("Alias '%s' created, value: [%s].", "usb_tests", "cts -m CtsUsbTests");
  }

  @Test
  public void call_emptyAlias_returnsErrorExitCode() {
    int exitCode = commandLine.execute(new String[] {"alias", ""});
    assertThat(exitCode).isEqualTo(ExitCode.USAGE);
  }

  @Test
  public void call_invalidAlias_returnsErrorExitCode() {
    int exitCode = commandLine.execute(new String[] {"alias", "invalid_alias_with_no_equal_sign"});
    assertThat(exitCode).isEqualTo(ExitCode.USAGE);
  }

  @Test
  public void call_tooManyArguments_returnsErrorExitCode() {
    int exitCode = commandLine.execute(new String[] {"alias", "too_many", "arguments"});
    assertThat(exitCode).isEqualTo(ExitCode.USAGE);
  }

  @Test
  public void call_noArgument_printsAllAliases() {
    aliasManager.addAlias("alias1", "alias1_value");
    aliasManager.addAlias("alias2", "alias2_value");

    int exitCode = commandLine.execute(new String[] {"alias"});

    assertThat(exitCode).isEqualTo(ExitCode.OK);
    verify(consoleUtil).printlnStdout("alias %s='%s'", "alias1", "alias1_value");
    verify(consoleUtil).printlnStdout("alias %s='%s'", "alias2", "alias2_value");
  }
}
