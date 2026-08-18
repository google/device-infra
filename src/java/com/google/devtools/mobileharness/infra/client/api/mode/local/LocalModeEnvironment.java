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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
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
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.InfraIncompatibleReason;
import com.google.devtools.mobileharness.infra.lab.controller.LocalFileBasedDeviceConfigManager;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoService;
import com.google.devtools.mobileharness.shared.labinfo.LocalLabInfoProvider;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceUpEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import io.grpc.BindableService;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.stream.IntStream;
import javax.inject.Singleton;

/**
 * Environment for {@link LocalMode} encapsulating runtime state and execution logic.
 *
 * <p>In production, a single process-level instance is shared across all {@link LocalMode}
 * instances via {@link #getInstance()}. For tests, dedicated instances are created via {@link
 * #createForTest()} and torn down via {@link #tearDownForTest()} by {@link LocalModeRule}.
 */
@Singleton
class LocalModeEnvironment {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static class SingletonHolder {
    private static final LocalModeEnvironment DEFAULT_INSTANCE = new LocalModeEnvironment();
  }

  static LocalModeEnvironment getInstance() {
    return SingletonHolder.DEFAULT_INSTANCE;
  }

  static LocalModeEnvironment createForTest() {
    return new LocalModeEnvironment();
  }

  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final LabInfoService labInfoService;
  private final DeviceTempRequiredDimensionManager tempRequiredDimensionManager;
  private final AbstractScheduler localScheduler;
  private final SettableFuture<LocalDeviceManager> localDeviceManagerFuture =
      SettableFuture.create();
  private final CountDownLatch firstDeviceLatch = new CountDownLatch(1);
  private final Object localEnvLock = new Object();

  private volatile LocalDeviceManager localDeviceManager;
  private volatile ProxyDeviceManager proxyDeviceManager;

  private LocalModeEnvironment() {
    this.threadPool = ThreadPools.createStandardThreadPool("local-mode-thread-pool");
    this.scheduledThreadPool =
        ThreadPools.createStandardScheduledThreadPool(
            "local-mode-scheduled-thread-pool", /* corePoolSize= */ 5);

    ShutdownHookManager.getInstance()
        .addShutdownHook(threadPool::shutdownNow, "local-mode-thread-pool-shutdown");
    ShutdownHookManager.getInstance()
        .addShutdownHook(
            scheduledThreadPool::shutdownNow, "local-mode-scheduled-thread-pool-shutdown");

    this.localScheduler = new SimpleScheduler(threadPool);

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ListeningExecutorService.class).toInstance(threadPool);
                bind(ListeningScheduledExecutorService.class).toInstance(scheduledThreadPool);
                bind(InstantSource.class).toInstance(InstantSource.system());
                bind(LabInfoProvider.class).to(LocalLabInfoProvider.class);
              }

              @Provides
              ListenableFuture<LocalDeviceManager> provideLocalDeviceManager() {
                return localDeviceManagerFuture;
              }
            });
    this.labInfoService = injector.getInstance(LabInfoService.class);
    this.tempRequiredDimensionManager =
        injector.getInstance(DeviceTempRequiredDimensionManager.class);
  }

  void initialize(EventBus globalInternalBus) throws InterruptedException {
    if (localDeviceManager == null && proxyDeviceManager == null) {
      synchronized (localEnvLock) {
        if (localDeviceManager == null && proxyDeviceManager == null) {
          logger.atInfo().log("Starting local device manager");

          ApiConfig.getInstance()
              .initialize(
                  /* isDefaultPublic= */ true,
                  /* isDefaultSynced= */ !Flags.enableDeviceConfigManager.getNonNull(),
                  "");

          // Subscribes LocalDeviceUpEvent.
          globalInternalBus.register(this);

          if (Flags.enableProxyMode.getNonNull()) {
            // Initializes ProxyDeviceManager.
            proxyDeviceManager =
                Guice.createInjector(
                        new AbstractModule() {
                          @Override
                          protected void configure() {
                            bind(ListeningExecutorService.class).toInstance(threadPool);
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
                  threadPool,
                  globalInternalBus,
                  new NoopExternalDeviceManager());
          localDeviceManager.initialize();
          localDeviceManagerFuture.set(localDeviceManager);

          // Starts device config manager.
          if (Flags.enableDeviceConfigManager.getNonNull()) {
            logFailure(
                threadPool.submit(
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

          // Initializes scheduler syncer.
          LocalDeviceManagerSchedulerSyncer localDeviceManagerSchedulerSyncer =
              new LocalDeviceManagerSchedulerSyncer(
                  localDeviceManager,
                  localScheduler,
                  tempRequiredDimensionManager,
                  ApiConfig.getInstance());
          ApiConfig.getInstance().addListener(localDeviceManagerSchedulerSyncer);
          // Notifies scheduler about device/test change.
          globalInternalBus.register(localDeviceManagerSchedulerSyncer);

          // Starts local device manager, scheduler and temp dimension manager.
          logFailure(
              threadPool.submit(threadRenaming(localDeviceManager, () -> "local-device-manager")),
              Level.SEVERE,
              "Fatal error in local device manager");
          localScheduler.start();
          tempRequiredDimensionManager.start();

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
              threadPool.submit(
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

  DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus)
      throws InterruptedException {
    initialize(globalInternalBus);
    return new LocalDeviceAllocator(
        jobInfo,
        new LocalDeviceVerifier(localDeviceManager),
        threadPool,
        proxyDeviceManager,
        localScheduler);
  }

  DeviceQuerier createDeviceQuerier() {
    return new LocalDeviceQuerier(
        localDeviceManagerFuture, firstDeviceLatch, tempRequiredDimensionManager);
  }

  DeviceReserver createDeviceReserver() {
    return new LocalDeviceReserver(tempRequiredDimensionManager, DeviceIdManager.getInstance());
  }

  DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      FileResolver fileResolver)
      throws MobileHarnessException, InterruptedException {
    EventBus globalInternalBus = setting.globalInternalBus().orElseThrow();
    initialize(globalInternalBus);
    ImmutableList<Device> devices;
    TestRunnerLauncher<TestRunner> launcher;

    if (Flags.enableProxyMode.getNonNull()) {
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

  LabInfoService getLabInfoService() {
    return labInfoService;
  }

  ImmutableList<BindableService> provideServicesForNonWorker() {
    return ImmutableList.of(labInfoService);
  }

  ImmutableList<BindableService> provideServicesForWorker() {
    return ImmutableList.of();
  }

  ImmutableList<BindableService> provideServicesDualMode() {
    return ImmutableList.of();
  }

  void tearDownForTest() {
    threadPool.shutdownNow();
    scheduledThreadPool.shutdownNow();
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
    return new NoOpTestFlowConverter(InfraIncompatibleReason.ATS2, "ATS uses classic mode");
  }

  @Subscribe
  private void onLocalDeviceUp(LocalDeviceUpEvent unused) {
    firstDeviceLatch.countDown();
  }
}
