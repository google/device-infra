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

package com.google.devtools.mobileharness.infra.lab;

import static com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools.createStandardScheduledThreadPool;
import static com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools.createStandardThreadPool;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.TestExecutorProvider;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector.Component;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorsAndDispatchers;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.provider.AllocatedDeviceProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.device.provider.LocalDeviceProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSenderFinder;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManager;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingManagerHolder;
import com.google.devtools.mobileharness.infra.controller.messaging.MessagingServiceModule;
import com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceLauncherProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.test.launcher.ThreadPoolLauncherProvisioningModule;
import com.google.devtools.mobileharness.infra.controller.test.manager.LabDirectTestRunnerUtil;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestManager;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.infra.lab.Annotations.DebugThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBusSubscriber;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.shared.file.resolver.AbstractFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.AtsFileServerFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.CacheFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.GcsFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.LocalFileResolver;
import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import java.time.Clock;
import java.time.InstantSource;
import java.util.Set;
import javax.inject.Inject;

/** Guice module for {@link LabServer}. */
@SuppressWarnings("AvoidObjectArrays")
public class LabServerModule extends AbstractModule {

  private final String[] labArgs;
  private final EventBus globalInternalBus;

  public LabServerModule(String[] labArgs) {
    this(labArgs, new EventBus(new SubscriberExceptionLoggingHandler()));
  }

  public LabServerModule(String[] labArgs, EventBus globalInternalBus) {
    this.labArgs = labArgs;
    this.globalInternalBus = globalInternalBus;
  }

  @Override
  protected void configure() {
    install(new LabServerConstantsModule());
    install(new MessagingServiceModule());
    // Binds controllers.
    install(new FactoryModuleBuilder().build(ExecTestServiceImpl.ExecTestServiceImplFactory.class));
    bind(DeviceHelperFactory.class).to(LocalDeviceManager.class);
    bind(new Key<TestManager<?>>() {}).to(ProxyTestManager.class);
    bind(LabDirectTestRunnerHolder.class).to(ProxyTestManager.class);
    bind(EventBus.class).annotatedWith(GlobalEventBus.class).toInstance(globalInternalBus);
    if (Flags.instance().enableDeviceTestDecoupling.get()) {
      install(new AllocatedDeviceProvisioningModule());
      install(new ThreadPoolLauncherProvisioningModule());
    } else {
      install(new LocalDeviceProvisioningModule());
      install(new LocalDeviceLauncherProvisioningModule());
    }

    Multibinder<Object> subscriberBinder =
        Multibinder.newSetBinder(binder(), Key.get(Object.class, GlobalEventBusSubscriber.class));
    install(ServiceModule.forService(EventBusService.class));

    // Binds utils.
    bind(ListeningExecutorService.class)
        .toInstance(createStandardThreadPool("mh-lab-server-main-thread"));
    bind(ListeningScheduledExecutorService.class)
        .annotatedWith(DebugThreadPool.class)
        .toInstance(createStandardScheduledThreadPool("mh-lab-server-debug-random-exit-task", 1));
    bind(InstantSource.class).toInstance(InstantSource.system());
  }

  @Provides
  @Singleton
  Clock provideClock() {
    return Clock.systemUTC();
  }

  @Provides
  @Singleton
  FileResolver provideFileResolver(
      ListeningExecutorService threadPool,
      LocalFileUtil localFileUtil,
      InstantSource instantSource) {
    // LocalFileResolver.
    AbstractFileResolver localFileResolver = new LocalFileResolver(threadPool, localFileUtil);

    // CacheFileResolver.
    AbstractFileResolver cacheFileResolver =
        new CacheFileResolver(threadPool, localFileUtil, instantSource);
    localFileResolver.setSuccessor(cacheFileResolver);

    // AtsFileServerFileResolver.
    AbstractFileResolver atsFileServerFileResolver =
        new AtsFileServerFileResolver(threadPool, localFileUtil);
    cacheFileResolver.setSuccessor(atsFileServerFileResolver);

    // GcsFileResolver.
    AbstractFileResolver gcsFileResolver = new GcsFileResolver(threadPool);
    atsFileServerFileResolver.setSuccessor(gcsFileResolver);

    return localFileResolver;
  }

  @Provides
  @Singleton
  LocalDeviceManager provideLocalDeviceManager(
      ExternalDeviceManager externalDeviceManager, ListeningExecutorService threadPool)
      throws InterruptedException {
    DetectorsAndDispatchers detectorsAndDispatchers =
        new DetectorDispatcherSelector(Component.LAB_SERVER).selectDetectorsAndDispatchers();
    if (detectorsAndDispatchers.supportedDetectors().isEmpty()) {
      new SystemUtil()
          .exit(
              ExitCode.Lab.NO_DETECTOR,
              String.format(
                  "Your lab server is not properly configured: %n%s%n",
                  "All detectors are not supported by the current system."));
    }

    LocalDeviceManager localDeviceManager =
        new LocalDeviceManager(
            detectorsAndDispatchers.supportedDetectors(),
            detectorsAndDispatchers.supportedDispatchers(),
            /* keepGoing= */ true,
            threadPool,
            globalInternalBus,
            externalDeviceManager);
    localDeviceManager.initialize();
    return localDeviceManager;
  }

  @Provides
  TestExecutorProvider provideTestExecutorProvider(LocalDeviceManager localDeviceManager) {
    return localDeviceManager;
  }

  @Provides
  @Singleton
  ExternalDeviceManager provideExternalDeviceManager() {
    return new NoopExternalDeviceManager();
  }

  @Provides
  @Singleton
  MessageSenderFinder provideMessageSenderFinder(ProxyTestManager testManager) {
    return messageSend -> LabDirectTestRunnerUtil.getMessageSender(testManager, messageSend);
  }

  @Provides
  @Singleton
  ExecTestServiceImpl provideExecTestService(
      ExecTestServiceImpl.ExecTestServiceImplFactory factory,
      ProxyTestManager testManager,
      MessagingManager messagingManager,
      ListeningExecutorService mainThreadPool) {
    TestMessageManager.createInstance(
        testId -> LabDirectTestRunnerUtil.getTestMessagePoster(testManager, testId));
    MessagingManagerHolder.initialize(messagingManager);
    return factory.create(mainThreadPool);
  }

  private static class EventBusService extends AbstractIdleService {
    private final EventBus eventBus;
    private final Set<Object> eventBusSubscribers;

    @Inject
    EventBusService(
        @GlobalEventBus EventBus eventBus,
        @GlobalEventBusSubscriber Set<Object> eventBusSubscribers) {
      this.eventBus = eventBus;
      this.eventBusSubscribers = eventBusSubscribers;
    }

    @Override
    protected void startUp() {
      eventBusSubscribers.forEach(eventBus::register);
    }

    @Override
    protected void shutDown() {
      eventBusSubscribers.forEach(eventBus::unregister);
    }
  }
}
