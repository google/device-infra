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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidRuntimeStatsDecoratorSpec;
import javax.inject.Inject;

/** Decorator for collecting Android runtime stats. */
public class AndroidRuntimeStatsDecorator extends BaseDecorator
    implements SpecConfigable<AndroidRuntimeStatsDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private AndroidRuntimeStatsDecoratorSpec unused;

  @Inject
  AndroidRuntimeStatsDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo.log().atInfo().alsoTo(logger).log("AndroidRuntimeStatsDecorator.run");
    unused = testInfo.jobInfo().combinedSpec(this);
    onStart(testInfo);
    getDecorated().run(testInfo);
  }

  private void onStart(TestInfo unusedTestInfo) {
    // TODO: Implement pre-run stats collection setup if needed.
  }
}
