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

package com.google.devtools.mobileharness.shared.util.comm.relay.client;

import com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/** Creates clients to the grpc relay service. */
public final class ClientCreator {

  /** Creates a channel to the grpc relay service from original channel. */
  public static Channel createChannel(Channel originalChannel, Destination destination) {
    return ClientInterceptors.intercept(originalChannel, createInterceptor(destination));
  }

  /** Creates a stub to the grpc relay service from original stub. */
  public static <T extends AbstractStub<T>> T createStub(T originalStub, Destination destination) {
    return originalStub.withInterceptors(createInterceptor(destination));
  }

  static ClientInterceptor createInterceptor(Destination destination) {
    return MetadataUtils.newAttachHeadersInterceptor(DestinationUtils.addDestination(destination));
  }

  private ClientCreator() {}
}
