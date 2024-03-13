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

import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceGrpc.MessageRelayServiceStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelManager;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Stub manager for Message Relay client to create and cache stubs. */
public class MessageRelayStubManager {

  private static final MessageRelayStubManager INSTANCE =
      new MessageRelayStubManager(
          ChannelManager.getInstance(),
          ThreadPools.createStandardThreadPool("message-relay-channel-executor"));

  public static MessageRelayStubManager getInstance() {
    return INSTANCE;
  }

  private final ChannelManager channelManager;
  private final Executor channelExecutor;

  private MessageRelayStubManager(ChannelManager channelManager, Executor channelExecutor) {
    this.channelManager = channelManager;
    this.channelExecutor = channelExecutor;
  }

  public MessageRelayServiceStub getStub(String target) {
    return channelManager.createStub(target, new ManagedChannelSupplier(target), this::createStub);
  }

  private MessageRelayServiceStub createStub(Channel channel) {
    return MessageRelayServiceGrpc.newStub(channel);
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
