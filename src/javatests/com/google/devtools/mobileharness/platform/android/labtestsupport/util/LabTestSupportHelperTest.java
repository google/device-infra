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

package com.google.devtools.mobileharness.platform.android.labtestsupport.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LabTestSupportHelperTest {

  private static final String DEVICE_ID = "device-test-00000";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;

  private LabTestSupportHelper labTestSupportHelper;

  @Before
  public void setUp() {
    labTestSupportHelper = new LabTestSupportHelper(androidAdbUtil, androidInstrumentationUtil);
  }

  @Test
  public void disableSmartLockForPasswordsAndFastPair_success() throws Exception {
    when(androidInstrumentationUtil.instrument(any(), anyInt(), any(), any()))
        .thenReturn("success=true");

    assertThat(
            labTestSupportHelper.disableSmartLockForPasswordsAndFastPair(
                DEVICE_ID, /* deviceSdkVersion= */ 34))
        .isTrue();

    verify(androidAdbUtil).setProperty(DEVICE_ID, LabTestSupportHelper.ALLOW_LTS_PROP_NAME, "true");
    verify(androidInstrumentationUtil)
        .instrument(
            DEVICE_ID,
            /* deviceSdkVersion= */ 34,
            AndroidInstrumentationSetting.create(
                LabTestSupportHelper.LAB_TEST_SUPPORT_PACKAGE,
                LabTestSupportHelper.LTS_CONFIG_PHENOTYPE_FLAGS_INSTRUMENTATION_RUNNER_NAME,
                /* className= */ null,
                /* otherOptions= */ null,
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
  }

  @Test
  public void disableSmartLockForPasswordsAndFastPair_instrumentationFailed_throwsException()
      throws Exception {
    when(androidInstrumentationUtil.instrument(any(), anyInt(), any(), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_FAILED,
                "instrumentation failed"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        labTestSupportHelper.disableSmartLockForPasswordsAndFastPair(
                            DEVICE_ID, /* deviceSdkVersion= */ 34))
                .getErrorId())
        .isEqualTo(
            AndroidErrorId.LAB_TEST_SUPPORT_DISABLE_SMART_LOCK_FOR_PASSWORDS_AND_FAST_PAIR_ERROR);
  }
}
