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

package com.google.devtools.mobileharness.platform.testbed.adhoc.controller;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/** Factory for creating {@link AdhocTestbedDriver}. */
public class AdhocTestbedDriverFactory {

  public Driver create(
      List<Device> devices,
      TestInfo testInfo,
      ListeningExecutorService threadPool,
      DriverFactory driverFactory,
      @Nullable BiFunction<Driver, String, Decorator> driverWrapper,
      @Nullable BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException, InterruptedException {
    return new AdhocTestbedDriver(
        devices, testInfo, threadPool, driverFactory, driverWrapper, decoratorExtender);
  }
}
