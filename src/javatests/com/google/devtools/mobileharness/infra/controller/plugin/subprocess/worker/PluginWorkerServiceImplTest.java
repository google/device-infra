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
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ShutdownRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ShutdownResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PluginWorkerServiceImplTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private PluginWorkerServiceGrpc.PluginWorkerServiceBlockingStub blockingStub;
  private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    WorkerEventDispatcher dispatcher = new WorkerEventDispatcher(ImmutableList.of());
    PluginWorkerServiceImpl service =
        new PluginWorkerServiceImpl(dispatcher, () -> shutdownTriggered.set(true));

    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start());

    blockingStub =
        PluginWorkerServiceGrpc.newBlockingStub(
            grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()));
  }

  @Test
  public void runTestEvent_returnsResponse() {
    RunTestEventRequest request =
        RunTestEventRequest.newBuilder()
            .setEventClassName("LocalTestStartingEvent")
            .setJobInfo(JobInfoSnapshot.newBuilder().setJobId("job_1").build())
            .setTestInfo(
                TestInfoSnapshot.newBuilder()
                    .setTestId("test_1")
                    .setStatus(TestStatus.RUNNING.name())
                    .build())
            .setDeviceInfo(DeviceInfoSnapshot.newBuilder().setDeviceId("device_1").build())
            .build();

    RunTestEventResponse response = blockingStub.runTestEvent(request);
    assertThat(response).isNotNull();
  }

  @Test
  public void shutdown_triggersShutdownCallback() {
    ShutdownResponse response = blockingStub.shutdown(ShutdownRequest.getDefaultInstance());
    assertThat(response.getSuccess()).isTrue();
  }
}
