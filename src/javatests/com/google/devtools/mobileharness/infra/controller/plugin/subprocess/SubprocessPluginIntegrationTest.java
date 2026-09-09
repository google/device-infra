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
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
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
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.io.File;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubprocessPluginIntegrationTest {

  @Plugin(type = PluginType.LAB)
  public static class IntegrationSamplePlugin {
    @Subscribe
    public void onStarting(LocalTestStartingEvent event) {
      event.getTest().properties().add("subprocess_integration_key", "subprocess_integration_val");
      event.getTest().status().set(TestStatus.RUNNING);
    }

    @Subscribe
    public void onEnded(LocalTestEndedEvent event) {
      event.getTest().properties().add("subprocess_ended_key", "subprocess_ended_val");
    }
  }

  @Test
  public void endToEnd_executesPluginInSubprocessAndReflectsMutations() throws Exception {
    try (SubprocessPluginLoader loader =
        new SubprocessPluginLoader(
            ImmutableList.of(),
            ImmutableList.of(IntegrationSamplePlugin.class.getName()),
            /* customWorkerRunnerJar= */ null)) {

      loader.load();

      EventBus eventBus = new EventBus();
      eventBus.register(loader.getSubscriber());

      JobInfo jobInfo =
          JobInfo.newBuilder()
              .setLocator(new JobLocator("job_integration", "sample_job"))
              .setType(
                  JobType.newBuilder()
                      .setDevice("AndroidRealDevice")
                      .setDriver("NoOpDriver")
                      .build())
              .build();
      TestInfo testInfo = jobInfo.tests().add("test_integration", "sample_test");
      Device device = new NoOpDevice("device_integration");

      Allocation allocation =
          new Allocation(testInfo.locator(), new DeviceLocator(device.getDeviceId()), null);
      DeviceInfo deviceInfo = DeviceInfo.newBuilder().setId(device.getDeviceId()).build();
      ImmutableMap<String, Device> localDevices = ImmutableMap.of(device.getDeviceId(), device);

      // Post starting event
      eventBus.post(new LocalTestStartingEvent(testInfo, localDevices, allocation, deviceInfo));
      assertThat(testInfo.properties().getAll())
          .containsEntry("subprocess_integration_key", "subprocess_integration_val");

      // Post ended event
      eventBus.post(
          new LocalTestEndedEvent(
              testInfo,
              localDevices,
              allocation,
              deviceInfo,
              /* shouldRebootDevice= */ true,
              /* testError= */ null));
      assertThat(testInfo.properties().getAll())
          .containsEntry("subprocess_ended_key", "subprocess_ended_val");
    }
  }

  @Test
  public void endToEnd_withHermeticWorkerBinary_executesPluginInSubprocessAndReflectsMutations()
      throws Exception {
    Assume.assumeTrue(new SystemUtil().isOnLinux());
    String hermeticWorkerBin =
        RunfilesUtil.getRunfilesLocation(
            "java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess/worker/plugin_worker_runner");
    String pluginJar =
        new File(
                IntegrationSamplePlugin.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .getAbsolutePath();

    try (SubprocessPluginLoader loader =
        new SubprocessPluginLoader(
            ImmutableList.of(pluginJar),
            ImmutableList.of(IntegrationSamplePlugin.class.getName()),
            /* customWorkerRunnerJar= */ null,
            hermeticWorkerBin)) {

      loader.load();

      EventBus eventBus = new EventBus();
      eventBus.register(loader.getSubscriber());

      JobInfo jobInfo =
          JobInfo.newBuilder()
              .setLocator(new JobLocator("job_hermetic", "sample_job"))
              .setType(
                  JobType.newBuilder()
                      .setDevice("AndroidRealDevice")
                      .setDriver("NoOpDriver")
                      .build())
              .build();
      TestInfo testInfo = jobInfo.tests().add("test_hermetic", "sample_test");
      Device device = new NoOpDevice("device_hermetic");

      Allocation allocation =
          new Allocation(testInfo.locator(), new DeviceLocator(device.getDeviceId()), null);
      DeviceInfo deviceInfo = DeviceInfo.newBuilder().setId(device.getDeviceId()).build();
      ImmutableMap<String, Device> localDevices = ImmutableMap.of(device.getDeviceId(), device);

      // Post starting event
      eventBus.post(new LocalTestStartingEvent(testInfo, localDevices, allocation, deviceInfo));
      assertThat(testInfo.properties().getAll())
          .containsEntry("subprocess_integration_key", "subprocess_integration_val");

      // Post ended event
      eventBus.post(
          new LocalTestEndedEvent(
              testInfo,
              localDevices,
              allocation,
              deviceInfo,
              /* shouldRebootDevice= */ true,
              /* testError= */ null));
      assertThat(testInfo.properties().getAll())
          .containsEntry("subprocess_ended_key", "subprocess_ended_val");
    }
  }
}
