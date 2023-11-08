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

package com.google.devtools.deviceaction.framework.devices;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DevicesTest {

  private static final String UUID = "id";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidAdbUtil mockAdbUtil;
  @Mock private AndroidFileUtil mockFileUtil;
  @Mock private AndroidPackageManagerUtil mockPackageManagerUtil;
  @Mock private AndroidSystemSettingUtil mockSystemSettingUtil;
  @Mock private AndroidSystemStateUtil mockSystemStateUtil;
  @Mock private BundletoolUtil mockBundletoolUtil;

  private final Sleeper sleeper = Sleeper.noOpSleeper();

  private Devices devices;

  @Before
  public void setup() {
    devices =
        new Devices(
            mockAdbUtil,
            mockFileUtil,
            mockPackageManagerUtil,
            mockSystemSettingUtil,
            mockSystemStateUtil,
            mockBundletoolUtil,
            sleeper);
  }

  @Test
  public void getDeviceKey_getExpectedResults() throws Exception {
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.BRAND)).thenReturn("google");
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.MODEL)).thenReturn("Pixel 7");
    when(mockAdbUtil.getIntProperty(UUID, AndroidProperty.SDK_VERSION)).thenReturn(31);
    when(mockAdbUtil.getProperty(UUID, AndroidProperty.BUILD_TYPE)).thenReturn("userdebug");

    assertThat(
            devices.getDeviceKey(
                Operand.newBuilder().setUuid(UUID).setDeviceType(DeviceType.ANDROID_PHONE).build()))
        .isEqualTo("google_pixel-7_s_userdebug");
  }
}
