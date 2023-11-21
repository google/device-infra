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

package com.google.devtools.mobileharness.infra.controller.device.external;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.daemon.health.handler.DrainHandler;
import java.time.Duration;

/**
 * The device manager which can operate the devices controlled by non-MH lab server device manager.
 */
public interface ExternalDeviceManager extends DrainHandler {

  /**
   * Reserves a device.
   *
   * @param deviceId the device to be reserved
   * @param timeout the timeout for the reservation to succeed. The actual timeout may a little
   *     longer than the assigned value depending on the implementation.
   * @return the DeviceReservation object representing a reservation.
   * @throws MobileHarnessException if failed to reserve a device.
   */
  DeviceReservation reserveDevice(String deviceId, Duration timeout)
      throws MobileHarnessException, InterruptedException;

  /** Gets the device status. */
  DeviceStatus getDeviceStatus(String deviceId);

  /** Gets whether this external DM manages device life cycle. */
  boolean isManagingDeviceLifeCycle();

  /** Gets whether the external DM recovers unhealthy devices. */
  boolean isManagingDeviceRecovery();

  /** The device status. */
  enum DeviceStatus {
    UNKNOWN,
    IDLE,
    // Reserved by MH lab server.
    RESERVED_BY_MH,
    // Allocated by the external device manager.
    RESERVED_BY_EXTERNAL_DM,
    UNAVAILABLE,
    // The device is almost idle, but still cannot be allocated.
    // When a test has released a device in the external device manager, i.e. Tradefed, we may want
    // to wait for a while and let other stack to use the device firstly to reduce allocation
    // conflicts and implement simple priority.
    NEAR_IDLE,
  }

  /** Represents a device reservation. */
  interface DeviceReservation extends AutoCloseable {
    @Override
    void close();

    String getReservationId();

    void markRunningTest();
  }
}
