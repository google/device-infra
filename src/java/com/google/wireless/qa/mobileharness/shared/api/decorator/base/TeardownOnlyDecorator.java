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

import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * A lifecycle decorator that only performs work during the teardown phase.
 *
 * <p>Subclasses implement {@link #tearDown(TeardownContext)} to define their teardown logic. The
 * {@link #setUp(SetupContext)} method always returns {@link SetupResult#continueDecorated()} and
 * cannot be overridden.
 *
 * <p>Use this base class for decorators that only need to perform actions after the decorated
 * driver runs (e.g., collecting screenshots, pulling files, gathering stats) and have no setup to
 * perform before.
 */
public abstract class TeardownOnlyDecorator extends LifecycleDecorator {

  protected TeardownOnlyDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  /** No-op. Teardown-only decorators do not perform any setup. */
  @Override
  protected final SetupResult setUp(SetupContext context) {
    return SetupResult.continueDecorated();
  }
}
