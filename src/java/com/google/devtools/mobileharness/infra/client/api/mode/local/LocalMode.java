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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.devicemanager.detector.Detector;
import com.google.devtools.mobileharness.api.devicemanager.dispatcher.Dispatcher;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.controller.device.BaseDetectorDispatcherPicker;
import com.google.devtools.mobileharness.infra.controller.device.DispatcherManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceTestRunner;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.local.LocalDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.NoOpTestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException.ErrorType;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceUpEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Execution mode which run tests on local devices. */
public class LocalMode implements ExecMode {

  /** LocalDeviceManager is singleton and shared by all LocalMode jobs in the same machine. */
  private static volatile LocalDeviceManager localDeviceManager;

  /** Future which is set when the device manager is initialized. */
  private static final SettableFuture<LocalDeviceManager> localDeviceManagerFuture =
      SettableFuture.create();

  /**
   * Count down when the first device has been dispatched or the device manager detector&dispatcher
   * have started for a while.
   */
  private static final CountDownLatch firstDeviceLatch = new CountDownLatch(1);

  /** Scheduler is singleton and shared by all LocalMode jobs in the same machine. */
  private static volatile AbstractScheduler localScheduler;

  /** Future which is set when the local scheduler is initialized. */
  private static final SettableFuture<AbstractScheduler> localSchedulerFuture =
      SettableFuture.create();

  /** Synchronization lock for {@link #localDeviceManager} and {@link #localScheduler}. */
  private static final Object LOCAL_ENV_LOCK = new Object();

  /** Starts the singleton local device manager and the local scheduler, if has not. */
  public void initialize(EventBus globalInternalBus) throws InterruptedException {
    if (localDeviceManager == null) {
      synchronized (LOCAL_ENV_LOCK) {
        if (localDeviceManager == null) {
          final ListeningExecutorService localEnvThreadPool =
              MoreExecutors.listeningDecorator(
                  Executors.newCachedThreadPool(
                      ThreadFactoryUtil.createThreadFactory("local-mode-thread-pool")));
          Runtime.getRuntime().addShutdownHook(new Thread(localEnvThreadPool::shutdownNow));

          ApiConfig.getInstance().init(/* defaultPublic= */ true, "");

          // Subscribes LocalDeviceUpEvent.
          globalInternalBus.register(this);

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
          localDeviceManager.initialize();
          localDeviceManagerFuture.set(localDeviceManager);

          // Prepares the global scheduler.
          localScheduler = new SimpleScheduler(localEnvThreadPool);
          localSchedulerFuture.set(localScheduler);

          // Notifies scheduler about device/test change.
          globalInternalBus.register(
              new LocalDeviceManagerSchedulerSyncer(localDeviceManager, localScheduler));

          // Starts local device manager and scheduler.
          logFailure(
              localEnvThreadPool.submit(
                  threadRenaming(localDeviceManager, () -> "local-device-manager")),
              Level.SEVERE,
              "Fatal error in local device manager");
          localScheduler.start();
          logFailure(
              localEnvThreadPool.submit(
                  (Callable<Void>)
                      () -> {
                        Sleeper.defaultSleeper().sleep(Duration.ofSeconds(10L));
                        firstDeviceLatch.countDown();
                        return null;
                      }),
              Level.INFO,
              "Error when waiting device manager started");
        }
      }
    }
  }

  @Override
  public DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus)
      throws InterruptedException {
    initialize(globalInternalBus);
    return new LocalDeviceAllocator(
        jobInfo, new LocalDeviceVerifier(localDeviceManager), localSchedulerFuture);
  }

  @Override
  public DeviceQuerier createDeviceQuerier() {
    return new LocalDeviceQuerier(localDeviceManagerFuture, firstDeviceLatch);
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
    ImmutableList<LocalDeviceTestRunner> secondaryDeviceRunners =
        deviceRunners.stream().skip(1L).collect(toImmutableList());
    TestRunnerLauncher<TestRunner> launcher =
        new LocalDeviceTestRunnerLauncher(primaryDeviceRunner, secondaryDeviceRunners);
    ImmutableList<Device> devices =
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

  /** Returns the local device manager created and owned by LocalMode. */
  public LocalDeviceManager getDeviceManager(EventBus globalInternalBus)
      throws InterruptedException {
    initialize(globalInternalBus);
    return localDeviceManager;
  }

  protected ImmutableList<Detector> createDeviceDetectorCandidates() {
    return BaseDetectorDispatcherPicker.createDetectorCandidates();
  }

  protected void addDeviceDispatchers(DispatcherManager dispatcherManager) {
    BaseDetectorDispatcherPicker.addDispatchers(dispatcherManager);
  }

  private List<Detector> checkAndGetDetectors() throws InterruptedException {
    ImmutableList<Detector> detectorCandidates = createDeviceDetectorCandidates();
    return BaseDetectorDispatcherPicker.checkDetectors(detectorCandidates);
  }

  private ImmutableList<Class<? extends Dispatcher>> getSupportedDispatchers() {
    DispatcherManager dispatcherManager = DispatcherManager.getInstance();
    addDeviceDispatchers(dispatcherManager);
    return dispatcherManager.getAllDispatchersInOrder();
  }

  @Subscribe
  private void onLocalDeviceUp(LocalDeviceUpEvent unused) {
    firstDeviceLatch.countDown();
  }
}
