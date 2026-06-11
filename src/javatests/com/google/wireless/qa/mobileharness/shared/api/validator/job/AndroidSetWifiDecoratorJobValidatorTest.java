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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidSetWifiDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
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
  @Mock private Params params;
  @Mock private JobInfo jobInfo;
  private final AndroidSetWifiDecoratorJobValidator validator =
      new AndroidSetWifiDecoratorJobValidator();

  @Before
  public void setUp() throws Exception {
    when(jobInfo.params()).thenReturn(params);
  }

  @Test
  public void testvalidateJob_useDefaultSsid_pass() throws Exception {
    when(params.get(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn("true");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testvalidateJob_useDefaultSsidAndCustomSsid_fail() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(true);
    when(params.get(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID)).thenReturn("ssid");

    assertThat(validator.validate(jobInfo))
        .containsExactly("Please leave wifi_ssid empty when use_default_ssid is true.");
  }

  @Test
  public void testvalidateJob_useCustomSsid_pass() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(false);
    when(params.get(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID)).thenReturn("ssid");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testvalidateJob_missingCustomSsid_fail() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(false);
    doThrow(new MobileHarnessException(BasicErrorId.JOB_PARAM_VALUE_NOT_FOUND, "ssid is empty"))
        .when(params)
        .checkExist(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID);

    assertThat(validator.validate(jobInfo)).hasSize(1);
  }

  @Test
  public void testvalidateJob_tooLargeRetryNum_fail() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(true);
    when(params.has(AndroidSetWifiDecoratorSpec.PARAM_WIFI_RETRY_NUM)).thenReturn(true);
    doThrow(new MobileHarnessException(BasicErrorId.JOB_PARAM_VALUE_NOT_FOUND, "ssid is empty"))
        .when(params)
        .checkInt(AndroidSetWifiDecoratorSpec.PARAM_WIFI_RETRY_NUM, 0, 5);

    assertThat(Iterables.getOnlyElement(validator.validate(jobInfo)))
        .contains("Please set wifi_retry_num within [0, 5].");
  }

  @Test
  public void testValidate_missingSsid_wifiSsidOptional_passes() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(false);
    when(params.getBool(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID_OPTIONAL, false))
        .thenReturn(true);

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void testValidate_hasSsid_wifiSsidOptional_passes() throws Exception {
    when(params.isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID)).thenReturn(false);
    when(params.getBool(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID_OPTIONAL, false))
        .thenReturn(true);
    when(params.get(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID)).thenReturn("ssid");

    assertThat(validator.validate(jobInfo)).isEmpty();
  }
}
