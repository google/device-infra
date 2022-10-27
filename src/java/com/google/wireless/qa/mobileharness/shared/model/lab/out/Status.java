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

package com.google.wireless.qa.mobileharness.shared.model.lab.out;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Device status. */
public class Status {
  /** The logger of this device. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Status of this device. */
  private volatile DeviceStatus status;

  /** Creates the status of a device. */
  public Status(DeviceStatus status) {
    this.status = status;
  }

  /** Updates the status with the given value. */
  @CanIgnoreReturnValue
  public synchronized Status set(DeviceStatus status) {
    logger.atInfo().log("Device status %s -> %s", this.status, status);
    this.status = status;
    return this;
  }

  /** Gets the current status. */
  public DeviceStatus get() {
    return status;
  }
}
