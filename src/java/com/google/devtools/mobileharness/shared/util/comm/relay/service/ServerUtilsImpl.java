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

import static io.grpc.ServerInterceptors.intercept;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import java.util.Collection;

/** Implementation of {@link ServerUtils}. */
public final class ServerUtilsImpl implements ServerUtils {

  private final ConnectionManager connectionManager = DirectConnectionManager.getInstance();

  @Override
  public ServerBuilder<?> enableGrpcRelay(
      ServerBuilder<?> serverBuilder, Collection<BindableService> dualModeServices) {
    RelayInterceptor relayInterceptor = new RelayInterceptor(connectionManager);
    for (BindableService service : dualModeServices) {
      serverBuilder.addService(intercept(service, relayInterceptor));
    }
    return serverBuilder.fallbackHandlerRegistry(new RelayRegistry(connectionManager));
  }
}
