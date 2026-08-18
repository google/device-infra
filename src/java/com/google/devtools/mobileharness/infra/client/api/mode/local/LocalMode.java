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

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.shared.file.resolver.FileResolver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import javax.inject.Inject;

/**
 * Execution mode which runs tests on local devices.
 *
 * <p>In production, whether instantiated directly via {@link #LocalMode()} or injected via {@link
 * LocalModeModule}, all {@link LocalMode} instances share the same underlying process-level runtime
 * environment (device manager, scheduler, thread pools) which persists throughout the lifetime of
 * the process.
 *
 * <p>For unit and integration tests, use {@link LocalModeRule} instead to provide a dedicated,
 * isolated environment per test that is automatically torn down upon test completion.
 */
public class LocalMode implements ExecMode {

  private final LocalModeEnvironment env;

  /**
   * Creates a {@link LocalMode} instance backed by the default process-level shared environment.
   */
  public LocalMode() {
    this(LocalModeEnvironment.getInstance());
  }

  @Inject
  LocalMode(LocalModeEnvironment env) {
    this.env = env;
  }

  @Override
  public void initialize(EventBus globalInternalBus) throws InterruptedException {
    env.initialize(globalInternalBus);
  }

  @Override
  public DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus)
      throws InterruptedException {
    return env.createDeviceAllocator(jobInfo, globalInternalBus);
  }

  @Override
  public DeviceQuerier createDeviceQuerier() {
    return env.createDeviceQuerier();
  }

  @Override
  public DeviceReserver createDeviceReserver() {
    return env.createDeviceReserver();
  }

  @Override
  public DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting,
      ListeningExecutorService threadPool,
      FileResolver fileResolver)
      throws MobileHarnessException, InterruptedException {
    return env.createTestRunner(setting, threadPool, fileResolver);
  }
}
