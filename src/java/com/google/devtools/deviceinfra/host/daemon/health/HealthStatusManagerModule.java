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

package com.google.devtools.deviceinfra.host.daemon.health;

import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthGrpc;
import com.google.devtools.deviceinfra.host.daemon.health.service.HealthServiceImpl;
import com.google.devtools.mobileharness.infra.daemon.health.handler.DrainHandler;
import com.google.devtools.mobileharness.infra.lab.Annotations.LocalOnlyGrpcService;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import io.grpc.BindableService;

/** Guice module for health manager initialization. */
public final class HealthStatusManagerModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(HealthStatusManager.class);
    bind(HealthGrpc.HealthImplBase.class).to(HealthServiceImpl.class);
    Multibinder.newSetBinder(binder(), BindableService.class, LocalOnlyGrpcService.class)
        .addBinding()
        .to(HealthServiceImpl.class);

    // Create an empty Multibinder in case there are no DrainHandler bindings.
    Multibinder.newSetBinder(binder(), DrainHandler.class);
  }
}
