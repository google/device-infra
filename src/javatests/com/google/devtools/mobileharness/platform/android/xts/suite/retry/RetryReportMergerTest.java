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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlanHelper;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.TextFormat;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RetryReportMergerTest {

  private static final String TEST_DATA_DIR =
      "javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry/testdata/";

  private static final String PREV_REPORT_ALL_PASSED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "prev_report_all_passed.textproto");
  private static final String MERGED_REPORT_FOR_ALL_PASSED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "merged_report_for_all_passed.textproto");

  private static final String PREV_REPORT_SOME_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "prev_report_some_failed.textproto");
  private static final String RETRY_REPORT_FOR_SOME_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "retry_report_for_some_failed.textproto");
  private static final String MERGED_REPORT_FOR_SOME_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "merged_report_for_some_failed.textproto");

  private static final String PREV_REPORT_ALL_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "prev_report_all_failed.textproto");
  private static final String RETRY_REPORT_FOR_ALL_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "retry_report_for_all_failed.textproto");
  private static final String MERGED_REPORT_FOR_ALL_FAILED_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(TEST_DATA_DIR + "merged_report_for_all_failed.textproto");

  private static final String PREV_REPORT_ALL_ASSUMPTION_FAILURE_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "prev_report_all_assumption_failure.textproto");
  private static final String RETRY_REPORT_FOR_ALL_ASSUMPTION_FAILURE_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "retry_report_for_all_assumption_failure.textproto");
  private static final String MERGED_REPORT_FOR_ALL_ASSUMPTION_FAILURE_TEXTPROTO =
      RunfilesUtil.getRunfilesLocation(
          TEST_DATA_DIR + "merged_report_for_all_assumption_failure.textproto");

  private static final Path RESULTS_DIR_PATH = Path.of("/path/to/xts-root/android-cts/results");

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Bind @Mock private PreviousResultLoader previousResultLoader;
  @Bind @Mock private RetryGenerator retryGenerator;

  private LocalFileUtil localFileUtil;

  @Inject private RetryReportMerger retryReportMerger;

  @Before
  public void setUp() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    this.localFileUtil = new LocalFileUtil();
  }

  @Test
  public void mergeReports_prevReportAllPassedTests() throws Exception {
    Result prevReport =
        TextFormat.parse(localFileUtil.readFile(PREV_REPORT_ALL_PASSED_TEXTPROTO), Result.class);

    Result expectedMergedReport =
        TextFormat.parse(
            localFileUtil.readFile(MERGED_REPORT_FOR_ALL_PASSED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(RESULTS_DIR_PATH, 0)).thenReturn(prevReport);
    SubPlan subPlan = new SubPlan();
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    Result mergedReport =
        retryReportMerger.mergeReports(
            RESULTS_DIR_PATH,
            0,
            /* retryType= */ null,
            /* retryResult= */ null,
            /* passedInModules= */ ImmutableList.of());

    assertThat(mergedReport).isEqualTo(expectedMergedReport);
  }

  @Test
  public void mergeReports_prevReportAllPassedTestsForAtsServer() throws Exception {
    Result prevReport =
        TextFormat.parse(localFileUtil.readFile(PREV_REPORT_ALL_PASSED_TEXTPROTO), Result.class);

    Result expectedMergedReport =
        TextFormat.parse(
            localFileUtil.readFile(MERGED_REPORT_FOR_ALL_PASSED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(RESULTS_DIR_PATH, "session_id"))
        .thenReturn(prevReport);
    SubPlan subPlan = new SubPlan();
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    Result mergedReport =
        retryReportMerger.mergeReports(
            RESULTS_DIR_PATH,
            "session_id",
            /* retryType= */ null,
            /* retryResult= */ null,
            /* passedInModules= */ ImmutableList.of());

    assertThat(mergedReport).isEqualTo(expectedMergedReport);
  }

  @Test
  public void mergeReports_prevReportHasSomeFailedTests() throws Exception {
    Result prevReport =
        TextFormat.parse(localFileUtil.readFile(PREV_REPORT_SOME_FAILED_TEXTPROTO), Result.class);
    Result retryReport =
        TextFormat.parse(
            localFileUtil.readFile(RETRY_REPORT_FOR_SOME_FAILED_TEXTPROTO), Result.class);
    Result expectedMergedReport =
        TextFormat.parse(
            localFileUtil.readFile(MERGED_REPORT_FOR_SOME_FAILED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(RESULTS_DIR_PATH, 0)).thenReturn(prevReport);
    SubPlan subPlan = new SubPlan();
    subPlan.addIncludeFilter(
        "arm64-v8a CtsAccelerationTestCases"
            + " android.acceleration.cts.HardwareAccelerationTest#testIsHardwareAccelerated");
    subPlan.addIncludeFilter(
        "arm64-v8a CtsAccelerationTestCases"
            + " android.acceleration.cts.SoftwareAccelerationTest#testIsHardwareAccelerated");
    subPlan.addIncludeFilter(
        "arm64-v8a CtsAccelerationTestCases"
            + " android.acceleration.cts.SoftwareAccelerationTest#testNotAttachedView");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    Result mergedReport =
        retryReportMerger.mergeReports(
            RESULTS_DIR_PATH,
            0,
            /* retryType= */ null,
            retryReport,
            /* passedInModules= */ ImmutableList.of());

    assertThat(mergedReport).isEqualTo(expectedMergedReport);
  }

  @Test
  public void mergeReports_prevReportAllFailedTests() throws Exception {
    Result prevReport =
        TextFormat.parse(localFileUtil.readFile(PREV_REPORT_ALL_FAILED_TEXTPROTO), Result.class);
    Result retryReport =
        TextFormat.parse(
            localFileUtil.readFile(RETRY_REPORT_FOR_ALL_FAILED_TEXTPROTO), Result.class);
    Result expectedMergedReport =
        TextFormat.parse(
            localFileUtil.readFile(MERGED_REPORT_FOR_ALL_FAILED_TEXTPROTO), Result.class);
    when(previousResultLoader.loadPreviousResult(RESULTS_DIR_PATH, 0)).thenReturn(prevReport);
    SubPlan subPlan = new SubPlan();
    subPlan.addIncludeFilter("arm64-v8a CtsAccelerationTestCases");
    subPlan.addIncludeFilter("arm64-v8a CtsAccelerationTestCases[instant]");
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    Result mergedReport =
        retryReportMerger.mergeReports(
            RESULTS_DIR_PATH,
            0,
            /* retryType= */ null,
            retryReport,
            /* passedInModules= */ ImmutableList.of());

    assertThat(mergedReport).isEqualTo(expectedMergedReport);
  }

  @Test
  public void mergeReports_prevReportAllAssumptionFailureTests_retryNotExecuted() throws Exception {
    Result prevReport =
        TextFormat.parse(
            localFileUtil.readFile(PREV_REPORT_ALL_ASSUMPTION_FAILURE_TEXTPROTO), Result.class);
    Result retryReport =
        TextFormat.parse(
            localFileUtil.readFile(RETRY_REPORT_FOR_ALL_ASSUMPTION_FAILURE_TEXTPROTO),
            Result.class);
    Result expectedMergedReport =
        TextFormat.parse(
            localFileUtil.readFile(MERGED_REPORT_FOR_ALL_ASSUMPTION_FAILURE_TEXTPROTO),
            Result.class);
    when(previousResultLoader.loadPreviousResult(RESULTS_DIR_PATH, 0)).thenReturn(prevReport);

    SubPlan subPlan =
        SubPlanHelper.createSubPlanForPreviousResult(
            prevReport,
            ImmutableSet.of("not_executed"),
            /* addSubPlanCmd= */ false,
            /* prevResultIncludeFilters= */ ImmutableSet.of(),
            /* prevResultExcludeFilters= */ ImmutableSet.of(),
            /* passedInModules= */ ImmutableSet.of());
    when(retryGenerator.generateRetrySubPlan(any())).thenReturn(subPlan);

    Result mergedReport =
        retryReportMerger.mergeReports(
            RESULTS_DIR_PATH,
            0,
            RetryType.NOT_EXECUTED,
            retryReport,
            /* passedInModules= */ ImmutableList.of());

    assertThat(mergedReport).isEqualTo(expectedMergedReport);
  }
}
