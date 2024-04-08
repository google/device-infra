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

package com.google.devtools.mobileharness.platform.android.xts.suite.subplan;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.util.Optional;
import java.util.regex.Pattern;
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
public final class SubPlanCreatorTest {

  private static final String TEST_DATA_DIR =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan/testdata/";

  private static final String PREV_REPORT_SOME_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "prev_report_some_failed.textproto");
  private static final String EXPECTED_SUBPLAN_FOR_ALL_RESULT_TYPES =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "expected_subplan_for_all_result_types.xml");
  private static final String EXPECTED_SUBPLAN_FOR_RESULT_TYPE_PASSED =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_result_type_passed.xml");
  private static final String EXPECTED_SUBPLAN_FOR_RESULT_TYPE_FAILED =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_result_type_failed.xml");

  private static final Splitter LINE_SPLITTER = Splitter.on(Pattern.compile("\r\n|\n|\r"));

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private PreviousResultLoader previousResultLoader;

  private LocalFileUtil realLocalFileUtil;

  @Inject private SubPlanCreator subPlanCreator;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    this.realLocalFileUtil = new LocalFileUtil();
  }

  @Test
  public void createAndSerializeSubPlan_allResultTypes() throws Exception {
    String subPlanName = "test-subplan";
    int sessionId = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionId))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionId(sessionId)
                .setSubPlanName(subPlanName)
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(subPlanFile.get().getName()).isEqualTo(subPlanName + ".xml");
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_ALL_RESULT_TYPES).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_resultTypePassed() throws Exception {
    int sessionId = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionId))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionId(sessionId)
                .setResultTypes(ImmutableSet.of(ResultType.PASSED))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_RESULT_TYPE_PASSED).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_resultTypeFailed() throws Exception {
    int sessionId = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionId))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionId(sessionId)
                .setResultTypes(ImmutableSet.of(ResultType.FAILED))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_RESULT_TYPE_FAILED).trim()));
  }

  private static String replaceLineBreak(String str) {
    return Joiner.on("\n").join(LINE_SPLITTER.omitEmptyStrings().splitToList(str));
  }
}
