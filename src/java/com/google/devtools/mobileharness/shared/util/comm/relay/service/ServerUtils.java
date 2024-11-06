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

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import java.util.Collection;

/** Utilities to enable Grpc Relay in a gRPC server. */
public interface ServerUtils {

  /**
   * Enables gRPC relay for the given server builder and services.
   *
   * @param serverBuilder the server builder to enable gRPC relay for.
   * @param dualModeServices the services that are registered directly at the server and can also be
   *     relayed to backend servers.
   * @return the server builder with gRPC relay enabled.
   */
  ServerBuilder<?> enableGrpcRelay(
      ServerBuilder<?> serverBuilder, Collection<BindableService> dualModeServices);
}
