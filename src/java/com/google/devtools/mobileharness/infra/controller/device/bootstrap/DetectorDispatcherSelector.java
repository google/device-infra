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
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Selector for selecting and preparing {@link DetectorsAndDispatchers} for a device manager. */
public final class DetectorDispatcherSelector {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final ListeningExecutorService executorService =
      ThreadPools.createStandardThreadPool("detector-dispatcher");

  /** The component where the device manager is. */
  public enum Component {
    LOCAL_MODE,
    LAB_SERVER,
  }

  private final Component component;

  public DetectorDispatcherSelector(Component component) {
    this.component = requireNonNull(component);
  }

  public DetectorsAndDispatchers selectDetectorsAndDispatchers() throws InterruptedException {
    ImmutableList<AsyncDetector> detectorCandidates = selectSupportedDetectors();
    ImmutableList<Class<? extends Dispatcher>> supportedDispatchers = selectSupportedDispatchers();
    return DetectorsAndDispatchers.of(
        checkDetectorsAsync(detectorCandidates), supportedDispatchers);
  }

  private ImmutableList<AsyncDetector> selectSupportedDetectors() {
    ImmutableList<Detector> detectorCandidates;
    if (component == Component.LOCAL_MODE) {
      detectorCandidates =
          Flags.instance().alwaysUseOssDetectorAndDispatcher.getNonNull()
              ? AllDetectorsAndDispatchers.detectorCandidatesForLocalModeOss()
              : AllDetectorsAndDispatchers.detectorCandidatesForLocalModeOss();
    } else {
      detectorCandidates =
          Flags.instance().alwaysUseOssDetectorAndDispatcher.getNonNull()
              ? AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss()
              : AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss();
    }
    return detectorCandidates.stream().map(AsyncDetector::new).collect(toImmutableList());
  }

  private ImmutableList<Class<? extends Dispatcher>> selectSupportedDispatchers() {
    DispatcherManager dispatcherManager = DispatcherManager.getInstance();
    if (component == Component.LOCAL_MODE) {
      if (Flags.instance().alwaysUseOssDetectorAndDispatcher.getNonNull()) {
        AllDetectorsAndDispatchers.addDispatchersForLocalModeOss(dispatcherManager);
      } else {
        AllDetectorsAndDispatchers.addDispatchersForLocalModeOss(dispatcherManager);
      }
    } else {
      if (Flags.instance().alwaysUseOssDetectorAndDispatcher.getNonNull()) {
        AllDetectorsAndDispatchers.addDispatchersForLabServerOss(dispatcherManager);
      } else {
        AllDetectorsAndDispatchers.addDispatchersForLabServerOss(dispatcherManager);
      }
    }
    return dispatcherManager.getAllDispatchersInOrder();
  }

  @VisibleForTesting
  static ImmutableList<Detector> checkDetectors(List<Detector> detectorCandidates)
      throws InterruptedException {
    return checkDetectorsAsync(
        detectorCandidates.stream().map(AsyncDetector::new).collect(toImmutableList()));
  }

  private static ImmutableList<Detector> checkDetectorsAsync(List<AsyncDetector> detectorCandidates)
      throws InterruptedException {
    ImmutableList.Builder<Detector> supportedDetectors = ImmutableList.builder();
    for (var result : detectorCandidates) {
      try {
        if (result.asyncPrecondition.get()) {
          supportedDetectors.add(result.detector);
        } else {
          logger.atWarning().log(
              "Current system environment does not support %s",
              result.detector.getClass().getSimpleName());
        }
      } catch (ExecutionException e) {
        throw new IllegalStateException(e);
      }
    }
    return supportedDetectors.build();
  }

  private static final class AsyncDetector {
    private final Detector detector;
    private final ListenableFuture<Boolean> asyncPrecondition;

    AsyncDetector(Detector detector) {
      this.detector = detector;
      asyncPrecondition = executorService.submit(detector::precondition);
    }
  }
}
