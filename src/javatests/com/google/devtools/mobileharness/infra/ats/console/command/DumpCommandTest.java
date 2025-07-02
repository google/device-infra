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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.constant.AtsConsoleDirs;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.plan.PlanHelper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine.ExitCode;

@RunWith(JUnit4.class)
public final class DumpCommandTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConsoleUtil consoleUtil;
  @Bind @Mock private ServerPreparer serverPreparer;
  @Bind @Mock private AtsSessionStub atsSessionStub;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private PlanHelper planHelper;
  @Bind @Mock private CommandExecutor cmdExecutor;
  @Bind private InstantSource instantSource = () -> Instant.ofEpochMilli(1000);

  @Inject private DumpCommand dumpCommand;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void dumpBugreport_success() throws Exception {
    when(serverPreparer.tryConnectToOlcServer()).thenReturn(Optional.empty());
    String tradefedPid = "12345";
    String mockTradefedStackTrace = "mock_tradefed_stack_trace";
    when(cmdExecutor.run(
            eq(Command.of("/bin/sh", "-c", "jps | grep CompatibilityConsole | awk '{print $1}'"))))
        .thenReturn(tradefedPid);
    when(cmdExecutor.run(eq(Command.of("/bin/sh", "-c", "jstack " + tradefedPid))))
        .thenReturn(mockTradefedStackTrace);

    assertThat(dumpCommand.bugreport()).isEqualTo(ExitCode.OK);
    verify(consoleUtil)
        .printlnStdout(
            "Bugreport dir: %s", DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000");
    verify(localFileUtil)
        .prepareDir(DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000");
    // ATS console logs:
    verify(localFileUtil)
        .copyFileOrDir(
            AtsConsoleDirs.getLogDir(),
            DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000/ats_console_logs_1000");
    // OLC server logs:
    verify(localFileUtil)
        .copyFileOrDir(
            OlcServerDirs.getLogDir(),
            DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000/olc_server_logs_1000");
    // ATS console stack trace:
    verify(localFileUtil)
        .writeToFile(
            eq(
                DirCommon.getTempDirRoot()
                    + "/ats_bugreport/ats_bugreport_1000/ats_console_stack_trace_1000.txt"),
            anyString());
    // Tradefed stack trace:
    verify(localFileUtil)
        .writeToFile(
            eq(
                DirCommon.getTempDirRoot()
                    + "/ats_bugreport/ats_bugreport_1000/tradefed_stack_trace_1000.txt"),
            eq(mockTradefedStackTrace));
    // Zipping files together:
    verify(localFileUtil)
        .zipDir(
            DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000",
            DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000.zip");
    verify(consoleUtil)
        .printlnStdout(
            "Output bugreport zip in %s",
            DirCommon.getTempDirRoot() + "/ats_bugreport/ats_bugreport_1000.zip");
  }

  @Test
  public void dumpStackTradefed_noRunningTradefed() throws Exception {
    when(cmdExecutor.run(
            eq(Command.of("/bin/sh", "-c", "jps | grep CompatibilityConsole | awk '{print $1}'"))))
        .thenReturn("");

    assertThat(dumpCommand.stack("tradefed")).isEqualTo(ExitCode.SOFTWARE);
    verify(consoleUtil).printlnStdout("No Tradefed process found.");
  }

  @Test
  public void dumpStackTradefed() throws Exception {
    String tradefedPid = "12345";
    String mockTradefedStackTrace = "mock_tradefed_stack_trace";
    when(cmdExecutor.run(
            eq(Command.of("/bin/sh", "-c", "jps | grep CompatibilityConsole | awk '{print $1}'"))))
        .thenReturn(tradefedPid);
    when(cmdExecutor.run(eq(Command.of("/bin/sh", "-c", "jstack " + tradefedPid))))
        .thenReturn(mockTradefedStackTrace);

    assertThat(dumpCommand.stack("tradefed")).isEqualTo(ExitCode.OK);
    verify(consoleUtil).printlnStdout(mockTradefedStackTrace);
  }
}
