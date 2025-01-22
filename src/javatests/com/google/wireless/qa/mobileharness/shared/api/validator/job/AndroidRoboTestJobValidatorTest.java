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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import static com.google.common.truth.Truth.assertThat;

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec.ControllerEndpoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidRoboTestJobValidatorTest {

  private AndroidRoboTestJobValidator validator;

  @Before
  public void setUp() throws Exception {
    validator = new AndroidRoboTestJobValidator();
  }

  @Test
  public void validate_hasNoErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setAppPackageId("com.some.app")
            .setCrawlTimeoutSecs(60)
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_noAppApk_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_noAppPackageId_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_noControllerEndpoint_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setAppPackageId("com.some.app")
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_invalidTimeoutValue_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(0)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_invalidCrawlerFlag_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .addCrawlerFlags("flag1=value1")
            .addCrawlerFlags("invalidFlag")
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  @Test
  public void validate_invalidCrawlerAsset_hasErrorMessages() throws Exception {
    JobInfo jobInfo = setUpJobInfo();
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setAppApk("appPath")
            .setCrawlerApk("crawlerPath")
            .setCrawlerStubApk("stubPath")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .addCrawlerAssets("asset1=some_asset")
            .addCrawlerAssets("invalidAssetValue")
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);

    assertThat(validator.validate(jobInfo)).isNotEmpty();
  }

  private JobInfo setUpJobInfo() {
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(
            Job.JobType.newBuilder().setDevice("device_type").setDriver("AndroidRoboTest").build())
        .build();
  }
}
