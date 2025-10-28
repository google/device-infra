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

package com.google.devtools.mobileharness.infra.master.central.model.job;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Job.JobProfile;
import com.google.devtools.mobileharness.infra.master.central.proto.Test.TestCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Test.TestProfile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link JobDao}. */
@RunWith(JUnit4.class)
public class JobDaoTest {

  private static final JobLocator JOB_LOCATOR = JobLocator.of("job_id", "job_name");

  @Test
  public void testOperations() {
    String testIdPrefix = "test_id_";
    int testCount = 3;
    List<TestDao> tests = new ArrayList<>(testCount);
    for (int i = 0; i < 3; i++) {
      TestLocator testLocator = TestLocator.of(testIdPrefix + i, "test_name", JOB_LOCATOR);
      TestDao test =
          TestDao.create(
              testLocator, TestProfile.getDefaultInstance(), TestCondition.getDefaultInstance());
      tests.add(test);
    }

    JobDao job =
        JobDao.create(
            JOB_LOCATOR, JobProfile.getDefaultInstance(), JobCondition.getDefaultInstance());

    // Add test 0.
    job.addTest(tests.get(0));
    assertThat(job.getTests()).hasSize(1);
    assertThat(job.getTest(testIdPrefix + 0)).hasValue(tests.get(0));
    assertThat(job.getTest(testIdPrefix + 1)).isEmpty();
    assertThat(job.removeTest(testIdPrefix + 1)).isEmpty();

    // Add test 1, 2.
    job.addTest(tests.get(1));
    job.addTest(tests.get(2));
    assertThat(job.getTests()).hasSize(3);
    for (int i = 0; i < testCount; i++) {
      assertThat(job.getTest(testIdPrefix + i)).hasValue(tests.get(i));
    }

    // Adding existing tests again has no effect.
    for (int i = 0; i < testCount; i++) {
      job.addTest(tests.get(i));
    }
    assertThat(job.getTests()).hasSize(3);

    // Remove test 2.
    assertThat(job.removeTest(testIdPrefix + 2)).hasValue(tests.get(2));
    assertThat(job.removeTest(testIdPrefix + 2)).isEmpty();
    assertThat(job.getTests()).hasSize(2);
  }
}
