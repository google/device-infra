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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.controller.device.DeviceManagementFilter;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceDetailsRetrieverTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidAdbInternalUtil androidAdbInternalUtil;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private DeviceManagementFilter deviceManagementFilter;

  private DeviceDetailsRetriever retriever;

  @Before
  public void setUp() {
    retriever =
        new DeviceDetailsRetriever(
            () -> androidAdbInternalUtil,
            () -> androidAdbUtil,
            () -> androidSystemSettingUtil,
            deviceManagementFilter);
  }

  @Test
  public void getAllLocalAndroidDevicesWithNeededDetails_filteringBlockedDevices()
      throws Exception {
    // Mock adb internal util to return two serials: "allowed_device" and "blocked_device"
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE, null))
        .thenReturn(ImmutableSet.of("allowed_device", "blocked_device"));

    // Mock DeviceManagementFilter allowed device setting
    when(deviceManagementFilter.isAllowed("allowed_device")).thenReturn(true);
    when(deviceManagementFilter.isAllowed("blocked_device")).thenReturn(false);

    SessionRequestInfo info =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("run cts")
            .setXtsRootDir("/path/to/xts")
            .setXtsType("cts")
            .build();
    ImmutableMap<String, DeviceDetails> result =
        retriever.getAllLocalAndroidDevicesWithNeededDetails(info);

    // Assert that only "allowed_device" is returned in the output
    assertThat(result.keySet()).containsExactly("allowed_device");
  }
}
