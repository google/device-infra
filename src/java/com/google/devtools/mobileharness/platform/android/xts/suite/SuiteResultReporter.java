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
import com.google.common.primitives.Longs;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.TestStatus;
import com.google.devtools.mobileharness.platform.android.xts.common.util.TimeUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/** Generates the invocation summary. */
public class SuiteResultReporter {

  /* Gets an invocation summary for the given result. */
  public String getSummary(@Nullable Result result) {
    AtomicInteger totalModules = new AtomicInteger(0);
    AtomicInteger completeModules = new AtomicInteger(0);
    Map<String, String> failedModule = new HashMap<>();
    StringBuilder invocationSummary = new StringBuilder();
    long totalTests = 0L;
    long passedTests = 0L;
    long failedTests = 0L;
    long skippedTests = 0L;
    long assumeFailureTests = 0L;

    if (result != null) {
      totalModules.set(result.getSummary().getModulesTotal());
      for (Module module : result.getModuleInfoList()) {
        if (module.getDone()) {
          completeModules.incrementAndGet();
        } else {
          failedModule.put(module.getName(), module.getReason().getMsg());
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
      }
    }
    // print a short report summary
    invocationSummary.append("\n============================================\n");
    invocationSummary.append("================= Results ==================\n");
    if (result != null) {
      printModuleTestTime(result.getModuleInfoList(), invocationSummary);
      printTopSlowModules(result.getModuleInfoList(), invocationSummary);
      // TODO: Add PreparationMetrics, ModuleCheckersMetric, ModuleRetriesInformation
    }
    invocationSummary.append("=============== Summary ===============\n");
    if (result != null) {
      // Print the time from invocation start to end
      invocationSummary.append(
          String.format(
              "Total Run time: %s\n",
              TimeUtil.formatElapsedTime(getReportEndTime(result) - getReportStartTime(result))));
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

    // TODO: Add shard information
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
              "    %s: %s\n", m.getName(), TimeUtil.formatElapsedTime(m.getRuntimeMillis())));
      totalRunTime += m.getRuntimeMillis();
    }
    invocationSummary.append(
        String.format(
            "Total aggregated tests run time: %s\n", TimeUtil.formatElapsedTime(totalRunTime)));
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
              moduleTime.get(i).getName(),
              (moduleTime.get(i).getTotalTests() / (moduleTime.get(i).getRuntimeMillis() / 1000f)),
              moduleTime.get(i).getTotalTests(),
              moduleTime.get(i).getRuntimeMillis()));
    }
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
}
