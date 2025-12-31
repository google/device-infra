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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyControllerInfoEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyUserDataEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MoblyTestInfoMapHelperTest {
  private static final String GEN_FILE_DIR_PASS =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util/testdata/parser/pass");
  private static final String GEN_FILE_DIR_ERROR =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util/testdata/parser/error");
  private static final String GEN_FILE_DIR_FAIL =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util/testdata/parser/fail");
  private static final String GEN_FILE_DIR_RETRIES =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util/testdata/parser/retries");

  private static final String JOB_NAME = "TestJob";
  private static final String TEST_NAME = "TestName";
  private static final String SUBTEST_NAME = "SubTestName";
  private static final String TEST_SIGNATURE = "TestSignature";
  private static final String RETRY_PARENT = "RetryParent";

  private MoblyTestInfoMapHelper mapper;
  private MoblyYamlParser parser;

  @Before
  public void before() {
    mapper = new MoblyTestInfoMapHelper();
    parser = new MoblyYamlParser();
  }

  @Test
  public void mapWithParser() throws Exception {
    // Parse test_summary.yaml file
    String summaryPath = PathUtil.join(GEN_FILE_DIR_ERROR, "mobly_logs", "test_summary.yaml");
    ImmutableList<MoblyYamlDocEntry> results = parser.parse(summaryPath);

    // Run list of results through MoblyTestInfoMapHelper
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_ERROR);
    mapper.map(testInfo, results);

    // Assert
    assertThat(testInfo.subTests().getAll()).hasSize(4);
    // breakfast value is duplicated in yaml file. Latest value is recorded. In this case, false.
    assertThat(testInfo.properties().get("breakfast")).isEqualTo("false");
    assertThat(testInfo.properties().get("pancake_quality")).isEqualTo("8.4");
    assertThat(testInfo.properties().get("lunch")).isEqualTo("false");
    assertThat(testInfo.properties().get("metrics"))
        .isEqualTo("{food=[banana, sandwich], drink=OJ}");

    // TestInfo with USERDATA specified of this test class, won't be overwritten by other USERDATA
    // not specify to this class or by testInfo of test cases under this test class.
    TestInfo subTestInfo = testInfo.subTests().getByName("HelloWorldTest").get(0);
    assertThat(subTestInfo.properties().get("mobly_test_class")).isEqualTo("HelloWorldTest");
    assertThat(subTestInfo.properties().get("test_type")).isEqualTo("mobly_class");
    assertThat(subTestInfo.properties().get("breakfast")).isEqualTo("true");
    assertThat(subTestInfo.properties().get("pancake_quality")).isEqualTo("8.2");

    subTestInfo = testInfo.subTests().getByName("test_hello").get(0);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(subTestInfo.status().get()).isEqualTo(TestStatus.DONE);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_ERROR);
    assertThat(subTestInfo.properties().get("mobly_test_class")).isEqualTo("HelloWorldTest");
    assertThat(subTestInfo.properties().get("mobly_uid")).isEqualTo("uuid-test-hello");
    assertThat(subTestInfo.properties().get("mobly_begin_time")).isEqualTo("1485396391375");
    assertThat(subTestInfo.properties().get("mobly_end_time")).isEqualTo("1485396391386");
    assertThat(subTestInfo.properties().get("test_type")).isEqualTo("mobly_test");
    // TestInfo with USERDATA specified of this test cases, won't be overwritten by other USERDATA
    // not specify to this class.
    assertThat(subTestInfo.properties().get("breakfast")).isEqualTo("true");
    assertThat(subTestInfo.properties().get("pancake_quality")).isEqualTo("8.3");

    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_STACK_TRACE_KEY))
        .isEqualTo("Line 1\\nLine2");
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY))
        .contains("Details: Test failure message");

    subTestInfo = testInfo.subTests().getByName("test_hello_again").get(0);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(subTestInfo.status().get()).isEqualTo(TestStatus.DONE);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_ERROR);
    assertThat(subTestInfo.properties().get("mobly_test_class")).isEqualTo("HelloWorldTest");
    assertThat(subTestInfo.properties().get("mobly_uid")).isEqualTo("uuid-test-hello-again");
    assertThat(subTestInfo.properties().get("mobly_begin_time")).isEqualTo("1485396391375");
    assertThat(subTestInfo.properties().get("mobly_end_time")).isEqualTo("1485396391386");
    assertThat(subTestInfo.properties().get("test_type")).isEqualTo("mobly_test");
    String errorMessage =
        subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY);
    assertThat(errorMessage).contains("Details: Test error message");
    assertThat(errorMessage)
        .contains("Stacktrace: extra error stack line 1\\nextra error stack line 2");
    assertThat(errorMessage).contains("------> on_fail");
    assertThat(errorMessage).contains("Extras: {}");

    subTestInfo = testInfo.subTests().getByName("test_hi").get(0);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(subTestInfo.status().get()).isEqualTo(TestStatus.DONE);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_ERROR);
    assertThat(subTestInfo.properties().get("mobly_test_class")).isEqualTo("HelloWorldTest");
    assertThat(subTestInfo.properties().get("mobly_uid")).isEqualTo("uuid-test-hi");
    assertThat(subTestInfo.properties().get("mobly_begin_time")).isEqualTo("1485396391387");
    assertThat(subTestInfo.properties().get("mobly_end_time")).isEqualTo("1485396391394");
    assertThat(subTestInfo.properties().get("test_type")).isEqualTo("mobly_test");
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY))
        .contains("Unable to parse the content of extra errors");
  }

  @Test
  public void mapUserDataSpongeMap() throws Exception {
    // Setup
    Map<String, String> moblyMap = new HashMap<>();
    moblyMap.put("breakfast", "true");
    moblyMap.put("pancake_quality", "8.4");

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("sponge_properties", moblyMap);
    userMap.put("timestamp", "10");
    userMap.put("Type", "UserData");

    MoblyUserDataEntry entry =
        MoblyUserDataEntry.builder().setTimestamp("10").setUserDataMap(userMap).build();

    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);

    // Run
    mapper.map(testInfo, entry);

    // Assert
    assertThat(testInfo.properties().get("breakfast")).isEqualTo("true");
    assertThat(testInfo.properties().get("pancake_quality")).isEqualTo("8.4");
  }

  @Test
  public void mapUserDataSpongeMapWithOnlyClassSpecified() throws Exception {
    // Setup
    Map<String, String> moblyMap = new HashMap<>();
    moblyMap.put("breakfast", "true");
    moblyMap.put("pancake_quality", "8.4");

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("Test Class", "HelloWorldTest");
    userMap.put("sponge_properties", moblyMap);
    userMap.put("timestamp", "10");
    userMap.put("Type", "UserData");

    MoblyUserDataEntry entry =
        MoblyUserDataEntry.builder().setTimestamp("10").setUserDataMap(userMap).build();

    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    testInfo.properties().add("mobly_test_class", "HelloWorldTest");
    testInfo.properties().add("test_type", "mobly_class");

    // Run
    mapper.map(testInfo, entry);

    // Assert
    assertThat(testInfo.properties().get("breakfast")).isEqualTo("true");
    assertThat(testInfo.properties().get("pancake_quality")).isEqualTo("8.4");
  }

  @Test
  public void mapUserDataSpongeMapWithClassNotFound() throws Exception {
    // Setup
    Map<String, String> moblyMap = new HashMap<>();
    moblyMap.put("breakfast", "true");
    moblyMap.put("pancake_quality", "8.4");

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("Test Class", "HelloWorldTest");
    userMap.put("sponge_properties", moblyMap);
    userMap.put("timestamp", "10");
    userMap.put("Type", "UserData");

    MoblyUserDataEntry entry =
        MoblyUserDataEntry.builder().setTimestamp("10").setUserDataMap(userMap).build();

    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    testInfo.properties().add("mobly_test_class", "AnotherHelloWorldTest");

    // Run
    mapper.map(testInfo, entry);

    // Assert
    assertThat(testInfo.properties().has("breakfast")).isFalse();
    assertThat(testInfo.properties().has("pancake_quality")).isFalse();
  }

  @Test
  public void mapUserDataNoSponge() throws Exception {
    // Setup
    Map<String, Object> userMap = new HashMap<>();
    userMap.put("timestamp", "10");
    userMap.put("Type", "UserData");
    userMap.put("breakfast", "true");
    userMap.put("pancake_quality", "8.4");
    MoblyUserDataEntry entry =
        MoblyUserDataEntry.builder().setTimestamp("10").setUserDataMap(userMap).build();
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);

    // Run
    mapper.map(testInfo, entry);

    // Assert
    assertThat(testInfo.properties().has("breakfast")).isFalse();
    assertThat(testInfo.properties().has("pancake_quality")).isFalse();
  }

  @Test
  public void mapPassRecordDocument() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    testInfo.properties().add("fake_key", "fake_value");
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.PASS)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .build();

    // Run
    mapper.map(testInfo, entry);
    TestInfo subTestInfo = testInfo.subTests().getOnly();

    // Assert
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY))
        .isEqualTo("TestClass");
    assertThat(subTestInfo).isNotNull();
    assertThat(subTestInfo.locator().getName()).isEqualTo(SUBTEST_NAME);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(subTestInfo.status().get()).isEqualTo(TestStatus.DONE);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.TEST_TYPE_KEY)).isNotNull();
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.TEST_TYPE_KEY))
        .isEqualTo(MoblyConstant.TestProperty.MOBLY_TEST_VALUE);
    assertThat(subTestInfo.properties().get("mobly_begin_time")).isEqualTo("10");
    assertThat(subTestInfo.properties().get("mobly_end_time")).isEqualTo("20");
  }

  @Test
  public void mapFailRecordDocument() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_FAIL);
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.FAIL)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setDetails("Test failed")
            .setTestClass("TestClass")
            .build();

    // Run
    mapper.map(testInfo, entry);
    TestInfo subTestInfo = testInfo.subTests().getOnly();

    // Assert
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY))
        .isEqualTo("TestClass");
    assertThat(subTestInfo).isNotNull();
    assertThat(subTestInfo.locator().getName()).isEqualTo(SUBTEST_NAME);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_FAILURE);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY))
        .contains("Test failed");
  }

  @Test
  public void mapErrorRecordDocument() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_ERROR);
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.ERROR)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .setDetails("something happened")
            .setStacktrace("big stacktrace here")
            .build();

    // Run
    mapper.map(testInfo, entry);
    TestInfo subTestInfo = testInfo.subTests().getOnly();

    // Assert
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY))
        .isEqualTo("TestClass");
    assertThat(subTestInfo).isNotNull();
    assertThat(subTestInfo.locator().getName()).isEqualTo(SUBTEST_NAME);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY))
        .contains("something happened");
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_STACK_TRACE_KEY))
        .isEqualTo("big stacktrace here");
  }

  @Test
  public void mapSkipRecordDocument() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.SKIP)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setDetails("Test failed")
            .setTestClass("TestClass")
            .build();

    // Run
    mapper.map(testInfo, entry);
    TestInfo subTestInfo = testInfo.subTests().getOnly();

    // Assert
    assertThat(subTestInfo).isNotNull();
    assertThat(subTestInfo.locator().getName()).isEqualTo(SUBTEST_NAME);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.SKIP);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_SKIPPED);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.SKIP_REASON_KEY))
        .contains("Test failed");
  }

  @Test
  public void map_testEntryWithRetries_propagatesRequiredFields() throws Exception {
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_RETRIES);
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.FAIL)
            .setTestClass("TestClass")
            .setSignature(TEST_SIGNATURE)
            .setParent(
                MoblyTestEntry.Parent.create(RETRY_PARENT, MoblyTestEntry.TestParentType.RETRY))
            .build();

    mapper.map(testInfo, entry);
    TestInfo subTestInfo = testInfo.subTests().getOnly();

    assertThat(subTestInfo).isNotNull();
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_SIGNATURE_KEY))
        .isEqualTo(TEST_SIGNATURE);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_RETRY_PARENT_KEY))
        .isEqualTo(RETRY_PARENT);
  }

  @Test
  public void mapListRootTestInfo() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    List<MoblyYamlDocEntry> entries = buildNewMoblySummaryEntryList();

    // Run
    mapper.map(testInfo, entries);

    // Assert
    assertThat(testInfo.subTests().getAll()).hasSize(4);
  }

  @Test
  public void mapListRootTestInfoProperties() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    List<MoblyYamlDocEntry> entries = buildNewMoblySummaryEntryList();

    // Run
    mapper.map(testInfo, entries);

    // Assert
    assertThat(testInfo.properties().get("breakfast")).isEqualTo("true");
    assertThat(testInfo.properties().get("pancake_quality")).isEqualTo("8.4");
    assertThat(testInfo.properties().has("dinner")).isFalse();
    assertThat(testInfo.properties().has("steak_quality")).isFalse();
  }

  @Test
  public void mapListSubtestsTestInfo() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    List<MoblyYamlDocEntry> entries = buildNewMoblySummaryEntryList();

    // Run
    mapper.map(testInfo, entries);

    // Assert
    TestInfo subTestInfo = testInfo.subTests().getByName("Test_1").get(0);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY))
        .isEqualTo("PancakeClass");

    subTestInfo = testInfo.subTests().getByName("Test_2").get(0);
    assertThat(subTestInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(subTestInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(ExtErrorId.MOBLY_TEST_CASE_FAILURE);
    assertThat(subTestInfo.properties().get(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY))
        .isEqualTo("WaffleClass");
  }

  @Test
  public void mapListSubtestsTestInfoProperties() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    List<MoblyYamlDocEntry> entries = buildNewMoblySummaryEntryList();

    // Run
    mapper.map(testInfo, entries);

    // Assert
    TestInfo subTestInfo = testInfo.subTests().getByName("Test_1").get(0);
    assertThat(subTestInfo.properties().has("dinner")).isFalse();
    assertThat(subTestInfo.properties().has("steak_quality")).isFalse();

    subTestInfo = testInfo.subTests().getByName("Test_2").get(0);
    assertThat(subTestInfo.properties().get("dinner")).isEqualTo("true");
    assertThat(subTestInfo.properties().get("steak_quality")).isEqualTo("2.3");
  }

  @Test
  public void mapListUserDataOrder() throws Exception {
    // Setup
    TestInfo testInfo = buildNewTestInfo(GEN_FILE_DIR_PASS);
    List<MoblyYamlDocEntry> entries = new ArrayList<>();

    // Reverse the order
    addUserMapsToMoblySummaryEntryList(entries);
    addMoblyTestEntriesToMoblySummaryEntryList(entries);
    entries.add(MoblyControllerInfoEntry.builder().setDevices("devices").build());

    // Run
    mapper.map(testInfo, entries);

    // Assert
    TestInfo subTestInfo = testInfo.subTests().getByName("Test_1").get(0);
    assertThat(subTestInfo.properties().has("dinner")).isFalse();
    assertThat(subTestInfo.properties().has("steak_quality")).isFalse();
    subTestInfo = testInfo.subTests().getByName("Test_2").get(0);
    assertThat(subTestInfo.properties().get("dinner")).isEqualTo("true");
    assertThat(subTestInfo.properties().get("steak_quality")).isEqualTo("2.3");
  }

  @Test
  public void createSpongeDescription() {
    // Setup
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.ERROR)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .setDetails("Test failure message")
            .addExtraError(
                MoblyTestEntry.ExtraError.builder()
                    .setPosition("on_fail")
                    .setDetails("Test error message")
                    .build())
            .build();

    // Run
    String result = mapper.createSpongeDescription(entry);

    // Assert
    assertThat(result).contains("Test failure message");
    assertThat(result).contains("on_fail");
  }

  @Test
  public void createSpongeDescriptionExtraErrorsList() {
    // Setup
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.ERROR)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .setDetails("Test failure message")
            .addExtraError(MoblyTestEntry.ExtraError.builder().setPosition("on_fail").build())
            .addExtraError(
                MoblyTestEntry.ExtraError.builder().setPosition("second_position").build())
            .build();

    // Run
    String result = mapper.createSpongeDescription(entry);

    // Assert
    assertThat(result).contains("Test failure message");
    assertThat(result).contains("on_fail");
    assertThat(result).contains("second_position");
  }

  @Test
  public void createSpongeDescriptionNoDetailsAndExtraErrors() {
    // Setup
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.ERROR)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .build();

    // Run
    String result = mapper.createSpongeDescription(entry);

    // Assert
    assertThat(result).isEqualTo("No error message found.");
  }

  @Test
  public void createSpongeDescriptionNoExtraErrors() {
    // Setup
    MoblyTestEntry entry =
        MoblyTestEntry.builder()
            .setTestName(SUBTEST_NAME)
            .setResult(MoblyResult.ERROR)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("TestClass")
            .setDetails("Test failure message")
            .build();

    // Run
    String result = mapper.createSpongeDescription(entry);

    // Assert
    assertThat(result).contains("Test failure message");
  }

  private void addMoblyTestEntriesToMoblySummaryEntryList(List<MoblyYamlDocEntry> entries) {
    entries.add(
        MoblyTestEntry.builder()
            .setTestName("Test_1")
            .setResult(MoblyResult.PASS)
            .setBeginTime(10L)
            .setEndTime(20L)
            .setTestClass("PancakeClass")
            .build());

    entries.add(
        MoblyTestEntry.builder()
            .setTestName("Test_2")
            .setResult(MoblyResult.FAIL)
            .setBeginTime(20L)
            .setEndTime(30L)
            .setTestClass("WaffleClass")
            .build());
  }

  private void addUserMapsToMoblySummaryEntryList(List<MoblyYamlDocEntry> entries) {
    Map<String, Object> spongeMap = new HashMap<>();
    spongeMap.put("breakfast", "true");
    spongeMap.put("pancake_quality", "8.4");

    Map<String, Object> userMap = new HashMap<>();
    userMap.put("sponge_properties", spongeMap);
    userMap.put("timestamp", "10");
    userMap.put("Type", "UserData");
    entries.add(MoblyUserDataEntry.builder().setTimestamp("10").setUserDataMap(userMap).build());

    spongeMap = new HashMap<>();
    spongeMap.put("dinner", "true");
    spongeMap.put("steak_quality", "2.3");

    userMap = new HashMap<>();
    userMap.put("sponge_properties", spongeMap);
    userMap.put("timestamp", "50");
    userMap.put("Type", "UserData");
    userMap.put("Test Name", "Test_2");
    entries.add(MoblyUserDataEntry.builder().setTimestamp("50").setUserDataMap(userMap).build());
  }

  private List<MoblyYamlDocEntry> buildNewMoblySummaryEntryList() {
    List<MoblyYamlDocEntry> entries = new ArrayList<>();
    addMoblyTestEntriesToMoblySummaryEntryList(entries);
    addUserMapsToMoblySummaryEntryList(entries);
    entries.add(MoblyControllerInfoEntry.builder().setDevices("devices").build());
    return entries;
  }

  private TestInfo buildNewTestInfo(String genFileDir) throws Exception {
    // Create job setting with genFileDir set
    JobSetting setting =
        JobSetting.newBuilder().setGenFileDir(genFileDir).setHasTestSubdirs(false).build();

    // Create JobInfo first
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(JOB_NAME))
            .setType(JobType.getDefaultInstance())
            .setSetting(setting)
            .build();

    // Create TestInfo in JobInfo
    jobInfo.tests().add(TEST_NAME);

    // Return the only TestInfo
    return jobInfo.tests().getOnly();
  }
}
