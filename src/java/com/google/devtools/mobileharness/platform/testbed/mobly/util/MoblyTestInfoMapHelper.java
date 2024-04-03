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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblySummaryEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyTestEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyUserDataEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlDocEntry;
import com.google.devtools.mobileharness.infra.ats.console.result.mobly.MoblyYamlParser;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant.TestProperty;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapper class that maps objects to a new child TestInfo under the given parent TestInfo.
 *
 * <p>You cannot create standalone TestInfos. Rather, you need to create a new TestInfo by attaching
 * it as a child of a given parent TestInfo. The final result should mirror the following:
 *
 * <pre>{@code
 * TestInfo (passed into map() method)
 *    -> subTests()
 *       -> TestInfo (one for each test)
 *          -> locator()
 *             -> test name
 *          -> result()
 *             -> test result (i.e. PASS, FAIL, etc.)
 *          -> errors()
 *             -> Error (if test failed or errored)
 *                -> ErrorCode
 *                -> message of error details
 *          -> properties()
 *             -> Property (if test skipped)
 *                -> skip reason key
 *                -> message of skip details
 *          -> timing()
 *             -> start time of test
 *             -> last modified time of test
 *       -> TestInfo (next test info)
 *          -> ...
 *          -> ...
 * }</pre>
 */
public class MoblyTestInfoMapHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String EXTRA_ERROR_SECTION_SEPARATOR =
      "\n======= EXTRA ERRORS FOUND =======\n";
  private static final String NO_ERROR_MESSAGE_FOUND = "No error message found.";

  private static final ImmutableMap<MoblyResult, TestResult> MOBLY_RESULT_TO_MH_RESULT =
      ImmutableMap.of(
          MoblyResult.FAIL,
          TestResult.FAIL,
          MoblyResult.SKIP,
          TestResult.SKIP,
          MoblyResult.ERROR,
          TestResult.ERROR,
          MoblyResult.NULL,
          TestResult.ERROR);

  private static final ImmutableMap<MoblyResult, ExtErrorId> MOBLY_RESULT_TO_MH_ERROR_ID =
      ImmutableMap.of(
          MoblyResult.FAIL,
          ExtErrorId.MOBLY_TEST_CASE_FAILURE,
          MoblyResult.SKIP,
          ExtErrorId.MOBLY_TEST_CASE_SKIPPED,
          MoblyResult.ERROR,
          ExtErrorId.MOBLY_TEST_CASE_ERROR,
          MoblyResult.NULL,
          ExtErrorId.MOBLY_TEST_CASE_ERROR);

  public static final String MOBLY_TESTS_PASSED = "mobly_tests_passed";
  public static final String MOBLY_TESTS_FAILED_AND_ERROR = "mobly_tests_failed_and_error";
  public static final String MOBLY_TESTS_DONE = "mobly_tests_done";
  public static final String MOBLY_TESTS_SKIPPED = "mobly_tests_skipped";
  public static final String MOBLY_TESTS_TOTAL = "mobly_tests_total";
  public static final String MOBLY_JOBS_PASSED = "mobly_jobs_passed";

  /**
   * Maps a single MoblyTestEntry to a child TestInfo under the given parent TestInfo.
   *
   * @param testInfo the parent TestInfo to put the sub TestInfo under
   */
  public void map(TestInfo testInfo, MoblyTestEntry moblyEntry) throws MobileHarnessException {
    logger.atInfo().log("Mapping %s to TestInfo object...", moblyEntry.getTestName());

    // Create subtest record by adding it to testInfo subTests
    TestInfo subTest = testInfo.subTests().add(moblyEntry.getTestName());
    MoblyResult moblyResult = moblyEntry.getResult();
    if (moblyResult == MoblyResult.PASS) {
      subTest.resultWithCause().setPass();
    } else {
      TestResult mhResult = MOBLY_RESULT_TO_MH_RESULT.get(moblyResult);
      ExtErrorId extErrorId = MOBLY_RESULT_TO_MH_ERROR_ID.get(moblyResult);
      subTest
          .resultWithCause()
          .setNonPassing(
              mhResult,
              // We do not need the Mobile Harness stacktrace here as this is on the Mobly test case
              // level.
              ErrorModelConverter.toCommonExceptionDetail(
                  ErrorModelConverter.toExceptionDetail(
                      new MobileHarnessException(extErrorId, createSpongeDescription(moblyEntry)),
                      /* addStackTrace= */ false)));
      if (moblyResult == MoblyResult.SKIP) {
        addSkipReason(subTest, moblyEntry);
      } else {
        addFailureOrErrorInfo(subTest, moblyEntry);
      }
    }
    subTest.status().set(TestStatus.DONE);

    // Populate TestInfo properties
    addMoblyProperties(subTest, moblyEntry);
  }

  /**
   * Adds the UserData data as a property for a TestInfo.
   *
   * <p>UserData with a sponge_properties key needs to have the data read into a TestInfo. If the
   * UserData does not have a sponge_properties key, then it will be ignored. sponge_properties can
   * either be a List or a Map. The format will determine how the data will be parsed.
   */
  public void map(TestInfo testInfo, MoblyUserDataEntry entry) throws MobileHarnessException {
    if (entry.getUserDataMap().containsKey(MoblyConstant.UserDataKey.SPONGE)) {
      if (entry.getUserDataMap().get(MoblyConstant.UserDataKey.SPONGE) instanceof Map) {
        @SuppressWarnings("unchecked") // snakeyaml only supports this return value atm
        Map<String, Object> spongeMap =
            (Map<String, Object>) entry.getUserDataMap().get(MoblyConstant.UserDataKey.SPONGE);

        Optional<String> optionalTestClass = Optional.empty();
        if (entry.getUserDataMap().containsKey(MoblyConstant.UserDataKey.TEST_CLASS)) {
          optionalTestClass =
              Optional.of(
                  (String) entry.getUserDataMap().get(MoblyConstant.UserDataKey.TEST_CLASS));
        }

        Optional<String> optionalTestName = Optional.empty();
        if (entry.getUserDataMap().containsKey(MoblyConstant.UserDataKey.TEST_NAME)) {
          optionalTestName =
              Optional.of((String) entry.getUserDataMap().get(MoblyConstant.UserDataKey.TEST_NAME));
        }
        /*
         * If both test class and test name are not specified in UserData, adds the
         * sponge_properties to the top testInfo
         */
        if (optionalTestClass.isEmpty() && optionalTestName.isEmpty()) {
          addUserDataPropertiesMap(testInfo, spongeMap);
          /*
           * If only test class is specified in UserData, adds the sponge_properties to the testInfo
           * with test_type as mobly_test_class and same test class value
           */
        } else if (optionalTestClass.isPresent() && optionalTestName.isEmpty()) {
          addUserDataPropertiesMap(testInfo, spongeMap, optionalTestClass.get());
          /*
           * Otherwise, adds the sponge_properties to the testInfo with same test name and same test
           * class if the value of test class is specified
           */
        } else {
          addUserDataPropertiesMap(testInfo, spongeMap, optionalTestName.get(), optionalTestClass);
        }
      } else {
        // Log warning and print out data, but do not stop process
        logger.atWarning().log(
            "Unrecognized UserData format: %s",
            entry.getUserDataMap().get(MoblyConstant.UserDataKey.SPONGE));
      }
    }
  }

  /**
   * Maps a list of MoblyYamlDocEntry objects to children TestInfo under the given parent TestInfo.
   *
   * <p>Only MoblyTestEntry objects are mapped since that is all TestInfo object cares about.
   */
  public void map(TestInfo testInfo, List<MoblyYamlDocEntry> moblyEntryList)
      throws MobileHarnessException {

    String testClass = null;
    for (MoblyYamlDocEntry entry : moblyEntryList) {
      if (Objects.equals(entry.getType(), MoblyYamlDocEntry.Type.RECORD)) {
        MoblyTestEntry testEntry = (MoblyTestEntry) entry;
        // Mobly summary entries in the list are ordered by test class so here we can create a new
        // sub test info when a new test class is seen.
        if (testEntry.getTestClass() != null && !testEntry.getTestClass().equals(testClass)) {
          TestInfo subTestInfo = testInfo.subTests().add(testEntry.getTestClass());
          // Set this to PASS to actually parse results.
          subTestInfo.resultWithCause().setPass();
          subTestInfo.status().set(TestStatus.DONE);
          subTestInfo
              .properties()
              .add(
                  MoblyConstant.TestProperty.TEST_TYPE_KEY,
                  MoblyConstant.TestProperty.MOBLY_TEST_CLASS_VALUE);

          subTestInfo
              .properties()
              .add(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY, testEntry.getTestClass());

          testClass = testEntry.getTestClass();
        }
        map(testInfo, testEntry);
      }
    }

    // Need to have all the test records present before adding relevant properties.
    for (MoblyYamlDocEntry entry : moblyEntryList) {
      if (Objects.equals(entry.getType(), MoblyYamlDocEntry.Type.USERDATA)) {
        map(testInfo, (MoblyUserDataEntry) entry);
      }
    }

    // Collect Mobly test summary.
    for (MoblyYamlDocEntry entry : moblyEntryList) {
      Map<String, String> moblyTestSummary = new HashMap<>();
      if (Objects.equals(entry.getType(), MoblyYamlDocEntry.Type.SUMMARY)) {
        MoblySummaryEntry summaryEntry = (MoblySummaryEntry) entry;
        moblyTestSummary.put(MOBLY_TESTS_PASSED, String.valueOf(summaryEntry.passed()));
        moblyTestSummary.put(
            MOBLY_TESTS_FAILED_AND_ERROR,
            String.valueOf(summaryEntry.failed() + summaryEntry.error()));
        moblyTestSummary.put(MOBLY_TESTS_DONE, String.valueOf(summaryEntry.executed()));
        moblyTestSummary.put(MOBLY_TESTS_SKIPPED, String.valueOf(summaryEntry.skipped()));
        moblyTestSummary.put(MOBLY_TESTS_TOTAL, String.valueOf(summaryEntry.requested()));
        testInfo.jobInfo().params().addAll(moblyTestSummary);
        // Only if failed number is 0 and number of passed + skipped = requested, then we treat the
        // mobly job as PASS.
        if ((summaryEntry.failed() == 0)
            && (summaryEntry.skipped() + summaryEntry.passed() == summaryEntry.requested())) {
          testInfo.properties().add(MOBLY_JOBS_PASSED, "true");
        }
      }
    }
  }

  /**
   * Adds error/failure messages and stack traces as properties to be picked up by {@link
   * com.google.wireless.qa.mobileharness.shared.sponge.MoblyTestSpongeTreeGenerator}.
   */
  private void addFailureOrErrorInfo(TestInfo testInfo, MoblyTestEntry moblyEntry) {
    testInfo
        .properties()
        .add(
            MoblyConstant.TestProperty.MOBLY_ERROR_MESSAGE_KEY,
            createSpongeDescription(moblyEntry));
    if (moblyEntry.getStacktrace().isPresent()) {
      testInfo
          .properties()
          .add(MoblyConstant.TestProperty.MOBLY_STACK_TRACE_KEY, moblyEntry.getStacktrace().get());
    }
  }

  /**
   * If the test was skipped, add the skip reason into the TestInfo properities list.
   *
   * @param testInfo the TestInfo to put the skip reason property into.
   */
  private void addSkipReason(TestInfo testInfo, MoblyTestEntry moblyEntry) {
    testInfo
        .properties()
        .add(MoblyConstant.TestProperty.SKIP_REASON_KEY, createSpongeDescription(moblyEntry));
  }

  /** Constructs a specific description string for sponge. */
  @VisibleForTesting
  String createSpongeDescription(MoblyTestEntry moblyEntry) {
    StringBuilder builder = new StringBuilder();

    if (moblyEntry.getDetails().isPresent()) {
      builder.append("Details: ");
      builder.append(moblyEntry.getDetails().get());
      builder.append("\n");
    }

    if (!moblyEntry.getExtraErrors().isEmpty()) {
      builder.append(EXTRA_ERROR_SECTION_SEPARATOR);
      moblyEntry.getExtraErrors().forEach(err -> builder.append(stringifyException(err)));
    }

    if (builder.length() == 0) {
      return NO_ERROR_MESSAGE_FOUND;
    }
    return builder.toString();
  }

  /**
   * Maps the MoblyTestEntry.ExtraError object to a String. This was based off of {@link
   * com.google.wireless.qa.mobileharness.shared.sponge.MoblyTestSpongeTreeGenerator#stringifyExceptionJson(JSONObject)}
   */
  private String stringifyException(MoblyTestEntry.ExtraError error) {
    StringBuilder builder = new StringBuilder();
    builder.append("------> ");
    builder.append(error.getPosition());
    builder.append("\n");

    if (error.getDetails().isPresent()) {
      builder.append(MoblyYamlParser.RESULT_EXTRA_ERRORS_DETAILS);
      builder.append(": ");
      builder.append(error.getDetails().get());
      builder.append("\n");
    }

    if (error.getExtras().isPresent()) {
      builder.append(MoblyYamlParser.RESULT_EXTRA_ERRORS_EXTRAS);
      builder.append(": ");
      builder.append(error.getExtras().get());
      builder.append("\n");
    }

    if (error.getStacktrace().isPresent()) {
      builder.append(MoblyYamlParser.RESULT_EXTRA_ERRORS_STACKTRACE);
      builder.append(": ");
      builder.append(error.getStacktrace().get());
      builder.append("\n");
    }
    return builder.toString();
  }

  /**
   * Adds general Mobly properties to the TestInfo given the MoblyTestEntry object for the Mobly
   * testcase.
   */
  private void addMoblyProperties(TestInfo testInfo, MoblyTestEntry moblyEntry) {
    // Added so the sponge tree generator can differentiate between a Mobly test and a TestInfo
    // object that was added by a decorator/plug-in
    testInfo
        .properties()
        .add(MoblyConstant.TestProperty.TEST_TYPE_KEY, MoblyConstant.TestProperty.MOBLY_TEST_VALUE);

    testInfo
        .properties()
        .add(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_KEY, moblyEntry.getTestClass());

    if (moblyEntry.getUid().isPresent()) {
      testInfo.properties().add(TestProperty.MOBLY_UID_KEY, moblyEntry.getUid().get());
    }

    if (moblyEntry.getBeginTime().isPresent()) {
      testInfo
          .properties()
          .add(
              MoblyConstant.TestProperty.MOBLY_BEGIN_TIME_KEY,
              Long.toString(moblyEntry.getBeginTime().get()));
    }
    if (moblyEntry.getEndTime().isPresent()) {
      testInfo
          .properties()
          .add(
              MoblyConstant.TestProperty.MOBLY_END_TIME_KEY,
              Long.toString(moblyEntry.getEndTime().get()));
    }
    if (moblyEntry.getRetryParent().isPresent()) {
      testInfo
          .properties()
          .add(
              MoblyConstant.TestProperty.MOBLY_RETRY_PARENT_KEY, moblyEntry.getRetryParent().get());
    }
    if (moblyEntry.getSignature().isPresent()) {
      testInfo
          .properties()
          .add(MoblyConstant.TestProperty.MOBLY_SIGNATURE_KEY, moblyEntry.getSignature().get());
    }
  }

  /**
   * Adds a Sponge property to a given TestInfo.
   *
   * @param testInfo the {@link TestInfo} to add the property to
   * @param spongeProperty the Sponge property to add
   */
  private static void addUserDataPropertyEntry(
      TestInfo testInfo, Map.Entry<String, Object> spongeProperty) {
    testInfo.properties().add(spongeProperty.getKey(), String.valueOf(spongeProperty.getValue()));
  }

  /**
   * Recursively finds and adds a given Sponge property to matching {@link TestInfo} objects with
   * test_type as mobly_test_class.
   *
   * @param testInfo the starting root {@link TestInfo} to try to add properties to
   * @param spongeProperty the Sponge property to find matching {@link TestInfo} objects for
   * @param testClass the test name to match for the adding the property
   */
  private static void addUserDataPropertyEntry(
      TestInfo testInfo, Map.Entry<String, Object> spongeProperty, String testClass) {

    if (testInfo.properties().has(TestProperty.MOBLY_TEST_CLASS_KEY)
        && testInfo.properties().get(TestProperty.MOBLY_TEST_CLASS_KEY).equals(testClass)
        && testInfo.properties().has(MoblyConstant.TestProperty.TEST_TYPE_KEY)
        && testInfo
            .properties()
            .get(MoblyConstant.TestProperty.TEST_TYPE_KEY)
            .equals(MoblyConstant.TestProperty.MOBLY_TEST_CLASS_VALUE)) {
      addUserDataPropertyEntry(testInfo, spongeProperty);
    } else {
      testInfo
          .subTests()
          .getAll()
          .values()
          .forEach(subTestInfo -> addUserDataPropertyEntry(subTestInfo, spongeProperty, testClass));
    }
  }

  /**
   * Recursively finds and adds a given Sponge property to matching {@link TestInfo} objects.
   *
   * @param testInfo the starting root {@link TestInfo} to try to add properties to
   * @param spongeProperty the Sponge property to find matching {@link TestInfo} objects for
   * @param testName the test name to match for the adding the property
   * @param optionalTestClass the test class to match for the adding the property
   */
  private static void addUserDataPropertyEntry(
      TestInfo testInfo,
      Map.Entry<String, Object> spongeProperty,
      String testName,
      Optional<String> optionalTestClass) {

    boolean matchTestClass =
        optionalTestClass.isPresent()
            && testInfo.properties().has(TestProperty.MOBLY_TEST_CLASS_KEY)
            && testInfo
                .properties()
                .get(TestProperty.MOBLY_TEST_CLASS_KEY)
                .equals(optionalTestClass.get());

    if (testInfo.locator().getName().equals(testName)
        && (optionalTestClass.isEmpty() || matchTestClass)) {
      addUserDataPropertyEntry(testInfo, spongeProperty);
    } else {
      testInfo
          .subTests()
          .getAll()
          .values()
          .forEach(
              subTestInfo ->
                  addUserDataPropertyEntry(
                      subTestInfo, spongeProperty, testName, optionalTestClass));
    }
  }

  /**
   * Reads in all the keys and values in spongeProperties map and saves the data in the given
   * TestInfo as properties.
   */
  private static void addUserDataPropertiesMap(
      TestInfo testInfo, Map<String, Object> spongeProperties) {
    spongeProperties.entrySet().forEach(entry -> addUserDataPropertyEntry(testInfo, entry));
  }

  /**
   * Reads in all the keys and values in spongeProperties map and recursively checks the TestInfo
   * for matching tests for which to set the properties for.
   *
   * <p>The criteria for adding the Sponge properties is that the Sponge properties should be added
   * if the test_type of {@link TestInfo} is mobly_test_class and it has the same test class as the
   * UserData test class value.
   */
  private static void addUserDataPropertiesMap(
      TestInfo testInfo, Map<String, Object> spongeProperties, String testClass) {
    spongeProperties
        .entrySet()
        .forEach(entry -> addUserDataPropertyEntry(testInfo, entry, testClass));
  }

  /**
   * Reads in all the keys and values in spongeProperties map and recursively checks the TestInfo
   * for matching tests for which to set the properties for.
   *
   * <p>The criteria for adding the Sponge properties is that the Sponge properties should be added
   * if the {@link TestInfo} has the same test name as the UserData test name value and has the same
   * test class as the UserData test class value if test class is specified in UserData.
   */
  private static void addUserDataPropertiesMap(
      TestInfo testInfo,
      Map<String, Object> spongeProperties,
      String testName,
      Optional<String> optionalTestClass) {
    spongeProperties
        .entrySet()
        .forEach(entry -> addUserDataPropertyEntry(testInfo, entry, testName, optionalTestClass));
  }
}
