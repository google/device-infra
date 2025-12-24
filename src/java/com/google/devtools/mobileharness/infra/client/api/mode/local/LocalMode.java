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
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.TestExecutor;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector.Component;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorsAndDispatchers;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfigFileProcessor;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager.ProxyDevices;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManagerModule;
import com.google.devtools.mobileharness.infra.controller.device.util.DeviceStatusInfoPrinter;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.launcher.ThreadPoolTestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.local.LocalTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.NoOpTestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.TestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto;
import com.google.devtools.mobileharness.infra.lab.controller.LocalFileBasedDeviceConfigManager;
import com.google.devtools.mobileharness.shared.context.InvocationContextExecutors;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceUpEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.IntStream;

/** Execution mode which run tests on local devices. */
public class LocalMode implements ExecMode {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  private static volatile ListeningExecutorService localEnvThreadPool;

  private static volatile ProxyDeviceManager proxyDeviceManager;

  /** Synchronization lock for {@link #localDeviceManager} and {@link #localScheduler}. */
  private static final Object LOCAL_ENV_LOCK = new Object();

  /** Starts the singleton local device manager and the local scheduler, if has not. */
  @Override
  public void initialize(EventBus globalInternalBus) throws InterruptedException {
    if (localDeviceManager == null && proxyDeviceManager == null) {
      synchronized (LOCAL_ENV_LOCK) {
        if (localDeviceManager == null && proxyDeviceManager == null) {
          logger.atInfo().log("Starting local device manager");

          localEnvThreadPool =
              InvocationContextExecutors.propagatingContext(
                  MoreExecutors.listeningDecorator(
                      Executors.newCachedThreadPool(
                          ThreadFactoryUtil.createThreadFactory("local-mode-thread-pool"))),
                  ListeningExecutorService.class);
          final ListeningScheduledExecutorService scheduledThreadPool =
              ThreadPools.createStandardScheduledThreadPool(
                  "local-mode-scheduled-thread-pool", /* corePoolSize= */ 1);
          ShutdownHookManager.getInstance()
              .addShutdownHook(localEnvThreadPool::shutdownNow, "local-mode-shutdown");

          ApiConfig.getInstance()
              .initialize(
                  /* isDefaultPublic= */ true,
                  /* isDefaultSynced= */ !Flags.instance().enableDeviceConfigManager.getNonNull(),
                  "");

          // Subscribes LocalDeviceUpEvent.
          globalInternalBus.register(this);

          if (Flags.instance().enableProxyMode.getNonNull()) {
            // Initializes ProxyDeviceManager.
            proxyDeviceManager =
                Guice.createInjector(
                        new AbstractModule() {
                          @Override
                          protected void configure() {
                            bind(ListeningExecutorService.class).toInstance(localEnvThreadPool);
                            install(new ProxyDeviceManagerModule());
                          }
                        })
                    .getInstance(ProxyDeviceManager.class);
            return;
          }

          // Initializes local device manager.
          DetectorsAndDispatchers detectorsAndDispatchers =
              new DetectorDispatcherSelector(Component.LOCAL_MODE).selectDetectorsAndDispatchers();
          localDeviceManager =
              new LocalDeviceManager(
                  detectorsAndDispatchers.supportedDetectors(),
                  detectorsAndDispatchers.supportedDispatchers(),
                  /* keepGoing= */ false,
                  localEnvThreadPool,
                  globalInternalBus,
                  new NoopExternalDeviceManager());
          localDeviceManager.initialize();
          localDeviceManagerFuture.set(localDeviceManager);

          // Starts device config manager.
          if (Flags.instance().enableDeviceConfigManager.getNonNull()) {
            logFailure(
                localEnvThreadPool.submit(
                    threadRenaming(
                        new LocalFileBasedDeviceConfigManager(
                            localDeviceManager,
                            DeviceIdManager.getInstance(),
                            ApiConfig.getInstance(),
                            new ApiConfigFileProcessor()),
                        () -> "device-config-manager")),
                Level.SEVERE,
                "Fatal error in device config manager");
          }

          // Initializes local scheduler.
          localScheduler = new SimpleScheduler(localEnvThreadPool);
          localSchedulerFuture.set(localScheduler);
          LocalDeviceManagerSchedulerSyncer localDeviceManagerSchedulerSyncer =
              new LocalDeviceManagerSchedulerSyncer(localDeviceManager, localScheduler);
          ApiConfig.getInstance().addListener(localDeviceManagerSchedulerSyncer);
          // Notifies scheduler about device/test change.
          globalInternalBus.register(localDeviceManagerSchedulerSyncer);

          // Starts local device manager and scheduler.
          logFailure(
              localEnvThreadPool.submit(
                  threadRenaming(localDeviceManager, () -> "local-device-manager")),
              Level.SEVERE,
              "Fatal error in local device manager");
          localScheduler.start();

          // Starts device status info printer.
          Duration printDeviceStatusInfoInterval = Duration.ofMinutes(2L);
          logFailure(
              scheduledThreadPool.scheduleWithFixedDelay(
                  threadRenaming(
                      () -> {
                        try {
                          logger.atInfo().log(
                              "%s",
                              DeviceStatusInfoPrinter.printDeviceStatusInfos(
                                  localDeviceManager.getAllDeviceStatusWithoutDuplicatedUuid(
                                      /* realtimeDetect= */ false)));
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      },
                      () -> "device-status-info-printer"),
                  printDeviceStatusInfoInterval,
                  printDeviceStatusInfoInterval),
              Level.WARNING,
              "Error when printing device status info");

          // Starts first device detector.
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
        jobInfo,
        new LocalDeviceVerifier(localDeviceManager),
        localEnvThreadPool,
        proxyDeviceManager,
        localSchedulerFuture);
  }

  @Override
  public DeviceQuerier createDeviceQuerier() {
    return new LocalDeviceQuerier(localDeviceManagerFuture, firstDeviceLatch);
  }

  @Override
  public DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      FileResolver fileResolver)
      throws MobileHarnessException, InterruptedException {
    EventBus globalInternalBus = setting.globalInternalBus().orElseThrow();
    initialize(globalInternalBus);
    ImmutableList<Device> devices;
    TestRunnerLauncher<TestRunner> launcher;

    if (Flags.instance().enableProxyMode.getNonNull()) {
      TestInfo testInfo = setting.testInfo();
      JobInfo jobInfo = testInfo.jobInfo();
      ProxyDevices proxyDevices =
          requireNonNull(
              getUnchecked(
                  proxyDeviceManager.getDevicesOfTest(testInfo.locator().toNewTestLocator())));
      devices =
          IntStream.range(0, jobInfo.subDeviceSpecs().getSubDeviceCount())
              .boxed()
              .map(subDeviceIndex -> requireNonNull(proxyDevices.devices().get(subDeviceIndex)))
              .collect(toImmutableList());
      launcher = new ThreadPoolTestRunnerLauncher<>(threadPool, globalInternalBus);
    } else {
      List<TestExecutor> testExecutors = new ArrayList<>();
      for (DeviceLocator deviceLocator : setting.allocation().getAllDeviceLocators()) {
        String deviceSerial = deviceLocator.getSerial();
        TestExecutor testExecutor =
            MobileHarnessExceptions.checkNotNull(
                localDeviceManager.getTestExecutorForDeviceId(deviceSerial),
                InfraErrorId.CLIENT_LOCAL_MODE_ALLOCATED_DEVICE_NOT_FOUND,
                String.format("Device %s not found", deviceSerial));
        testExecutors.add(testExecutor);
      }
      TestExecutor primaryTestExecutor = testExecutors.get(0);
      ImmutableList<TestExecutor> secondaryTestExecutors =
          testExecutors.stream().skip(1L).collect(toImmutableList());
      launcher = new LocalDeviceTestRunnerLauncher(primaryTestExecutor, secondaryTestExecutors);
      devices = testExecutors.stream().map(TestExecutor::getDevice).collect(toImmutableList());
    }
    return doCreateTestRunner(launcher, setting, devices, threadPool);
  }

  private DirectTestRunner doCreateTestRunner(
      TestRunnerLauncher<TestRunner> launcher,
      DirectTestRunnerSetting setting,
      List<Device> devices,
      ListeningExecutorService threadPool)
      throws MobileHarnessException {
    return new LocalTestRunner(
        launcher, setting, devices, threadPool, createTestFlowConverterOss());
  }

  private static TestFlowConverter createTestFlowConverterOss() {
    return new NoOpTestFlowConverter(
        IncompatibleReasonProto.InfraIncompatibleReason.ATS2, "ATS uses classic mode");
  }

  @Subscribe
  private void onLocalDeviceUp(LocalDeviceUpEvent unused) {
    firstDeviceLatch.countDown();
  }
}
