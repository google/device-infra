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

package com.google.devtools.mobileharness.api.devicemanager.detector.model;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import javax.annotation.Nullable;

/** The data model containing all information of a detection result. */
@AutoValue
public abstract class DetectionResult {
  public static DetectionResult of(String deviceControlId, DetectionType detectionType) {
    return of(deviceControlId, detectionType, null /* detectionDetail */);
  }

  public static DetectionResult of(
      String deviceControlId, DetectionType type, @Nullable Object detectionDetail) {
    return new AutoValue_DetectionResult(
        deviceControlId, type, Optional.ofNullable(detectionDetail));
  }

  /** The device control ID detected by detector. */
  public abstract String deviceControlId();

  /** The detector detection type. */
  public abstract DetectionType detectionType();

  /** The detector detection detail, defined by different detector. */
  public abstract Optional<Object> detectionDetail();

  /** The detection type enum corresponding to each detector. */
  public enum DetectionType {
    ADB,
    CROS,
    DHCP,
    EMBEDDED_LINUX,
    EMULATOR,
    EVB_BOARD,
    FAILED,
    FASTBOOT,
    FUCHSIA,
    LINUX,
    IDEVICE_ID,
    MAC,
    MANEKI,
    MONITOR,
    NEST,
    NO_OP,
    OPEN_WRT,
    PIXEL_BUDS,
    SIMULATOR,
    STATIC_TESTBED,
    TTB,
    LIBUSB,
    VIDEO,
    WINDOWS,
  }
}
