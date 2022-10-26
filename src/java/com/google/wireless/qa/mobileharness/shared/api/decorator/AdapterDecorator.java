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

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.BaseDriver;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * DEPRECATED. For adapting the {@link Decorator} implementation using the old {@link
 * com.google.wireless.qa.mobileharness.shared.api.job.TestInfo} data model only. Please migrate to
 * the new {@link TestInfo} and inherit {@link BaseDecorator} instead.
 */
@Deprecated
public abstract class AdapterDecorator extends BaseDriver implements Decorator {

  /** The driver going to be decorated. */
  private final Driver decorated;

  /**
   * Creates a decorator of the given driver. Do NOT modify the parameter list. This constructor is
   * required by the framework. All sub-class of this class should have a constructor with the same
   * parameter list.
   */
  public AdapterDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated.getDevice(), testInfo);
    this.decorated = decorated;
  }

  @Override
  public final void run(com.google.wireless.qa.mobileharness.shared.model.job.TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    run(new com.google.wireless.qa.mobileharness.shared.api.job.TestInfo(testInfo));
  }

  /** Returns the decorated driver. */
  @Override
  public Driver getDecorated() {
    return decorated;
  }
}
