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

package com.google.devtools.mobileharness.platform.android.device;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidDeviceHelper}. */
@RunWith(JUnit4.class)
public final class AndroidDeviceHelperTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private BaseDevice device;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;

  // For test purposes DEVICE_ID contains both upper case and lower case letters.
  private static final String DEVICE_ID = "363005DC750400ec";
  private static final int SDK_VERSION = 19;
  private static final String RELEASE_VERSION = "6.0.1";
  private static final String ABI = "x86";
  private static final int SCREEN_DENSITY = 240;

  private AndroidDeviceHelper androidDeviceHelper;

  @Before
  public void setUp() throws Exception {
    when(device.getDeviceId()).thenReturn(DEVICE_ID);

    androidDeviceHelper = new AndroidDeviceHelper(androidAdbUtil, androidSystemSettingUtil);
  }

  @Test
  public void updateAndroidPropertyDimensions_skipBlocklistProperties() throws Exception {
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    for (AndroidProperty property : AndroidDeviceHelper.PROP_NOT_SET_AS_DIMENSION) {
      verify(device, never()).getDimension(Ascii.toLowerCase(property.name()));
    }
  }

  @Test
  public void updateAndroidPropertyDimensions_retainPropertyOriginalValue() throws Exception {
    String deviceBuild = "RD1A.200810.022.A4";
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);
    when(device.getDimension(Ascii.toLowerCase(AndroidProperty.SERIAL.name())))
        .thenReturn(ImmutableList.of());
    when(device.getDimension(Ascii.toLowerCase(AndroidProperty.BUILD.name())))
        .thenReturn(ImmutableList.of());
    when(androidAdbUtil.getProperty(DEVICE_ID, AndroidProperty.SERIAL)).thenReturn(DEVICE_ID);
    when(androidAdbUtil.getProperty(DEVICE_ID, AndroidProperty.BUILD)).thenReturn(deviceBuild);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    verify(device)
        .updateDimension(
            Ascii.toLowerCase(AndroidProperty.SERIAL.name()),
            new String[] {DEVICE_ID, Ascii.toLowerCase(DEVICE_ID)});
    verify(device)
        .updateDimension(
            Ascii.toLowerCase(AndroidProperty.BUILD.name()),
            new String[] {deviceBuild, Ascii.toLowerCase(deviceBuild)});
  }

  @Test
  public void updateAndroidPropertyDimensions_shortReleaseVersion() throws Exception {
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    verify(device)
        .updateDimension(
            Ascii.toLowerCase(AndroidProperty.RELEASE_VERSION.name()),
            new String[] {RELEASE_VERSION});
    verify(device).updateDimension(Dimension.Name.RELEASE_VERSION_MAJOR, new String[] {"6.0"});
  }

  @Test
  public void updateAndroidPropertyDimensions_setAbiToProperty() throws Exception {
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    verify(device).setProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_ABI, ABI);
  }

  @Test
  public void updateAndroidPropertyDimensions_setScreenDensityToProperty() throws Exception {
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    verify(device)
        .setProperty(
            AndroidDeviceHelper.PROPERTY_NAME_CACHED_SCREEN_DENSITY,
            String.valueOf(SCREEN_DENSITY));
  }

  @Test
  public void updateAndroidPropertyDimensions_setSdkVersionToProperty() throws Exception {
    mockCommonSetupSteps(String.valueOf(SDK_VERSION), RELEASE_VERSION);

    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    verify(device)
        .setProperty(
            AndroidDeviceHelper.PROPERTY_NAME_CACHED_SDK_VERSION, String.valueOf(SDK_VERSION));
  }

  private void mockCommonSetupSteps(String sdkVersion, String releaseVersion) throws Exception {
    for (AndroidProperty key : AndroidProperty.values()) {
      switch (key) {
        case SDK_VERSION:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn(sdkVersion);
          break;
        case SCREEN_DENSITY:
          when(androidAdbUtil.getProperty(DEVICE_ID, key))
              .thenReturn(String.valueOf(SCREEN_DENSITY));
          break;
        case ABI:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn(ABI);
          break;
        case MODEL:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn("nexus 5");
          break;
        case RELEASE_VERSION:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn(releaseVersion);
          break;
        case SERIAL:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn(DEVICE_ID);
          break;
        default:
          when(androidAdbUtil.getProperty(DEVICE_ID, key)).thenReturn(key.name());
      }
      when(device.getDimension(Ascii.toLowerCase(key.name())))
          .thenReturn(ImmutableList.of(key.name()));
    }
  }
}
