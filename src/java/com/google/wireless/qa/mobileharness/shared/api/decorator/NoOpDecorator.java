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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.NoOpDecoratorSpec;
import javax.inject.Inject;

/** Simple {@link Decorator} implementation for tests only. */
@DecoratorAnnotation(help = "Do nothing in the decorator.")
public class NoOpDecorator extends BaseDecorator implements SpecConfigable<NoOpDecoratorSpec> {

  @Inject
  NoOpDecorator(Driver decoratedDriver, TestInfo testInfo) {
    super(decoratedDriver, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    NoOpDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this);
    try {
      getDecorated().run(testInfo);
    } finally {
      if (spec.hasExpectedResultBeforePostRun()) {
        // Checks if expected result is matched.
        TestResult expectedResult = spec.getExpectedResultBeforePostRun();
        TestResult currentResult = Result.upgradeTestResult(testInfo.result().get());
        if (currentResult.equals(expectedResult)) {
          testInfo.log().atInfo().log(
              "NoOpDecorator got expected result [%s] before postRun(), set result to PASS",
              expectedResult);
          testInfo.result().set(Job.TestResult.PASS);
        } else {
          testInfo
              .errors()
              .addAndLog(
                  ErrorCode.TEST_FAILED,
                  String.format(
                      "NoOpDecorator expected result [%s] before postRun() but got [%s],"
                          + " set result to FAIL",
                      expectedResult, currentResult));
          testInfo.result().set(Job.TestResult.FAIL);
        }
      }
    }
  }
}
