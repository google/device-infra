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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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

  private static final String PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "prev_report_some_failed_and_not_executed.textproto");
  private static final String EXPECTED_SUBPLAN_FOR_EXCLUDE_NON_TF_MODULE =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_all_result_types_exclude_nontf.xml");
  private static final String EXPECTED_SUBPLAN_FOR_EXCLUDE_TF_MODULE =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_all_result_types_exclude_tf_module.xml");
  private static final String EXPECTED_SUBPLAN_FOR_EXCLUDE_TF_TEST_CASE =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_all_result_types_exclude_tf_testcase.xml");
  private static final String EXPECTED_SUBPLAN_FOR_ALL_RESULT_TYPES =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "expected_subplan_for_all_result_types.xml");
  private static final String EXPECTED_SUBPLAN_FOR_RESULT_TYPE_PASSED =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_result_type_passed.xml");
  private static final String EXPECTED_SUBPLAN_FOR_RESULT_TYPE_FAILED =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_result_type_failed.xml");
  private static final String EXPECTED_SUBPLAN_FOR_RESULT_TYPE_NOT_EXECUTED =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "expected_subplan_for_result_type_not_executed.xml");

  private static final Splitter LINE_SPLITTER = Splitter.on(Pattern.compile("\r\n|\n|\r"));

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private ConfigurationUtil configurationUtil;

  private LocalFileUtil realLocalFileUtil;

  @Inject private SubPlanCreator subPlanCreator;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    this.realLocalFileUtil = new LocalFileUtil();
    when(configurationUtil.getConfigsV2FromDirs(any())).thenReturn(ImmutableMap.of());
  }

  @Test
  public void createAndSerializeSubPlan_allResultTypes() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
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
  public void createAndSerializeSubPlan_excludeNonTfModule() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);
    Configuration config =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("HelloWorldTest"))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any())).thenReturn(ImmutableMap.of("key", config));

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setPassedInExcludeFilters(ImmutableSet.of("HelloWorldTest"))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(subPlanFile.get().getName()).isEqualTo(subPlanName + ".xml");
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_EXCLUDE_NON_TF_MODULE).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_excludeTfModule() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);
    Configuration config =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("HelloWorldTest"))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any())).thenReturn(ImmutableMap.of("key", config));

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setPassedInExcludeFilters(ImmutableSet.of("CtsAccelerationTestCases"))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(subPlanFile.get().getName()).isEqualTo(subPlanName + ".xml");
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_EXCLUDE_TF_MODULE).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_excludeTfTestCase() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);
    Configuration config =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule("HelloWorldTest"))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any())).thenReturn(ImmutableMap.of("key", config));

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setPassedInExcludeFilters(
                    ImmutableSet.of(
                        "arm64-v8a CtsAccelerationTestCases android.acceleration.cts."
                            + "SoftwareAccelerationTest#testIsHardwareAccelerated"))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(subPlanFile.get().getName()).isEqualTo(subPlanName + ".xml");
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_EXCLUDE_TF_TEST_CASE).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_resultTypePassed() throws Exception {
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
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
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
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

  @Test
  public void createAndSerializeSubPlan_resultTypeNotExecuted() throws Exception {
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setResultTypes(ImmutableSet.of(ResultType.NOT_EXECUTED))
                .build());

    assertThat(subPlanFile).isPresent();
    assertThat(
            replaceLineBreak(
                realLocalFileUtil.readFile(subPlanFile.get().toPath().toString()).trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(EXPECTED_SUBPLAN_FOR_RESULT_TYPE_NOT_EXECUTED).trim()));
  }

  @Test
  public void createAndSerializeSubPlan_setModule() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setModule("AddedModule")
                .build());

    assertThat(subPlanFile).isPresent();
    SubPlan genSubPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subPlanFile.get())) {
      genSubPlan.parse(inputStream);
    }
    assertThat(genSubPlan.getIncludeFiltersMultimap())
        .containsEntry("AddedModule", SubPlan.ALL_TESTS_IN_MODULE);
  }

  @Test
  public void createAndSerializeSubPlan_setModuleWithAbiAndTest() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setModule("AddedModule")
                .setAbi("armeabi-v7a")
                .setTest("android.test.Foo#test1")
                .build());

    assertThat(subPlanFile).isPresent();
    SubPlan genSubPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subPlanFile.get())) {
      genSubPlan.parse(inputStream);
    }
    assertThat(genSubPlan.getIncludeFiltersMultimap())
        .containsEntry("armeabi-v7a AddedModule", "android.test.Foo#test1");
  }

  @Test
  public void createAndSerializeSubPlan_setModuleForNonTf() throws Exception {
    String subPlanName = "test-subplan";
    int sessionIndex = 0;
    String xtsType = "cts";
    File xtsRootDir = temporaryFolder.newFolder("xts_root_dir");
    temporaryFolder.newFolder(xtsRootDir.getName(), String.format("android-%s", xtsType));

    Result prevReport =
        TextFormat.parse(
            realLocalFileUtil.readFile(PREV_REPORT_SOME_FAILED_AND_NOT_EXECUTED_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(
            XtsDirUtil.getXtsResultsDir(xtsRootDir.toPath(), xtsType), sessionIndex))
        .thenReturn(prevReport);

    Optional<File> subPlanFile =
        subPlanCreator.createAndSerializeSubPlan(
            AddSubPlanArgs.builder()
                .setXtsRootDir(xtsRootDir.toPath())
                .setXtsType(xtsType)
                .setSessionIndex(sessionIndex)
                .setSubPlanName(subPlanName)
                .setModule("AddedModule")
                .setIsNonTradefedModule(true)
                .build());

    assertThat(subPlanFile).isPresent();
    SubPlan genSubPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subPlanFile.get())) {
      genSubPlan.parse(inputStream);
    }
    assertThat(genSubPlan.getNonTfIncludeFiltersMultimap())
        .containsEntry("AddedModule", SubPlan.ALL_TESTS_IN_MODULE);
  }

  private static String replaceLineBreak(String str) {
    return Joiner.on("\n").join(LINE_SPLITTER.omitEmptyStrings().splitToList(str));
  }
}
