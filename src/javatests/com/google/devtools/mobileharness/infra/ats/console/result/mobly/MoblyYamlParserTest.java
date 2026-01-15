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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MoblyYamlParserTest {
  private static final String GEN_FILE_DIR_PASS =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/pass/test_summary.yaml");

  private static final String GEN_FILE_DIR_FAIL =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/fail/test_summary.yaml");

  private static final String GEN_FILE_DIR_ERROR =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/error/test_summary.yaml");

  private static final String GEN_FILE_DIR_SKIP =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/skip/test_summary.yaml");

  private static final String GEN_FILE_DIR_SETUP_CLASS_ERROR =
      TestRunfilesUtil.getRunfilesLocation(
          "result/mobly/testdata/parser/setup_class_error/error_test_summary.yaml");

  private static final String GEN_FILE_DIR_SETUP_CLASS_FAIL =
      TestRunfilesUtil.getRunfilesLocation(
          "result/mobly/testdata/parser/setup_class_error/fail_test_summary.yaml");

  private static final String GEN_FILE_DIR_RETRY =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/retry/test_summary.yaml");

  private static final String GEN_FILE_DIR_REPEAT =
      TestRunfilesUtil.getRunfilesLocation("result/mobly/testdata/parser/repeat/test_summary.yaml");

  private MoblyYamlParser parser;

  @Before
  public void setUp() {
    parser = new MoblyYamlParser();
  }

  @Test
  public void parsePassTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_PASS);

    assertThat(results).hasSize(3);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry.getEndTime()).hasValue(1663584280787L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    assertThat(results.get(2)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(2);
    assertThat(summaryEntry)
        .isEqualTo(MoblySummaryEntry.builder().setRequested(2).setExecuted(2).setPassed(2).build());
  }

  @Test
  public void parseFailTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_FAIL);

    assertThat(results).hasSize(3);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry.getEndTime()).hasValue(1663584280787L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.FAIL);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    assertThat(results.get(2)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(2);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(2)
                .setExecuted(2)
                .setPassed(1)
                .setFailed(1)
                .build());
  }

  @Test
  public void parseErrorTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_ERROR);

    assertThat(results).hasSize(3);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry.getEndTime()).hasValue(1663584280787L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    assertThat(results.get(2)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(2);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(2)
                .setExecuted(2)
                .setPassed(1)
                .setError(1)
                .build());
  }

  @Test
  public void parseSkipTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_SKIP);

    assertThat(results).hasSize(3);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry.getEndTime()).hasValue(1663584280787L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.SKIP);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    assertThat(results.get(2)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(2);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(2)
                .setExecuted(1)
                .setSkipped(1)
                .setPassed(1)
                .build());
  }

  @Test
  public void parseSetupClassErrorTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_SETUP_CLASS_ERROR);

    assertThat(results).hasSize(4);

    // Test cases in a class with a setup_class error should have their results set to ERROR.
    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    // Test case in a new test class without a setup_class error, keep original result.
    assertThat(results.get(2)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(2);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.SKIP);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest2");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world2_1");

    // Summary entry should have error count set to 2.
    assertThat(results.get(3)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(3);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(3)
                .setExecuted(0)
                .setSkipped(1)
                .setPassed(0)
                .setError(2)
                .build());
  }

  @Test
  public void parseSetupClassFailTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_SETUP_CLASS_FAIL);

    assertThat(results).hasSize(4);

    // Test cases in a class with a setup_class fail should have their results set to ERROR.
    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(1);
    assertThat(entry.getBeginTime()).hasValue(1663584280794L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_2");

    // Test case in a new test class without a setup_class error, keep original result.
    assertThat(results.get(2)).isInstanceOf(MoblyTestEntry.class);
    entry = (MoblyTestEntry) results.get(2);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.SKIP);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest2");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world2_1");

    // Summary entry should have error count set to 2.
    assertThat(results.get(3)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(3);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(3)
                .setExecuted(0)
                .setSkipped(1)
                .setPassed(0)
                .setError(2)
                .build());
  }

  @Test
  public void parseRetryTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_RETRY);

    assertThat(results).hasSize(2);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry = (MoblyTestEntry) results.get(0);
    assertThat(entry.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry.getEndTime()).hasValue(1663584283819L);
    assertThat(entry.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry.getTestClass()).isEqualTo("HelloWorldTest1");
    assertThat(entry.getTestName()).isEqualTo("test_hello_world1_1");

    assertThat(results.get(1)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(1);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(1)
                .setExecuted(2)
                .setPassed(1)
                .setFailed(0)
                .setError(0)
                .setSkipped(0)
                .build());
  }

  @Test
  public void parseRepeatTest() throws Exception {
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(GEN_FILE_DIR_REPEAT);

    assertThat(results).hasSize(4);

    assertThat(results.get(0)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry1 = (MoblyTestEntry) results.get(0);
    assertThat(entry1.getTestClass()).isEqualTo("HelloWorldTestRepeat");
    assertThat(entry1.getTestName()).isEqualTo("test_hello_world1_1");
    assertThat(entry1.getResult()).isEqualTo(MoblyResult.ERROR);
    assertThat(entry1.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry1.getEndTime()).hasValue(1663584284820L);
    assertThat(entry1.getDetails())
        .hasValue(
            """
            teardown details

            ----------------------------------------------

            Last failure: failure details

            Refer to the test_summary.yaml for all attempts.\
            """);
    assertThat(entry1.getStacktrace())
        .hasValue(
            """
            teardown stacktrace

            ----------------------------------------------

            Last failure:

            failure stacktrace

            Refer to the test_summary.yaml for all attempts.\
            """);

    assertThat(results.get(1)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry2 = (MoblyTestEntry) results.get(1);
    assertThat(entry2.getTestClass()).isEqualTo("HelloWorldTestRepeatAllPass");
    assertThat(entry2.getTestName()).isEqualTo("test_hello_world2_1");
    assertThat(entry2.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry2.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry2.getEndTime()).hasValue(1663584284821L);

    assertThat(results.get(2)).isInstanceOf(MoblyTestEntry.class);
    MoblyTestEntry entry3 = (MoblyTestEntry) results.get(2);
    assertThat(entry3.getTestClass()).isEqualTo("HelloWorldTestRepeatFailNoTeardown");
    assertThat(entry3.getTestName()).isEqualTo("test_hello_world3_1");
    assertThat(entry3.getResult()).isEqualTo(MoblyResult.PASS);
    assertThat(entry3.getBeginTime()).hasValue(1663584277766L);
    assertThat(entry3.getEndTime()).hasValue(1663584283819L);

    assertThat(results.get(3)).isInstanceOf(MoblySummaryEntry.class);
    MoblySummaryEntry summaryEntry = (MoblySummaryEntry) results.get(3);
    assertThat(summaryEntry)
        .isEqualTo(
            MoblySummaryEntry.builder()
                .setRequested(3)
                .setExecuted(7)
                .setPassed(2)
                .setFailed(0)
                .setError(1)
                .setSkipped(0)
                .build());
  }
}
