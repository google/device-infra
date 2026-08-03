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
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants.ExecutionMode;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.StepSkippableLifecycleDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/**
 * A step-skippable decorator extending {@link LifecycleDecorator}.
 *
 * <p>Reads an execution mode string from job properties to conditionally skip the decorator's setup
 * or teardown step.
 *
 * <p><b>Important:</b> If the subclass is expected to be used as separate instances (to execute
 * setup and teardown steps in different jobs) and intends to share states between the setup and
 * teardown steps, it should use the {@link #setState} and {@link #getState} methods, instead of
 * using class member variables, or arbitrary job or test properties.
 */
public abstract class StepSkippableLifecycleDecorator extends LifecycleDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ExecutionMode mode;

  public StepSkippableLifecycleDecorator(Driver decorated, TestInfo testInfo)
      throws MobileHarnessException {
    super(decorated, testInfo);
    this.mode = extractMode(testInfo.jobInfo());
  }

  /**
   * Returns the {@link ExecutionMode} based on the job properties.
   *
   * @param jobInfo the job info
   * @return the execution mode, defaults to {@link ExecutionMode#FULL} if not set
   * @throws MobileHarnessException if the execution mode is unknown
   */
  private static ExecutionMode extractMode(JobInfo jobInfo) throws MobileHarnessException {
    Optional<String> modeStr =
        jobInfo.properties().getOptional(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE);
    if (modeStr.isEmpty()) {
      return ExecutionMode.FULL;
    }
    try {
      return ExecutionMode.valueOf(modeStr.get());
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          ExtErrorId.STEP_SKIPPABLE_LIFECYCLE_DECORATOR_UNKNOWN_EXECUTION_MODE,
          "Unknown execution mode: " + modeStr.get(),
          e);
    }
  }

  /** Gets whether setup should execute according to the Job's execution mode property. */
  private boolean shouldRunSetup() {
    return mode == ExecutionMode.FULL || mode == ExecutionMode.SETUP_ONLY;
  }

  /** Gets whether teardown should execute according to the Job's execution mode property. */
  private boolean shouldRunTeardown() {
    return mode == ExecutionMode.FULL || mode == ExecutionMode.TEARDOWN_ONLY;
  }

  @CanIgnoreReturnValue
  @Override
  protected final SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (shouldRunSetup()) {
      return skippableSetUp(context);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Decorator %s setup skipped.", getClass().getSimpleName());
      return SetupResult.continueDecorated();
    }
  }

  @Override
  protected final void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (shouldRunTeardown()) {
      skippableTearDown(context);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Decorator %s teardown skipped.", getClass().getSimpleName());
    }
  }

  /** Subclasses implement their setup logic here, which can be skipped. */
  @CanIgnoreReturnValue
  protected SetupResult skippableSetUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    return SetupResult.continueDecorated();
  }

  /** Subclasses implement their teardown logic here, which can be skipped. */
  protected void skippableTearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {}

  /**
   * Saves state into TestInfo properties to be relayed (e.g. by session plugin) to a subsequent
   * test.
   *
   * <p><b>Note:</b> The state is namespaced to the combination of test (via {@link TestInfo}),
   * decorator, and device to avoid collisions.
   *
   * @param deviceId The device identifier. Any identifier that is unique among devices within a
   *     test is acceptable (e.g., device UUID or Control ID). It is the caller's responsibility to
   *     ensure that the same ID type is used for both {@link #setState} and {@link #getState}.
   * @implNote The property key in {@link TestInfo} is formatted as: {@code
   *     step_skippable_lifecycle_decorator_state::<device-id>::<decorator-class-name>::<key>}.
   */
  protected final void setState(TestInfo testInfo, String deviceId, String key, String value) {
    StepSkippableLifecycleDecoratorUtil.setState(
        testInfo, deviceId, getClass().getName(), key, value);
  }

  /**
   * Retrieves state that was saved by this decorator (e.g. from a prior test).
   *
   * @param deviceId The device identifier. Any identifier that is unique among devices within a
   *     test is acceptable (e.g., device UUID or Control ID). It is the caller's responsibility to
   *     ensure that the same ID type is used for both {@link #setState} and {@link #getState}.
   */
  protected final Optional<String> getState(TestInfo testInfo, String deviceId, String key) {
    return StepSkippableLifecycleDecoratorUtil.getState(
        testInfo, deviceId, getClass().getName(), key);
  }
}
