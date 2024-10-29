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

import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import io.grpc.Channel;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;

/** Manages the connections to the backend targets. */
@SuppressWarnings("GoogleInternalAnnotationsChecker")
@ThreadSafe
public interface ConnectionManager {

  /** Connects to the specific target by applying the connection creator. */
  <T> T connectToTarget(Destination target, Function<Channel, T> connectionCreator);
}
