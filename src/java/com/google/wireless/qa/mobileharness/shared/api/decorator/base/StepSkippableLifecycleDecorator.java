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
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.StepSkippableLifecycleDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/**
 * A step-skippable decorator extending {@link AbstractLifecycleDecorator}.
 *
 * <p>Reads an execution mode string from job properties to conditionally skip the decorator's setup
 * or teardown step.
 *
 * <p><b>Important:</b> If the subclass is expected to be used as separate instances (to execute
 * setup and teardown steps in different jobs) and intends to share states between the setup and
 * teardown steps, it should use the {@link #setState} and {@link #getState} methods, instead of
 * using class member variables, or arbitrary job or test properties.
 */
public abstract class StepSkippableLifecycleDecorator extends AbstractLifecycleDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PROP_EXECUTION_MODE =
      "step_skippable_lifecycle_decorator_execution_mode";

  /** Execution mode of the step skippable lifecycle decorator. */
  public enum ExecutionMode {
    FULL,
    SETUP_ONLY,
    TEARDOWN_ONLY;
  }

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
    Optional<String> modeStr = jobInfo.properties().getOptional(PROP_EXECUTION_MODE);
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

  @Override
  protected final void setUp(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (shouldRunSetup()) {
      skippableSetUp(testInfo);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Decorator %s setup skipped.", getClass().getSimpleName());
    }
  }

  @Override
  protected final void tearDown(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (shouldRunTeardown()) {
      skippableTearDown(testInfo);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Decorator %s teardown skipped.", getClass().getSimpleName());
    }
  }

  /** Subclasses implement their setup logic here, which can be skipped. */
  protected void skippableSetUp(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /** Subclasses implement their teardown logic here, which can be skipped. */
  protected void skippableTearDown(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /**
   * Saves state into JobInfo properties to be relayed (e.g. by session plugin) to a subsequent job.
   */
  protected final void setState(JobInfo jobInfo, String key, String value) {
    StepSkippableLifecycleDecoratorUtil.setState(jobInfo, getNamespacedKey(key), value);
  }

  /** Retrieves state that was saved by this decorator (e.g. from a prior job). */
  protected final Optional<String> getState(JobInfo jobInfo, String key) {
    return StepSkippableLifecycleDecoratorUtil.getState(jobInfo, getNamespacedKey(key));
  }

  private String getNamespacedKey(String key) {
    return getClass().getSimpleName() + "_" + key;
  }
}
