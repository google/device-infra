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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryReportMerger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Path;
import javax.inject.Inject;
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
public final class SessionResultHandlerUtilTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  LocalFileUtil localFileUtil = new LocalFileUtil();

  @Bind @Mock CompatibilityReportMerger compatibilityReportMerger;
  @Bind @Mock CompatibilityReportCreator reportCreator;
  @Bind @Mock RetryReportMerger retryReportMerger;
  @Bind @Mock PreviousResultLoader previousResultLoader;
  @Bind @Mock SessionInfo sessionInfo;

  @Inject private SessionResultHandlerUtil sessionResultHandlerUtil;

  @Before
  public void setup() {

    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void copyRetryFiles_success() throws Exception {
    Path oldDir = folder.newFolder("old_dir").toPath();
    Path newDir = folder.newFolder("new_dir").toPath();
    oldDir.resolve("file1.txt").toFile().createNewFile();
    oldDir.resolve("file2.txt").toFile().createNewFile();
    oldDir.resolve("dir1").toFile().mkdir();
    oldDir.resolve("dir1").resolve("file3.txt").toFile().createNewFile();
    oldDir.resolve("dir1").resolve("file4.txt").toFile().createNewFile();
    oldDir.resolve("dir2").toFile().mkdir();
    oldDir.resolve("dir2").resolve("file5.txt").toFile().createNewFile();

    newDir.resolve("file1.txt").toFile().createNewFile();
    newDir.resolve("dir1").toFile().mkdir();
    newDir.resolve("dir1").resolve("file3.txt").toFile().createNewFile();
    newDir.resolve("dir2").toFile().mkdir();
    newDir.resolve("dir2").resolve("file5.txt").toFile().createNewFile();

    sessionResultHandlerUtil.copyRetryFiles(oldDir.toString(), newDir.toString());
    assertThat(newDir.resolve("file1.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("file2.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").resolve("file3.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir1").resolve("file4.txt").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir2").toFile().exists()).isTrue();
    assertThat(newDir.resolve("dir2").resolve("file5.txt").toFile().exists()).isTrue();
  }
}
