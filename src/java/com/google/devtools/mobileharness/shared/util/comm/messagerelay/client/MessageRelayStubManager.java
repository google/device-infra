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
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelManager;
import com.google.devtools.mobileharness.shared.util.comm.stub.ManagedChannelSupplier;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.util.function.Function;

/** Stub manager for Message Relay client to create and cache stubs. */
public class MessageRelayStubManager {

  private static final MessageRelayStubManager INSTANCE =
      new MessageRelayStubManager(
          ChannelManager.getInstance(), ManagedChannelSupplier.getInstance());

  public static MessageRelayStubManager getInstance() {
    return INSTANCE;
  }

  private final ChannelManager channelManager;
  private final Function<String, ? extends ManagedChannel> managedChannelSupplier;

  private MessageRelayStubManager(
      ChannelManager channelManager,
      Function<String, ? extends ManagedChannel> managedChannelSupplier) {
    this.channelManager = channelManager;
    this.managedChannelSupplier = managedChannelSupplier;
  }

  public MessageRelayServiceStub getStub(String target) {
    return channelManager.createStub(
        target, () -> managedChannelSupplier.apply(target), this::createStub);
  }

  private MessageRelayServiceStub createStub(Channel channel) {
    return MessageRelayServiceGrpc.newStub(channel);
  }
}
