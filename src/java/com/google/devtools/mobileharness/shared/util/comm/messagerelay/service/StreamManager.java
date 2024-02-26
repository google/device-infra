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

package com.google.devtools.mobileharness.shared.util.comm.messagerelay.service;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.messagerelay.proto.MessageRelayServiceProto.RelayMessage;
import io.grpc.stub.StreamObserver;
import java.util.Collection;

/** Manages all the bidirectional gRPC streams that connects the MessageRelayService. */
final class StreamManager {
  /** The instance of this singleton. */
  private static final StreamManager instance = new StreamManager();

  // Stores all valid bidirectional streams connected to this service. The key is hostname, and
  // value is the stream's information.
  private final Multimap<String, RelayStream> validStreams =
      Multimaps.synchronizedMultimap(HashMultimap.create());

  private StreamManager() {}

  static StreamManager getInstance() {
    return instance;
  }

  /** Adds a stream to the manager */
  void addStream(RelayStream stream) {
    validStreams.put(stream.hostname(), stream);
  }

  /** Removes a stream from the manager. */
  void removeStream(RelayStream stream) {
    validStreams.remove(stream.hostname(), stream);
  }

  /** Gets the number of streams that are connected from the host. */
  int getStreamSize(String hostname) {
    Collection<RelayStream> hostStreams = validStreams.get(hostname);
    synchronized (validStreams) {
      return hostStreams.size();
    }
  }

  /** Picks up a stream to forward message to the host. */
  RelayStream pickUpStream(String hostname) throws MobileHarnessException {
    Collection<RelayStream> hostStreams = validStreams.get(hostname);
    synchronized (validStreams) {
      if (!hostStreams.isEmpty()) {
        return hostStreams.iterator().next();
      } else {
        throw new MobileHarnessException(
            InfraErrorId.MESSAGE_RELAY_NO_AVAILABLE_STREAM,
            String.format("No available stream for host %s to forward message", hostname));
      }
    }
  }

  @AutoValue
  abstract static class RelayStream {
    static RelayStream create(
        String hostname, String streamId, StreamObserver<RelayMessage> observer) {
      return new AutoValue_StreamManager_RelayStream(hostname, streamId, observer);
    }

    abstract String hostname();

    abstract String streamId();

    abstract StreamObserver<RelayMessage> observer();
  }
}
