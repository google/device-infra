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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.devicemanager.detector.AdbDetector;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.detector.FailedDeviceDetector;
import com.google.devtools.mobileharness.api.devicemanager.detector.FastbootDetector;
import com.google.devtools.mobileharness.api.devicemanager.detector.NoOpDeviceDetector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.AndroidLocalEmulatorDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.AndroidRealDeviceDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.NoOpDeviceDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.util.ClassUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import java.util.Optional;

/** All detectors and dispatchers for {@link DetectorDispatcherSelector} in different components. */
final class AllDetectorsAndDispatchers {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final ReflectionUtil REFLECTION_UTIL = new ReflectionUtil();

  private AllDetectorsAndDispatchers() {}

  public static ImmutableList<Detector> detectorCandidatesForLocalModeOss() {
    return detectorCandidatesForLocalModeInternalOssAndLabServerOss();
  }

  public static ImmutableList<Detector> detectorCandidatesForLabServerOss() {
    ImmutableList.Builder<Detector> detectorCandidates = ImmutableList.builder();
    detectorCandidates
        .addAll(detectorCandidatesForLocalModeInternalOssAndLabServerOss())
        .add(new FailedDeviceDetector());

    if (Flags.enableFastbootDetector.getNonNull()) {
      detectorCandidates.add(new FastbootDetector());
    }

    return detectorCandidates.build();
  }

  private static ImmutableList<Detector>
      detectorCandidatesForLocalModeInternalOssAndLabServerOss() {
    ImmutableList.Builder<Detector> detectorCandidates = ImmutableList.builder();

    // Android Desktop Executor detector.
    if (Flags.androidDesktopExecutorDevicesNum.getNonNull() > 0) {
      addDetector(detectorCandidates, "AndroidDesktopExecutorDetector");
      return detectorCandidates.build();
    }

    // ADB detector.
    if (Flags.detectAdbDevice.getNonNull()) {
      detectorCandidates.add(new AdbDetector());
    }

    // Android JIT emulator detector.
    if (Flags.enableEmulatorDetection.getNonNull()) {
      if (Flags.androidJitEmulatorNum.getNonNull() > 0) {
        addDetector(detectorCandidates, "AndroidJitEmulatorDetector");
      }
    }

    // NoOpDevice detector.
    if (Flags.noOpDeviceNum.getNonNull() > 0) {
      detectorCandidates.add(new NoOpDeviceDetector());
    }

    return detectorCandidates.build();
  }

  public static void addDispatchersForLocalModeOss(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);

    addDispatchersForLocalModeLabServerOss(dispatcherManager);
  }

  public static void addDispatchersForLabServerOss(DispatcherManager dispatcherManager) {
    addDispatchersForAll(dispatcherManager);
    addDispatchersForLocalModeLabServerOss(dispatcherManager);

    // Adds the failed device dispatcher.
    addDispatchersAsDependencyOfAll(dispatcherManager, ImmutableList.of("FailedDeviceDispatcher"));
  }

  private static void addDispatchersForAll(DispatcherManager dispatcherManager) {
    // AndroidRealDevice dispatcher.
    if (Flags.detectAdbDevice.getNonNull()) {
      dispatcherManager.add(AndroidRealDeviceDispatcher.class);
    }

    // NoOpDevice dispatcher.
    if (Flags.noOpDeviceNum.getNonNull() > 0) {
      dispatcherManager.add(NoOpDeviceDispatcher.class);
    }

    // Android JIT emulator dispatcher.
    if (Flags.enableEmulatorDetection.getNonNull()) {
      if (Flags.androidJitEmulatorNum.getNonNull() > 0) {
        addDispatcher(dispatcherManager, "AndroidJitEmulatorDispatcher");
      }
    }

    // Android Desktop Executor dispatcher.
    if (Flags.androidDesktopExecutorDevicesNum.getNonNull() > 0) {
      addDispatcher(dispatcherManager, "AndroidDesktopExecutorDeviceDispatcher");
    }
  }

  private static void addDispatchersForLocalModeLabServerOss(DispatcherManager dispatcherManager) {
    if (Flags.enableEmulatorDetection.getNonNull()) {
      dispatcherManager.add(AndroidLocalEmulatorDispatcher.class);
      dispatcherManager.addDependency(
          AndroidRealDeviceDispatcher.class.getSimpleName(),
          AndroidLocalEmulatorDispatcher.class.getSimpleName());
    }
  }

  /** Adds the dispatchers to be dependencies of all existing dispatchers. */
  private static void addDispatchersAsDependencyOfAll(
      DispatcherManager dispatcherManager, ImmutableList<String> dispatcherNames) {

    ImmutableList<Class<? extends Dispatcher>> dispatcherClasses =
        dispatcherNames.stream()
            .map(AllDetectorsAndDispatchers::loadDispatcherClass)
            .flatMap(Optional::stream)
            .collect(toImmutableList());
    for (Class<? extends Dispatcher> dispatcherClass : dispatcherClasses) {
      dispatcherManager
          .getAllDispatchersInOrder()
          .forEach(
              existingDispatcher ->
                  dispatcherManager.addDependency(
                      existingDispatcher.getSimpleName(), dispatcherClass.getSimpleName()));
    }
    for (Class<? extends Dispatcher> dispatcherClass : dispatcherClasses) {
      dispatcherManager.add(dispatcherClass);
    }
  }

  @VisibleForTesting
  static Optional<Detector> createDetector(String detectorClassSimpleName) {
    String detectorClassName = Detector.class.getPackageName() + "." + detectorClassSimpleName;
    try {
      return Optional.of(
          REFLECTION_UTIL
              .loadClass(
                  detectorClassName,
                  Detector.class,
                  AllDetectorsAndDispatchers.class.getClassLoader())
              .getConstructor()
              .newInstance());
    } catch (MobileHarnessException | ReflectiveOperationException e) {
      logger.atFine().withCause(e).log(
          "Detector class [%s] is not added as runtime_deps, skipping", detectorClassName);
      return Optional.empty();
    }
  }

  private static void addDetector(
      ImmutableList.Builder<Detector> detectorCandidates, String detectorClassSimpleName) {
    createDetector(detectorClassSimpleName).ifPresent(detectorCandidates::add);
  }

  @VisibleForTesting
  static Optional<Class<? extends Dispatcher>> loadDispatcherClass(
      String dispatcherClassSimpleName) {
    try {
      return Optional.of(ClassUtil.getDispatcherClass(dispatcherClassSimpleName));
    } catch (MobileHarnessException e) {
      logger.atFine().withCause(e).log(
          "Dispatcher class [%s] is not added as runtime_deps, skipping",
          dispatcherClassSimpleName);
      return Optional.empty();
    }
  }

  private static void addDispatcher(
      DispatcherManager dispatcherManager, String dispatcherClassSimpleName) {
    loadDispatcherClass(dispatcherClassSimpleName).ifPresent(dispatcherManager::add);
  }
}
