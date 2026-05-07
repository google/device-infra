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

package com.google.wireless.qa.mobileharness.shared.api.decorator.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StepSkippableLifecycleDecoratorUtilTest {

  private JobInfo jobInfo;

  @Before
  public void setUp() throws Exception {
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                Job.JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();
  }

  @Test
  public void setState_getState_success() {
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "MyDecorator", "my_key", "my_value");

    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo, "device_id", "MyDecorator", "my_key"))
        .hasValue("my_value");

    // Also verify the exact property name stored in JobInfo properties
    String expectedKey = "step_skippable_lifecycle_decorator_state::device_id::MyDecorator::my_key";
    assertThat(jobInfo.properties().get(expectedKey)).isEqualTo("my_value");
  }

  @Test
  public void getState_notFound_returnsEmpty() {
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo, "device_id", "MyDecorator", "unknown_key"))
        .isEmpty();
  }

  @Test
  public void setState_differentTestsDevicesDecorators_noCollision() throws Exception {
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "Decorator1", "key", "value1");
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "Decorator1", "key", "value2");
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id_2", "Decorator1", "key", "value3");
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "Decorator2", "key", "value4");

    // Different tests on the same device will collide if they use the same key
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(jobInfo, "device_id", "Decorator1", "key"))
        .hasValue("value2");
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(jobInfo, "device_id", "Decorator1", "key"))
        .hasValue("value2");
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo, "device_id_2", "Decorator1", "key"))
        .hasValue("value3");
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(jobInfo, "device_id", "Decorator2", "key"))
        .hasValue("value4");
  }

  @Test
  public void relayStates_success() throws Exception {
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "MyDecorator", "key1", "val1");
    StepSkippableLifecycleDecoratorUtil.setState(
        jobInfo, "device_id", "MyDecorator", "key2", "val2");

    // Add an unrelated property to ensure it's filtered out
    jobInfo.properties().add("some_other_key", "some_value");

    JobInfo jobInfo2 =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id_2", "job_name_2"))
            .setType(
                Job.JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();

    StepSkippableLifecycleDecoratorUtil.relayStates(jobInfo, jobInfo2);

    // Relayed state should be retrievable if we use the same device id and key
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo2, "device_id", "MyDecorator", "key1"))
        .hasValue("val1");
    assertThat(
            StepSkippableLifecycleDecoratorUtil.getState(
                jobInfo2, "device_id", "MyDecorator", "key2"))
        .hasValue("val2");
    assertThat(jobInfo2.properties().get("some_other_key")).isNull();
  }
}
