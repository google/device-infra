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

package com.google.devtools.mobileharness.platform.android.lightning.networkconnector;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.connectivity.ConnectToWifiArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.wireless.qa.mobileharness.shared.android.WifiUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.testing.FakeLogCollector;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link NetworkConnector}. */
@RunWith(JUnit4.class)
public final class NetworkConnectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Rule public final SetFlags flags = new SetFlags();

  private static final String DEVICE_ID = "363005dc750400ec";
  private static final String PATH_TO_WIFI_UTIL_APK = "/path/to/wifiutil.apk";
  private static final int DEFAULT_SDK_VERSION = 28;

  private NetworkConnector networkConnector;
  private FakeLogCollector testLog;

  @Mock private Device device;
  @Mock private WifiUtil wifiUtil;
  @Mock private AndroidConnectivityUtil connectivityUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidSystemSettingUtil systemSettingUtil;

  @Before
  public void setUp() throws Exception {
    testLog = new FakeLogCollector();
    networkConnector =
        new NetworkConnector(wifiUtil, connectivityUtil, apkInstaller, systemSettingUtil);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(DEFAULT_SDK_VERSION);
  }

  @Test
  public void connectToWifi() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog)).thenReturn(true);

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(false)
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .build(),
                testLog))
        .isTrue();

    InOrder order = inOrder(connectivityUtil, wifiUtil, apkInstaller);
    order.verify(connectivityUtil).getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION);
    order.verify(wifiUtil).getWifiUtilApkPath();
    order
        .verify(apkInstaller)
        .installApkIfNotExist(
            device,
            ApkInstallArgs.builder()
                .setApkPath(PATH_TO_WIFI_UTIL_APK)
                .setGrantPermissions(true)
                .build(),
            testLog);
    order.verify(connectivityUtil).connectToWifi(wifiArgs, testLog);
  }

  @Test
  public void connectToWifi_shouldNotManageDevices() throws Exception {
    flags.set("should_manage_devices", "false");

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder().setWifiSsid("ssid").setWifiPsk("psk").build(),
                testLog))
        .isFalse();
  }

  @Test
  public void connectToWifi_failedToGetNetworkSsid_logMessage() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_DUMPSYS_ERROR, "Error"));
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog)).thenReturn(true);

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .build(),
                testLog))
        .isTrue();

    verify(wifiUtil).getWifiUtilApkPath();
    verify(apkInstaller)
        .installApkIfNotExist(
            device,
            ApkInstallArgs.builder()
                .setApkPath(PATH_TO_WIFI_UTIL_APK)
                .setGrantPermissions(true)
                .build(),
            testLog);
    verify(connectivityUtil).connectToWifi(wifiArgs, testLog);
  }

  @Test
  public void connectToWifi_currentSsidIsEqualsToGivenSsid_skip() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION))
        .thenReturn("connected-ssid");

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder().setWifiSsid("connected-ssid").setWifiPsk("psk").build(),
                testLog))
        .isTrue();

    verify(wifiUtil, never()).getWifiUtilApkPath();
    verify(apkInstaller, never()).installApkIfNotExist(any(), any(), any());
    verify(connectivityUtil, never()).connectToWifi(any(), any());
  }

  @Test
  public void connectToWifi_failedToGetWifiUtilApkPath_throwException() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath())
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_WIFI_UTIL_APK_NOT_FOUND, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        networkConnector.connectToWifi(
                            device,
                            WifiConnectArgs.builder()
                                .setWifiSsid("ssid")
                                .setWifiPsk("psk")
                                .setLogFailuresOnly(false)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.NETWORK_CONNECTOR_INSTALL_WIFIUTIL_ERROR);

    verify(apkInstaller, never()).installApkIfNotExist(any(), any(), any());
    verify(connectivityUtil, never()).connectToWifi(any(), any());
  }

  @Test
  public void connectToWifi_failedToGetWifiUtilApkPath_logFailuresOnly() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath())
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_WIFI_UTIL_APK_NOT_FOUND, "Error"));

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(true)
                    .build(),
                testLog))
        .isFalse();

    verify(apkInstaller, never()).installApkIfNotExist(any(), any(), any());
    verify(connectivityUtil, never()).connectToWifi(any(), any());
  }

  @Test
  public void connectToWifi_failedToConnectWifi_logFailuresOnly() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_CONNECTIVITY_FAIL_CONNECT_TO_WIFI, "Error"))
        .thenReturn(false);

    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(true)
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .build(),
                testLog))
        .isFalse();
    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(true)
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .build(),
                testLog))
        .isFalse();
  }

  @Test
  public void connectToWifi_failedToConnectWifi_throwException() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_CONNECTIVITY_FAIL_CONNECT_TO_WIFI, "Error"))
        .thenReturn(false);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        networkConnector.connectToWifi(
                            device,
                            WifiConnectArgs.builder()
                                .setWifiSsid("ssid")
                                .setWifiPsk("psk")
                                .setLogFailuresOnly(false)
                                .setWaitTimeout(Duration.ofMinutes(1))
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.NETWORK_CONNECTOR_CONNECT_TO_WIFI_ERROR);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        networkConnector.connectToWifi(
                            device,
                            WifiConnectArgs.builder()
                                .setWifiSsid("ssid")
                                .setWifiPsk("psk")
                                .setLogFailuresOnly(false)
                                .setWaitTimeout(Duration.ofMinutes(1))
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.NETWORK_CONNECTOR_CONNECT_TO_WIFI_FAILURE);
  }

  @Test
  public void connectToWifi_failedToConnectWifi_retryTwoTimes() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_CONNECTIVITY_FAIL_CONNECT_TO_WIFI, "Error"))
        .thenReturn(false);
    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(true)
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .setRetryNum(2)
                    .build(),
                testLog))
        .isFalse();
    InOrder order = inOrder(connectivityUtil);
    order.verify(connectivityUtil, times(2)).connectToWifi(wifiArgs, testLog);
    order
        .verify(connectivityUtil, times(1))
        .connectToWifi(wifiArgs.toBuilder().setForceTryConnect(true).build(), testLog);
  }

  @Test
  public void connectToWifi_successAtFirstAttempt_retryIsNonZero() throws Exception {
    when(connectivityUtil.getNetworkSsid(DEVICE_ID, DEFAULT_SDK_VERSION)).thenReturn("");
    when(wifiUtil.getWifiUtilApkPath()).thenReturn(PATH_TO_WIFI_UTIL_APK);
    ConnectToWifiArgs wifiArgs =
        ConnectToWifiArgs.builder()
            .setSerial(DEVICE_ID)
            .setSdkVersion(DEFAULT_SDK_VERSION)
            .setWifiSsid("ssid")
            .setWifiPsk("psk")
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofMinutes(1))
            .build();
    when(connectivityUtil.connectToWifi(wifiArgs, testLog)).thenReturn(true);
    assertThat(
            networkConnector.connectToWifi(
                device,
                WifiConnectArgs.builder()
                    .setWifiSsid("ssid")
                    .setWifiPsk("psk")
                    .setLogFailuresOnly(true)
                    .setRetryNum(2)
                    .setWaitTimeout(Duration.ofMinutes(1))
                    .build(),
                testLog))
        .isTrue();
    InOrder order = inOrder(connectivityUtil);
    order.verify(connectivityUtil, times(1)).connectToWifi(wifiArgs, testLog);
  }
}
