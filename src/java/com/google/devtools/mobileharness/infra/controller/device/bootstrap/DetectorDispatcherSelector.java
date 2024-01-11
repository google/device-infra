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

package com.google.devtools.mobileharness.infra.controller.device.bootstrap;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;
import java.util.List;

/** Selector for selecting and preparing {@link DetectorsAndDispatchers} for a device manager. */
public final class DetectorDispatcherSelector {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The component where the device manager is. */
  public enum Component {
    LOCAL_MODE_3P,
    LAB_SERVER,
  }

  private final Component component;

  public DetectorDispatcherSelector(Component component) {
    this.component = requireNonNull(component);
  }

  public DetectorsAndDispatchers selectDetectorsAndDispatchers() throws InterruptedException {
    ImmutableList<Detector> supportedDetectors = selectSupportedDetectors();
    ImmutableList<Class<? extends Dispatcher>> supportedDispatchers = selectSupportedDispatchers();
    return DetectorsAndDispatchers.of(supportedDetectors, supportedDispatchers);
  }

  private ImmutableList<Detector> selectSupportedDetectors() throws InterruptedException {
    ImmutableList<Detector> detectorCandidates;
    switch (component) {
      case LOCAL_MODE_3P:
        detectorCandidates = AllDetectorsAndDispatchers.detectorCandidatesForLocalMode3pAndOss();
        break;
      default: // LAB_SERVER
        detectorCandidates = AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss();
        break;
    }
    return checkDetectors(detectorCandidates);
  }

  private ImmutableList<Class<? extends Dispatcher>> selectSupportedDispatchers() {
    DispatcherManager dispatcherManager = DispatcherManager.getInstance();
    switch (component) {
      case LOCAL_MODE_3P:
        AllDetectorsAndDispatchers.addDispatchersForLocalMode3pAndOss(dispatcherManager);
        break;
      default: // LAB_SERVER
        AllDetectorsAndDispatchers.addDispatchersForLabServerOss(dispatcherManager);
        break;
    }
    return dispatcherManager.getAllDispatchersInOrder();
  }

  @VisibleForTesting
  static ImmutableList<Detector> checkDetectors(List<Detector> detectorCandidates)
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
}
