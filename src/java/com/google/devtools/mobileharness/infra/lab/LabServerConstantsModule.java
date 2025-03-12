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

import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcDnsAddress;
import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcShardName;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabGrpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.LabRpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.RpcPort;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaCloudRpc;
import com.google.devtools.mobileharness.infra.lab.Annotations.ServViaStubby;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;

/** Bindings for constants used in lab server. */
public final class LabServerConstantsModule extends AbstractModule {
  @Override
  protected void configure() {
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
}
