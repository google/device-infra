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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.shared.constant.inject.Annotations.SystemProperties;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.nio.file.Path;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.ParameterException;

@RunWith(JUnit4.class)
public final class RunCommandTest {

  private static final String XTS_ROOT_DIR = "/fake_xts_root";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Captor private ArgumentCaptor<AtsSessionPluginConfig> atsSessionPluginConfigCaptor;

  @Bind @SystemProperties
  private static final ImmutableMap<String, String> SYSTEM_PROPERTIES =
      ImmutableMap.of("XTS_ROOT", XTS_ROOT_DIR);

  @Mock @Bind private LocalFileUtil localFileUtil;
  @Mock @Bind @Nullable @ConsoleLineReader private LineReader lineReader;
  @Mock @Bind private CommandExecutor commandExecutor;
  @Mock @Bind private ConsoleUtil consoleUtil;
  @Mock @Bind private AtsSessionStub atsSessionStub;
  @Mock @Bind private ResultListerHelper resultListerHelper;
  @Mock @Bind private CommandHelper commandHelper;

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
                            Path.of("/fake_server_binary"),
                            Path.of("/fake_java_binary"),
                            Path.of("/fake_working_dir")))))
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
  public void validateCommandParameters_retryCommandRequiresSessionIdOrResultDirName() {
    commandLine.parseArgs("retry", "--shard-count", "2");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains(
            "Must provide option '--retry <retry_session_id>' or '--retry-result-dir"
                + " <retry_session_result_dir_name>' for retry command");
  }

  @Test
  public void
      validateCommandParameters_retryCommandCannotSpecifySessionIdAndResultDirNameAtTheSameTime() {
    commandLine.parseArgs(
        "retry",
        "--retry",
        "0",
        "--retry-result-dir",
        "2025.02.10_15.11.19.261_7233",
        "--shard-count",
        "2");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains(
            "Option '--retry <retry_session_id>' and '--retry-result-dir"
                + " <retry_session_result_dir_name>' are mutually exclusive");
  }

  @Test
  public void validateCommandParameters_retryCommandNotSupportIncludeFilter() {
    commandLine.parseArgs("retry", "--retry", "0", "--include-filter", "moduleA");

    assertThat(assertThrows(ParameterException.class, () -> runCommand.validateCommandParameters()))
        .hasMessageThat()
        .contains("Option '--include-filter' is not supported in retry command");
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
  public void parseArgs_strictIncludeFilter() throws Exception {
    when(commandHelper.getXtsType()).thenReturn("cts");
    when(atsSessionStub.runSession(any(), any()))
        .thenReturn(immediateFuture(AtsSessionPluginOutput.getDefaultInstance()));

    commandLine.parseArgs(
        "cts", "--strict-include-filter", "filter1", "--strict-include-filter", "filter2");
    var unused = runCommand.runWithCommand(ImmutableList.of("run", "cts"));

    verify(atsSessionStub)
        .runSession(
            eq(RunCommand.RUN_COMMAND_SESSION_NAME), atsSessionPluginConfigCaptor.capture());
    com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto
            .RunCommand
        runCmd = atsSessionPluginConfigCaptor.getValue().getRunCommand();
    assertThat(runCmd.getStrictIncludeFilterList()).containsExactly("filter1", "filter2");
  }

  @Test
  public void parseArgs_moduleMetadataFilterIsNotKeyValuePair() {
    assertThat(
            assertThrows(
                ParameterException.class,
                () -> commandLine.parseArgs("cts", "--module-metadata-include-filter", "name1")))
        .hasMessageThat()
        .contains("Must provide key value pair");

    assertThat(
            assertThrows(
                ParameterException.class,
                () -> commandLine.parseArgs("cts", "--module-metadata-exclude-filter")))
        .hasMessageThat()
        .contains("Must provide key value pair");

    assertThat(
            assertThrows(
                ParameterException.class,
                () ->
                    commandLine.parseArgs(
                        "cts", "--compatibility:module-metadata-exclude-filter", "-s", "device1")))
        .hasMessageThat()
        .contains("Must provide key value pair");
  }

  @Test
  public void parseArgs_moduleMetadataFilter() {
    commandLine.parseArgs(
        "cts",
        "-s",
        "device1",
        "tf-arg0",
        "--module-metadata-include-filter",
        "key1",
        "value1",
        "tf-arg1",
        "-opt0",
        "--compatibility:module-metadata-include-filter",
        "key2",
        "value2",
        "--product-type",
        "product1",
        "--compatibility:module-metadata-exclude-filter",
        "key3",
        "value3",
        "-opt1",
        "opt1-value",
        "-opt2",
        "--module-metadata-exclude-filter",
        "key4",
        "value4",
        "-s",
        "device2");

    assertThat(runCommand.getSerials()).containsExactly("device1", "device2");
    assertThat(runCommand.getProductTypes()).containsExactly("product1");
    assertThat(runCommand.getModuleMetadataIncludeFilters())
        .containsExactly("key1", "value1", "key2", "value2");
    assertThat(runCommand.getModuleMetadataExcludeFilters())
        .containsExactly("key3", "value3", "key4", "value4");
    assertThat(runCommand.getExtraRunCmdArgs())
        .containsExactly("tf-arg0", "tf-arg1", "-opt0", "-opt1", "opt1-value", "-opt2");
  }

  @Test
  public void parseArgsThenRunWithCommand_retrySessionIdMappedToRetrySessionResultDirName()
      throws Exception {
    String retrySessionResultDirName = "2025.02.10_15.11.19.261_7233";
    when(commandHelper.getXtsType()).thenReturn("cts");
    when(resultListerHelper.listResultDirsInOrder(XTS_ROOT_DIR + "/android-cts/results"))
        .thenReturn(ImmutableList.of(new File(retrySessionResultDirName)));
    when(atsSessionStub.runSession(any(), any()))
        .thenReturn(immediateFuture(AtsSessionPluginOutput.getDefaultInstance()));

    commandLine.parseArgs("retry", "--retry", "0");
    var unused = runCommand.runWithCommand(ImmutableList.of("run", "retry", "--retry", "0"));

    verify(atsSessionStub)
        .runSession(
            eq(RunCommand.RUN_COMMAND_SESSION_NAME), atsSessionPluginConfigCaptor.capture());
    com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto
            .RunCommand
        runCmd = atsSessionPluginConfigCaptor.getValue().getRunCommand();
    assertThat(runCmd.hasRetrySessionIndex()).isFalse();
    assertThat(runCmd.getRetrySessionResultDirName()).isEqualTo(retrySessionResultDirName);
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
