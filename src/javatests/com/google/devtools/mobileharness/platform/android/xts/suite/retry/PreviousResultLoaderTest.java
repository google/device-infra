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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
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
public final class PreviousResultLoaderTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind private LocalFileUtil localFileUtil;
  @Bind @Mock private ResultListerHelper resultListerHelper;
  @Bind @Mock private CompatibilityReportParser compatibilityReportParser;

  @Inject private PreviousResultLoader previousResultLoader;

  @Before
  public void setUp() {
    localFileUtil = new LocalFileUtil();
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void loadPreviousResult_useLegacyResult_byPreviousSessionIndex() throws Exception {
    Path resultsDir =
        prepareResultsDir(/* numOfResultDirs= */ 3, /* skipCreatingTestResultProtoFile= */ true);

    assertThat(
            previousResultLoader.loadPreviousResult(
                resultsDir,
                /* previousSessionIndex= */ 0,
                /* previousSessionResultDirName= */ null))
        .isEqualTo(Result.getDefaultInstance());
  }

  @Test
  public void loadPreviousResult_useLegacyResult_byPreviousSessionResultDirName() throws Exception {
    Path resultsDir =
        prepareResultsDir(/* numOfResultDirs= */ 3, /* skipCreatingTestResultProtoFile= */ true);

    assertThat(
            previousResultLoader.loadPreviousResult(
                resultsDir, /* previousSessionIndex= */ null, "result_dir_1"))
        .isEqualTo(Result.getDefaultInstance());
  }

  private Path prepareResultsDir(int numOfResultDirs, boolean skipCreatingTestResultProtoFile)
      throws Exception {
    Path resultsDir = tmpFolder.newFolder("results").toPath();
    ImmutableList.Builder<File> resultDirs = ImmutableList.builder();
    for (int i = 0; i < numOfResultDirs; i++) {
      Path resultDir = resultsDir.resolve(String.format("result_dir_%s", i));
      resultDirs.add(resultDir.toFile());
      resultDir.toFile().mkdirs();
      Path testResultXmlFile = resultDir.resolve(SuiteCommon.TEST_RESULT_XML_FILE_NAME);
      testResultXmlFile.toFile().createNewFile();
      when(compatibilityReportParser.parse(testResultXmlFile, /* shallow= */ false))
          .thenReturn(Optional.of(Result.getDefaultInstance()));
      if (!skipCreatingTestResultProtoFile) {
        resultsDir.resolve(SuiteCommon.TEST_RESULT_PB_FILE_NAME).toFile().createNewFile();
      }
    }

    when(resultListerHelper.listResultDirsInOrder(resultsDir.toAbsolutePath().toString()))
        .thenReturn(resultDirs.build());

    return resultsDir;
  }
}
