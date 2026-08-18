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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import com.google.devtools.mobileharness.api.testrunner.device.cache.LocalSessionDeviceCache;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Guice module for {@link LocalMode}.
 *
 * <p>This module provides bindings for {@link LocalModeEnvironment}, {@link SessionDeviceCache},
 * {@link DeviceReserver}, and {@link LabInfoService}.
 *
 * <p>In production (via {@link #LocalModeModule()}), this module binds the default process-level
 * shared environment which continues running across the process lifecycle.
 *
 * <p><b>Note for tests:</b> Do not install {@code new LocalModeModule()} directly in unit or
 * integration tests. Use {@link LocalModeRule#getModule()} instead to provide an isolated
 * environment per test and automatically tear down resources.
 */
public class LocalModeModule extends AbstractModule {

  @Nullable private final LocalModeEnvironment env;

  /**
   * Creates a {@link LocalModeModule} backed by the default process-level shared environment for
   * production.
   */
  public LocalModeModule() {
    this(null);
  }

  LocalModeModule(@Nullable LocalModeEnvironment env) {
    this.env = env;
  }

  @Override
  protected void configure() {
    bind(LocalModeEnvironment.class)
        .toInstance(env != null ? env : LocalModeEnvironment.getInstance());
    bind(SessionDeviceCache.class).to(LocalSessionDeviceCache.class).in(Singleton.class);
  }

  @Provides
  DeviceReserver provideDeviceReserver(LocalModeEnvironment env) {
    return env.createDeviceReserver();
  }

  @Provides
  LabInfoService provideLabInfoService(LocalModeEnvironment env) {
    return env.getLabInfoService();
  }
}
