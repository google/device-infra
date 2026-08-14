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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubprocessPluginSkewTest {

  @Plugin(type = PluginType.LAB)
  public static class CrashingPlugin {
    @Subscribe
    public void onStarting(LocalTestStartingEvent event) {
      throw new RuntimeException(
          "Simulated plugin exception (e.g. NoSuchMethodError / Protobuf version skew)");
    }
  }

  @Test
  public void workerPluginException_doesNotCrashHostProcess() throws Exception {
    try (SubprocessPluginLoader loader =
        new SubprocessPluginLoader(
            ImmutableList.of(),
            ImmutableList.of(CrashingPlugin.class.getName()),
            /* customWorkerRunnerJar= */ null)) {

      loader.load();

      EventBus eventBus = new EventBus();
      eventBus.register(loader.getSubscriber());

      JobInfo jobInfo =
          JobInfo.newBuilder()
              .setLocator(new JobLocator("job_skew", "skew_job"))
              .setType(
                  JobType.newBuilder()
                      .setDevice("AndroidRealDevice")
                      .setDriver("NoOpDriver")
                      .build())
              .build();
      TestInfo testInfo = jobInfo.tests().add("test_skew", "skew_test");
      Device device = new NoOpDevice("device_skew");

      Allocation allocation =
          new Allocation(testInfo.locator(), new DeviceLocator(device.getDeviceId()), null);
      DeviceInfo deviceInfo = DeviceInfo.newBuilder().setId(device.getDeviceId()).build();
      ImmutableMap<String, Device> localDevices = ImmutableMap.of(device.getDeviceId(), device);

      // Event execution should be isolated; host process continues unaffected
      eventBus.post(new LocalTestStartingEvent(testInfo, localDevices, allocation, deviceInfo));
      eventBus.post(
          new LocalTestEndedEvent(
              testInfo,
              localDevices,
              allocation,
              deviceInfo,
              /* shouldRebootDevice= */ true,
              /* testError= */ null));

      // Host test completes normally
      assertThat(testInfo.locator().getId()).isEqualTo("test_skew");
    }
  }
}
