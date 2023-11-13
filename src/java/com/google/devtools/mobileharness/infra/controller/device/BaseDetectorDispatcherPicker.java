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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.detector.NoOpDeviceDetector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.AndroidRealDeviceDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.NoOpDeviceDispatcher;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.List;

/** Picker for picking devices detectors / dispatchers for (OSS) MH local mode / lab. */
public class BaseDetectorDispatcherPicker {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static ImmutableList<Detector> checkAndGetSupportedDetectors()
      throws InterruptedException {
    ImmutableList<Detector> detectorCandidates = createDetectorCandidates();
    return BaseDetectorDispatcherPicker.checkDetectors(detectorCandidates);
  }

  public static ImmutableList<Detector> createDetectorCandidates() {
    ImmutableList.Builder<Detector> detectorCandidates = ImmutableList.builder();
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      detectorCandidates.add(
          new com.google.devtools.mobileharness.api.devicemanager.detector.BaseAdbDetector());
    }
    if (Flags.instance().noOpDeviceNum.getNonNull() > 0) {
      detectorCandidates.add(new NoOpDeviceDetector());
    }
    return detectorCandidates.build();
  }

  public static ImmutableList<Detector> checkDetectors(List<Detector> detectorCandidates)
      throws InterruptedException {
    ImmutableList.Builder<Detector> supportedDetectors = ImmutableList.builder();
    for (Detector detector : detectorCandidates) {
      if (detector.precondition()) {
        supportedDetectors.add(detector);
      } else {
        logger.atWarning().log(
            "Current system environment does not support %s", detector.getClass().getSimpleName());
      }
    }
    return supportedDetectors.build();
  }

  public static ImmutableList<Class<? extends Dispatcher>> getSupportedDispatchers() {
    DispatcherManager dispatcherManager = DispatcherManager.getInstance();
    addDispatchers(dispatcherManager);
    return dispatcherManager.getAllDispatchersInOrder();
  }

  public static void addDispatchers(DispatcherManager dispatcherManager) {
    // Adds Android real device dispatcher.
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      dispatcherManager.add(AndroidRealDeviceDispatcher.class);
    }

    // Adds NoOp dispatcher.
    if (Flags.instance().noOpDeviceNum.getNonNull() > 0) {
      dispatcherManager.add(NoOpDeviceDispatcher.class);
    }
  }

  private BaseDetectorDispatcherPicker() {}
}
