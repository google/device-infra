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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSetWifiDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidSetWifiDecoratorJobValidator}. */
@RunWith(JUnit4.class)
public class AndroidSetWifiDecoratorJobValidatorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private JobInfo jobInfo;

  private AndroidSetWifiDecoratorJobValidator validator;

  @Before
  public void setUp() {
    validator = new AndroidSetWifiDecoratorJobValidator();
  }

  @Test
  public void testvalidateJob_useDefaultSsid_pass() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setUseDefaultSsid(true).build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testvalidateJob_useDefaultSsidAndCustomSsid_fail() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setUseDefaultSsid(true)
            .setWifiSsid("ssid")
            .build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo))
        .containsExactly("Please leave wifi_ssid empty when use_default_ssid is true.");
  }

  @Test
  public void testvalidateJob_useCustomSsid_pass() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setWifiSsid("ssid").setWifiPsk("psk").build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testvalidateJob_notSpecifySsid_fail() throws Exception {
    AndroidSetWifiDecoratorSpec spec = AndroidSetWifiDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo))
        .containsExactly("Param \"wifi_ssid\" is not found or empty.");
  }

  @Test
  public void testvalidateJob_specifyRetryNum_pass() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setWifiRetryNum(3)
            .build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testvalidateJob_specifyInvalidRetryNum_fail() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setWifiRetryNum(10)
            .build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo))
        .containsExactly("Please set wifi_retry_num within [0, 5].");
  }

  @Test
  public void testvalidateJob_wifiSsidOptionalTrue_missingSsid_pass() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setWifiSsidOptional(true).build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }
}
