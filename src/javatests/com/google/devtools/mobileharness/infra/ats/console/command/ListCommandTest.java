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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.console.AtsConsole;
import com.google.devtools.mobileharness.infra.ats.console.AtsConsoleModule;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.truth.Correspondences;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ListCommandTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();

  @Mock private LineReader lineReader;

  @Captor private ArgumentCaptor<String> consoleStdoutCaptor;

  private String publicDirPath;
  private String tmpDirPath;
  private String xtsRootDirPath;

  @Inject private AtsConsole atsConsole;

  @Before
  public void setUp() throws Exception {
    // Prepares environment.
    int olcServerPort = PortProber.pickUnusedPort();
    publicDirPath = tmpFolder.newFolder("public_dir").toString();
    tmpDirPath = tmpFolder.newFolder("tmp_dir").toString();
    String xtsResourceDirPath = tmpFolder.newFolder("xts_resource_dir").toString();
    xtsRootDirPath = tmpFolder.newFolder("xts_root_dir").toString();
    Path olcServerBinary =
        Path.of(
            RunfilesUtil.getRunfilesLocation(
                "java/com/google/devtools/mobileharness/infra/ats/common/"
                    + "olcserver/ats_olc_server_local_mode_deploy.jar"));

    // Sets flags.
    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "ats_console_olc_server_path",
            olcServerBinary.toString(),
            "olc_server_port",
            Integer.toString(olcServerPort),
            "public_dir",
            publicDirPath,
            "detect_adb_device",
            "false",
            "no_op_device_num",
            "1",
            "simplified_log_format",
            "true",
            "tmp_dir_root",
            tmpDirPath,
            "xts_res_dir_root",
            xtsResourceDirPath);
    flags.setAllFlags(flagMap);
    ImmutableList<String> flagList =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    FlagsString deviceInfraServiceFlags = FlagsString.of(String.join(" ", flagList), flagList);

    // Sets console stdout/stderr.
    ByteArrayOutputStream consoleOutOutputStream = new ByteArrayOutputStream();
    PrintStream consoleOutPrintStream = new PrintStream(consoleOutOutputStream, false, UTF_8);
    ByteArrayOutputStream consoleErrOutputStream = new ByteArrayOutputStream();
    PrintStream consoleErrPrintStream = new PrintStream(consoleErrOutputStream, false, UTF_8);

    // Creates ATS console.
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                "fake_console_id",
                deviceInfraServiceFlags,
                /* mainArgs= */ ImmutableList.of(),
                /* systemProperties= */ ImmutableMap.of("XTS_ROOT", xtsRootDirPath),
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream,
                future -> {},
                /* parseCommandOnly= */ false));
    injector.injectMembers(this);
    atsConsole.injector = injector;

    // Prepares the cts configs file for testing
    realLocalFileUtil.copyFileOrDir(TEST_CTS_CONFIG_DIR, xtsRootDirPath);
  }

  @After
  public void tearDown() throws Exception {
    Path serverLogDir = Path.of(publicDirPath, "olc_server_log");
    if (Files.exists(serverLogDir)) {
      try (Stream<Path> files = Files.list(serverLogDir)) {
        files.forEach(
            path -> {
              if (!Files.isDirectory(path)) {
                try {
                  String fileContent = Files.readString(path);
                  logger.atInfo().log(
                      "OLC server log file [%s]:\n"
                          + "**BEGIN******************\n"
                          + "%s\n"
                          + "**END******************\n",
                      path, fileContent);
                } catch (IOException e) {
                  logger.atWarning().withCause(e).log("Failed to read %s", path);
                }
              }
            });
      }
    }
  }

  @Test
  public void listDevicesAndModules_expectedOutput() throws Exception {
    when(lineReader.readLine(anyString()))
        .thenReturn("list devices")
        .thenReturn("list devices all")
        .thenReturn("list modules")
        .thenReturn("exit");

    atsConsole.run();

    verify(lineReader, atLeastOnce()).printAbove(consoleStdoutCaptor.capture());

    assertThat(consoleStdoutCaptor.getAllValues())
        .comparingElementsUsing(Correspondences.contains())
        .contains("TestDeviceState");
    assertThat(consoleStdoutCaptor.getAllValues())
        .comparingElementsUsing(Correspondences.contains())
        .containsAtLeast("NoOpDevice-0", "NoOpDevice-0");
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

    when(lineReader.readLine(anyString())).thenReturn("list results").thenReturn("exit");

    atsConsole.run();

    verify(lineReader)
        .printAbove(
            "Session  Pass  Fail  Modules Complete  Result Directory     Test Plan  Device"
                + " serial(s)  Build ID            Product\n"
                + "0        117   0     1 of 1            2023.11.30_12.34.56  cts        ABC, DEF "
                + "         SQ3A.220705.003.A1  redfin");
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

    when(lineReader.readLine(anyString())).thenReturn("list subplans").thenReturn("exit");

    atsConsole.run();

    InOrder lineReaderInOrder = inOrder(lineReader);
    lineReaderInOrder.verify(lineReader).printAbove("a_subplan");
    lineReaderInOrder.verify(lineReader).printAbove("subplan1");
    lineReaderInOrder.verify(lineReader).printAbove("subplan2");
  }
}
