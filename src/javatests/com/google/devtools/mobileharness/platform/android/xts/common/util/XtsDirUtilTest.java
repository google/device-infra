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

import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class XtsDirUtilTest {

  @Rule public final SetFlags setFlags = new SetFlags();

  private static final String SESSION_ID = "test_session_123";

  @Test
  public void generateTimestampDirName_matchesPattern() {
    assertThat(XtsDirUtil.generateTimestampDirName())
        .matches("^\\d{4}\\.\\d{2}\\.\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{3}_\\d{4}$");
  }

  @Test
  public void getXtsDynamicDownloadRootDir() {
    setFlags.set("xts_res_dir_root", "/flag/xts/root");
    assertThat(XtsDirUtil.getXtsDynamicDownloadRootDir())
        .isEqualTo(Path.of("/flag/xts/root/mcts_dynamic_download"));
  }

  @Test
  public void getXtsDynamicDownloadDir() {
    setFlags.set("xts_res_dir_root", "/flag/xts/root");
    assertThat(XtsDirUtil.getXtsDynamicDownloadDir(SESSION_ID))
        .isEqualTo(Path.of("/flag/xts/root/mcts_dynamic_download/ats_session_test_session_123"));
  }

  @Test
  public void getXtsDynamicDownloadTestCasesDir() {
    setFlags.set("xts_res_dir_root", "/flag/xts/root");
    assertThat(XtsDirUtil.getXtsDynamicDownloadTestCasesDir(SESSION_ID))
        .isEqualTo(
            Path.of(
                "/flag/xts/root/mcts_dynamic_download/ats_session_test_session_123/android/xts/mcts/testcases"));
  }

  @Test
  public void getXtsDynamicDownloadJdkDir() {
    setFlags.set("xts_res_dir_root", "/flag/xts/root");
    assertThat(XtsDirUtil.getXtsDynamicDownloadJdkDir(SESSION_ID))
        .isEqualTo(
            Path.of(
                "/flag/xts/root/mcts_dynamic_download/ats_session_test_session_123/android/xts/mcts/tool/jdk"));
  }
}
