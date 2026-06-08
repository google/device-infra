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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link LocalDeviceUtilImpl}. */
@RunWith(JUnit4.class)
public final class LocalDeviceUtilImplTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private DeviceDetailsRetriever deviceDetailsRetriever;
  @Bind @Mock private AndroidAdbUtil androidAdbUtil;

  @Inject private LocalDeviceUtilImpl localDeviceUtil;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void getLocalAvailableDevices_noAvailableDevices_throwsException() throws Exception {
    when(deviceDetailsRetriever.getAllLocalAndroidDevicesWithNeededDetails(any()))
        .thenReturn(ImmutableMap.of());

    SessionRequestInfo sessionRequestInfo = defaultSessionRequestInfoBuilder().build();

    assertThrows(
        MobileHarnessException.class,
        () -> localDeviceUtil.getLocalAvailableDevices(sessionRequestInfo));
  }

  @Test
  public void getLocalAvailableDevices_hasAvailableDevices() throws Exception {
    DeviceDetails deviceDetails = DeviceDetails.builder().setId("device1").build();
    when(deviceDetailsRetriever.getAllLocalAndroidDevicesWithNeededDetails(any()))
        .thenReturn(ImmutableMap.of("device1", deviceDetails));

    SessionRequestInfo sessionRequestInfo =
        defaultSessionRequestInfoBuilder().setDeviceSerials(ImmutableList.of("device1")).build();

    assertThat(localDeviceUtil.getLocalAvailableDevices(sessionRequestInfo))
        .containsExactly(deviceDetails);
  }

  @Test
  public void getDeviceInfoFromLocal_noLocalDevices_returnsEmpty() throws Exception {
    when(deviceDetailsRetriever.getAllLocalAndroidDevicesWithNeededDetails(any()))
        .thenReturn(ImmutableMap.of());

    SessionRequestInfo sessionRequestInfo = defaultSessionRequestInfoBuilder().build();

    assertThat(localDeviceUtil.getDeviceInfoFromLocal(sessionRequestInfo)).isEmpty();
  }

  @Test
  public void getDeviceInfoFromLocal_returnsDeviceInfo() throws Exception {
    DeviceDetails deviceDetails = DeviceDetails.builder().setId("device1").build();
    when(deviceDetailsRetriever.getAllLocalAndroidDevicesWithNeededDetails(any()))
        .thenReturn(ImmutableMap.of("device1", deviceDetails));
    when(androidAdbUtil.getProperty("device1", AndroidProperty.ABILIST)).thenReturn("arm64-v8a");
    when(androidAdbUtil.getProperty("device1", AndroidProperty.ABI)).thenReturn("arm64-v8a");

    SessionRequestInfo sessionRequestInfo = defaultSessionRequestInfoBuilder().build();

    Optional<DeviceInfo> deviceInfo = localDeviceUtil.getDeviceInfoFromLocal(sessionRequestInfo);
    assertThat(deviceInfo).isPresent();
    assertThat(deviceInfo.get().getDeviceId()).isEqualTo("device1");
    assertThat(deviceInfo.get().hasSupportedAbiList()).isTrue();
    assertThat(deviceInfo.get().getSupportedAbiList()).isEqualTo("arm64-v8a");
    assertThat(deviceInfo.get().hasSupportedAbi()).isTrue();
    assertThat(deviceInfo.get().getSupportedAbi()).isEqualTo("arm64-v8a");
  }

  private SessionRequestInfo.Builder defaultSessionRequestInfoBuilder() {
    return SessionRequestInfo.builder()
        .setTestPlan("cts")
        .setCommandLineArgs("cts")
        .setXtsType("cts")
        .setXtsRootDir("/path");
  }
}
