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

package com.google.devtools.deviceinfra.infra.client.api.mode.local;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.deviceinfra.infra.core.devicemanager.DispatcherManager;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.detector.NoOpDeviceDetector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.AndroidRealDeviceDispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.NoOpDeviceDispatcher;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceTestRunner;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.scheduler.Scheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.local.LocalDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.NoOpTestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException.ErrorType;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Execution mode which run tests on local devices. */
public class LocalMode implements ExecMode {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** LocalDeviceManager is singleton and shared by all LocalMode jobs in the same machine. */
  private static volatile LocalDeviceManager localDeviceManager;

  /** Scheduler is singleton and shared by all LocalMode jobs in the same machine. */
  private static volatile Scheduler localScheduler;

  /** Synchronization lock for {@link #localDeviceManager} and {@link #localScheduler}. */
  private static final Object LOCAL_ENV_LOCK = new Object();

  /** Starts the singleton local device manager and the local scheduler, if has not. */
  public void initialize(EventBus globalInternalBus) throws InterruptedException {
    if (localDeviceManager == null) {
      synchronized (LOCAL_ENV_LOCK) {
        if (localDeviceManager == null) {
          final ListeningExecutorService localEnvThreadPool =
              MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
          Runtime.getRuntime().addShutdownHook(new Thread(localEnvThreadPool::shutdownNow));

          // For the iOS device testing, it always needs DeviceStat. The IosRealDeviceDetector needs
          // check the device last reboot time.
          localDeviceManager =
              new LocalDeviceManager(
                  checkAndGetDetectors(),
                  getSupportedDispatchers(),
                  /* keepGoing= */ false,
                  localEnvThreadPool,
                  globalInternalBus,
                  new NoopExternalDeviceManager());

          // Prepares the global scheduler.
          localScheduler = new SimpleScheduler(localEnvThreadPool);

          // Notifies scheduler about device/test change.
          globalInternalBus.register(
              new LocalDeviceManagerSchedulerSyncer(localDeviceManager, localScheduler));

          MoreFutures.logFailure(
              localEnvThreadPool.submit(localDeviceManager),
              Level.SEVERE,
              "Fatal error in local device manager");
          localScheduler.start();
        }
      }
    }
  }

  @Override
  public DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus)
      throws InterruptedException {
    initialize(globalInternalBus);
    return new LocalDeviceAllocator(jobInfo, localDeviceManager, localScheduler);
  }

  @Override
  public DeviceQuerier createDeviceQuerier() {
    return new LocalDeviceQuerier(localDeviceManager);
  }

  @Override
  public DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting, ListeningExecutorService testThreadPool)
      throws MobileHarnessException, InterruptedException {
    initialize(
        setting
            .globalInternalBus()
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        ErrorCode.ILLEGAL_ARGUMENT,
                        ErrorType.INFRA_ERROR,
                        "Local mode test runner should have global internal event bus")));
    List<LocalDeviceTestRunner> deviceRunners = new ArrayList<>();
    for (DeviceLocator deviceLocator : setting.allocation().getAllDeviceLocators()) {
      String deviceSerial = deviceLocator.getSerial();
      LocalDeviceTestRunner deviceRunner = localDeviceManager.getLocalDeviceRunner(deviceSerial);
      MobileHarnessException.checkNotNull(
          deviceRunner, ErrorCode.DEVICE_NOT_FOUND, "Device " + deviceSerial + " not found");
      deviceRunners.add(deviceRunner);
    }
    LocalDeviceTestRunner primaryDeviceRunner = deviceRunners.get(0);
    List<LocalDeviceTestRunner> secondaryDeviceRunners =
        deviceRunners.stream().skip(1L).collect(toImmutableList());
    TestRunnerLauncher<TestRunner> launcher =
        new LocalDeviceTestRunnerLauncher(primaryDeviceRunner, secondaryDeviceRunners);
    List<Device> devices =
        deviceRunners.stream().map(LocalDeviceTestRunner::getDevice).collect(toImmutableList());
    return doCreateTestRunner(launcher, setting, devices, testThreadPool);
  }

  protected DirectTestRunner doCreateTestRunner(
      TestRunnerLauncher<TestRunner> launcher,
      DirectTestRunnerSetting setting,
      List<Device> devices,
      ListeningExecutorService testThreadPool)
      throws MobileHarnessException {
    return new LocalDirectTestRunner(
        launcher,
        setting,
        devices,
        testThreadPool,
        new NoOpTestFlowConverter(
            IncompatibleReasonProto.InfraIncompatibleReason.ATS2, "ATS2 uses classic mode"));
  }

  protected ImmutableList<Detector> createDeviceDetectorCandidates() {
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

  protected void addDeviceDispatchers(DispatcherManager dispatcherManager) {
    // Adds Android real device dispatcher.
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      dispatcherManager.add(AndroidRealDeviceDispatcher.class);
    }

    // Adds NoOp dispatcher.
    if (Flags.instance().noOpDeviceNum.getNonNull() > 0) {
      dispatcherManager.add(NoOpDeviceDispatcher.class);
    }
  }

  private List<Detector> checkAndGetDetectors() throws InterruptedException {
    ImmutableList<Detector> detectorCandidates = createDeviceDetectorCandidates();
    List<Detector> validDetectors = new ArrayList<>();
    for (Detector detector : detectorCandidates) {
      if (detector.precondition()) {
        validDetectors.add(detector);
      } else {
        logger.atWarning().log(
            "Current system environment does not support %s", detector.getClass().getSimpleName());
      }
    }
    return validDetectors;
  }

  private ImmutableList<Class<? extends Dispatcher>> getSupportedDispatchers() {
    DispatcherManager dispatcherManager = DispatcherManager.getInstance();
    addDeviceDispatchers(dispatcherManager);
    return dispatcherManager.getAllDispatchersInOrder();
  }
}
