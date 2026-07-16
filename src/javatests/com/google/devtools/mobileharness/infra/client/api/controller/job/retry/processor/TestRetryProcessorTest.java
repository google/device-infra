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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link TestRetryProcessor}. */
@RunWith(JUnit4.class)
public final class TestRetryProcessorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AndroidInstrumentationTestRetryProcessor androidInstrumentationTestRetryProcessor;
  private TestRetryProcessor processor;

  @Before
  public void setUp() {
    processor = new TestRetryProcessor(androidInstrumentationTestRetryProcessor);
  }

  @Test
  public void generateRetryTestTargetsProperty_wrongDriver_notDelegate() throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("OtherDriver").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");

    assertThat(processor.generateRetryTestTargetsProperty(test)).isEmpty();

    verifyNoInteractions(androidInstrumentationTestRetryProcessor);
  }

  @Test
  public void generateRetryTestTargetsProperty_androidInstrumentationDriver_delegates()
      throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    jobInfo.params().add("enable_android_instrumentation_failed_tests_only_retry", "true");
    TestInfo test = jobInfo.tests().add("test_name");

    when(androidInstrumentationTestRetryProcessor.generateRetryTestTargetsProperty(test))
        .thenReturn(ImmutableMap.of("key", "value"));

    assertThat(processor.generateRetryTestTargetsProperty(test)).containsExactly("key", "value");
  }

  @Test
  public void
      generateRetryTestTargetsProperty_androidInstrumentationDriver_paramNotEnabled_notDelegate()
          throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .build();
    TestInfo test = jobInfo.tests().add("test_name");

    assertThat(processor.generateRetryTestTargetsProperty(test)).isEmpty();

    verifyNoInteractions(androidInstrumentationTestRetryProcessor);
  }
}
