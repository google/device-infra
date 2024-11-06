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

package com.google.devtools.mobileharness.shared.util.comm.relay.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelManager;
import com.google.devtools.mobileharness.shared.util.comm.stub.ManagedChannelSupplier;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;

/** Manages connections to the direct targets. */
@SuppressWarnings("GoogleInternalAnnotationsChecker")
@ThreadSafe
public final class DirectConnectionManager implements ConnectionManager {

  private static final DirectConnectionManager INSTANCE =
      new DirectConnectionManager(
          ChannelManager.getInstance(), ManagedChannelSupplier.getInstance());

  private final ChannelManager channelManager;
  private final Function<String, ? extends ManagedChannel> managedChannelSupplier;

  public static DirectConnectionManager getInstance() {
    return INSTANCE;
  }

  @VisibleForTesting
  public DirectConnectionManager(
      ChannelManager channelManager,
      Function<String, ? extends ManagedChannel> managedChannelSupplier) {
    this.channelManager = channelManager;
    this.managedChannelSupplier = managedChannelSupplier;
  }

  @Override
  public <T> T connectToTarget(Destination target, Function<Channel, T> connectionCreator) {
    String grpcTarget = DestinationUtils.getTarget(target);
    return channelManager.createStub(
        grpcTarget, () -> managedChannelSupplier.apply(grpcTarget), connectionCreator);
  }
}
