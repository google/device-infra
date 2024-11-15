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
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
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
import java.nio.file.Path;
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
                    BoundFieldModule.of(this),
                    new ConsoleCommandTestModule(
                        consoleInfo,
                        ServerEnvironment.of(
                            Path.of("/fake_server_binary"), Path.of("/fake_java_binary")))))
            .create(RunCommand.class);
    commandLine =
        new CommandLine(runCommand)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setUnmatchedOptionsArePositionalParams(true);
  }

  @Test
  public void validateCommandParameters_retryCommandNotSupportSubplanOption() {
    commandLine.parseArgs("retry", "--retry", "0", "--subplan", "subplan1");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Option '--subplan <subplan_name>' is not supported in retry command");
  }

  @Test
  public void validateCommandParameters_retryCommandRequiresSessionId() {
    commandLine.parseArgs("retry", "--shard-count", "2");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Option '--retry <retry_session_id>' is required for retry command");
  }

  @Test
  public void validateCommandParameters_cannotSpecifyIncludeFilterAndModuleAtTheSameTime() {
    commandLine.parseArgs("cts", "--module", "module_a", "--include-filter", "module_b");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Don't use '--include-filter' and '--module/-m' options at the same time");
  }

  @Test
  public void validateCommandParameters_quotingExtraArgs() {
    commandLine.parseArgs(
        "cts",
        "--include-filter",
        "\"CtsCompanionDeviceManagerMultiDeviceTestCases"
            + " CompanionDeviceManagerTestClass#test_associate_createsAssociation_classicBluetooth\"");
  }

  @Test
  public void validateCommandParameters_invalidExtraArgs() {
    commandLine.parseArgs(
        "cts",
        "--include-filter",
        "CtsCompanionDeviceManagerMultiDeviceTestCases",
        "CompanionDeviceManagerTestClass#test_associate_createsAssociation_classicBluetooth");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Invalid arguments provided. Unprocessed arguments:");
  }

  @Test
  public void validateCommandParameters_invalidModuleArgs() {
    commandLine.parseArgs("cts", "--module-arg", "arg");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains(
            "Invalid module arguments provided. Unprocessed arguments: arg\nExpected format:"
                + " <module_name>:<arg_name>:[<arg_key>:=]<arg_value>.\n");
  }

  @Test
  public void parseArgs_propertyIsNotKeyValuePair() {
    assertThat(
            assertThrows(
                ParameterException.class,
                () -> commandLine.parseArgs("cts", "--property", "name1")))
        .hasMessageThat()
        .contains("Must provide key value pair");

    assertThat(
            assertThrows(
                ParameterException.class, () -> commandLine.parseArgs("cts", "--property")))
        .hasMessageThat()
        .contains("Must provide key value pair");
  }

  @Test
  public void parseArgs_propertyOption() {
    commandLine.parseArgs(
        "cts",
        "-s",
        "device1",
        "tf-arg0",
        "--property",
        "name1",
        "value1",
        "tf-arg1",
        "-opt0",
        "--product-type",
        "product1",
        "--property",
        "name2",
        "value2",
        "-opt1",
        "opt1-value",
        "-opt2",
        "-s",
        "device2");

    assertThat(runCommand.getSerials()).containsExactly("device1", "device2");
    assertThat(runCommand.getProductTypes()).containsExactly("product1");
    assertThat(runCommand.getDevicePropertiesMap())
        .containsExactly("name1", "value1", "name2", "value2");
    assertThat(runCommand.getExtraRunCmdArgs())
        .containsExactly("tf-arg0", "tf-arg1", "-opt0", "-opt1", "opt1-value", "-opt2");

    commandLine.parseArgs("cts", "--property", "name1", "value1");

    assertThat(runCommand.getDevicePropertiesMap()).containsExactly("name1", "value1");
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
    assertThat(runCommand.showHelpMessage("cts", Path.of("/ats"))).isEqualTo(ExitCode.OK);
    verify(consoleUtil)
        .printlnStdout(
            "'cts' configuration: Setup that allows to point to a remote config and run it.\n"
                + "\n"
                + "aaa\n"
                + "ccc");
  }
}
