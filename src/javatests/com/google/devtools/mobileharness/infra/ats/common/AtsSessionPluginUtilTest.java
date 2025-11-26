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
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
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
  public void copyTestPropertiesForDynamicDownloadJobs() {
    TestInfo currentTest = mockCurrentTest();
    TestInfo nextTest = mockNextTest();

    AtsSessionPluginUtil.copyTestPropertiesForDynamicDownloadJobs(currentTest, nextTest);

    Properties nextTestProperties = nextTest.properties();
    assertThat(nextTestProperties.get(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY))
        .isEqualTo(AOSP_VERSION);
    assertThat(nextTestProperties.get(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY))
        .isEqualTo(TVP_VERSION);
    assertThat(nextTestProperties.get(XtsConstants.DEVICE_ABI_PROPERTY_KEY)).isEqualTo(ABI);
    assertThat(nextTestProperties.get(XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY))
        .isEqualTo(MCTS_MODULES_INFO);
  }

  private TestInfo mockCurrentTest() {
    TestInfo currentTest = mock(TestInfo.class);
    Properties currentTestProperties = new Properties(new Timing());
    currentTestProperties.add(XtsConstants.DEVICE_AOSP_VERSION_PROPERTY_KEY, AOSP_VERSION);
    currentTestProperties.add(XtsConstants.DEVICE_TVP_VERSION_PROPERTY_KEY, TVP_VERSION);
    currentTestProperties.add(XtsConstants.DEVICE_ABI_PROPERTY_KEY, ABI);
    currentTestProperties.add(
        XtsConstants.DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY, MCTS_MODULES_INFO);
    when(currentTest.properties()).thenReturn(currentTestProperties);
    return currentTest;
  }

  private TestInfo mockNextTest() {
    TestInfo nextTest = mock(TestInfo.class);
    Properties nextTestProperties = new Properties(new Timing());
    when(nextTest.properties()).thenReturn(nextTestProperties);
    return nextTest;
  }
}
