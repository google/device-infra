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
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunnerProvider;
import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcDnsAddress;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcShardName;
import com.google.devtools.mobileharness.infra.lab.Annotations.DeviceRunner;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabRpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaCloudRpc;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.devtools.mobileharness.infra.lab.rpc.service.PrepareTestServiceImpl;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;

/** Module for UTRS. Shared configuration for both Lab and Prod environment. */
public final class TestRunModule extends AbstractModule {

  @Provides
  @Singleton
  PrepareTestServiceImpl providePrepareTestServiceImpl(
      @DeviceRunner LocalDeviceRunnerProvider localDeviceRunnerProvider,
      ProxyTestManager testManager,
      JobManager jobManager,
      NetUtil netUtil,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      FileResolver fileResolver,
      @GlobalEventBus EventBus eventBus,
      @CloudRpcDnsAddress String cloudRpcDnsAddress,
      @CloudRpcShardName String cloudRpcShardName,
      @LabRpcPort int labRpcPort,
      @ServViaStubby boolean servViaStubby,
      @ServViaCloudRpc boolean servViaCloudRpc) {
    return new PrepareTestServiceImpl(
        localDeviceRunnerProvider,
        jobManager,
        testManager,
        localFileUtil,
        netUtil,
        systemUtil,
        fileResolver,
        servViaStubby,
        labRpcPort,
        servViaCloudRpc,
        cloudRpcDnsAddress,
        cloudRpcShardName,
        eventBus);
  }
}
