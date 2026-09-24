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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsChunkedTestCasesRestorerTest {

  private static final String XTS_ROOT_DIR = "/path/to/xts_root_dir";
  private static final String CHUNKED_TEST_CASES_DIR =
      XTS_ROOT_DIR + "/android-cts/chunked-testcases";
  private static final String TEST_CASES_DIR = XTS_ROOT_DIR + "/android-cts/testcases";
  private static final String RESTORER_BINARY = XTS_ROOT_DIR + "/android-cts/tools/cts_restorer";

  private static final Command EXPECTED_COMMAND =
      Command.of(
              RESTORER_BINARY,
              "--chunked-dir-path",
              CHUNKED_TEST_CASES_DIR,
              "--output-dir-path",
              TEST_CASES_DIR)
          .timeout(Duration.ofHours(1));

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private LocalFileUtil localFileUtil;
  @Mock private CommandExecutor commandExecutor;

  private XtsChunkedTestCasesRestorer restorer;

  @Before
  public void setUp() {
    restorer = new XtsChunkedTestCasesRestorer(localFileUtil, commandExecutor);
  }

  @Test
  public void getChunkedTestCasesDir_returnsPathUnderXtsDir() {
    assertThat(XtsChunkedTestCasesRestorer.getChunkedTestCasesDir(XTS_ROOT_DIR, "cts"))
        .isEqualTo(CHUNKED_TEST_CASES_DIR);
  }

  @Test
  public void restoreTestCases_notChunked_noOp() throws Exception {
    when(localFileUtil.isDirExist(CHUNKED_TEST_CASES_DIR)).thenReturn(false);

    restorer.restoreTestCases(XTS_ROOT_DIR, "cts");

    verify(commandExecutor, never()).run(any(Command.class));
  }

  @Test
  public void restoreTestCases_chunked_runsRestorer() throws Exception {
    when(localFileUtil.isDirExist(CHUNKED_TEST_CASES_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(RESTORER_BINARY)).thenReturn(true);
    when(commandExecutor.run(EXPECTED_COMMAND)).thenReturn("COMMAND_OUTPUT");

    restorer.restoreTestCases(XTS_ROOT_DIR, "cts");

    verify(commandExecutor).run(EXPECTED_COMMAND);
  }

  @Test
  public void restoreTestCases_restorerBinaryMissing_throwsUserFacingException() throws Exception {
    when(localFileUtil.isDirExist(CHUNKED_TEST_CASES_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(RESTORER_BINARY)).thenReturn(false);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> restorer.restoreTestCases(XTS_ROOT_DIR, "cts"));

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR);
    assertThat(exception).hasMessageThat().contains(RESTORER_BINARY);
    verify(commandExecutor, never()).run(any(Command.class));
  }

  @Test
  public void restoreTestCases_restorerFailed_throwsUserFacingException() throws Exception {
    when(localFileUtil.isDirExist(CHUNKED_TEST_CASES_DIR)).thenReturn(true);
    when(localFileUtil.isFileExist(RESTORER_BINARY)).thenReturn(true);
    // A real exception (rather than a Mockito mock) is required here because a mocked Throwable
    // has no stack trace, which breaks callers rendering the cause.
    MobileHarnessException restoreException =
        new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "restore failed");
    when(commandExecutor.run(EXPECTED_COMMAND))
        .thenAnswer(
            invocation -> {
              throw restoreException;
            });

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> restorer.restoreTestCases(XTS_ROOT_DIR, "cts"));

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR);
    assertThat(exception).hasCauseThat().isSameInstanceAs(restoreException);
  }
}
