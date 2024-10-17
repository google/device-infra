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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Metadata;

/** Utils to add and get the destination from the headers. */
public final class DestinationUtils {
  static final Metadata.Key<String> DESTINATION_KEY =
      Metadata.Key.of("relay-destination-id", Metadata.ASCII_STRING_MARSHALLER);

  public static String getDestination(Metadata headers) {
    return headers.get(DESTINATION_KEY);
  }

  @CanIgnoreReturnValue
  public static Metadata addDestination(Metadata headers, String destination) {
    headers.put(DESTINATION_KEY, destination);
    return headers;
  }

  @CanIgnoreReturnValue
  public static Metadata addDestination(String destination) {
    return addDestination(new Metadata(), destination);
  }

  private DestinationUtils() {}
}
