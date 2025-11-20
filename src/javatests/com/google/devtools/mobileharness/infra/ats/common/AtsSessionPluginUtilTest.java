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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AtsSessionPluginUtilTest {

  private static final String AOSP_VERSION = "aosp_version";
  private static final String TVP_VERSION = "tvp_version";
  private static final String ABI = "abi";
  private static final String MCTS_MODULES_INFO = "mcts_modules_info";

  @Test
  public void copyJobPropertiesForDynamicDownloadJobs_dynamicDownloadEnabled() {
    JobInfo currentJob = mockCurrentJob();
    JobInfo nextJob = mockNextJob(/* isDynamicDownloadEnabled= */ true);

    AtsSessionPluginUtil.copyJobPropertiesForDynamicDownloadJobs(currentJob, nextJob);

    Properties nextJobProperties = nextJob.properties();
    assertThat(nextJobProperties.get(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY))
        .isEqualTo(AOSP_VERSION);
    assertThat(nextJobProperties.get(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY))
        .isEqualTo(TVP_VERSION);
    assertThat(nextJobProperties.get(XtsConstants.DEVICE_ABI_PROPERTY_KEY)).isEqualTo(ABI);
    assertThat(nextJobProperties.get(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY))
        .isEqualTo(MCTS_MODULES_INFO);
  }

  @Test
  public void copyJobPropertiesForDynamicDownloadJobs_dynamicDownloadDisabled() {
    JobInfo currentJob = mockCurrentJob();
    JobInfo nextJob = mockNextJob(/* isDynamicDownloadEnabled= */ false);

    AtsSessionPluginUtil.copyJobPropertiesForDynamicDownloadJobs(currentJob, nextJob);

    Properties nextJobProperties = nextJob.properties();
    assertThat(nextJobProperties.has(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY)).isFalse();
    assertThat(nextJobProperties.has(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY)).isFalse();
    assertThat(nextJobProperties.has(XtsConstants.DEVICE_ABI_PROPERTY_KEY)).isFalse();
    assertThat(nextJobProperties.has(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY)).isFalse();
  }

  private JobInfo mockCurrentJob() {
    JobInfo currentJob = mock(JobInfo.class);
    Properties currentJobProperties = new Properties(new Timing());
    currentJobProperties.add(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY, AOSP_VERSION);
    currentJobProperties.add(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY, TVP_VERSION);
    currentJobProperties.add(XtsConstants.DEVICE_ABI_PROPERTY_KEY, ABI);
    currentJobProperties.add(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY, MCTS_MODULES_INFO);
    when(currentJob.properties()).thenReturn(currentJobProperties);
    return currentJob;
  }

  private JobInfo mockNextJob(boolean isDynamicDownloadEnabled) {
    JobInfo nextJob = mock(JobInfo.class);
    Properties nextJobProperties = new Properties(new Timing());
    nextJobProperties.add(
        XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED, String.valueOf(isDynamicDownloadEnabled));
    when(nextJob.properties()).thenReturn(nextJobProperties);
    return nextJob;
  }
}
