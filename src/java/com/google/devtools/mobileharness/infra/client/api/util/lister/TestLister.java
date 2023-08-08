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

package com.google.devtools.mobileharness.infra.client.api.util.lister;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.lister.Lister;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Job plugin to generate tests for a job, when users don't specify the test list.
 *
 * <p>Note this test lister is running on user(client) machines only. It does NOT run on devices.
 */
public class TestLister {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(required = false, help = "The filter of test lister by regular expression.")
  public static final String PARAM_LISTER_FILTER = "lister_filter";

  /** Factory for creating {@link Lister} instances. */
  private final ListerFactory listFactory;

  /** Creates a test lister for generating tests of a job. */
  public TestLister() {
    this(new ListerFactory());
  }

  /** Constructor for test only. */
  @VisibleForTesting
  TestLister(ListerFactory listFactory) {
    this.listFactory = listFactory;
  }

  /** Generates the tests of the job. */
  @Subscribe
  public void onJobStart(JobStartEvent event) throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = event.getJob();
    if (!jobInfo.tests().isEmpty()) {
      return;
    }

    // Only use test lister when no test specified.
    String driver = jobInfo.type().getDriver();
    Class<? extends Lister> listerClass = ClassUtil.getListerClass(driver);
    MobileHarnessException.checkNotNull(
        listerClass,
        ErrorCode.TEST_LISTER_ERROR,
        String.format(
            "Can't find test lister for driver: %s (expected class with name %s)",
            driver, driver + Lister.class.getSimpleName()));
    Lister lister = listFactory.createLister(listerClass);

    jobInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Generating tests using %s...", listerClass.getSimpleName());
    // The test lister may add TestInfo into JobInfo directly.
    List<String> tests = lister.listTests(jobInfo);
    if (tests.isEmpty() && jobInfo.tests().isEmpty()) {
      throw new MobileHarnessException(
          ErrorCode.TEST_LISTER_ERROR,
          "Failed to generate the test list of the job with driver: " + driver);
    }

    Pattern filterPattern = null;
    String filterRegex = jobInfo.params().get(PARAM_LISTER_FILTER);
    if (!Strings.isNullOrEmpty(filterRegex)) {
      try {
        filterPattern = Pattern.compile(filterRegex);
      } catch (PatternSyntaxException exception) {
        throw new MobileHarnessException(
            ErrorCode.TEST_LISTER_ERROR,
            String.format("Param %s is not a valid regular expression", filterRegex),
            exception);
      }
      jobInfo.log().atInfo().alsoTo(logger).log("lister_filter enabled: %s", filterRegex);
    }
    // Filters the tests added by the lister.
    for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
      String testName = testInfo.locator().getName();
      if (filterPattern != null && !filterPattern.matcher(testName).matches()) {
        jobInfo.log().atInfo().alsoTo(logger).log("Test %s ignored by lister_filter", testName);
        String testId = testInfo.locator().getId();
        jobInfo.tests().remove(testId);
      }
    }
    // Filters the tests returned by the lister.
    for (String test : tests) {
      if (filterPattern != null && !filterPattern.matcher(test).matches()) {
        jobInfo.log().atInfo().alsoTo(logger).log("Test %s ignored by lister_filter", test);
      } else {
        jobInfo.tests().add(test.replace("$", "\\$"));
      }
    }
    jobInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Generated tests:\n- %s\n", Joiner.on("\n- ").join(jobInfo.tests().getAll().keys()));
  }
}
