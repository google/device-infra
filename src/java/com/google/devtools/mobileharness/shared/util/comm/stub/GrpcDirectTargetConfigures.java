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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.Reflection;
import com.google.devtools.mobileharness.shared.util.comm.relay.client.ClientCreator;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.ServerSpec;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.util.function.Function;
import javax.inject.Inject;

/** Util to create direct blocking interface of gRPC stubs. */
public final class GrpcDirectTargetConfigures {

  private final ChannelManager channelManager;
  private final Function<String, ? extends ManagedChannel> managedChannelSupplier;

  @Inject
  GrpcDirectTargetConfigures() {
    this(ChannelManager.getInstance(), ManagedChannelSupplier.getInstance());
  }

  @VisibleForTesting
  GrpcDirectTargetConfigures(
      ChannelManager channelManager,
      Function<String, ? extends ManagedChannel> managedChannelSupplier) {
    this.channelManager = channelManager;
    this.managedChannelSupplier = managedChannelSupplier;
  }

  /**
   * Creates a grpc stub.
   *
   * <p>The grpc service should have a blocking/future stub interface that has the same API as the
   * generated blocking/future stub. For example, the FooServiceGrpcStub has a method
   * newBlockingInterface that creates a FooServiceGrpcStub.BlockingInterface from a channel.
   *
   * <p>Usage:
   *
   * <pre>
   *   FooServiceGrpcStub.BlockingInterface stubInterface =
   *       grpcDirectTargetConfigures.createStubInterface(
   *           FooServiceGrpcStub::newBlockingInterface, stubConfiguration);
   * </pre>
   *
   * @param stubCreator a function to create the stub.
   * @param stubConfiguration the stub configuration.
   */
  public <I> I createStubInterface(
      Function<Channel, I> stubCreator, StubConfiguration stubConfiguration) {
    if (stubConfiguration.getDirectTarget().hasRelayDestination()) {
      stubCreator =
          stubCreator.compose(
              c ->
                  ClientCreator.createChannel(
                      c, stubConfiguration.getDirectTarget().getRelayDestination()));
    }
    return createStub(stubConfiguration.getDirectTarget().getServerSpec(), stubCreator);
  }

  /**
   * Creates a blocking/future interface for the given stub.
   *
   * <p>Usage:
   *
   * <pre>
   *   FooServiceGrpc.FooServiceBlockingStub stub = FooServiceGrpc.newBlockingStub(channel);
   *   FooServiceGrpcStub.BlockingInterface stubInterface =
   *       GrpcDirectTargetConfigures.createBlockingInterface(
   *           stub, FooServiceGrpcStub.BlockingInterface.class);
   * </pre>
   *
   * @throws IllegalArgumentException when invoke methods if the methods of the stub does agree with
   *     the methods of the interface.
   */
  public static <I, T> I newBlockingInterface(T stub, Class<I> blockingInterface) {
    return Reflection.newProxy(blockingInterface, new DirectInvocationHandler<T>(stub));
  }

  private <T> T createStub(ServerSpec serverSpec, Function<Channel, T> stubCreator) {
    return channelManager.createStub(
        serverSpec.getTarget(),
        () -> managedChannelSupplier.apply(serverSpec.getTarget()),
        stubCreator);
  }
}
