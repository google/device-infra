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

package com.google.wireless.qa.mobileharness.shared.model.lab;

/** For locating a local, or remote device. */
public class DeviceLocator {
  /** Device serial number. Unique within a lab, but may not be unique across labs. */
  private final String serial;

  /** Belong lab locator, or {@link LabLocator#LOCALHOST} for local device. */
  private final LabLocator labLocator;

  /** Creates a locator for a local device. */
  @Deprecated
  public DeviceLocator(String serial) {
    this(serial, LabLocator.LOCALHOST);
  }

  /** Creates a locator for a remote device. */
  public DeviceLocator(String deviceSerial, LabLocator labLocator) {
    this.serial = deviceSerial;
    this.labLocator = labLocator;
  }

  public DeviceLocator(
      com.google.devtools.mobileharness.api.model.lab.DeviceLocator newDeviceLocator) {
    this(newDeviceLocator.id(), new LabLocator(newDeviceLocator.labLocator().toProto()));
  }

  /** Returns universal unique ID, which is unique across labs. */
  public String getUniversalId() {
    if (LabLocator.LOCALHOST.equals(labLocator)) {
      return serial;
    } else {
      return serial + "@" + labLocator.getIp();
    }
  }

  /**
   * Returns the device serial number. It is unique within a lab, but may not be unique across labs.
   */
  public String getSerial() {
    return serial;
  }

  /** Returns the belong lab location, or {@link LabLocator#LOCALHOST} for local device. */
  public LabLocator getLabLocator() {
    return labLocator;
  }

  @Override
  public String toString() {
    if (LabLocator.LOCALHOST.equals(labLocator)) {
      return serial;
    } else {
      return serial + "@" + labLocator;
    }
  }

  public com.google.devtools.mobileharness.api.model.lab.DeviceLocator toNewDeviceLocator() {
    return com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
        serial, labLocator.toNewLabLocator());
  }
}
