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

package com.google.devtools.mobileharness.api.devicemanager.detector;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.faileddevice.FailedDeviceTable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Mobile Harness Detector for FailedDevice.
 *
 * <p>This detector uses the {@link FailedDeviceTable} to get a list of devices that the runner
 * reports as failing the SetUp step and returns these deviceId as DetectionType.FAILED. These
 * detection results will later lead to these devices being transformed from normal devices such as
 * AndroidRealDevices to FailedDevices and waiting for specific recovery jobs.
 */
public class FailedDeviceDetector implements Detector {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * A table/map in memory that records FailedDevice IDs. When a device fails initialization for a
   * few times, its ID will be put into this table.
   */
  private final FailedDeviceTable failedDeviceTable;

  public FailedDeviceDetector() {
    failedDeviceTable = FailedDeviceTable.getInstance();
  }

  @VisibleForTesting
  FailedDeviceDetector(FailedDeviceTable failedDeviceTable) {
    this.failedDeviceTable = failedDeviceTable;
  }

  @Override
  public boolean precondition() throws InterruptedException {
    return true;
  }

  /**
   * Detects FailedDevices.
   *
   * @return list of {@link DetectionResult} from the entries of FailedDeviceTable.
   * @throws MobileHarnessException if fails to detect devices
   * @throws InterruptedException if the current thread or its sub-thread is {@linkplain
   *     Thread#interrupt() interrupted} by another thread
   */
  @Override
  public List<DetectionResult> detectDevices() throws MobileHarnessException, InterruptedException {
    List<DetectionResult> results =
        failedDeviceTable.getFailedDeviceIds().stream()
            .map(id -> DetectionResult.of(id, DetectionType.FAILED))
            .collect(Collectors.toList());
    logger.atInfo().atMostEvery(30, SECONDS).log("Detected FailedDevices: %s", results);
    return results;
  }
}
