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

package com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.grpc;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc.DeviceConfigServiceBlockingStub;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceGrpc.DeviceConfigServiceFutureStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.Annotation;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;

/** The Guice module to provide the GRPC stubs used for calling device config service APIs. */
public final class DeviceConfigGrpcStubModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String configDnsName;

  public DeviceConfigGrpcStubModule() {
    configDnsName = Flags.instance().configServiceGrpcTarget.getNonNull();

    logger.atInfo().log("ConfigDnsName: %s", configDnsName);
  }

  @Override
  protected void configure() {
    bind(DeviceConfigServiceBlockingStub.class)
        .toInstance(
            DeviceConfigServiceGrpc.newBlockingStub(
                ClientInterceptors.intercept(getChannel(), getInterceptors())));

    bind(DeviceConfigServiceFutureStub.class)
        .toInstance(
            DeviceConfigServiceGrpc.newFutureStub(
                ClientInterceptors.intercept(getChannel(), getInterceptors())));

    bind(DeviceConfigStub.class)
        .annotatedWith(Annotation.DeviceConfigGrpcStub.class)
        .to(DeviceConfigGrpcStub.class)
        .in(Singleton.class);
  }

  /** Gets the {@link Channel} of GRPC by the DNS name of the flag. */
  private Channel getChannel() {
    return com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory.createChannel(
        configDnsName, directExecutor());
  }

  /** Gets a list of {@link ClientInterceptor}s used before requests are dispatched to channels. */
  private ImmutableList<ClientInterceptor> getInterceptors() {
    return ImmutableList.of();
  }
}
