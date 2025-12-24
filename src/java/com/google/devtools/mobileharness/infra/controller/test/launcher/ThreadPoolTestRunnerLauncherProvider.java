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

package com.google.devtools.mobileharness.infra.controller.test.launcher;

import com.google.common.eventbus.EventBus;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.lab.Annotations.GlobalEventBus;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * Provider for {@link ThreadPoolTestRunnerLauncher}.
 *
 * <p>This provider creates instances of {@link ThreadPoolTestRunnerLauncher} which use a shared
 * thread pool and event bus.
 */
public class ThreadPoolTestRunnerLauncherProvider implements LauncherProvider {

  private final ExecutorService threadPool;
  private final EventBus globalInternalEventBus;

  @Inject
  ThreadPoolTestRunnerLauncherProvider(
      @TestRunnerThreadPool ExecutorService threadPool,
      @GlobalEventBus EventBus globalInternalEventBus) {
    this.threadPool = threadPool;
    this.globalInternalEventBus = globalInternalEventBus;
  }

  @Override
  public TestRunnerLauncher<? super ProxyTestRunner> getLauncher(
      String testId, JobFeature jobFeature, List<Device> devices) {
    return new ThreadPoolTestRunnerLauncher<>(threadPool, globalInternalEventBus);
  }
}
