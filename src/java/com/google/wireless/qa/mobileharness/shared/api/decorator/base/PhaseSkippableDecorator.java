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
import com.google.common.reflect.TypeToken;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants.ExecutionMode;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.lang.reflect.ParameterizedType;
import javax.annotation.Nullable;

/**
 * A stateless composition shell extending {@link LifecycleDecorator} that combines distinct
 * single-phase decorators ({@link SetupOnlyDecorator} and {@link TeardownOnlyDecorator}).
 *
 * <p>Supports dynamic execution skipping based on job properties ({@link
 * PhaseSkippableDecoratorConstants#PROP_EXECUTION_MODE}: FULL, SETUP_ONLY, TEARDOWN_ONLY).
 */
public abstract class PhaseSkippableDecorator<
        S extends SetupOnlyDecorator, T extends TeardownOnlyDecorator>
    extends LifecycleDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Class<S> setupDecoratorClass;
  private final Class<T> teardownDecoratorClass;
  private final ExecutionMode mode;
  @Nullable private final S setupDecorator;
  @Nullable private final T teardownDecorator;

  // Safe to ignore unchecked warning because class signature enforces S and T bounds on type
  // arguments.
  @SuppressWarnings("unchecked")
  protected PhaseSkippableDecorator(Driver decorated, TestInfo testInfo)
      throws MobileHarnessException {
    super(decorated, testInfo);

    // Get the execution mode from job properties.
    this.mode = PhaseSkippableDecoratorUtil.getExecutionMode(testInfo.jobInfo());

    // Resolve the generic type arguments (S and T) for the setup and teardown decorators.
    ParameterizedType type =
        (ParameterizedType)
            TypeToken.of((Class<? extends PhaseSkippableDecorator<?, ?>>) getClass())
                .getSupertype(PhaseSkippableDecorator.class)
                .getType();
    this.setupDecoratorClass = (Class<S>) type.getActualTypeArguments()[0];
    this.teardownDecoratorClass = (Class<T>) type.getActualTypeArguments()[1];

    // Instantiate the single-phase child decorators based on the execution mode.
    DriverFactory driverFactory = new DriverFactory();
    this.setupDecorator =
        (mode == ExecutionMode.FULL || mode == ExecutionMode.SETUP_ONLY)
            ? (S) driverFactory.decorateDriver(decorated, testInfo, setupDecoratorClass)
            : null;
    this.teardownDecorator =
        (mode == ExecutionMode.FULL || mode == ExecutionMode.TEARDOWN_ONLY)
            ? (T) driverFactory.decorateDriver(decorated, testInfo, teardownDecoratorClass)
            : null;
  }

  @CanIgnoreReturnValue
  @Override
  protected final SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    if (setupDecorator != null) {
      return setupDecorator.setUp(context);
    }
    context
        .testInfo()
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Decorator %s setup skipped.", getClass().getSimpleName());
    return SetupResult.continueDecorated();
  }

  @Override
  protected final void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    if (teardownDecorator != null) {
      teardownDecorator.tearDown(context);
    } else {
      context
          .testInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Decorator %s teardown skipped.", getClass().getSimpleName());
    }
  }
}
