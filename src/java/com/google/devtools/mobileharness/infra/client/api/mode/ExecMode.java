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

package com.google.devtools.mobileharness.infra.client.api.mode;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.reserver.DeviceReserver;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;

/**
 * Execution mode of Mobile Harness client. Implements this interface for running against different
 * environments or labs.
 */
public interface ExecMode {
  /** Creates allocator for allocating devices of the given job. */
  DeviceAllocator createDeviceAllocator(JobInfo jobInfo, EventBus globalInternalBus)
      throws MobileHarnessException, InterruptedException;

  /** Creates reserver for reserve devices. */
  default DeviceReserver createDeviceReserver()
      throws MobileHarnessException, InterruptedException {
    return new DeviceReserver() {};
  }

  /** Creates a querier to query the information of the available devices/labs. */
  DeviceQuerier createDeviceQuerier() throws MobileHarnessException, InterruptedException;

  /** Creates a test runner for executing the given test on the given device. */
  DirectTestRunner createTestRunner(
      DirectTestRunnerSetting setting, ListeningExecutorService testThreadPool)
      throws MobileHarnessException, InterruptedException;
}
