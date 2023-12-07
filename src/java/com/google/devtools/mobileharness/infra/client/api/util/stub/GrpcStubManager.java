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

package com.google.devtools.mobileharness.infra.client.api.util.stub;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.ExecTestGrpcStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.PrepareTestGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelManager;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import io.grpc.ManagedChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** gRPC stub manager for creating stubs and caching {@link ManagedChannel}s automatically. */
public class GrpcStubManager {

  private static final GrpcStubManager INSTANCE =
      new GrpcStubManager(
          ChannelManager.getInstance(),
          MoreExecutors.listeningDecorator(
              Executors.newCachedThreadPool(
                  ThreadFactoryUtil.createThreadFactory(
                      "grpc-channel-executor", /* daemon= */ true))));

  public static GrpcStubManager getInstance() {
    return INSTANCE;
  }

  private final ChannelManager channelManager;
  private final Executor channelExecutor;

  private GrpcStubManager(ChannelManager channelManager, Executor channelExecutor) {
    this.channelManager = channelManager;
    this.channelExecutor = channelExecutor;
  }

  public VersionStub getVersionStub(String grpcTarget) {
    return channelManager.createStub(
        grpcTarget, new ManagedChannelSupplier(grpcTarget), VersionGrpcStub::new);
  }

  public PrepareTestStub getPrepareTestStub(String grpcTarget) {
    return channelManager.createStub(
        grpcTarget, new ManagedChannelSupplier(grpcTarget), PrepareTestGrpcStub::new);
  }

  public ExecTestStub getExecTestGrpcStub(String grpcTarget) {
    return channelManager.createStub(
        grpcTarget, new ManagedChannelSupplier(grpcTarget), ExecTestGrpcStub::new);
  }

  private class ManagedChannelSupplier implements Supplier<ManagedChannel> {

    private final String grpcTarget;

    private ManagedChannelSupplier(String grpcTarget) {
      this.grpcTarget = grpcTarget;
    }

    @Override
    public ManagedChannel get() {
      return ChannelFactory.createChannel(grpcTarget, channelExecutor);
    }
  }
}
