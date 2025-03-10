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

package com.google.devtools.mobileharness.infra.ats.console;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Mock private LineReader lineReader;
  @Mock private History history;

  private PrintStream consoleOutPrintStream;
  private PrintStream consoleErrPrintStream;

  private FlagsString deviceInfraServiceFlags;
  private ImmutableMap<String, String> systemProperties;

  @Inject private AtsConsole atsConsole;

  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  @Before
  public void setUp() throws Exception {
    int olcServerPort = PortProber.pickUnusedPort();
    String publicDirPath = tmpFolder.newFolder("public_dir").toString();
    String tmpDirPath = tmpFolder.newFolder("tmp_dir").toString();
    String xtsResourceDirPath = tmpFolder.newFolder("xts_resource_dir").toString();
    String xtsServerResourceDirPath = tmpFolder.newFolder("xts_server_resource_dir").toString();
    String xtsRootDirPath = tmpFolder.newFolder("xts_root_dir").toString();
    String versionFilePath = PathUtil.join(xtsRootDirPath, "android-cts/tools/version.txt");
    localFileUtil.writeToFile(versionFilePath, "fake_version");

    Path olcServerBinary =
        Path.of(
            RunfilesUtil.getRunfilesLocation(
                "java/com/google/devtools/mobileharness/infra/ats/common/"
                    + "olcserver/ats_olc_server_local_mode_deploy.jar"));

    systemProperties = ImmutableMap.of("XTS_ROOT", xtsRootDirPath);

    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "ats_console_olc_server_path",
            olcServerBinary.toString(),
            "detect_adb_device",
            "false",
            "external_adb_initializer_template",
            "true",
            "olc_server_port",
            Integer.toString(olcServerPort),
            "public_dir",
            publicDirPath,
            "simplified_log_format",
            "true",
            "tmp_dir_root",
            tmpDirPath,
            "xts_res_dir_root",
            xtsResourceDirPath,
            "xts_server_res_dir_root",
            xtsServerResourceDirPath);
    flags.setAllFlags(flagMap);
    ImmutableList<String> flagList =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    deviceInfraServiceFlags = FlagsString.of(String.join(" ", flagList), flagList);

    ByteArrayOutputStream consoleOutOutputStream = new ByteArrayOutputStream();
    consoleOutPrintStream = new PrintStream(consoleOutOutputStream, false, UTF_8);

    ByteArrayOutputStream consoleErrOutputStream = new ByteArrayOutputStream();
    consoleErrPrintStream = new PrintStream(consoleErrOutputStream, false, UTF_8);

    when(lineReader.getHistory()).thenReturn(history);

    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                "fake_console_id",
                deviceInfraServiceFlags,
                ImmutableList.of(),
                systemProperties,
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream,
                future -> {},
                /* parseCommandOnly= */ false));
    injector.injectMembers(this);
    atsConsole.injector = injector;
  }

  @Test
  public void startsConsoleWithHelp_exitConsoleAfterCommandExecution() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                "fake_console_id",
                deviceInfraServiceFlags,
                ImmutableList.of("help"),
                systemProperties,
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream,
                future -> {},
                /* parseCommandOnly= */ false));
    injector.injectMembers(this);
    atsConsole.injector = injector;

    atsConsole.run();

    verify(lineReader)
        .printAbove(startsWith("Using commandline arguments as starting command: [help]"));

    verify(history).add("help");
  }

  @Test
  public void runCtsV_enableAtsConsoleOlcServer() throws Exception {
    when(lineReader.readLine(anyString())).thenReturn("run -s abc cts-v").thenReturn("exit");

    atsConsole.run();

    verify(lineReader, timeout(TimeUnit.SECONDS.toMillis(15L)))
        .printAbove(
            (AttributedString)
                argThat(
                    argument -> argument.toString().contains("Detected no local Android devices")));
  }
}
