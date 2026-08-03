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
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants.ExecutionMode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhaseSkippableDecoratorUtilTest {

  private TestInfo testInfo;

  @Before
  public void setUp() throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();
    testInfo = jobInfo.tests().add("test_id", "test_name");
  }

  @Test
  public void setState_getState_success() throws Exception {
    JobType message =
        JobType.newBuilder().setDevice("test_device").setDriver("test_driver").build();

    PhaseSkippableDecoratorUtil.setState(testInfo, "device_id_1", message);

    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                testInfo, "device_id_1", JobType.getDefaultInstance()))
        .hasValue(message);

    String expectedKey =
        "phase_skippable_decorator_state::device_id_1::com.google.wireless.qa.mobileharness.shared.proto.Job$JobType";
    assertThat(testInfo.properties().get(expectedKey)).contains("device: \"test_device\"");
    assertThat(testInfo.properties().get(expectedKey)).contains("driver: \"test_driver\"");
  }

  @Test
  public void getState_notFound_returnsEmpty() throws Exception {
    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                testInfo, "device_id_1", JobType.getDefaultInstance()))
        .isEmpty();
  }

  @Test
  public void setState_differentDevices_noCollision() throws Exception {
    JobType message1 = JobType.newBuilder().setDevice("device_1").setDriver("driver_1").build();
    JobType message2 = JobType.newBuilder().setDevice("device_2").setDriver("driver_2").build();

    PhaseSkippableDecoratorUtil.setState(testInfo, "dev_1", message1);
    PhaseSkippableDecoratorUtil.setState(testInfo, "dev_2", message2);

    assertThat(
            PhaseSkippableDecoratorUtil.getState(testInfo, "dev_1", JobType.getDefaultInstance()))
        .hasValue(message1);
    assertThat(
            PhaseSkippableDecoratorUtil.getState(testInfo, "dev_2", JobType.getDefaultInstance()))
        .hasValue(message2);
  }

  @Test
  public void relayStates_success() throws Exception {
    JobType message =
        JobType.newBuilder().setDevice("test_device").setDriver("test_driver").build();
    PhaseSkippableDecoratorUtil.setState(testInfo, "device_id", message);

    testInfo.properties().add("unrelated_key", "unrelated_value");

    JobInfo jobInfo2 =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id_2", "job_name_2"))
            .setType(JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();
    TestInfo testInfo2 = jobInfo2.tests().add("test_id_2", "test_name_2");

    PhaseSkippableDecoratorUtil.relayStates(testInfo, testInfo2);

    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                testInfo2, "device_id", JobType.getDefaultInstance()))
        .hasValue(message);
    assertThat(testInfo2.properties().get("unrelated_key")).isNull();
  }

  @Test
  public void getState_unknownFields_ignoredSuccess() throws Exception {
    String expectedKey =
        "phase_skippable_decorator_state::device_id_1::com.google.wireless.qa.mobileharness.shared.proto.Job$JobType";
    String textProtoWithUnknown =
        "device: \"test_device\"\ndriver: \"test_driver\"\n999: \"unknown_val\"\n";
    testInfo.properties().add(expectedKey, textProtoWithUnknown);

    JobType expectedMessage =
        JobType.newBuilder().setDevice("test_device").setDriver("test_driver").build();
    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                testInfo, "device_id_1", JobType.getDefaultInstance()))
        .hasValue(expectedMessage);
  }

  @Test
  public void setState_getState_subTestUsesRootTest() throws Exception {
    TestInfo subTestInfo = testInfo.subTests().add("sub_test_id", "sub_test_name");

    JobType message = JobType.newBuilder().setDevice("sub_device").setDriver("sub_driver").build();
    PhaseSkippableDecoratorUtil.setState(subTestInfo, "device_id_1", message);

    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                testInfo, "device_id_1", JobType.getDefaultInstance()))
        .hasValue(message);
    assertThat(
            PhaseSkippableDecoratorUtil.getState(
                subTestInfo, "device_id_1", JobType.getDefaultInstance()))
        .hasValue(message);
  }

  @Test
  public void getExecutionMode_unspecified_returnsFull() throws Exception {
    assertThat(PhaseSkippableDecoratorUtil.getExecutionMode(testInfo.jobInfo()))
        .isEqualTo(ExecutionMode.FULL);
  }

  @Test
  public void getExecutionMode_setupOnly_returnsSetupOnly() throws Exception {
    testInfo
        .jobInfo()
        .properties()
        .add(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, "SETUP_ONLY");
    assertThat(PhaseSkippableDecoratorUtil.getExecutionMode(testInfo.jobInfo()))
        .isEqualTo(ExecutionMode.SETUP_ONLY);
  }

  @Test
  public void getExecutionMode_teardownOnly_returnsTeardownOnly() throws Exception {
    testInfo
        .jobInfo()
        .properties()
        .add(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, "TEARDOWN_ONLY");
    assertThat(PhaseSkippableDecoratorUtil.getExecutionMode(testInfo.jobInfo()))
        .isEqualTo(ExecutionMode.TEARDOWN_ONLY);
  }

  @Test
  public void getExecutionMode_unknown_throwsException() throws Exception {
    testInfo
        .jobInfo()
        .properties()
        .add(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, "INVALID_MODE");
    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> PhaseSkippableDecoratorUtil.getExecutionMode(testInfo.jobInfo()));
    assertThat(exception.getErrorId())
        .isEqualTo(ExtErrorId.PHASE_SKIPPABLE_DECORATOR_UNKNOWN_EXECUTION_MODE);
  }
}
