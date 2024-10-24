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

package com.google.devtools.mobileharness.shared.util.comm.relay;

import static io.grpc.Metadata.BINARY_HEADER_SUFFIX;

import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Metadata;
import io.grpc.Metadata.BinaryMarshaller;

/** Utils to add and get the destination from the headers. */
public final class DestinationUtils {
  private static final BinaryMarshaller<Destination> DESTINATION_MARSHALLER =
      new BinaryMarshaller<Destination>() {
        @Override
        public byte[] toBytes(Destination value) {
          return value.toByteArray();
        }

        @Override
        public Destination parseBytes(byte[] serialized) {
          try {
            return Destination.parseFrom(serialized);
          } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Failed to parse the destination", e);
          }
        }
      };

  static final Metadata.Key<Destination> DESTINATION_KEY =
      Metadata.Key.of("relay-destination" + BINARY_HEADER_SUFFIX, DESTINATION_MARSHALLER);

  public static Destination getDestination(Metadata headers) {
    return headers.get(DESTINATION_KEY);
  }

  @CanIgnoreReturnValue
  public static Metadata addDestination(Metadata headers, Destination destination) {
    headers.put(DESTINATION_KEY, destination);
    return headers;
  }

  @CanIgnoreReturnValue
  public static Metadata addDestination(Destination destination) {
    return addDestination(new Metadata(), destination);
  }

  public static String getTarget(Destination destination) {
    return destination.getServiceLocator().getGrpcTarget();
  }

  private DestinationUtils() {}
}
