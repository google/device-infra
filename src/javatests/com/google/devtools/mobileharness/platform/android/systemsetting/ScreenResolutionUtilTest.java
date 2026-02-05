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

package com.google.devtools.mobileharness.platform.android.systemsetting;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.wireless.qa.mobileharness.shared.util.ScreenResolution;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ScreenResolutionUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AndroidAdbUtil adbUtil;

  private static final String DEVICE_ID = "device_id";

  private ScreenResolutionUtil screenResolutionUtil;

  @Before
  public void setUp() {
    screenResolutionUtil = new ScreenResolutionUtil(adbUtil);
  }

  @Test
  public void getScreenResolution_resolutionPattern_singleDisplayId() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn(
            """
              Display: mDisplayId=0
                init=1080x1920 420dpi cur=540x960 app=1080x1794
            """);

    assertThat(screenResolutionUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.createWithOverride(1080, 1920, 540, 960));
  }

  @Test
  public void getScreenResolution_resolutionPattern_displayIdZeroNotFound_fallbackToAnyDisplay()
      throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn(
            """
              Display:
                init=1080x1920 420dpi cur=540x960 app=1080x1794
            """);

    assertThat(screenResolutionUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.createWithOverride(1080, 1920, 540, 960));
  }

  @Test
  public void getScreenResolution_resolutionPattern_multipleDisplayIds_displayIdZeroLast()
      throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn(
            """
              Display: mDisplayId=8
                init=704x584 1dpi cur=704x584
              Display: mDisplayId=0 (organized)
                init=2208x1840 420dpi cur=2208x1840
            """);

    assertThat(screenResolutionUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.createWithOverride(2208, 1840, 2208, 1840));
  }

  @Test
  public void getScreenResolution_resolutionPattern_multipleDisplayIds_displayIdZeroFirst()
      throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn(
            """
              Display: mDisplayId=0 (organized)
                init=2208x1840 420dpi cur=2208x1840
              Display: mDisplayId=8
                init=704x584 1dpi cur=704x584
            """);

    assertThat(screenResolutionUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.createWithOverride(2208, 1840, 2208, 1840));
  }

  @Test
  public void getScreenResolution_displayPattern() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn("xyz DisplayWidth=123 xyz DisplayHeight=321 xyz");

    assertThat(screenResolutionUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.create(123, 321));
  }

  @Test
  public void getScreenResolution_invalidWindowInfo_throwsException() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW)).thenReturn("xyz xyz");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> screenResolutionUtil.getScreenResolution(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_RESOLUTION_ERROR);
  }
}
