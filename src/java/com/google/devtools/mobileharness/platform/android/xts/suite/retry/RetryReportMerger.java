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
import static java.lang.Math.min;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
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
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommonUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
  private final Clock clock;

  @Inject
  RetryReportMerger(
      PreviousResultLoader previousResultLoader, RetryGenerator retryGenerator, Clock clock) {
    this.previousResultLoader = previousResultLoader;
    this.retryGenerator = retryGenerator;
    this.clock = clock;
  }

  /** The merged result. */
  @AutoValue
  public abstract static class MergedResult {

    /** Creates a {@link MergedResult}. */
    public static MergedResult of(
        Result mergedResult, Result previousResult, @Nullable Result retryResult) {
      return new AutoValue_RetryReportMerger_MergedResult(
          mergedResult, previousResult, Optional.ofNullable(retryResult));
    }

    /** The merged result. */
    public abstract Result mergedResult();

    /** The previous result. */
    public abstract Result previousResult();

    /** The retry result. */
    public abstract Optional<Result> retryResult();
  }

  /**
   * Merges result from the previous session and the result from the retry.
   *
   * <p>Either {@code previousSessionIndex} or {@code previousSessionResultDirName} must be
   * provided.
   *
   * @param resultsDir the path to the "results" directory which stores the results for the previous
   *     sessions
   * @param previousSessionIndex index of the previous session being retried
   * @param previousSessionResultDirName name of the previous session result dir
   * @param retryType test statuses for retry
   * @param retryResult the result report for the "retry" run
   * @param passedInModules passed in module names from the command
   * @param passedInExcludeFilters passed in exclude filters from the command
   */
  public MergedResult mergeReports(
      Path resultsDir,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName,
      @Nullable RetryType retryType,
      @Nullable Result retryResult,
      ImmutableList<String> passedInModules,
      ImmutableSet<String> passedInExcludeFilters,
      ImmutableSet<String> skippedModuleIds)
      throws MobileHarnessException {
    Preconditions.checkState(previousSessionIndex != null || previousSessionResultDirName != null);
    // Loads the previous session result and its subplan used for the retry
    Result previousResult =
        previousResultLoader.loadPreviousResult(
            resultsDir, previousSessionIndex, previousSessionResultDirName);
    RetryArgs.Builder retryArgs = RetryArgs.builder().setResultsDir(resultsDir);
    if (previousSessionIndex != null) {
      retryArgs.setPreviousSessionIndex(previousSessionIndex);
    }
    if (previousSessionResultDirName != null) {
      retryArgs.setPreviousSessionResultDirName(previousSessionResultDirName);
    }
    if (retryType != null) {
      retryArgs.setRetryType(retryType);
    }
    if (!passedInModules.isEmpty()) {
      retryArgs.setPassedInModules(ImmutableSet.copyOf(passedInModules));
    }
    if (!passedInExcludeFilters.isEmpty()) {
      retryArgs.setPassedInExcludeFilters(
          passedInExcludeFilters.stream().map(SuiteTestFilter::create).collect(toImmutableSet()));
    }
    return mergeReportsHelper(
        retryArgs.build(), previousResult, retryResult, passedInExcludeFilters, skippedModuleIds);
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
   * @param passedInExcludeFilters passed in exclude filters from the command
   */
  public MergedResult mergeReports(
      Path resultsDir,
      String previousSessionId,
      @Nullable RetryType retryType,
      @Nullable Result retryResult,
      ImmutableList<String> passedInModules,
      ImmutableSet<String> passedInExcludeFilters,
      ImmutableSet<String> skippedModuleIds)
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
    if (!passedInExcludeFilters.isEmpty()) {
      retryArgs.setPassedInExcludeFilters(
          passedInExcludeFilters.stream().map(SuiteTestFilter::create).collect(toImmutableSet()));
    }
    return mergeReportsHelper(
        retryArgs.build(), previousResult, retryResult, passedInExcludeFilters, skippedModuleIds);
  }

  private MergedResult mergeReportsHelper(
      RetryArgs retryArgs,
      Result previousResult,
      @Nullable Result retryResult,
      ImmutableSet<String> passedInExcludeFilters,
      ImmutableSet<String> skippedModuleIds)
      throws MobileHarnessException {
    SubPlan subPlan = retryGenerator.generateRetrySubPlan(retryArgs);
    logger.atInfo().log("Merging previous result and retry result [RetryArgs: %s]", retryArgs);
    long startTime = clock.instant().toEpochMilli();
    try {
      return MergedResult.of(
          doMergeReports(
              previousResult, subPlan, retryResult, passedInExcludeFilters, skippedModuleIds),
          previousResult,
          retryResult);
    } finally {
      logger.atInfo().log(
          "Done merging previous result and retry result, took %s [RetryArgs: %s]",
          Duration.between(Instant.ofEpochMilli(startTime), clock.instant()), retryArgs);
    }
  }

  private Result doMergeReports(
      Result previousResult,
      SubPlan retrySubPlan,
      @Nullable Result retryResult,
      ImmutableSet<String> passedInExcludeFilters,
      ImmutableSet<String> skippedModuleIds) {
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
    // Also add the filters passed in by the command.
    mergedResult.addAllExcludeFilter(passedInExcludeFilters);

    // If no tests were retried, mark tests as cached, and inherit some info from previous result
    if (retryResult == null
        || (!retrySubPlan.hasAnyTfIncludeFilters() && !retrySubPlan.hasAnyNonTfIncludeFilters())) {
      logger.atInfo().log("No tests were retried, marking all tests as cached");
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
      return mergedResult.addAllExcludeFilter(passedInExcludeFilters).build();
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
    commandLineArgs.ifPresent(attributes::add);
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

    // Gets retry subplan include/exclude filters multimap ahead to avoid duplicated works.
    SetMultimap<String, String> retrySubPlanIncludeFiltersMultimap =
        retrySubPlan.getIncludeFiltersMultimap();
    SetMultimap<String, String> retrySubPlanNonTfIncludeFiltersMultimap =
        retrySubPlan.getNonTfIncludeFiltersMultimap();
    SetMultimap<String, String> retrySubPlanExcludeFiltersMultimap =
        retrySubPlan.getExcludeFiltersMultimap();
    SetMultimap<String, String> retrySubPlanNonTfExcludeFiltersMultimap =
        retrySubPlan.getNonTfExcludeFiltersMultimap();

    for (Module moduleFromPrevSession : previousResult.getModuleInfoList()) {
      // If the module is in the previous result but skipped in the retry session, remove it from
      // the merged result.
      String moduleId =
          AbiUtil.createId(moduleFromPrevSession.getAbi(), moduleFromPrevSession.getName());
      if (!modulesFromRetry.containsKey(moduleId) && skippedModuleIds.contains(moduleId)) {
        continue;
      }
      mergedResult.addModuleInfo(
          createMergedModule(
              moduleFromPrevSession,
              modulesFromRetry,
              moduleFromPrevSession.getIsNonTfModule()
                  ? retrySubPlanNonTfIncludeFiltersMultimap
                  : retrySubPlanIncludeFiltersMultimap,
              moduleFromPrevSession.getIsNonTfModule()
                  ? retrySubPlanNonTfExcludeFiltersMultimap
                  : retrySubPlanExcludeFiltersMultimap));
    }

    // Prepare the Summary
    long passedInSummary = 0;
    long failedInSummary = 0;
    int modulesDoneInSummary = 0;
    int modulesTotalInSummary = 0;
    for (Module module :
        mergedResult.getModuleInfoList().stream()
            .filter(module -> !SuiteCommonUtil.isModuleChecker(module))
            .collect(toImmutableList())) {
      if (module.getDone()) {
        modulesDoneInSummary++;
      }
      modulesTotalInSummary++;
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
      SetMultimap<String, String> subPlanIncludeFilters,
      SetMultimap<String, String> subPlanExcludeFilters) {
    String moduleId =
        AbiUtil.createId(moduleFromPrevSession.getAbi(), moduleFromPrevSession.getName());
    boolean moduleFoundInRetry = modulesFromRetry.containsKey(moduleId);
    if (!moduleFromPrevSession.getIsNonTfModule()
        && Flags.instance().useTfRetry.getNonNull()
        && moduleFoundInRetry) {
      // If the module is a TF module, and the retry used TF retry, and the module was retried, use
      // the module from the retry result.
      return modulesFromRetry.get(moduleId);
    }

    boolean moduleNotRetried = false;
    if (subPlanExcludeFilters.containsEntry(moduleId, SubPlan.ALL_TESTS_IN_MODULE)
        || !subPlanIncludeFilters.containsKey(moduleId)) {
      moduleNotRetried = true;
    } else if (!moduleFoundInRetry) {
      logger.atInfo().log("Module %s was not retried", moduleId);
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
        moduleId,
        moduleFromPrevSession,
        moduleFromRetry,
        subPlanIncludeFilters.get(moduleId),
        subPlanExcludeFilters.get(moduleId));
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
      Set<String> matchedRetryIncludeTestFilters,
      Set<String> matchedRetryExcludeTestFilters) {
    if (matchedRetryIncludeTestFilters.contains(SubPlan.ALL_TESTS_IN_MODULE)
        && matchedRetryIncludeTestFilters.size() == 1
        && matchedRetryExcludeTestFilters.isEmpty()) {
      // Try best to determine if the entire module was retried.
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

    // Gets test case name and test name from exclude filters.
    Map<String, ImmutableSet<String>> excludedTestCaseNamesAndTestNamesInRetry =
        matchedRetryExcludeTestFilters.stream()
            .map(TestCaseNameAndTestName::parse)
            .collect(
                groupingBy(
                    TestCaseNameAndTestName::testCaseName,
                    HashMap::new,
                    mapping(TestCaseNameAndTestName::testName, toImmutableSet())));

    // Gets test cases that are only in retry.
    HashSet<String> notCheckedTestCasesFromRetry = new HashSet<>(testCasesFromRetry.keySet());
    for (TestCase testCaseFromPrevSession : moduleFromPrevSession.getTestCaseList()) {
      notCheckedTestCasesFromRetry.remove(testCaseFromPrevSession.getName());
    }

    if (Flags.instance().xtsRetryReportMergerParallelTestCaseMerge.getNonNull()) {
      moduleFromPrevSession.getTestCaseList().parallelStream()
          .map(
              testCaseFromPrevSession ->
                  createMergedTestCase(
                      moduleId,
                      testCaseFromPrevSession,
                      testCasesFromRetry,
                      excludedTestCaseNamesAndTestNamesInRetry.getOrDefault(
                          testCaseFromPrevSession.getName(), ImmutableSet.of())))
          .forEachOrdered(mergedModuleBuilder::addTestCase);
    } else {
      for (TestCase testCaseFromPrevSession : moduleFromPrevSession.getTestCaseList()) {
        mergedModuleBuilder.addTestCase(
            createMergedTestCase(
                moduleId,
                testCaseFromPrevSession,
                testCasesFromRetry,
                excludedTestCaseNamesAndTestNamesInRetry.getOrDefault(
                    testCaseFromPrevSession.getName(), ImmutableSet.of())));
      }
    }

    if (!notCheckedTestCasesFromRetry.isEmpty()) {
      logger.atInfo().log(
          "Found %d test cases in retry result (module %s) but not in previous result",
          notCheckedTestCasesFromRetry.size(), moduleId);
      for (String testCaseName : notCheckedTestCasesFromRetry) {
        mergedModuleBuilder.addTestCase(testCasesFromRetry.get(testCaseName));
      }
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
      ImmutableSet<String> excludedTestNamesInTestCaseRetry) {
    String testCaseName = testCaseFromPrevSession.getName();
    boolean testCaseFoundInRetry = testCasesFromRetry.containsKey(testCaseName);
    boolean testCaseNotRetried = false;
    if (excludedTestNamesInTestCaseRetry.contains("")) {
      testCaseNotRetried = true;
    } else if (!testCaseFoundInRetry) {
      logger.atFine().log(
          "TestCase %s %s was not retried", moduleId, testCaseFromPrevSession.getName());
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
        excludedTestNamesInTestCaseRetry);
  }

  private TestCase doMergeTestCase(
      String moduleId,
      TestCase testCaseFromPrevSession,
      TestCase testCaseFromRetry,
      ImmutableSet<String> excludedTestNamesInTestCaseRetry) {
    String testCaseName = testCaseFromRetry.getName();
    TestCase.Builder mergedTestCaseBuilder = TestCase.newBuilder().setName(testCaseName);

    // Map of test name to test
    ImmutableMap<String, Test> testsFromRetry =
        testCaseFromRetry.getTestList().stream()
            .collect(toImmutableMap(Test::getName, Function.identity()));

    HashSet<String> notCheckedTestsFromRetry =
        testsFromRetry.keySet().stream().collect(toCollection(HashSet::new));
    for (Test testFromPrevSession : testCaseFromPrevSession.getTestList()) {
      notCheckedTestsFromRetry.remove(testFromPrevSession.getName());
      mergedTestCaseBuilder.addTest(
          createMergedTest(
              moduleId,
              testCaseName,
              testFromPrevSession,
              testsFromRetry,
              excludedTestNamesInTestCaseRetry.contains(testFromPrevSession.getName())));
    }
    if (!notCheckedTestsFromRetry.isEmpty()) {
      logger.atInfo().log(
          "Found %d tests in retry result (module %s, test case %s) but not in previous result",
          notCheckedTestsFromRetry.size(), moduleId, testCaseName);
      for (String testName : notCheckedTestsFromRetry) {
        mergedTestCaseBuilder.addTest(testsFromRetry.get(testName));
      }
    }

    return mergedTestCaseBuilder.build();
  }

  private Test createMergedTest(
      String moduleId,
      String testCaseName,
      Test testFromPrevSession,
      Map<String, Test> testsFromRetry,
      boolean excludedInRetry) {
    String testName = testFromPrevSession.getName();
    boolean testFoundInRetry = testsFromRetry.containsKey(testName);
    boolean testNotRetried = false;
    if (excludedInRetry) {
      testNotRetried = true;
    } else if (!testFoundInRetry) {
      logger.atFine().log("Test %s %s#%s was not retried ", moduleId, testCaseName, testName);
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

  @AutoValue
  abstract static class TestCaseNameAndTestName {

    abstract String testCaseName();

    abstract String testName();

    private static TestCaseNameAndTestName parse(String originalString) {
      int testCaseNameLength = originalString.indexOf('#');
      if (testCaseNameLength == -1) {
        testCaseNameLength = originalString.length();
      }

      String testCaseName = originalString.substring(0, testCaseNameLength);
      String testName =
          originalString.substring(min(testCaseNameLength + 1, originalString.length()));

      return new AutoValue_RetryReportMerger_TestCaseNameAndTestName(testCaseName, testName);
    }
  }
}
