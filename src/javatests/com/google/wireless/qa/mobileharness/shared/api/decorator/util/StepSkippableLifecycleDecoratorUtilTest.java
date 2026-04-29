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
  public void setUp() {
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                Job.JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();
  }

  @Test
  public void setState_getState_success() {
    StepSkippableLifecycleDecoratorUtil.setState(jobInfo, "my_key", "my_value");

    assertThat(StepSkippableLifecycleDecoratorUtil.getState(jobInfo, "my_key"))
        .hasValue("my_value");

    // Also verify the exact property name stored in JobInfo properties
    assertThat(jobInfo.properties().get("step_skippable_lifecycle_decorator_state_my_key"))
        .isEqualTo("my_value");
  }

  @Test
  public void getState_notFound_returnsEmpty() {
    assertThat(StepSkippableLifecycleDecoratorUtil.getState(jobInfo, "unknown_key")).isEmpty();
  }

  @Test
  public void relayStates_success() {
    StepSkippableLifecycleDecoratorUtil.setState(jobInfo, "key1", "val1");
    StepSkippableLifecycleDecoratorUtil.setState(jobInfo, "key2", "val2");

    // Add an unrelated property to ensure it's filtered out
    jobInfo.properties().add("some_other_key", "some_value");

    JobInfo jobInfo2 =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id_2", "job_name_2"))
            .setType(
                Job.JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();

    StepSkippableLifecycleDecoratorUtil.relayStates(jobInfo, jobInfo2);

    assertThat(StepSkippableLifecycleDecoratorUtil.getState(jobInfo2, "key1")).hasValue("val1");
    assertThat(StepSkippableLifecycleDecoratorUtil.getState(jobInfo2, "key2")).hasValue("val2");
    assertThat(jobInfo2.properties().get("some_other_key")).isNull();
  }
}
