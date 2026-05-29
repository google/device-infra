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
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.constant.BuiltinFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerChannel;
import com.google.devtools.mobileharness.shared.util.base.StackTraceExtractor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlags flags = new SetFlags();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs();
  @Rule public final PrintTestName printTestName = new PrintTestName();

  @Mock private LineReader lineReader;
  @Mock private History history;

  private PrintStream consoleOutPrintStream;
  private PrintStream consoleErrPrintStream;

  private FlagsString deviceInfraServiceFlags;
  private ImmutableMap<String, String> systemProperties;

  @Inject private AtsConsole atsConsole;
  @Inject @ServerChannel private Channel channel;

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
        ImmutableMap.<String, String>builder()
            .putAll(BuiltinFlags.atsConsoleFlagMap())
            // keep-sorted start
            .put("ats_console_olc_server_path", olcServerBinary.toString())
            .put("detect_adb_device", "false")
            .put("external_adb_initializer_template", "true")
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
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream),
            new CommonModule(ImmutableList.of(), System.getenv(), systemProperties));
    injector.injectMembers(this);
    atsConsole.injector = injector;
  }

  @After
  public void tearDown() throws Exception {
    cleanUpChannel();
    assertThat(StackTraceExtractor.extract(captureLogs.getLogs())).isEmpty();
  }

  @Test
  public void startsConsoleWithHelp_exitConsoleAfterCommandExecution() throws Exception {
    cleanUpChannel();
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                "fake_console_id",
                deviceInfraServiceFlags,
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream),
            new CommonModule(ImmutableList.of("help"), System.getenv(), systemProperties));
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

  @Test
  public void run_nonEmbeddedModeWithFilter_throwsException() throws Exception {
    flags.set("ats_console_olc_server_embedded_mode", "false");
    cleanUpChannel();
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                "fake_console_id",
                deviceInfraServiceFlags,
                lineReader,
                consoleOutPrintStream,
                consoleErrPrintStream),
            new CommonModule(
                ImmutableList.of("--xts_device_allowlist=id1", "run", "cts"),
                System.getenv(),
                systemProperties));
    injector.injectMembers(this);
    atsConsole.injector = injector;

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> atsConsole.run());
    assertThat(exception).hasMessageThat().contains("only supported in xTS Console embedded mode");
  }

  private void cleanUpChannel() throws InterruptedException {
    if (channel instanceof ManagedChannel managedChannel) {
      managedChannel.shutdownNow();
      managedChannel.awaitTermination(1L, TimeUnit.SECONDS);
    }
  }
}
