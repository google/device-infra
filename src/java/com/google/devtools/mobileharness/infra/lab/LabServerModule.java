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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunnerProvider;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorDispatcherSelector.Component;
import com.google.devtools.mobileharness.infra.controller.device.bootstrap.DetectorsAndDispatchers;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.LabTestMessagePosterUtil;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestManager;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcDnsAddress;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcShardName;
import com.google.devtools.mobileharness.infra.lab.Annotations.DebugThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.DeviceRunner;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabGrpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabRpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.RpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaCloudRpc;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForDevice;
import com.google.devtools.mobileharness.infra.lab.controller.WranglerLabPublisherModule;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.shared.file.resolver.AbstractFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.AtsFileServerFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.CacheFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.LocalFileResolver;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;

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
    // Binds controllers.
    install(new FactoryModuleBuilder().build(ExecTestServiceImpl.ExecTestServiceImplFactory.class));
    install(new FactoryModuleBuilder().build(MasterSyncerForDevice.Factory.class));
    install(new WranglerLabPublisherModule());
    bind(LocalDeviceRunnerProvider.class)
        .annotatedWith(DeviceRunner.class)
        .to(LocalDeviceManager.class);
    bind(DeviceHelperFactory.class).to(LocalDeviceManager.class);
    bind(new Key<TestManager<?>>() {}).to(ProxyTestManager.class);
    bind(ProxyTestManager.class).in(Singleton.class);
    bind(LabDirectTestRunnerHolder.class).to(ProxyTestManager.class);
    bind(EventBus.class).annotatedWith(GlobalEventBus.class).toInstance(globalInternalBus);

    // Binds ports.
    String cloudRpcDnsName = getCloudRpcName();
    String shardName = getShardName();
    bind(String.class).annotatedWith(CloudRpcDnsAddress.class).toInstance(cloudRpcDnsName);
    bind(String.class).annotatedWith(CloudRpcShardName.class).toInstance(shardName);
    bind(Integer.class)
        .annotatedWith(LabRpcPort.class)
        .toInstance(Flags.instance().rpcPort.getNonNull());
    bind(Integer.class)
        .annotatedWith(LabGrpcPort.class)
        .toInstance(Flags.instance().grpcPort.getNonNull());
    bind(Boolean.class)
        .annotatedWith(ServViaCloudRpc.class)
        .toInstance(Flags.instance().servViaCloudRpc.getNonNull());
    bind(Boolean.class).annotatedWith(ServViaStubby.class).toInstance(enableStubbyRpcServer());
    bind(Integer.class)
        .annotatedWith(RpcPort.class)
        .toInstance(Flags.instance().rpcPort.getNonNull());

    // Binds utils.
    bind(ListeningExecutorService.class)
        .toInstance(createStandardThreadPool("mh-lab-server-main-thread"));
    bind(ListeningScheduledExecutorService.class)
        .annotatedWith(DebugThreadPool.class)
        .toInstance(createStandardScheduledThreadPool("mh-lab-server-debug-random-exit-task", 1));
    bind(LocalFileUtil.class).in(Singleton.class);
    bind(SystemUtil.class).in(Singleton.class);
    bind(NetUtil.class).in(Singleton.class);
  }

  private static String getCloudRpcName() {

    return Flags.instance().mhProxySpec.getNonNull();
  }

  private static String getShardName() {
    return "<n/a>";
  }

  private static boolean enableStubbyRpcServer() {
    return Flags.instance().enableStubbyRpcServer.getNonNull() && !DeviceUtil.inSharedLab();
  }

  @Provides
  @Singleton
  FileResolver provideFileResolver(
      ListeningExecutorService threadPool, LocalFileUtil localFileUtil) {
    // LocalFileResolver.
    AbstractFileResolver localFileResolver = new LocalFileResolver(threadPool, localFileUtil);

    // CacheFileResolver.
    AbstractFileResolver cacheFileResolver = new CacheFileResolver(threadPool, localFileUtil);
    localFileResolver.setSuccessor(cacheFileResolver);

    // AtsFileServerFileResolver.
    AbstractFileResolver atsFileServerFileResolver =
        new AtsFileServerFileResolver(threadPool, localFileUtil);
    cacheFileResolver.setSuccessor(atsFileServerFileResolver);

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
                  "Your lab server is not properly configured: %n%s%n"
                      + "Please follow%n - go/mh-codelab-android or"
                      + "%n - go/mh-codelab-ios %nto set up lab server.",
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
  @Singleton
  ExternalDeviceManager provideExternalDeviceManager() {
    return new NoopExternalDeviceManager();
  }

  @Provides
  @Singleton
  ExecTestServiceImpl provideExecTestService(
      ExecTestServiceImpl.ExecTestServiceImplFactory factory,
      ProxyTestManager testManager,
      ListeningExecutorService mainThreadPool) {
    // TestMessageManager is created for ExecTestServiceImpl to consume. It's a singleton that
    // requires testManager to be injected, hence it's initialization when ExecTestServiceImpl
    // object is provided.
    TestMessageManager.createInstance(
        testId -> LabTestMessagePosterUtil.getPosterFromProxyTestManager(testManager, testId));
    return factory.create(mainThreadPool);
  }
}
