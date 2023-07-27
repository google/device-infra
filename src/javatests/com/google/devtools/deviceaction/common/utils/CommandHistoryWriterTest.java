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

package com.google.devtools.deviceaction.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecord;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
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
public final class CommandHistoryWriterTest {

  private static final Instant FAKE_INSTANT = Instant.ofEpochSecond(1230000000);
  private static final String GEN_FILE_DIR = "gen_file_dir";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private CommandRecord commandRecord;

  private File genDir;

  private File cmdHistory;

  private CommandHistoryWriter writer;

  @Before
  public void setUp() throws Exception {
    genDir = tmpFolder.newFolder(GEN_FILE_DIR);
    cmdHistory = genDir.toPath().resolve("command_history.txt").toFile();
    writer = new CommandHistoryWriter(genDir.toPath(), new LocalFileUtil());
    writer.init();
  }

  @Test
  public void onAddCommandResult_expectedResults() throws Exception {
    when(commandRecord.command()).thenReturn(ImmutableList.of(".../platform-tools/adb", "shell"));
    when(commandRecord.startTime()).thenReturn(FAKE_INSTANT);

    assertThat(cmdHistory.exists()).isTrue();
    assertThat(Files.size(cmdHistory.toPath())).isEqualTo(0);
    writer.onAddCommandResult(commandRecord, FakeCommandResult.of("pass", "", 0));
    assertThat(Files.readAllLines(cmdHistory.toPath())).hasSize(5);
  }

  @Test
  public void onAddCommandResult_skipCommonCommands() throws Exception {
    when(commandRecord.command())
        .thenReturn(ImmutableList.of("adb", "shell", "am broadcast  check.if.device.is.ready"));
    when(commandRecord.startTime()).thenReturn(FAKE_INSTANT);

    assertThat(cmdHistory.exists()).isTrue();
    assertThat(Files.size(cmdHistory.toPath())).isEqualTo(0);
    writer.onAddCommandResult(commandRecord, FakeCommandResult.of("pass", "", 0));
    assertThat(Files.readAllLines(cmdHistory.toPath())).hasSize(0);
  }
}
