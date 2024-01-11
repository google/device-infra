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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.devicemanager.detector.BaseAdbDetector;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.detector.NoOpDeviceDetector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.AndroidRealDeviceDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.NoOpDeviceDispatcher;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.inject.Guice;

/** All detectors and dispatchers for {@link DetectorDispatcherSelector} in different components. */
final class AllDetectorsAndDispatchers {

  private AllDetectorsAndDispatchers() {}

  public static ImmutableList<Detector> detectorCandidatesForLocalModeInternal() {
    ImmutableList.Builder<Detector> detectorCandidates = ImmutableList.builder();
    detectorCandidates.addAll(detectorCandidatesForLocalModeInternal3pOssAndLabServerOss());
    detectorCandidates.addAll(
        getByComponent(
                "com.google.devtools.mobileharness.infra.controller.device.bootstrap."
                    + "DetectorsAndDispatchersForLocalModeInternalOnly")
            .detectorCandidates());
    return detectorCandidates.build();
  }

  public static ImmutableList<Detector> detectorCandidatesForLocalMode3pAndOss() {
    return detectorCandidatesForLocalModeInternal3pOssAndLabServerOss();
  }

  public static ImmutableList<Detector> detectorCandidatesForLabServerInternal() {
    return getByComponent(
            "com.google.devtools.mobileharness.infra.controller.device.bootstrap."
                + "DetectorsAndDispatchersForLabServerInternalOnly")
        .detectorCandidates();
  }

  public static ImmutableList<Detector> detectorCandidatesForLabServerOss() {
    return detectorCandidatesForLocalModeInternal3pOssAndLabServerOss();
  }

  private static ImmutableList<Detector>
      detectorCandidatesForLocalModeInternal3pOssAndLabServerOss() {
    ImmutableList.Builder<Detector> detectorCandidates = ImmutableList.builder();

    // ADB detector.
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      detectorCandidates.add(createAdbDetectorOss());
    }

    // NoOpDevice detector.
    if (Flags.instance().noOpDeviceNum.getNonNull() > 0) {
      detectorCandidates.add(new NoOpDeviceDetector());
    }
    return detectorCandidates.build();
  }

  private static Detector createAdbDetectorOss() {
    return new BaseAdbDetector();
  }

  public static void addDispatchersForLocalModeInternal(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);
    getByComponent(
            "com.google.devtools.mobileharness.infra.controller.device.bootstrap."
                + "DetectorsAndDispatchersForLocalModeInternalOnly")
        .addDispatchers(dispatcherManager);
  }

  public static void addDispatchersForLocalMode3pAndOss(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);
  }

  public static void addDispatchersForLabServerInternal(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);
    getByComponent(
            "com.google.devtools.mobileharness.infra.controller.device.bootstrap."
                + "DetectorsAndDispatchersForLabServerInternalOnly")
        .addDispatchers(dispatcherManager);
  }

  public static void addDispatchersForLabServerOss(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);
  }

  private static void addDispatchersForAll(DispatcherManager dispatcherManager) {
    // AndroidRealDevice dispatcher.
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      dispatcherManager.add(AndroidRealDeviceDispatcher.class);
    }

    // NoOpDevice dispatcher.
    if (Flags.instance().noOpDeviceNum.getNonNull() > 0) {
      dispatcherManager.add(NoOpDeviceDispatcher.class);
    }
  }

  private static DetectorsAndDispatchersByComponent getByComponent(String className) {
    try {
      return Guice.createInjector()
          .getInstance(
              new ReflectionUtil()
                  .loadClass(
                      className,
                      DetectorsAndDispatchersByComponent.class,
                      AllDetectorsAndDispatchers.class.getClassLoader()));
    } catch (MobileHarnessException | ClassNotFoundException e) {
      throw new IllegalStateException(
          String.format("Class %s is not added to runtime_deps", className), e);
    }
  }
}
