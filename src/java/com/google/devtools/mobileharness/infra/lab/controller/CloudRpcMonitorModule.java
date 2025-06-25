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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.bridge.proto.ProxyMetadata;
import com.google.devtools.mobileharness.infra.bridge.rpc.stub.BridgeStub;
import com.google.devtools.mobileharness.infra.bridge.rpc.stub.BridgeStubAnnotation.GrpcStub;
import com.google.devtools.mobileharness.infra.bridge.rpc.stub.grpc.BridgeGrpcStubModule;
import com.google.devtools.mobileharness.shared.constant.environment.MobileHarnessServerEnvironment;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferBridgeStub;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStub;
import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.version.rpc.stub.bridge.VersionBridgeStub;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.wireless.qa.mobileharness.shared.comm.cloudrpc.CloudRpcServerType;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;

/** Bindings for enabling CloudRpc2 monitoring. */
public class CloudRpcMonitorModule extends AbstractModule {
  private final MobileHarnessServerEnvironment mobileHarnessServerEnvironment;

  public CloudRpcMonitorModule(MobileHarnessServerEnvironment mobileHarnessServerEnvironment) {
    this.mobileHarnessServerEnvironment = mobileHarnessServerEnvironment;
  }

  @Override
  protected void configure() {
    install(new BridgeGrpcStubModule(mobileHarnessServerEnvironment.getBridgeServerSpec()));
    install(ServiceModule.forService(CloudRpcMonitor.class));
  }

  @Provides
  VersionStub provideVersionStub(
      @GrpcStub BridgeStub bridgeStub,
      MobileHarnessServerEnvironment mobileHarnessServerEnvironment,
      NetUtil netUtil)
      throws MobileHarnessException {
    return new VersionBridgeStub(
        ProxyMetadata.newBuilder()
            .setEndpoint(CloudRpcServerType.LAB_SERVER.getEndpoint())
            .setShard(netUtil.getLocalHostName())
            .build(),
        bridgeStub,
        mobileHarnessServerEnvironment);
  }

  @Provides
  CloudFileTransferStub provideCloudFileTransferStub(
      @GrpcStub BridgeStub bridgeStub,
      MobileHarnessServerEnvironment mobileHarnessServerEnvironment,
      NetUtil netUtil)
      throws MobileHarnessException {
    return new CloudFileTransferBridgeStub(
        bridgeStub,
        ProxyMetadata.newBuilder()
            .setEndpoint(CloudRpcServerType.LAB_CLOUD_FILE_TRANSFER.getEndpoint())
            .setShard(netUtil.getLocalHostName())
            .build(),
        mobileHarnessServerEnvironment);
  }
}
