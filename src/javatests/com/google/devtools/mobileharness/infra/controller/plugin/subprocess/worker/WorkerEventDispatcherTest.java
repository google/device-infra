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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WorkerEventDispatcherTest {

  public static class SampleTestPlugin {
    @Subscribe
    public void onTestStarting(LocalTestStartingEvent event) {
      event.getTest().properties().add("plugin_mutated_prop", "mutated_value");
      event.getTest().status().set(TestStatus.DONE);
    }
  }

  private WorkerEventDispatcher dispatcher;

  @Before
  public void setUp() {
    dispatcher = new WorkerEventDispatcher(ImmutableList.of(new SampleTestPlugin()));
  }

  @Test
  public void dispatchEvent_dispatchesToPluginAndCollectsDeltas() {
    RunTestEventRequest request =
        RunTestEventRequest.newBuilder()
            .setEventClassName("LocalTestStartingEvent")
            .setJobInfo(
                JobInfoSnapshot.newBuilder().setJobId("job_1").setJobName("sample_job").build())
            .setTestInfo(
                TestInfoSnapshot.newBuilder()
                    .setTestId("test_1")
                    .setTestName("sample_test")
                    .setStatus(TestStatus.RUNNING.name())
                    .build())
            .setDeviceInfo(DeviceInfoSnapshot.newBuilder().setDeviceId("device_1").build())
            .build();

    RunTestEventResponse response = dispatcher.dispatchEvent(request);

    assertThat(response.getAddedPropertiesMap())
        .containsEntry("plugin_mutated_prop", "mutated_value");
    assertThat(response.getOverrideStatus()).isEqualTo(TestStatus.DONE.name());
  }
}
