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
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc.PluginWorkerServiceImplBase;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.test.event.LocalDriverStartingEventImpl;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubprocessPluginSubscriberTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final AtomicReference<RunTestEventRequest> receivedRequest = new AtomicReference<>();
  private TestInfo testInfo;
  private Device device;
  private SubprocessPluginSubscriber subscriber;

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();

    PluginWorkerServiceImplBase fakeService =
        new PluginWorkerServiceImplBase() {
          @Override
          public void runTestEvent(
              RunTestEventRequest request, StreamObserver<RunTestEventResponse> responseObserver) {
            receivedRequest.set(request);
            responseObserver.onNext(
                RunTestEventResponse.newBuilder()
                    .putAddedProperties("prop_from_subprocess", "value_123")
                    .setOverrideStatus(TestStatus.DONE.name())
                    .build());
            responseObserver.onCompleted();
          }
        };

    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(fakeService)
            .build()
            .start());

    PluginWorkerServiceGrpc.PluginWorkerServiceBlockingStub stub =
        PluginWorkerServiceGrpc.newBlockingStub(
            grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()));

    subscriber = new SubprocessPluginSubscriber(() -> stub);

    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_123", "sample_job"))
            .setType(
                JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("NoOpDriver").build())
            .build();
    testInfo = jobInfo.tests().add("test_456", "sample_test");
    testInfo.status().set(TestStatus.RUNNING);

    device = new NoOpDevice("device_1");
  }

  @Test
  public void onLocalTestStarting_forwardsEventAndAppliesDelta() throws Exception {
    LocalTestStartingEvent event =
        new LocalTestStartingEvent(
            testInfo,
            ImmutableMap.of(device.getDeviceId(), device),
            new Allocation(testInfo.locator(), new DeviceLocator(device.getDeviceId()), null),
            DeviceInfo.newBuilder().setId(device.getDeviceId()).build());

    subscriber.onLocalTestStarting(event);

    assertThat(receivedRequest.get()).isNotNull();
    assertThat(receivedRequest.get().getEventClassName()).isEqualTo("LocalTestStartingEvent");
    assertThat(testInfo.properties().getAll()).containsEntry("prop_from_subprocess", "value_123");
    assertThat(testInfo.status().get()).isEqualTo(TestStatus.DONE);
  }

  @Test
  public void onLocalDriverStarting_forwardsDriverName() throws Exception {
    LocalDriverStartingEvent event =
        new LocalDriverStartingEventImpl(
            "NoOpDriver",
            DeviceFeature.getDefaultInstance(),
            ImmutableList.of(),
            device,
            testInfo,
            new com.google.devtools.mobileharness.api.model.allocation.Allocation(
                TestLocator.of(
                    testInfo.locator().getId(),
                    testInfo.locator().getName(),
                    com.google.devtools.mobileharness.api.model.job.JobLocator.of(
                        testInfo.jobInfo().locator().getId(),
                        testInfo.jobInfo().locator().getName())),
                com.google.devtools.mobileharness.api.model.lab.DeviceLocator.of(
                    device.getDeviceId(), LabLocator.LOCALHOST)));

    subscriber.onLocalDriverStarting(event);

    assertThat(receivedRequest.get()).isNotNull();
    assertThat(receivedRequest.get().getEventClassName()).isEqualTo("LocalDriverStartingEvent");
    assertThat(receivedRequest.get().getDriverName()).isEqualTo("NoOpDriver");
  }
}
