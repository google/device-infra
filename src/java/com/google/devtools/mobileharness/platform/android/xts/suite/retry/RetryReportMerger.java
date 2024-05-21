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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Run;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.RunHistory;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Test;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Merger to merge report from the retry run and report from the previous session. */
public class RetryReportMerger {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PreviousResultLoader previousResultLoader;
  private final RetryGenerator retryGenerator;

  @Inject
  RetryReportMerger(PreviousResultLoader previousResultLoader, RetryGenerator retryGenerator) {
    this.previousResultLoader = previousResultLoader;
    this.retryGenerator = retryGenerator;
  }

  /**
   * Merges result from the previous session and the result from the retry.
   *
   * @param resultsDir the path to the "results" directory which stores the results for the previous
   *     sessions
   * @param previousSessionIndex index of the previous session being retried
   * @param retryType test statuses for retry
   * @param retryResult the result report for the "retry" run
   * @param passedInModules passed in module names from the command
   */
  public Result mergeReports(
      Path resultsDir,
      int previousSessionIndex,
      @Nullable RetryType retryType,
      @Nullable Result retryResult,
      ImmutableList<String> passedInModules)
      throws MobileHarnessException {
    // Loads the previous session result and its subplan used for the retry
    Result previousResult =
        previousResultLoader.loadPreviousResult(resultsDir, previousSessionIndex);
    RetryArgs.Builder retryArgs =
        RetryArgs.builder().setResultsDir(resultsDir).setPreviousSessionIndex(previousSessionIndex);
    if (retryType != null) {
      retryArgs.setRetryType(retryType);
    }
    if (!passedInModules.isEmpty()) {
      retryArgs.setPassedInModules(ImmutableSet.copyOf(passedInModules));
    }
    SubPlan subPlan = retryGenerator.generateRetrySubPlan(retryArgs.build());
    return mergeReports(previousResult, subPlan, retryResult);
  }

  /**
   * Merges result from the previous session and the result from the retry. This is for ATS Server's
   * sessions.
   *
   * @param resultsDir the path to the "results" directory which stores the results for the previous
   *     sessions
   * @param previousSessionId id of the previous session being retried
   * @param retryType test statuses for retry
   * @param retryResult the result report for the "retry" run
   * @param passedInModules passed in module names from the command
   */
  public Result mergeReports(
      Path resultsDir,
      String previousSessionId,
      @Nullable RetryType retryType,
      @Nullable Result retryResult,
      ImmutableList<String> passedInModules)
      throws MobileHarnessException {
    // Loads the previous session result and its subplan used for the retry
    Result previousResult = previousResultLoader.loadPreviousResult(resultsDir, previousSessionId);
    RetryArgs.Builder retryArgs =
        RetryArgs.builder().setResultsDir(resultsDir).setPreviousSessionId(previousSessionId);
    if (retryType != null) {
      retryArgs.setRetryType(retryType);
    }
    if (!passedInModules.isEmpty()) {
      retryArgs.setPassedInModules(ImmutableSet.copyOf(passedInModules));
    }
    SubPlan subPlan = retryGenerator.generateRetrySubPlan(retryArgs.build());
    return mergeReports(previousResult, subPlan, retryResult);
  }

  private Result mergeReports(
      Result previousResult, SubPlan retrySubPlan, @Nullable Result retryResult) {
    Result.Builder mergedResult = Result.newBuilder();
    // If the previous result was ran with some filters given by the user command, reflect them in
    // the merged report too.
    //
    if (!previousResult.getModuleFilterList().isEmpty()) {
      mergedResult.addAllModuleFilter(previousResult.getModuleFilterList());
    }
    if (!previousResult.getTestFilter().isEmpty()) {
      mergedResult.setTestFilter(previousResult.getTestFilter());
    }
    if (!previousResult.getIncludeFilterList().isEmpty()) {
      mergedResult.addAllIncludeFilter(previousResult.getIncludeFilterList());
    }
    if (!previousResult.getExcludeFilterList().isEmpty()) {
      mergedResult.addAllExcludeFilter(previousResult.getExcludeFilterList());
    }

    // If no tests were retried, mark tests as cached, and inherit some info from previous result
    if (retryResult == null
        || (retrySubPlan.getIncludeFiltersMultimap().isEmpty()
            && retrySubPlan.getNonTfIncludeFiltersMultimap().isEmpty())) {
      List<Module.Builder> moduleBuildersFromPrevSession =
          previousResult.toBuilder().getModuleInfoBuilderList();
      mergedResult =
          previousResult.toBuilder()
              .setIsRetryResult(true)
              .clearModuleInfo()
              .addAllModuleInfo(
                  moduleBuildersFromPrevSession.stream()
                      .map(
                          moduleBuilder -> {
                            markTestsInModuleCached(moduleBuilder);
                            return moduleBuilder.build();
                          })
                      .collect(toImmutableList()));
      addRunHistoryForRetry(mergedResult, previousResult);
      return mergedResult.build();
    }

    // Update result attributes if needed
    List<Attribute> attributes = new ArrayList<>();
    // Need to retrieve COMMAND_LINE_ARGS from the previous result, and keep other attributes from
    // the retry result
    retryResult.getAttributeList().stream()
        .filter(attribute -> !attribute.getKey().equals(XmlConstants.COMMAND_LINE_ARGS))
        .forEach(attributes::add);
    Optional<Attribute> commandLineArgs =
        previousResult.getAttributeList().stream()
            .filter(attribute -> attribute.getKey().equals(XmlConstants.COMMAND_LINE_ARGS))
            .findFirst();
    if (commandLineArgs.isPresent()) {
      attributes.add(commandLineArgs.get());
    }
    mergedResult
        .setIsRetryResult(true)
        .setBuild(retryResult.toBuilder().getBuildBuilder())
        .addAllAttribute(attributes);
    addRunHistoryForRetry(mergedResult, previousResult);

    // Map of module id to module
    ImmutableMap<String, Module> modulesFromRetry =
        retryResult.getModuleInfoList().stream()
            .collect(
                toImmutableMap(
                    module -> AbiUtil.createId(module.getAbi(), module.getName()),
                    Function.identity()));

    for (Module moduleFromPrevSession : previousResult.getModuleInfoList()) {
      mergedResult.addModuleInfo(
          createMergedModule(
              moduleFromPrevSession,
              modulesFromRetry,
              moduleFromPrevSession.getIsNonTfModule()
                  ? retrySubPlan.getNonTfIncludeFiltersMultimap()
                  : retrySubPlan.getIncludeFiltersMultimap()));
    }

    // Prepare the Summary
    long passedInSummary = 0;
    long failedInSummary = 0;
    int modulesDoneInSummary = 0;
    int modulesTotalInSummary = mergedResult.getModuleInfoCount();
    for (Module module : mergedResult.getModuleInfoList()) {
      if (module.getDone()) {
        modulesDoneInSummary++;
      }
      passedInSummary += module.getPassed();
      failedInSummary += module.getFailedTests();
    }

    return mergedResult
        .setSummary(
            Summary.newBuilder()
                .setPassed(passedInSummary)
                .setFailed(failedInSummary)
                .setModulesDone(modulesDoneInSummary)
                .setModulesTotal(modulesTotalInSummary))
        .build();
  }

  private void addRunHistoryForRetry(Result.Builder resultBuilder, Result previousResult) {
    Run runFromPrevSession = createOneRunHistory(previousResult);
    if (previousResult.hasRunHistory()) {
      resultBuilder.setRunHistory(
          previousResult.getRunHistory().toBuilder().addRun(runFromPrevSession));
    } else {
      resultBuilder.setRunHistory(RunHistory.newBuilder().addRun(runFromPrevSession));
    }
  }

  private Module createMergedModule(
      Module moduleFromPrevSession,
      Map<String, Module> modulesFromRetry,
      SetMultimap<String, String> subPlanIncludeFilters) {
    String moduleId =
        AbiUtil.createId(moduleFromPrevSession.getAbi(), moduleFromPrevSession.getName());
    boolean moduleFoundInRetry = modulesFromRetry.containsKey(moduleId);
    boolean moduleNotRetried = false;
    if (subPlanIncludeFilters.containsKey(moduleId) && !moduleFoundInRetry) {
      logger.atWarning().log(
          "Module %s should've been retried but not found in retry result", moduleId);
      moduleNotRetried = true;
    } else if (!subPlanIncludeFilters.containsKey(moduleId)) {
      moduleNotRetried = true;
    }

    if (moduleNotRetried) {
      // The module was not retried, mark its tests as cached
      Module.Builder moduleBuilder = moduleFromPrevSession.toBuilder();
      markTestsInModuleCached(moduleBuilder);
      return moduleBuilder.build();
    }

    Module moduleFromRetry = modulesFromRetry.get(moduleId);
    return doMergeModule(
        moduleId, moduleFromPrevSession, moduleFromRetry, subPlanIncludeFilters.get(moduleId));
  }

  private void markTestsInModuleCached(Module.Builder moduleBuilder) {
    moduleBuilder
        .getTestCaseBuilderList()
        .forEach(
            testCaseBuilder ->
                testCaseBuilder
                    .getTestBuilderList()
                    .forEach(
                        testBuilder -> {
                          boolean isCached =
                              testBuilder.getMetricList().stream()
                                  .anyMatch(
                                      metric ->
                                          metric.getKey().equals(XmlConstants.METRIC_CACHED_KEY));
                          if (!isCached) {
                            testBuilder.addMetric(
                                Metric.newBuilder()
                                    .setKey(XmlConstants.METRIC_CACHED_KEY)
                                    .setContent("true"));
                          }
                        }));
  }

  private Module doMergeModule(
      String moduleId,
      Module moduleFromPrevSession,
      Module moduleFromRetry,
      Set<String> matchedRetryTestFilters) {
    if (matchedRetryTestFilters.contains(SubPlan.ALL_TESTS_IN_MODULE)) {
      // The entire module was retried.
      return moduleFromRetry;
    }
    Module.Builder mergedModuleBuilder =
        Module.newBuilder()
            .setAbi(moduleFromRetry.getAbi())
            .setName(moduleFromRetry.getName())
            .setDone(moduleFromRetry.getDone())
            .setRuntimeMillis(moduleFromRetry.getRuntimeMillis())
            .setPrepTimeMillis(moduleFromRetry.getPrepTimeMillis())
            .setTeardownTimeMillis(moduleFromRetry.getTeardownTimeMillis());
    if (moduleFromRetry.hasReason()) {
      mergedModuleBuilder.setReason(moduleFromRetry.getReason());
    }
    if (moduleFromRetry.hasIsNonTfModule()) {
      mergedModuleBuilder.setIsNonTfModule(moduleFromRetry.getIsNonTfModule());
    }

    // Map of test case name to test case
    ImmutableMap<String, TestCase> testCasesFromRetry =
        moduleFromRetry.getTestCaseList().stream()
            .collect(toImmutableMap(TestCase::getName, Function.identity()));

    for (TestCase testCaseFromPrevSession : moduleFromPrevSession.getTestCaseList()) {
      mergedModuleBuilder.addTestCase(
          createMergedTestCase(
              moduleId,
              testCaseFromPrevSession,
              testCasesFromRetry,
              matchedRetryTestFilters.stream()
                  .filter(filter -> filter.contains(testCaseFromPrevSession.getName()))
                  .collect(toImmutableSet())));
    }

    int passedTests = 0;
    int failedTests = 0;
    int totalTests = 0;
    for (TestCase testCase : mergedModuleBuilder.getTestCaseList()) {
      for (Test test : testCase.getTestList()) {
        if (test.getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.PASSED))) {
          passedTests++;
        } else if (test.getResult()
            .equals(TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE))) {
          failedTests++;
        }
        totalTests++;
      }
    }
    return mergedModuleBuilder
        .setPassed(passedTests)
        .setFailedTests(failedTests)
        .setTotalTests(totalTests)
        .build();
  }

  private TestCase createMergedTestCase(
      String moduleId,
      TestCase testCaseFromPrevSession,
      Map<String, TestCase> testCasesFromRetry,
      Set<String> matchedRetryTestFilters) {
    String testCaseName = testCaseFromPrevSession.getName();
    boolean testCaseFoundInRetry = testCasesFromRetry.containsKey(testCaseName);
    boolean testCaseNotRetried = false;
    if (!matchedRetryTestFilters.isEmpty() && !testCaseFoundInRetry) {
      logger.atWarning().log(
          "TestCase %s %s should've been retried but not found in retry result",
          moduleId, testCaseFromPrevSession.getName());
      testCaseNotRetried = true;
    } else if (matchedRetryTestFilters.isEmpty()) {
      testCaseNotRetried = true;
    }
    if (testCaseNotRetried) {
      // The test case was not retried, mark its tests as cached
      TestCase.Builder testCaseBuilder = testCaseFromPrevSession.toBuilder();
      testCaseBuilder
          .getTestBuilderList()
          .forEach(
              testBuilder -> {
                boolean isCached =
                    testBuilder.getMetricList().stream()
                        .anyMatch(metric -> metric.getKey().equals(XmlConstants.METRIC_CACHED_KEY));
                if (!isCached) {
                  testBuilder.addMetric(
                      Metric.newBuilder()
                          .setKey(XmlConstants.METRIC_CACHED_KEY)
                          .setContent("true"));
                }
              });
      return testCaseBuilder.build();
    }

    return doMergeTestCase(
        moduleId,
        testCaseFromPrevSession,
        testCasesFromRetry.get(testCaseName),
        matchedRetryTestFilters);
  }

  private TestCase doMergeTestCase(
      String moduleId,
      TestCase testCaseFromPrevSession,
      TestCase testCaseFromRetry,
      Set<String> matchedRetryTestFilters) {
    String testCaseName = testCaseFromRetry.getName();
    TestCase.Builder mergedTestCaseBuilder = TestCase.newBuilder().setName(testCaseName);

    // Map of test name to test
    ImmutableMap<String, Test> testsFromRetry =
        testCaseFromRetry.getTestList().stream()
            .collect(toImmutableMap(Test::getName, Function.identity()));

    for (Test testFromPrevSession : testCaseFromPrevSession.getTestList()) {
      mergedTestCaseBuilder.addTest(
          createMergedTest(
              moduleId,
              testCaseName,
              testFromPrevSession,
              testsFromRetry,
              matchedRetryTestFilters.stream()
                  .filter(filter -> filter.contains(testFromPrevSession.getName()))
                  .collect(toImmutableSet())));
    }

    return mergedTestCaseBuilder.build();
  }

  private Test createMergedTest(
      String moduleId,
      String testCaseName,
      Test testFromPrevSession,
      Map<String, Test> testsFromRetry,
      Set<String> matchedRetryTestFilters) {
    String testName = testFromPrevSession.getName();
    boolean testFoundInRetry = testsFromRetry.containsKey(testName);
    boolean testNotRetried = false;
    if (!matchedRetryTestFilters.isEmpty() && !testFoundInRetry) {
      logger.atWarning().log(
          "Test %s %s#%s should've been retried but not found in retry result",
          moduleId, testCaseName, testName);
      testNotRetried = true;
    } else if (matchedRetryTestFilters.isEmpty()) {
      testNotRetried = true;
    }
    if (testNotRetried) {
      // The test was not retried, mark it as cached
      Test.Builder testBuilder = testFromPrevSession.toBuilder();
      boolean isCached =
          testBuilder.getMetricList().stream()
              .anyMatch(metric -> metric.getKey().equals(XmlConstants.METRIC_CACHED_KEY));
      if (!isCached) {
        testBuilder.addMetric(
            Metric.newBuilder().setKey(XmlConstants.METRIC_CACHED_KEY).setContent("true"));
      }
      return testBuilder.build();
    }

    return testsFromRetry.get(testName);
  }

  private Run createOneRunHistory(Result result) {
    Run.Builder runBuilder = Run.newBuilder();
    for (Attribute attr : result.getAttributeList()) {
      if (attr.getKey().equals(XmlConstants.START_TIME_ATTR)) {
        Long startTime = Longs.tryParse(attr.getValue());
        if (startTime != null) {
          runBuilder.setStartTimeMillis(startTime);
        }
      } else if (attr.getKey().equals(XmlConstants.END_TIME_ATTR)) {
        Long endTime = Longs.tryParse(attr.getValue());
        if (endTime != null) {
          runBuilder.setEndTimeMillis(endTime);
        }
      } else if (attr.getKey().equals(XmlConstants.COMMAND_LINE_ARGS)) {
        runBuilder.setCommandLineArgs(attr.getValue());
      } else if (attr.getKey().equals(XmlConstants.HOST_NAME_ATTR)) {
        runBuilder.setHostName(attr.getValue());
      }
    }
    return runBuilder
        .setPassedTests(result.getSummary().getPassed())
        .setFailedTests(result.getSummary().getFailed())
        .build();
  }
}
