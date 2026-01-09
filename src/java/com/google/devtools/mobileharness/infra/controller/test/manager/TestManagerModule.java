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

package com.google.devtools.mobileharness.infra.controller.test.manager;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.controller.test.manager.Annotations.KillExecutor;
import com.google.devtools.mobileharness.infra.lab.controller.LabDirectTestRunnerHolder;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import javax.inject.Singleton;

/** Module for providing common dependencies for test managers. */
public class TestManagerModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(new Key<TestManager<?>>() {}).to(ProxyTestManager.class);
    bind(LabDirectTestRunnerHolder.class).to(ProxyTestManager.class);
  }

  @Provides
  @Singleton
  @KillExecutor
  ListeningExecutorService provideKillExecutor() {
    return ThreadPools.createStandardThreadPool("proxy-test-kill");
  }
}
