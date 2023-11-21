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
import com.google.devtools.mobileharness.infra.controller.device.DeviceHelperFactory;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunnerProvider;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.external.NoopExternalDeviceManager;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcDnsAddress;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcShardName;
import com.google.devtools.mobileharness.infra.lab.Annotations.DeviceManagerThreadPool;
import com.google.devtools.mobileharness.infra.lab.Annotations.DeviceRunner;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabRpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaCloudRpc;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/**
 * Module for UTRS on lab host.
 *
 * <p>Configurations specified for lab host environment.
 */
class UtrsTestRunLabModule extends AbstractModule {

  private final String cloudRpcDnsAddress;
  private final String cloudRpcShardName;
  private final int port;
  private final boolean servViaStubby;
  private final boolean servViaCloudRpc;
  private final EventBus globalInternalBus;

  UtrsTestRunLabModule(
      String cloudRpcDnsAddress,
      String cloudRpcShardName,
      int port,
      boolean servViaStubby,
      boolean servViaCloudRpc,
      EventBus globalInternalBus) {
    this.cloudRpcDnsAddress = cloudRpcDnsAddress;
    this.cloudRpcShardName = cloudRpcShardName;
    this.port = port;
    this.servViaStubby = servViaStubby;
    this.servViaCloudRpc = servViaCloudRpc;
    this.globalInternalBus = globalInternalBus;
  }

  @Override
  protected void configure() {
    install(new TestRunModule());
    install(new FactoryModuleBuilder().build(ExecTestServiceImpl.ExecTestServiceImplFactory.class));

    bind(String.class).annotatedWith(CloudRpcDnsAddress.class).toInstance(cloudRpcDnsAddress);
    bind(String.class).annotatedWith(CloudRpcShardName.class).toInstance(cloudRpcShardName);
    bind(Integer.class).annotatedWith(LabRpcPort.class).toInstance(port);
    bind(Boolean.class).annotatedWith(ServViaCloudRpc.class).toInstance(servViaCloudRpc);
    bind(Boolean.class).annotatedWith(ServViaStubby.class).toInstance(servViaStubby);
    bind(LocalDeviceRunnerProvider.class)
        .annotatedWith(DeviceRunner.class)
        .to(LocalDeviceManager.class);
    bind(DeviceHelperFactory.class).to(LocalDeviceManager.class);
  }

  @Provides
  @Singleton
  LocalDeviceManager provideLocalDeviceManager(
      ExternalDeviceManager externalDeviceManager,
      @DeviceManagerThreadPool ListeningExecutorService deviceManagerThreadPool)
      throws InterruptedException {
    LocalDeviceManager localDeviceManager =
        new LocalDeviceManager(
            com.google.devtools.mobileharness.infra.controller.device.BaseDetectorDispatcherPicker
                .checkAndGetSupportedDetectors(),
            com.google.devtools.mobileharness.infra.controller.device.BaseDetectorDispatcherPicker
                .getSupportedDispatchers(),
            /* keepGoing= */ true,
            deviceManagerThreadPool,
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
}
