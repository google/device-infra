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

package com.google.wireless.qa.mobileharness.shared.api.decorator.base;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.decorator.BaseDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * A generic base decorator class that provides native, framework-level enforcement of the setup and
 * teardown lifecycle steps.
 */
public abstract class AbstractLifecycleDecorator extends BaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected AbstractLifecycleDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  /** Invoked before the decorated driver executes. */
  protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {}

  /** Invoked after the decorated driver executes, regardless of success or failure. */
  protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {}

  @Override
  public final void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String className = getClass().getSimpleName();

    testInfo.log().atInfo().alsoTo(logger).log("Decorator %s setup starting.", className);
    setUp(testInfo);
    testInfo.log().atInfo().alsoTo(logger).log("Decorator %s setup finished.", className);

    try {
      getDecorated().run(testInfo);
    } finally {
      testInfo.log().atInfo().alsoTo(logger).log("Decorator %s teardown starting.", className);
      tearDown(testInfo);
      testInfo.log().atInfo().alsoTo(logger).log("Decorator %s teardown finished.", className);
    }
  }
}
