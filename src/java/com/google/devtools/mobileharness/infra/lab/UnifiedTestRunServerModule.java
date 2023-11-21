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

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestMessagePosterUtil;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.MainThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.RpcPort;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.shared.file.resolver.AbstractFileResolver;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageManager;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;

/** Guice module installed by UnifiedTestRunServer class to provide dependencies. */
public class UnifiedTestRunServerModule extends AbstractModule {

  private final String[] labArgs;
  private final EventBus globalInternalBus;

  public UnifiedTestRunServerModule(String[] labArgs) {
    this(labArgs, new EventBus(new SubscriberExceptionLoggingHandler()));
  }

  public UnifiedTestRunServerModule(String[] labArgs, EventBus globalInternalBus) {
    this.labArgs = labArgs;
    this.globalInternalBus = globalInternalBus;
  }

  @Override
  protected void configure() {
    String cloudRpcDnsName = getCloudRpcName();
    String shardName = getShardName();

    install(new TestRunBaseModule());
    install(new UtrsThreadPoolFactoryModule());
    install(
        new UtrsTestRunLabModule(
            cloudRpcDnsName,
            shardName,
            Flags.instance().rpcPort.getNonNull(),
            enableStubbyRpcServer(),
            Flags.instance().servViaCloudRpc.getNonNull(),
            globalInternalBus));

    bind(LabDirectTestRunnerHolder.class).to(ProxyTestManager.class);
    bind(EventBus.class).annotatedWith(GlobalEventBus.class).toInstance(globalInternalBus);
    bind(Integer.class)
        .annotatedWith(RpcPort.class)
        .toInstance(Flags.instance().rpcPort.getNonNull());
    bind(DeviceHelperFactory.class).to(LocalDeviceManager.class);
  }

  private String getCloudRpcName() {

    return Flags.instance().mhProxySpec.getNonNull();
  }

  private String getShardName() {
    return "<n/a>";
  }

  private static boolean enableStubbyRpcServer() {
    return Flags.instance().enableStubbyRpcServer.getNonNull() && !DeviceUtil.inSharedLab();
  }

  @Provides
  @Singleton
  FileResolver provideFileResolver() {
    return createFileResolver();
  }

  @SuppressWarnings("unused")
  private FileResolver createFileResolver() {
    return new AbstractFileResolver(null) {
      @Override
      protected boolean shouldActuallyResolve(ResolveSource resolveSource) {
        return false;
      }

      @Override
      protected ResolveResult actuallyResolve(ResolveSource resolveSource)
          throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
        throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
            BasicErrorId.RESOLVE_FILE_GENERIC_ERROR, "Should not run this method.");
      }
    };
  }

  @Provides
  @Singleton
  ExecTestServiceImpl provideExecTestService(
      ExecTestServiceImpl.ExecTestServiceImplFactory factory,
      ProxyTestManager testManager,
      @MainThreadPool ListeningExecutorService mainThreadPool) {
    // TestMessageManager is created for ExecTestServiceImpl to consume. It's a singleton that
    // requires testManager to be injected, hence it's initialization when ExecTestServiceImpl
    // object is provided.
    TestMessageManager.createInstance(
        testId -> TestMessagePosterUtil.getPosterFromProxyTestManager(testManager, testId));
    return factory.create(mainThreadPool);
  }
}
