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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import java.util.LinkedHashSet;
import java.util.Set;

/** Helper class to determine which module or test should run or not. */
public final class RetryResultHelper {

  /**
   * Whether any tests within the given {@link Module} should be run, based on the content of {@code
   * types}.
   *
   * <p>The given types can only be passed, failed or not_executed.
   *
   * @return true if at least one test in the module should run
   */
  public static boolean shouldRunModule(
      Module module,
      Set<String> types,
      boolean addSubPlanCmd,
      ImmutableSet<String> passedInModules) {
    if (!addSubPlanCmd && !passedInModules.isEmpty()) {
      String moduleNameWithoutParam = module.getName();
      int moduleParamIdx = moduleNameWithoutParam.indexOf("[");
      if (moduleParamIdx > -1) {
        moduleNameWithoutParam = moduleNameWithoutParam.substring(0, moduleParamIdx);
      }
      if (!passedInModules.contains(moduleNameWithoutParam)) {
        return false;
      }
    }

    if (types.contains("not_executed") && !module.getDone()) {
      // module has not_executed tests that should be re-run
      return true;
    }
    // If module has some states that needs to be retried.
    for (String status : getStatusesToRun(types, addSubPlanCmd)) {
      if (getNumTestsInState(module, status) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether all tests within the given {@link Module} should be run, based on the content of {@code
   * types}.
   *
   * <p>The given types can only be passed, failed or not_executed.
   *
   * @return true if every test in the module should run
   */
  public static boolean shouldRunEntireModule(
      Module module,
      Set<String> types,
      boolean addSubPlanCmd,
      Set<SuiteTestFilter> prevResultIncludeFilters,
      Set<SuiteTestFilter> prevResultExcludeFilters) {
    if (!types.contains("not_executed") && !module.getDone()) {
      // If module is not done but types do not contain not_executed, should not run the entire
      // module.
      return false;
    }

    // Not run the entire module if any of tests has a status out of the retry types.
    Set<String> statusesToRun = getStatusesToRun(types, addSubPlanCmd);
    for (TestStatus status : TestStatus.values()) {
      String statusStr = TestStatus.convertToTestStatusCompatibilityString(status);
      if (!statusesToRun.contains(statusStr)) {
        if (getNumTestsInState(module, statusStr) > 0) {
          return false;
        }
      }
    }

    // User passed in include-filter matches a test in the module, so only part of the module tests
    // were ran.
    if (!prevResultIncludeFilters.isEmpty()
        && prevResultIncludeFilters.stream()
            .anyMatch(
                filter ->
                    filter.matchModule(
                            module.getName(), module.getAbi(), /* moduleParameter= */ null)
                        && filter.testName().isPresent())) {
      return false;
    }
    // User passed in exclude-filter matches a test in the module, so only part of the module tests
    // were ran.
    if (!prevResultExcludeFilters.isEmpty()
        && prevResultExcludeFilters.stream()
            .anyMatch(
                filter ->
                    filter.matchModule(
                            module.getName(), module.getAbi(), /* moduleParameter= */ null)
                        && filter.testName().isPresent())) {
      return false;
    }

    return true;
  }

  /**
   * Returns whether or not a test case should be run or not.
   *
   * <p>The given types can only be passed, failed or not_executed.
   */
  public static boolean shouldRunTest(Test test, Set<String> types, boolean addSubPlanCmd) {
    return getStatusesToRun(types, addSubPlanCmd).contains(test.getResult());
  }

  private static long getNumTestsInState(Module module, String testStatus) {
    return module.getTestCaseList().stream()
        .flatMap(testCase -> testCase.getTestList().stream())
        .filter(test -> Ascii.equalsIgnoreCase(test.getResult(), testStatus))
        .count();
  }

  private static Set<String> getStatusesToRun(Set<String> types, boolean addSubPlanCmd) {
    Set<String> statusesToRun = new LinkedHashSet<>();
    if (addSubPlanCmd) {
      if (types.contains("passed")) {
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED));
      }
      if (types.contains("failed")) {
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE));
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING));
      }
    } else {
      if (types.contains("failed")) {
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE));
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.INCOMPLETE));
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.SKIPPED));
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.WARNING));
      }
      if (types.contains("not_executed")) {
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.INCOMPLETE));
        statusesToRun.add(TestStatus.convertToTestStatusCompatibilityString(TestStatus.SKIPPED));
      }
    }

    return statusesToRun;
  }

  private RetryResultHelper() {}
}
