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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.constant.BuiltinFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.truth.Correspondences;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;
import org.jline.reader.LineReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

@RunWith(JUnit4.class)
public class ListCommandTest {

  private static final ImmutableList<String> CTS_MODULE_LIST =
      ImmutableList.of(
          "CtsAbiOverrideHostTestCases",
          "CtsBluetoothMultiDevicesTestCases",
          "CtsConfigV2TestCases");

  private static final String TEST_CTS_CONFIG_DIR =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/console/command/testdata/android-cts");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlags flags = new SetFlags();

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();
  private final SystemUtil systemUtil = new SystemUtil();

  @Mock @Bind @Nullable @ConsoleLineReader private LineReader lineReader;

  private ImmutableMap<String, String> systemProperties;

  private FlagsString deviceInfraServiceFlags;

  @Captor private ArgumentCaptor<String> consoleStdoutCaptor;

  private String xtsRootDirPath;
  private CommandLine commandLine;

  @Before
  public void setUp() throws Exception {
    int olcServerPort = PortProber.pickUnusedPort();
    String publicDirPath = tmpFolder.newFolder("public_dir").toString();
    String tmpDirPath = tmpFolder.newFolder("tmp_dir").toString();
    String xtsResourceDirPath = tmpFolder.newFolder("xts_resource_dir").toString();
    String xtsServerResourceDirPath = tmpFolder.newFolder("xts_server_resource_dir").toString();
    xtsRootDirPath = tmpFolder.newFolder("xts_root_dir").toString();
    Path olcServerBinary =
        Path.of(
            RunfilesUtil.getRunfilesLocation(
                "java/com/google/devtools/mobileharness/infra/ats/common/"
                    + "olcserver/ats_olc_server_local_mode_deploy.jar"));

    ImmutableMap<String, String> flagMap =
        ImmutableMap.<String, String>builder()
            .putAll(BuiltinFlags.atsConsoleFlagMap())
            // keep-sorted start
            .put("ats_console_olc_server_embedded_mode", "false")
            .put("ats_console_olc_server_path", olcServerBinary.toString())
            .put("detect_adb_device", "false")
            .put("no_op_device_num", "1")
            .put("olc_server_port", Integer.toString(olcServerPort))
            .put("public_dir", publicDirPath)
            .put("tmp_dir_root", tmpDirPath)
            .put("xts_res_dir_root", xtsResourceDirPath)
            .put("xts_server_res_dir_root", xtsServerResourceDirPath)
            // keep-sorted end
            .buildOrThrow();
    flags.setAll(flagMap);

    ImmutableList<String> flagList =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    deviceInfraServiceFlags = FlagsString.of(String.join(" ", flagList), flagList);

    systemProperties = ImmutableMap.of("XTS_ROOT", xtsRootDirPath);

    Injector injector =
        Guice.createInjector(
            new ConsoleCommandTestModule(
                ServerEnvironment.of(
                    olcServerBinary,
                    Path.of(systemUtil.getJavaBin()),
                    tmpFolder.getRoot().toPath()),
                systemProperties,
                deviceInfraServiceFlags),
            BoundFieldModule.of(this));
    injector.injectMembers(this);
    commandLine = new CommandLine(RootCommand.class, new GuiceFactory(injector));
    commandLine.setExecutionExceptionHandler(
        (exception, commandLineToUse, parseResult) -> {
          throw exception;
        });

    realLocalFileUtil.copyFileOrDir(TEST_CTS_CONFIG_DIR, xtsRootDirPath);
  }

  @Test
  public void listDevices_expectedOutput() throws Exception {
    int exitCode = commandLine.execute("list", "devices");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader, atLeastOnce()).printAbove(consoleStdoutCaptor.capture());
    assertThat(consoleStdoutCaptor.getAllValues())
        .comparingElementsUsing(Correspondences.contains())
        .contains("NoOpDevice-0");
  }

  @Test
  public void listDevicesAll_expectedOutput() throws Exception {
    int exitCode = commandLine.execute("list", "devices", "all");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader, atLeastOnce()).printAbove(consoleStdoutCaptor.capture());
    assertThat(consoleStdoutCaptor.getAllValues())
        .comparingElementsUsing(Correspondences.contains())
        .contains("TestDeviceState");
    assertThat(consoleStdoutCaptor.getAllValues())
        .comparingElementsUsing(Correspondences.contains())
        .contains("NoOpDevice-0");
  }

  @Test
  public void listModules_expectedOutput() throws Exception {
    int exitCode = commandLine.execute("list", "modules");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader, atLeastOnce()).printAbove(consoleStdoutCaptor.capture());
    for (String module : CTS_MODULE_LIST) {
      assertThat(consoleStdoutCaptor.getAllValues())
          .comparingElementsUsing(Correspondences.contains())
          .contains(module);
    }
  }

  @Test
  public void listResults() throws Exception {
    String resultFileOriginalPath =
        RunfilesUtil.getRunfilesLocation(
            "javatests/com/google/devtools/mobileharness/infra/ats/console/command/testdata/test_result.xml");
    String resultFilePath =
        PathUtil.join(
            xtsRootDirPath, "android-cts", "results", "2023.11.30_12.34.56", "test_result.xml");
    LocalFileUtil localFileUtil = new LocalFileUtil();
    localFileUtil.prepareParentDir(resultFilePath);
    localFileUtil.copyFileOrDir(resultFileOriginalPath, resultFilePath);

    int exitCode = commandLine.execute("list", "results");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader)
        .printAbove(
            "Session  Pass  Fail  Warning  Modules Complete  Result Directory     Test Plan  Device"
                + " serial(s)  Build ID            Product\n"
                + "0        117   0     0        1 of 1            2023.11.30_12.34.56  cts       "
                + " ABC, DEF          SQ3A.220705.003.A1  redfin");
  }

  @Test
  public void listCommands() throws Exception {
    commandLine.execute("run", "cts");

    Sleeper.defaultSleeper().sleep(Duration.ofMillis(100));

    int exitCode = commandLine.execute("list", "commands");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader).printAbove(matches("Command (1|n/a): \\[0m:00\\] (cts)?"));
  }

  @Test
  public void listSubPlans() throws Exception {
    String subPlanFilePath1 =
        PathUtil.join(xtsRootDirPath, "android-cts", "subplans", "subplan1.xml");
    String subPlanFilePath2 =
        PathUtil.join(xtsRootDirPath, "android-cts", "subplans", "subplan2.xml");
    String subPlanFilePath3 =
        PathUtil.join(xtsRootDirPath, "android-cts", "subplans", "a_subplan.xml");

    LocalFileUtil localFileUtil = new LocalFileUtil();
    localFileUtil.prepareParentDir(subPlanFilePath1);
    Files.createFile(Path.of(subPlanFilePath1));
    Files.createFile(Path.of(subPlanFilePath2));
    Files.createFile(Path.of(subPlanFilePath3));

    int exitCode = commandLine.execute("list", "subplans");

    assertThat(exitCode).isEqualTo(0);
    verify(lineReader).printAbove("a_subplan");
    verify(lineReader).printAbove("subplan1");
    verify(lineReader).printAbove("subplan2");
  }
}
