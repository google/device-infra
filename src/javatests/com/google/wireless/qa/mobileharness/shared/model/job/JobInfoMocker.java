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

package com.google.wireless.qa.mobileharness.shared.model.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;

/** Utility for mocking {@link JobInfo}. */
public final class JobInfoMocker {

  public static final String FAKE_JOB_ID = "fake-job-id";
  public static final String FAKE_JOB_NAME = "fake-job-name";
  public static final String FAKE_JOB_DRIVER = "NoOpDriver";
  public static final String FAKE_JOB_DEVICE = "NoOpDevice";

  /**
   * Creates a mocked {@link JobInfo}, which calls real methods and can be stubbed.
   *
   * <p>In detail, this method is equivalent to "{@code spy(<a real JobInfo with default settings
   * and a mocked LocalFileUtil>)}".
   */
  public static JobInfo mockJobInfo() {
    return spy(
        JobInfo.newBuilder()
            .setLocator(new JobLocator(FAKE_JOB_ID, FAKE_JOB_NAME))
            .setType(
                JobType.newBuilder().setDriver(FAKE_JOB_DRIVER).setDevice(FAKE_JOB_DEVICE).build())
            .setFileUtil(mock(LocalFileUtil.class))
            .build());
  }

  private JobInfoMocker() {}
}
