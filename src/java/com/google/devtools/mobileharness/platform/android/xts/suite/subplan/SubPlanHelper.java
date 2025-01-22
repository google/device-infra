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

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryResultHelper;
import java.util.Optional;
import java.util.Set;

/** SubPlan helper class. */
public class SubPlanHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Creates a subplan per given previous result.
   *
   * <p>The given types can only be passed, failed or not_executed.
   *
   * @param passedInModules When from "run retry" command, if not empty, only the matched modules
   *     will be added to the subplan
   */
  public static SubPlan createSubPlanForPreviousResult(
      Result previousResult,
      Set<String> types,
      boolean addSubPlanCmd,
      ImmutableSet<SuiteTestFilter> prevResultIncludeFilters,
      ImmutableSet<SuiteTestFilter> prevResultExcludeFilters,
      ImmutableSet<String> passedInModules) {
    SubPlan subPlan = new SubPlan();
    for (Module module : previousResult.getModuleInfoList()) {
      boolean isNonTfModule = module.getIsNonTfModule();
      // Always add the include filter for the module, and rely on below to determine whether to
      // add the exclude filter, or include filter for specific tests.
      // Note: For include and exclude filters passed to TF via subplan, its handling priority is:
      // exclude filter > include filter for specific test > include filter for the module
      addIncludeFilter(
          subPlan, String.format("%s %s", module.getAbi(), module.getName()), isNonTfModule);
      // If the previous result has test filter, we can say that the previous run is on a specific
      // test of a specific module, then add the test include filter and rely on below to determine
      // whether to add the exclude filter.
      if (!previousResult.getTestFilter().isEmpty()) {
        addIncludeFilter(
            subPlan,
            String.format(
                "%s %s %s", module.getAbi(), module.getName(), previousResult.getTestFilter()),
            isNonTfModule);
      }
      if (RetryResultHelper.shouldRunModule(module, types, addSubPlanCmd, passedInModules)) {
        if (types.contains("not_executed") && !module.getDone()) {
          logger.atInfo().log(
              "Module [%s %s] was not done in previous run", module.getAbi(), module.getName());
          // If the previous result has include/exclude filters, for example the previous run was
          // running with given subplan file, previous include/exclude filters should be applied to
          // the current run when the module is not done.
          if (!prevResultIncludeFilters.isEmpty()) {
            prevResultIncludeFilters.stream()
                .filter(
                    filter ->
                        filter.matchModule(
                            module.getName(), module.getAbi(), /* moduleParameter= */ null))
                .forEach(
                    prevResultIncludeFilter ->
                        addIncludeFilter(
                            subPlan, prevResultIncludeFilter.filterString(), isNonTfModule));
          }
          if (!prevResultExcludeFilters.isEmpty()) {
            prevResultExcludeFilters.stream()
                .filter(
                    filter ->
                        filter.matchModule(
                            module.getName(), module.getAbi(), /* moduleParameter= */ null))
                .forEach(
                    prevResultExcludeFilter ->
                        addExcludeFilter(
                            subPlan, prevResultExcludeFilter.filterString(), isNonTfModule));
          }
          // Exclude tests that should not be run
          for (TestCase testCase : module.getTestCaseList()) {
            for (Test test : testCase.getTestList()) {
              if (!RetryResultHelper.shouldRunTest(test, types, addSubPlanCmd)) {
                addExcludeFilter(
                    subPlan,
                    String.format(
                        "%s %s %s#%s",
                        module.getAbi(), module.getName(), testCase.getName(), test.getName()),
                    isNonTfModule);
              }
            }
          }
        } else if (!RetryResultHelper.shouldRunEntireModule(
            module, types, addSubPlanCmd, prevResultIncludeFilters, prevResultExcludeFilters)) {
          // Only include test cases that should be run if the module is done in previous run, and
          // only some of test cases(not all) in the module should be run.
          for (TestCase testCase : module.getTestCaseList()) {
            for (Test test : testCase.getTestList()) {
              if (RetryResultHelper.shouldRunTest(test, types, addSubPlanCmd)) {
                addIncludeFilter(
                    subPlan,
                    String.format(
                        "%s %s %s#%s",
                        module.getAbi(), module.getName(), testCase.getName(), test.getName()),
                    isNonTfModule);
              }
            }
          }
        }
      } else {
        // Should not run the module, exclude it explicitly to avoid test plans including it
        addExcludeFilter(
            subPlan, String.format("%s %s", module.getAbi(), module.getName()), isNonTfModule);
      }
    }
    // Need to retrieve the test plan from the COMMAND_LINE_ARGS in the previous result
    Optional<Attribute> commandLineArgs =
        previousResult.getAttributeList().stream()
            .filter(attribute -> attribute.getKey().equals(XmlConstants.COMMAND_LINE_ARGS))
            .findFirst();
    commandLineArgs.ifPresent(
        args ->
            subPlan.setPreviousSessionXtsTestPlan(
                Splitter.on(' ')
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToStream(args.getValue())
                    .findFirst()
                    .orElse("")));
    if (previousResult.hasBuild()) {
      subPlan.setPreviousSessionDeviceBuildFingerprint(
          previousResult.getBuild().getBuildFingerprint());
      if (!previousResult.getBuild().getBuildFingerprintUnaltered().isEmpty()) {
        subPlan.setPreviousSessionDeviceBuildFingerprintUnaltered(
            previousResult.getBuild().getBuildFingerprintUnaltered());
      }
      subPlan.setPreviousSessionDeviceVendorBuildFingerprint(
          previousResult.getBuild().getBuildVendorFingerprint());
    }
    return subPlan;
  }

  private static void addIncludeFilter(SubPlan subPlan, String filter, boolean isNonTfModule) {
    if (isNonTfModule) {
      subPlan.addNonTfIncludeFilter(filter);
    } else {
      subPlan.addIncludeFilter(filter);
    }
  }

  private static void addExcludeFilter(SubPlan subPlan, String filter, boolean isNonTfModule) {
    if (isNonTfModule) {
      subPlan.addNonTfExcludeFilter(filter);
    } else {
      subPlan.addExcludeFilter(filter);
    }
  }

  public static void addPassedInFiltersToSubPlan(
      SubPlan subPlan,
      ImmutableSet<SuiteTestFilter> passedInIncludeFilters,
      ImmutableSet<SuiteTestFilter> passedInExcludeFilters,
      ImmutableSet<String> allNonTfModules) {

    for (SuiteTestFilter filter : passedInIncludeFilters) {
      if (allNonTfModules.stream()
          .map(Ascii::toLowerCase)
          .anyMatch(Ascii.toLowerCase(filter.moduleName())::contains)) {
        subPlan.addNonTfIncludeFilter(filter.filterString());
      } else {
        subPlan.addIncludeFilter(filter.filterString());
      }
    }

    for (SuiteTestFilter filter : passedInExcludeFilters) {
      if (allNonTfModules.stream()
          .map(Ascii::toLowerCase)
          .anyMatch(Ascii.toLowerCase(filter.moduleName())::contains)) {
        subPlan.addNonTfExcludeFilter(filter.filterString());
      } else {
        subPlan.addExcludeFilter(filter.filterString());
      }
    }
  }

  private SubPlanHelper() {}
}
