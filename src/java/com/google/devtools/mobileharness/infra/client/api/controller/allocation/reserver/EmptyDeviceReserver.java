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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver;

import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;

/** Empty implementation of {@link DeviceReserver}. */
public class EmptyDeviceReserver implements DeviceReserver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void addTempAllocationKeyToDevice(
      DeviceLocator deviceLocator,
      String allocationDimensionName,
      String allocationKey,
      Duration ttl) {
    logger.atWarning().log("EmptyDeviceReserver has no real implementation.");
  }
}
