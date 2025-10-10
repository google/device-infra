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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidLogcatMonitoringDecorator} */
@RunWith(JUnit4.class)
public class AndroidLogcatMonitoringDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Device device;
  @Mock private Driver decoratedDriver;

  private TestInfo testInfo;
  private JobInfo jobInfo;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    doNothing().when(decoratedDriver).run(testInfo);
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setSetting(JobSetting.newBuilder().setGenFileDir("/path/to/genFileDir").build())
            .setType(JobType.newBuilder().setDevice("device_type").setDriver("NoOpDriver").build())
            .build();
    testInfo = jobInfo.tests().add("fake test");
  }

  @Test
  public void decorator_run_succeeds() throws Exception {
    AndroidLogcatMonitoringDecoratorSpec spec =
        AndroidLogcatMonitoringDecoratorSpec.newBuilder()
            .addReportAsFailurePackages("com.test.me")
            .build();
    jobInfo.scopedSpecs().add("AndroidLogcatMonitoringDecorator", spec);

    AndroidLogcatMonitoringDecorator decorator =
        new AndroidLogcatMonitoringDecorator(decoratedDriver, testInfo);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }
}
