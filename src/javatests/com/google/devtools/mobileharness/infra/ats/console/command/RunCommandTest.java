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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlResultFormatter;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlResultUtil;
import com.google.devtools.mobileharness.infra.ats.console.testbed.config.YamlTestbedUpdater;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

@RunWith(JUnit4.class)
public final class RunCommandTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String MOBLY_TESTCASES_DIR = "/path/to/mobly_testcases_dir";
  private static final String MOBLY_TEST_ZIP_SUITE_MAIN_FILE = "/path/to/mobly_suite_main.py";
  private static final File MOBLY_TEST_ZIP_A =
      new File(MOBLY_TESTCASES_DIR, "mobly_test_zip_a.zip");
  private static final File MOBLY_TEST_ZIP_B =
      new File(MOBLY_TESTCASES_DIR, "mobly_test_zip_b.zip");
  private static final File MOBLY_TEST_ZIP_C_WITH_WRONG_FILE_EXTENSION =
      new File(MOBLY_TESTCASES_DIR, "mobly_test_zip_c.mmm");

  private static final String CONNECTED_DEVICE1_SERIAL = "device1";
  private static final String CONNECTED_DEVICE2_SERIAL = "device2";
  private static final ImmutableSet<String> CONNECTED_DEVICES =
      ImmutableSet.of(CONNECTED_DEVICE1_SERIAL, CONNECTED_DEVICE2_SERIAL);

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  @Mock @Bind private CommandExecutor commandExecutor;
  @Mock @Bind private AndroidAdbInternalUtil androidAdbInternalUtil;
  @Mock @Bind private LocalFileUtil localFileUtil;
  @Mock @Bind private YamlTestbedUpdater yamlTestbedUpdater;
  @Mock @Bind private XmlResultFormatter xmlResultFormatter;
  @Mock @Bind private XmlResultUtil xmlResultUtil;
  @Mock @Bind private MoblyAospTestSetupUtil moblyAospTestSetupUtil;

  @Bind private ConsoleUtil consoleUtil;

  private CommandLine commandLine;
  private ConsoleInfo consoleInfo;

  private static class PrintStreams {
    @Bind
    @ConsoleOutput(ConsoleOutput.Type.OUT_STREAM)
    private PrintStream outPrintStream;

    @Bind
    @ConsoleOutput(ConsoleOutput.Type.ERR_STREAM)
    private PrintStream errPrintStream;
  }

  @Before
  public void setUp() throws Exception {
    out.reset();
    err.reset();
    PrintStreams printStreams = new PrintStreams();
    printStreams.outPrintStream = new PrintStream(out);
    printStreams.errPrintStream = new PrintStream(err);
    System.setOut(printStreams.outPrintStream);
    System.setErr(printStreams.errPrintStream);
    consoleUtil =
        spy(Guice.createInjector(BoundFieldModule.of(printStreams)).getInstance(ConsoleUtil.class));

    consoleInfo =
        new ConsoleInfo(
            ImmutableMap.of(
                "MOBLY_TEST_ZIP_SUITE_MAIN_FILE",
                MOBLY_TEST_ZIP_SUITE_MAIN_FILE,
                "MOBLY_TESTCASES_DIR",
                MOBLY_TESTCASES_DIR));
    Injector injector =
        Guice.createInjector(BoundFieldModule.of(this), new ConsoleCommandTestModule(consoleInfo));
    injector.injectMembers(this);
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));

    doCallRealMethod().when(consoleUtil).printlnStdout(anyString(), any());
    doCallRealMethod().when(consoleUtil).printlnStderr(anyString(), any());
    doCallRealMethod().when(consoleUtil).completeHomeDirectory(anyString());
    when(localFileUtil.isDirExist(MOBLY_TESTCASES_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(MOBLY_TEST_ZIP_SUITE_MAIN_FILE)).thenReturn(true);
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE))
        .thenReturn(CONNECTED_DEVICES);
    when(consoleUtil.isZipFile(MOBLY_TEST_ZIP_A)).thenReturn(true);
    when(consoleUtil.isZipFile(MOBLY_TEST_ZIP_B)).thenReturn(true);
    when(consoleUtil.isZipFile(MOBLY_TEST_ZIP_C_WITH_WRONG_FILE_EXTENSION)).thenReturn(false);
  }

  @After
  public void restoreStreams() {
    String output = out.toString(UTF_8);
    String error = err.toString(UTF_8);

    System.setOut(originalOut);
    System.setErr(originalErr);

    // Also prints out and err, so they can be shown on the sponge
    System.out.println(output);
    System.out.println(error);
  }

  @Test
  public void run_unsupportedConfig() {
    // Exiting with code 2 corresponds to picocli.CommandLine.ParameterException.
    int exitCode = commandLine.execute("run", "cts");

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  public void run_emptyConfig() {
    // Exiting with code 2 corresponds to picocli.CommandLine.ParameterException.
    int exitCode = commandLine.execute("run", "");

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  public void run_missingMoblyTestCasesDir() throws Exception {
    Injector injector =
        Guice.createInjector(
            BoundFieldModule.of(this),
            new ConsoleCommandTestModule(new ConsoleInfo(ImmutableMap.of())));
    injector.injectMembers(this);
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));

    int exitCode = commandLine.execute("run", "cts-v");

    assertThat(exitCode).isEqualTo(1);
    assertThat(err.toString(UTF_8)).contains("Mobly test cases dir is not set");
  }

  @Test
  public void run_missingMoblyTestZipSuiteMainFile() throws Exception {
    consoleInfo = new ConsoleInfo(ImmutableMap.of("MOBLY_TESTCASES_DIR", MOBLY_TESTCASES_DIR));
    Injector injector =
        Guice.createInjector(BoundFieldModule.of(this), new ConsoleCommandTestModule(consoleInfo));
    injector.injectMembers(this);
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));

    int exitCode = commandLine.execute("run", "cts-v");

    assertThat(exitCode).isEqualTo(1);
    assertThat(err.toString(UTF_8)).contains("\"suite_main.py\" file is required");
  }

  @Test
  public void run_filterMoblyTestCasesBasedOnGivenModule() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A, MOBLY_TEST_ZIP_B));
    when(yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of(CONNECTED_DEVICE1_SERIAL), MOBLY_TESTCASES_DIR, null))
        .thenReturn("/path/to/mobly_config_file");
    when(moblyAospTestSetupUtil.setupEnvAndGenerateTestCommand(
            any(Path.class),
            any(Path.class),
            any(Path.class),
            any(Path.class),
            anyString(),
            eq(null),
            eq("python3"),
            eq(null)))
        .thenReturn(new String[] {""});

    commandLine.execute("run", "cts-v", "-m", "mobly_test_zip_a");

    verify(localFileUtil, times(3)).prepareDir(any(Path.class));
    verify(commandExecutor).exec(any(Command.class));
  }

  @Test
  public void run_allFoundMoblyTestCases() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A, MOBLY_TEST_ZIP_B));
    when(yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of(CONNECTED_DEVICE1_SERIAL), MOBLY_TESTCASES_DIR, null))
        .thenReturn("/path/to/mobly_config_file");
    when(moblyAospTestSetupUtil.setupEnvAndGenerateTestCommand(
            any(Path.class),
            any(Path.class),
            any(Path.class),
            any(Path.class),
            anyString(),
            eq(null),
            eq("python3"),
            eq(null)))
        .thenReturn(new String[] {""});

    commandLine.execute("run", "cts-v");

    verify(localFileUtil, times(5)).prepareDir(any(Path.class));
    verify(commandExecutor, times(2)).exec(any(Command.class));
  }

  @Test
  public void run_moblyExecutableWithUnexpectedFileExtension_filterOut() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_C_WITH_WRONG_FILE_EXTENSION));

    commandLine.execute("run", "cts-v");

    assertThat(err.toString(UTF_8)).contains("Found no match");
    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void run_noConnectedDevices() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE))
        .thenReturn(ImmutableSet.of());

    commandLine.execute("run", "cts-v");

    verify(androidAdbInternalUtil).getDeviceSerialsByState(DeviceState.DEVICE);
    verify(localFileUtil, never()).prepareDir(any(Path.class));
    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void run_givenDeviceNotDetected() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A));

    commandLine.execute("run", "cts-v", "-s", "abc");

    verify(androidAdbInternalUtil).getDeviceSerialsByState(DeviceState.DEVICE);
    verify(localFileUtil, never()).prepareDir(any(Path.class));
    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void run_givenDeviceDetected() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A));
    when(yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of(CONNECTED_DEVICE2_SERIAL), MOBLY_TESTCASES_DIR, null))
        .thenReturn("/path/to/mobly_config_file");
    when(moblyAospTestSetupUtil.setupEnvAndGenerateTestCommand(
            any(Path.class),
            any(Path.class),
            any(Path.class),
            any(Path.class),
            anyString(),
            eq(null),
            eq("python3"),
            eq(null)))
        .thenReturn(new String[] {""});

    commandLine.execute("run", "cts-v", "-s", CONNECTED_DEVICE2_SERIAL);

    verify(localFileUtil, times(3)).prepareDir(any(Path.class));
    verify(commandExecutor).exec(any(Command.class));
  }

  @Test
  public void run_multiDevice_oneDeviceNotDetected() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE))
        .thenReturn(ImmutableSet.of(CONNECTED_DEVICE1_SERIAL));

    commandLine.execute(
        "run", "cts-v", "--serials", CONNECTED_DEVICE1_SERIAL + ", " + CONNECTED_DEVICE2_SERIAL);

    verify(androidAdbInternalUtil).getDeviceSerialsByState(DeviceState.DEVICE);
    verify(localFileUtil, never()).prepareDir(any(Path.class));
    verify(commandExecutor, never()).exec(any(Command.class));
  }

  @Test
  public void run_multiDevice_allDevicesDetected() throws Exception {
    when(localFileUtil.listFiles(MOBLY_TESTCASES_DIR, /* recursively= */ false))
        .thenReturn(ImmutableList.of(MOBLY_TEST_ZIP_A));
    when(yamlTestbedUpdater.prepareMoblyConfig(
            ImmutableList.of(CONNECTED_DEVICE1_SERIAL, CONNECTED_DEVICE2_SERIAL),
            MOBLY_TESTCASES_DIR,
            null))
        .thenReturn("/path/to/mobly_config_file");
    when(moblyAospTestSetupUtil.setupEnvAndGenerateTestCommand(
            any(Path.class),
            any(Path.class),
            any(Path.class),
            any(Path.class),
            anyString(),
            eq(null),
            eq("python3"),
            eq(null)))
        .thenReturn(new String[] {""});

    commandLine.execute(
        "run", "cts-v", "--serials", CONNECTED_DEVICE1_SERIAL + ", " + CONNECTED_DEVICE2_SERIAL);

    verify(localFileUtil, times(3)).prepareDir(any(Path.class));
    verify(commandExecutor).exec(any(Command.class));
  }
}
