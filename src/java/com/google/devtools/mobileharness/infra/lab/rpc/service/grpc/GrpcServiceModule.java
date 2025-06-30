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

package com.google.devtools.mobileharness.infra.lab.rpc.service.grpc;

import com.google.devtools.mobileharness.infra.lab.Annotations.CloudRpcGrpcService;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import io.grpc.BindableService;

/** Bindings for common gRPC services. */
public final class GrpcServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder.newSetBinder(binder(), BindableService.class, CloudRpcGrpcService.class)
        .addBinding()
        .to(ExecTestGrpcImpl.class);
    Multibinder.newSetBinder(binder(), BindableService.class, CloudRpcGrpcService.class)
        .addBinding()
        .to(PrepareTestGrpcImpl.class);
    Multibinder.newSetBinder(binder(), BindableService.class, CloudRpcGrpcService.class)
        .addBinding()
        .to(StatGrpcImpl.class);
  }
}
