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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingLong;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryStatsHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryStatsHelper.RetryStatistics;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Generates the invocation summary. */
public class SuiteResultReporter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RetryStatsHelper retryStatsHelper;

  @Inject
  SuiteResultReporter(RetryStatsHelper retryStatsHelper) {
    this.retryStatsHelper = retryStatsHelper;
  }

  private static final Comparator<String> MODULE_CHECKER_NAME_COMPARATOR =
      (String m1, String m2) -> {
        // module name is in format of "<abi> <module name with parameter if any>"
        ImmutableList<String> module1NameList = AbiUtil.parseId(m1);
        ImmutableList<String> module2NameList = AbiUtil.parseId(m2);
        int moduleNameCompareResult = module1NameList.get(1).compareTo(module2NameList.get(1));
        if (moduleNameCompareResult != 0) {
          return moduleNameCompareResult;
        }
        return module1NameList.get(0).compareTo(module2NameList.get(0));
      };

  /* Gets an invocation summary for the given result. */
  public String getSummary(@Nullable Result result, @Nullable Result previousResult) {
    logger.atInfo().log("Getting invocation summary...");
    AtomicInteger totalModules = new AtomicInteger(0);
    AtomicInteger completeModules = new AtomicInteger(0);
    Map<String, String> failedModule = new HashMap<>();
    // Map holding the preparation time for each Module.
    Map<String, ModulePrepTimes> preparationMap = new HashMap<>();
    TreeMultimap<String, Module> moduleCheckers =
        TreeMultimap.create(
            MODULE_CHECKER_NAME_COMPARATOR,
            (Module m1, Module m2) ->
                // Reverses based on the module checker name
                m2.getName().compareTo(m1.getName()));
    StringBuilder invocationSummary = new StringBuilder();
    long totalTests = 0L;
    long passedTests = 0L;
    long failedTests = 0L;
    long skippedTests = 0L;
    long assumeFailureTests = 0L;
    // Retry information
    long totalRetrySuccess = 0L;
    Map<String, Long> moduleRetrySuccess = new LinkedHashMap<>();
    long totalRetryFailure = 0L;
    Map<String, Long> moduleRetryFailure = new LinkedHashMap<>();
    Duration totalRetryTime = Duration.ZERO;
    Map<String, Duration> moduleRetryTime = new LinkedHashMap<>();

    if (result != null) {
      HashMap<String, Module> previousModulesMap = new HashMap<>();
      if (previousResult != null) {
        for (Module prevModule : previousResult.getModuleInfoList()) {
          previousModulesMap.put(getModuleName(prevModule), prevModule);
        }
      }

      for (Module module : result.getModuleInfoList()) {
        String moduleName = getModuleName(module);
        if (SuiteCommonUtil.isModuleChecker(module)) {
          moduleCheckers.put(getModuleCheckerName(module), module);
          continue;
        }
        totalModules.incrementAndGet();
        if (module.getDone()) {
          completeModules.incrementAndGet();
        } else {
          failedModule.put(moduleName, module.getReason().getMsg());
        }
        totalTests += module.getTotalTests();
        passedTests += module.getPassed();
        failedTests +=
            getNumTestsInState(
                module, TestStatus.convertToTestStatusCompatibilityString(TestStatus.FAILURE));
        skippedTests +=
            getNumTestsInState(
                module, TestStatus.convertToTestStatusCompatibilityString(TestStatus.IGNORED));
        skippedTests +=
            getNumTestsInState(
                module, TestStatus.convertToTestStatusCompatibilityString(TestStatus.SKIPPED));
        assumeFailureTests +=
            getNumTestsInState(
                module,
                TestStatus.convertToTestStatusCompatibilityString(TestStatus.ASSUMPTION_FAILURE));

        // Get the module metrics for target preparation
        if (module.hasPrepTimeMillis() && module.hasTeardownTimeMillis()) {
          preparationMap.put(
              moduleName,
              new ModulePrepTimes(module.getPrepTimeMillis(), module.getTeardownTimeMillis()));
        }

        if (previousResult != null) {
          Module previousModule = previousModulesMap.get(moduleName);
          if (previousModule != null) {
            RetryStatistics retryStatistics =
                retryStatsHelper.calculateModuleRetryStats(module, previousModule);
            totalRetrySuccess += retryStatistics.retrySuccess();
            moduleRetrySuccess.put(moduleName, retryStatistics.retrySuccess());
            totalRetryFailure += retryStatistics.retryFailure();
            moduleRetryFailure.put(moduleName, retryStatistics.retryFailure());
            totalRetryTime = totalRetryTime.plus(retryStatistics.retryTime());
            moduleRetryTime.put(moduleName, retryStatistics.retryTime());
          } else {
            logger.atWarning().log("Not able to find module %s in previous result", moduleName);
          }
        }
      }
    }
    // print a short report summary
    invocationSummary.append("============================================\n");
    invocationSummary.append("================= Results ==================\n");
    if (result != null) {
      printModuleTestTime(result.getModuleInfoList(), invocationSummary);
      printTopSlowModules(result.getModuleInfoList(), invocationSummary);
      printPreparationMetrics(preparationMap, invocationSummary);
      printModuleCheckersMetric(moduleCheckers, invocationSummary);
      printModuleRetriesInformation(
          totalRetrySuccess,
          moduleRetrySuccess,
          totalRetryFailure,
          moduleRetryFailure,
          totalRetryTime,
          moduleRetryTime,
          invocationSummary);
    }
    invocationSummary.append("=============== Summary ===============\n");
    if (result != null) {
      // Print the time from invocation start to end
      invocationSummary.append(
          String.format(
              "Total Run time: %s\n",
              TimeUtils.toReadableDurationString(
                  Duration.ofMillis(getReportEndTime(result) - getReportStartTime(result)))));
    }
    invocationSummary.append(
        String.format("%s/%s modules completed\n", completeModules, totalModules));
    if (!failedModule.isEmpty()) {
      invocationSummary.append("Module(s) with run failure(s):\n");
      for (Entry<String, String> e : failedModule.entrySet()) {
        invocationSummary.append(String.format("    %s: %s\n", e.getKey(), e.getValue()));
      }
    }
    invocationSummary.append(String.format("Total Tests       : %s\n", totalTests));
    invocationSummary.append(String.format("PASSED            : %s\n", passedTests));
    invocationSummary.append(String.format("FAILED            : %s\n", failedTests));

    if (skippedTests > 0L) {
      invocationSummary.append(String.format("IGNORED           : %s\n", skippedTests));
    }
    if (assumeFailureTests > 0L) {
      invocationSummary.append(String.format("ASSUMPTION_FAILURE: %s\n", assumeFailureTests));
    }

    if (completeModules.get() != totalModules.get()) {
      invocationSummary.append(
          "IMPORTANT: Some modules failed to run to completion, tests counts "
              + "may be inaccurate.\n");
    }

    invocationSummary.append("============== End of Results ==============\n");
    invocationSummary.append("============================================\n");
    return invocationSummary.toString();
  }

  /** Displays the time consumed by each module to run. */
  private void printModuleTestTime(Collection<Module> results, StringBuilder invocationSummary) {
    List<Module> moduleTime = new ArrayList<>();
    moduleTime.addAll(results);
    Collections.sort(moduleTime, comparingLong(Module::getRuntimeMillis).reversed());
    long totalRunTime = 0L;
    invocationSummary.append("=============== Consumed Time ==============\n");
    for (Module m : moduleTime) {
      invocationSummary.append(
          String.format(
              "    %s: %s\n",
              getModuleName(m),
              TimeUtils.toReadableDurationString(Duration.ofMillis(m.getRuntimeMillis()))));
      totalRunTime += m.getRuntimeMillis();
    }
    invocationSummary.append(
        String.format(
            "Total aggregated tests run time: %s\n",
            TimeUtils.toReadableDurationString(Duration.ofMillis(totalRunTime))));
  }

  /**
   * Displays the average tests / second of modules because elapsed is not always relevant. (Some
   * modules have way more test cases than others so only looking at elapsed time is not a good
   * metric for slow modules).
   */
  private void printTopSlowModules(Collection<Module> results, StringBuilder invocationSummary) {
    List<Module> moduleTime = new ArrayList<>();
    moduleTime.addAll(results);
    // We don't consider module which runs in less than 5 sec or that didn't run tests
    for (Module m : results) {
      if (m.getRuntimeMillis() < 5000) {
        moduleTime.remove(m);
      }
      if (m.getTotalTests() == 0) {
        moduleTime.remove(m);
      }
    }
    Collections.sort(
        moduleTime,
        comparing((Module arg) -> ((float) arg.getTotalTests() / arg.getRuntimeMillis())));
    int maxModuleDisplay = moduleTime.size();
    if (maxModuleDisplay == 0) {
      return;
    }
    invocationSummary.append(
        String.format("============== TOP %s Slow Modules ==============\n", maxModuleDisplay));
    for (int i = 0; i < maxModuleDisplay; i++) {
      invocationSummary.append(
          String.format(
              "    %s: %.02f tests/sec [%s tests / %s msec]\n",
              getModuleName(moduleTime.get(i)),
              (moduleTime.get(i).getTotalTests() / (moduleTime.get(i).getRuntimeMillis() / 1000f)),
              moduleTime.get(i).getTotalTests(),
              moduleTime.get(i).getRuntimeMillis()));
    }
  }

  /** Print the collected times for Module preparation and tear Down. */
  private void printPreparationMetrics(
      Map<String, ModulePrepTimes> metrics, StringBuilder invocationSummary) {
    if (metrics.isEmpty()) {
      return;
    }
    invocationSummary.append("============== Modules Preparation Times ==============\n");
    long totalPrep = 0L;
    long totalTear = 0L;

    for (String moduleName : metrics.keySet()) {
      invocationSummary.append(
          String.format("    %s => %s\n", moduleName, metrics.get(moduleName)));
      totalPrep += metrics.get(moduleName).prepTime;
      totalTear += metrics.get(moduleName).tearDownTime;
    }
    invocationSummary.append(
        String.format(
            "Total preparation time: %s  ||  Total tear down time: %s\n",
            TimeUtils.toReadableDurationString(Duration.ofMillis(totalPrep)),
            TimeUtils.toReadableDurationString(Duration.ofMillis(totalTear))));
    invocationSummary.append("=======================================================\n");
  }

  private void printModuleCheckersMetric(
      TreeMultimap<String, Module> moduleCheckers, StringBuilder invocationSummary) {
    if (moduleCheckers.isEmpty()) {
      return;
    }
    invocationSummary.append("============== Modules Checkers Times ==============\n");
    long totalTime = 0L;
    for (Entry<String, SortedSet<Module>> entry : Multimaps.asMap(moduleCheckers).entrySet()) {
      for (Module module : entry.getValue()) {
        invocationSummary.append(
            String.format(
                "    %s: %s\n",
                module.getName(),
                TimeUtils.toReadableDurationString(Duration.ofMillis(module.getRuntimeMillis()))));
        totalTime += module.getRuntimeMillis();
      }
    }
    invocationSummary.append(
        String.format(
            "Total module checkers time: %s\n",
            TimeUtils.toReadableDurationString(Duration.ofMillis(totalTime))));
    invocationSummary.append("====================================================\n");
  }

  private void printModuleRetriesInformation(
      long totalRetrySuccess,
      Map<String, Long> moduleRetrySuccess,
      long totalRetryFailure,
      Map<String, Long> moduleRetryFailure,
      Duration totalRetryTime,
      Map<String, Duration> moduleRetryTime,
      StringBuilder invocationSummary) {
    if (moduleRetrySuccess.isEmpty() || totalRetryTime.isZero()) {
      return;
    }
    invocationSummary.append("============== Modules Retries Information ==============\n");
    for (String t : moduleRetrySuccess.keySet()) {
      if (moduleRetryTime.get(t).isZero()) {
        continue;
      }
      invocationSummary.append(
          String.format(
              "    %s:\n"
                  + "        Retry Success (Failed test became Pass)   = %s\n"
                  + "        Retry Failure (Failed test stayed Failed) = %s\n"
                  + "        Retry Time                                = %s\n",
              t,
              moduleRetrySuccess.get(t),
              moduleRetryFailure.get(t),
              TimeUtils.toReadableDurationString(moduleRetryTime.get(t))));
    }
    invocationSummary.append("Summary:\n");
    invocationSummary.append(
        String.format(
            "Total Retry Success (Failed test became Pass) = %s\n"
                + "Total Retry Failure (Failed test stayed Failed) = %s\n"
                + "Total Retry Time                                = %s\n",
            totalRetrySuccess,
            totalRetryFailure,
            TimeUtils.toReadableDurationString(totalRetryTime)));
    invocationSummary.append("====================================================\n");
  }

  private static long getReportStartTime(Result result) {
    return result.getAttributeList().stream()
        .filter(attr -> attr.getKey().equals(XmlConstants.START_TIME_ATTR))
        .findFirst()
        .map(attr -> Longs.tryParse(attr.getValue()))
        .orElse(0L);
  }

  private static long getReportEndTime(Result result) {
    return result.getAttributeList().stream()
        .filter(attr -> attr.getKey().equals(XmlConstants.END_TIME_ATTR))
        .findFirst()
        .map(attr -> Longs.tryParse(attr.getValue()))
        .orElse(Instant.now().toEpochMilli());
  }

  private static long getNumTestsInState(Module module, String testStatus) {
    return module.getTestCaseList().stream()
        .flatMap(testCase -> testCase.getTestList().stream())
        .filter(test -> Ascii.equalsIgnoreCase(test.getResult(), testStatus))
        .count();
  }

  private static String getModuleName(Module module) {
    return AbiUtil.createId(module.getAbi(), module.getName());
  }

  private static String getModuleCheckerName(Module module) {
    // Module check name is in the format of "<Pre/Post>ModuleChecker_<abi> <module name with
    // parameter if any>"
    List<String> moduleCheckerNameList = Splitter.on("_").splitToList(module.getName());
    if (moduleCheckerNameList.size() != 2) {
      logger.atWarning().log(
          "Module checker name [%s] is not in the correct format.", module.getName());
      return module.getName();
    }
    return moduleCheckerNameList.get(1);
  }

  /** Object holder for the preparation and tear down time of one module. */
  private static class ModulePrepTimes {

    public final long prepTime;
    public final long tearDownTime;

    public ModulePrepTimes(long prepTime, long tearTime) {
      this.prepTime = prepTime;
      this.tearDownTime = tearTime;
    }

    @Override
    public String toString() {
      return String.format("prep = %s ms || clean = %s ms", prepTime, tearDownTime);
    }
  }
}
