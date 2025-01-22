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

package com.google.devtools.mobileharness.infra.controller.scheduler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.infra.controller.event.handler.EventCollector;
import com.google.wireless.qa.mobileharness.shared.api.decorator.EmptyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.EmptyDecorator1;
import com.google.wireless.qa.mobileharness.shared.api.decorator.EmptyDecorator2;
import com.google.wireless.qa.mobileharness.shared.api.device.EmptyDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.EmptyDriver;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Dimensions;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AbstractScheduler}. */
@RunWith(JUnit4.class)
public class AbstractSchedulerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AllocationEvent event1;
  @Mock private AllocationEvent event2;
  @Mock private AllocationEvent event3;

  /** Instance under test. */
  @Spy private AbstractScheduler scheduler;

  @Test
  public void testHandlingEvents() throws MobileHarnessException {
    EventCollector eventCollector = new EventCollector(AllocationEvent.class);
    scheduler.registerEventHandler(eventCollector);
    scheduler.postEvent(event1);
    scheduler.postEvent(event2);
    scheduler.unregisterEventHandler(eventCollector);
    scheduler.postEvent(event3);

    eventCollector.verifyEvents(ImmutableList.<Object>of(event1, event2));
  }

  @Test
  public void testSupportingJob() {
    JobType jobType =
        JobType.newBuilder()
            .setDevice(EmptyDevice.class.getSimpleName())
            .setDriver(EmptyDriver.class.getSimpleName())
            .addDecorator(EmptyDecorator.class.getSimpleName())
            .addDecorator(EmptyDecorator1.class.getSimpleName())
            .build();
    JobUser jobUser =
        JobUser.newBuilder().setRunAs("run_as_user").setActualUser("actual_user").build();

    JobScheduleUnit job = mock(JobScheduleUnit.class);
    Params params = new Params(null);
    Dimensions dimensions = new Dimensions(null);
    when(job.type()).thenReturn(jobType);
    when(job.jobUser()).thenReturn(jobUser);
    when(job.params()).thenReturn(params);
    when(job.dimensions()).thenReturn(dimensions);

    DeviceScheduleUnit deviceInfo =
        new DeviceScheduleUnit(DeviceLocator.of("device_serial", LabLocator.LOCALHOST));
    deviceInfo.types().add(EmptyDevice.class.getSimpleName());
    deviceInfo.drivers().add(EmptyDriver.class.getSimpleName());

    // Decorators not supported.
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isFalse();

    // Decorators supported now.
    deviceInfo = new DeviceScheduleUnit(DeviceLocator.of("device_serial", LabLocator.LOCALHOST));
    deviceInfo.types().add(EmptyDevice.class.getSimpleName());
    deviceInfo.drivers().add(EmptyDriver.class.getSimpleName());
    deviceInfo
        .decorators()
        .add(EmptyDecorator.class.getSimpleName())
        .add(EmptyDecorator1.class.getSimpleName())
        .add(EmptyDecorator2.class.getSimpleName());
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isTrue();

    // Dimension not supported.
    dimensions.add("model", "regex:nexus.*");
    dimensions.add("sdk_version", "20");
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isFalse();

    // Dimension supported now.
    deviceInfo.dimensions().supported().add("model", "nexus 5").add("sdk_version", "20");
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isTrue();

    // User not supported.
    deviceInfo.owners().add("a").add("b");
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isFalse();

    // User supported now.
    deviceInfo.owners().add("run_as_user");
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isTrue();

    dimensions = new Dimensions(null);
    when(job.dimensions()).thenReturn(dimensions);
    deviceInfo
        .dimensions()
        .supported()
        .addAll(
            ImmutableList.of(
                StrPair.newBuilder()
                    .setName("country_code")
                    .setValue(Dimension.Value.ALL_VALUE_FOR_DEVICE)
                    .build()));
    dimensions.add("country_code", "regex:us|fr");
    assertThat(scheduler.ifDeviceSupports(deviceInfo, job)).isTrue();
  }
}
