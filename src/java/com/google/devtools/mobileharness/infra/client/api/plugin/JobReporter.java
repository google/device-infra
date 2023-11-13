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

package com.google.devtools.mobileharness.infra.client.api.plugin;

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The reporter that report the job log outside JobRunner. */
public class JobReporter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Subscribe
  public void onJobEnd(JobEndEvent event) {
    JobInfo jobInfo = event.getJob();
    List<String> notFinishedTests = new ArrayList<>();
    List<String> passedTests = new ArrayList<>();
    List<String> failedTests = new ArrayList<>();
    List<String> skippedTests = new ArrayList<>();

    ListMultimap<String, TestInfo> finalizedTests = jobInfo.tests().getFinalized();
    Set<String> testNames = new TreeSet<>(finalizedTests.keySet());
    for (String testName : testNames) {
      List<TestInfo> testInfos = getAssignedTestInfo(finalizedTests.get(testName));

      if (testInfos.isEmpty()) {
        // If no running info, it should be a test not assigned.
        notFinishedTests.add(testName);
      } else if (testInfos.size() == 1) {
        switch (testInfos.get(0).result().get()) {
          case UNKNOWN:
            // If only run once and result is UNKNOWN, it should be a test not finished.
            notFinishedTests.add(testName);
            break;
          case PASS:
            // If only run once and result is PASS, it should be a passed test.
            passedTests.add(testName);
            break;
          case SKIP:
            // If only run once and result is SKIP, it should be a skipped test.
            skippedTests.add(testName);
            break;
          default:
            // Otherwise it's a failed test.
            failedTests.add(testName);
        }
      } else {
        int passedNumber = 0;
        int skippedNumber = 0;
        for (TestInfo testInfo : testInfos) {
          if (testInfo.result().get().equals(TestResult.PASS)) {
            ++passedNumber;
          } else if (testInfo.result().get().equals(TestResult.SKIP)) {
            ++skippedNumber;
          }
        }
        if (passedNumber > 0 && passedNumber + skippedNumber == testInfos.size()) {
          // If all retries passed or skipped, it should be a passed test. NOTICE we have enforce
          // retry feature which may cause several passed retries.
          passedTests.add(testName);
        } else if (skippedNumber == testInfos.size()) {
          skippedTests.add(testName);
        } else if (passedNumber > 0 || skippedNumber > 0) {
          // If some retry passed, and others failed/error, it should be a flaky test.
          continue;
        } else {
          failedTests.add(testName);
        }
      }
    }

    String notFinishedTestsDetails = getResultDetail("Pending", notFinishedTests, finalizedTests);
    String notFinishedTestsCount = getResultCount("Pending", notFinishedTests);
    String passedTestsDetails = getResultDetail("Passed", passedTests, finalizedTests);
    String passedTestsCount = getResultCount("Passed", passedTests);
    String skippedTestsDetails = getResultDetail("Skipped", skippedTests, finalizedTests);
    String skippedTestCount = getResultCount("Skipped", skippedTests);
    String failedTestsDetails = getResultDetail("Failed/Error", failedTests, finalizedTests);
    String failedTestsCount = getResultCount("Failed/Error", failedTests);

    String jobResult = jobInfo.result().get().name() + "!";
    jobInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "The job %s %s%n%s%s%s%s%s%nTests run: %d, %s%s%s%s%n",
            jobInfo.locator().getName(),
            jobResult,
            notFinishedTestsDetails,
            passedTestsDetails,
            failedTestsDetails,
            skippedTestsDetails,
            jobResult,
            testNames.size(),
            notFinishedTestsCount,
            passedTestsCount,
            failedTestsCount,
            skippedTestCount);
  }

  private List<TestInfo> getAssignedTestInfo(List<TestInfo> finalizedTest) {
    List<TestInfo> testInfos = new ArrayList<>();
    for (TestInfo testInfo : finalizedTest) {
      if (!testInfo.status().get().equals(TestStatus.NEW)) {
        testInfos.add(testInfo);
      }
    }
    return testInfos;
  }

  private String getResultDetail(
      String resultTag, List<String> testNames, ListMultimap<String, TestInfo> finalizedTests) {
    if (testNames.isEmpty()) {
      return "";
    } else {
      return String.format(
          "%s tests(%d) {%n%s}%n",
          resultTag, testNames.size(), getResults(testNames, finalizedTests));
    }
  }

  private String getResultCount(String resultTag, List<String> testNames) {
    if (testNames.isEmpty()) {
      return "";
    } else {
      return String.format("%s: %d   ", resultTag, testNames.size());
    }
  }

  private String getResults(List<String> testNames, ListMultimap<String, TestInfo> finalizedTests) {
    StringBuilder result = new StringBuilder();
    for (String testName : testNames) {
      result.append("  ").append(testName).append(":");
      List<TestInfo> testInfos = finalizedTests.get(testName);
      if (testInfos.size() > 1) {

        result.append("\n    Attempts: ").append(testInfos.size());
        // Count the testInfo each result number. resultNum[0..4] count the 5 kinds {@link
        // TestResult} resultNum[5] counts the [NotAssigned] test which status is TestStatus.New.
        int[] resultNum = new int[6];
        for (TestInfo testInfo : testInfos) {
          if (testInfo.status().get().equals(TestStatus.NEW)
              || testInfo.status().get().equals(TestStatus.SUSPENDED)) {
            resultNum[5]++;
          } else {
            resultNum[testInfo.result().get().getNumber()]++;
          }
        }
        for (int i = 0; i < 5; ++i) {
          if (resultNum[i] > 0) {
            result.append(",  ").append(TestResult.forNumber(i)).append(": ").append(resultNum[i]);
          }
        }
        if (resultNum[5] > 0) {
          result.append(",  NOT_ASSIGNED: ").append(resultNum[5]);
        }
      } else {
        result.append(" ").append(Iterables.getOnlyElement(testInfos).result().get());
      }
      result.append("\n");
    }
    return String.valueOf(result);
  }
}
