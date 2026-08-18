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

import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.inject.AbstractModule;
import javax.inject.Singleton;

/**
 * Guice module for {@link LocalMode} in production environments.
 *
 * <p>This module binds {@link LocalMode} and {@link ExecMode} to the default process-level shared
 * environment which continues running across the process lifecycle.
 *
 * <p><b>Note for tests:</b> Do not install this module in unit or integration tests. Use {@link
 * LocalModeRule#getModule()} instead to provide an isolated environment per test and automatically
 * tear down resources.
 */
public class LocalModeModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(LocalModeEnvironment.class).toInstance(LocalModeEnvironment.getInstance());
    bind(LocalMode.class).in(Singleton.class);
    bind(ExecMode.class).to(LocalMode.class);
  }
}
