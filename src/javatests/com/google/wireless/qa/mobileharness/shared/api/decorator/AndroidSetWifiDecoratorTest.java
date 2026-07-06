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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.platform.android.lightning.networkconnector.NetworkConnector;
import com.google.devtools.mobileharness.platform.android.lightning.networkconnector.WifiConnectArgs;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSetWifiDecoratorSpec;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidSetWifiDecorator}. */
@RunWith(JUnit4.class)
public class AndroidSetWifiDecoratorTest {
  private static final Log log = new Log(new Timing());
  private static final String DEVICE_ID = "123456";
  private static final String WIFI_SSID = "guest";
  private static final String WIFI_PSK = "psk";
  private static final int WIFI_RETRY_NUM = 2;
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private NetworkConnector networkConnector;
  @Mock private Driver decoratedDriver;
  @Mock private AndroidRealDevice device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Result result;
  @Mock private Warnings warnings;

  private AndroidSetWifiDecorator decorator;

  @Before
  public void setUp() throws MobileHarnessException, InterruptedException {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(testInfo.warnings()).thenReturn(warnings);

    decorator = new AndroidSetWifiDecorator(decoratedDriver, testInfo, networkConnector);
  }

  @Test
  public void testSetWifi_specifySsidAndPsk() throws MobileHarnessException, InterruptedException {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid(WIFI_SSID)
            .setWifiPsk(WIFI_PSK)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(networkConnector)
        .connectToWifi(
            device,
            WifiConnectArgs.builder()
                .setWifiSsid(WIFI_SSID)
                .setWifiPsk(WIFI_PSK)
                .setScanSsid(false)
                .setWaitTimeout(Duration.ofMinutes(2))
                .setRetryNum(0)
                .build(),
            log);
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void testSetWifi_specifySsidAndScanSsid()
      throws MobileHarnessException, InterruptedException {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid(WIFI_SSID)
            .setWifiPsk(WIFI_PSK)
            .setWifiScanSsid(true)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(networkConnector)
        .connectToWifi(
            device,
            WifiConnectArgs.builder()
                .setWifiSsid(WIFI_SSID)
                .setWifiPsk(WIFI_PSK)
                .setScanSsid(true)
                .setWaitTimeout(Duration.ofMinutes(2))
                .setRetryNum(0)
                .build(),
            log);
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void testSetWifi_notSpecifiSsid_throwException()
      throws MobileHarnessException, InterruptedException {
    AndroidSetWifiDecoratorSpec spec = AndroidSetWifiDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_SSID_NOT_PRESENT_ERROR);
  }

  @Test
  public void testSetWifi_defaultConfig_success()
      throws MobileHarnessException, InterruptedException {
    String ssid = "ssid1";
    String psk = "psk1";
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setUseDefaultSsid(true).build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(device.getProperty(
            Ascii.toLowerCase(PropertyName.Test.AndroidSetWifiDecorator.DEFAULT_WIFI_SSID.name())))
        .thenReturn(ssid);
    when(device.getProperty(
            Ascii.toLowerCase(PropertyName.Test.AndroidSetWifiDecorator.DEFAULT_WIFI_PSK.name())))
        .thenReturn(psk);

    decorator.run(testInfo);

    verify(networkConnector)
        .connectToWifi(
            device,
            WifiConnectArgs.builder()
                .setWifiSsid("ssid1")
                .setWifiPsk("psk1")
                .setScanSsid(false)
                .setWaitTimeout(Duration.ofMinutes(2))
                .setRetryNum(0)
                .build(),
            log);
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void testSetWifi_defaultConfigWithEmptySsid_throwException()
      throws MobileHarnessException, InterruptedException {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setUseDefaultSsid(true).build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_GET_DEFAULT_SSID_ERROR);
  }

  @Test
  public void testSetWifi_specifyRetryNum() throws MobileHarnessException, InterruptedException {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid(WIFI_SSID)
            .setWifiPsk(WIFI_PSK)
            .setWifiRetryNum(WIFI_RETRY_NUM)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(networkConnector)
        .connectToWifi(
            device,
            WifiConnectArgs.builder()
                .setWifiSsid(WIFI_SSID)
                .setWifiPsk(WIFI_PSK)
                .setScanSsid(false)
                .setWaitTimeout(Duration.ofMinutes(2))
                .setRetryNum(WIFI_RETRY_NUM)
                .build(),
            log);
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void testRun_missingCustomSsid_wifiSsidOptional_skipsAndRunsDecorated() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder().setWifiSsidOptional(true).build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verifyNoInteractions(networkConnector);
  }

  @Test
  public void testRun_missingDefaultSsid_wifiSsidOptional_skipsAndRunsDecorated() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setUseDefaultSsid(true)
            .setWifiSsidOptional(true)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(device.getProperty(
            Ascii.toLowerCase(PropertyName.Test.AndroidSetWifiDecorator.DEFAULT_WIFI_SSID.name())))
        .thenReturn(null);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verifyNoInteractions(networkConnector);
  }

  @Test
  public void testRun_providedSsidConnectionFails_wifiSsidOptional_fails() throws Exception {
    AndroidSetWifiDecoratorSpec spec =
        AndroidSetWifiDecoratorSpec.newBuilder()
            .setWifiSsid(WIFI_SSID)
            .setWifiPsk(WIFI_PSK)
            .setWifiSsidOptional(true)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    MobileHarnessException exception =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_SET_WIFI_DECORATOR_SSID_NOT_PRESENT_ERROR, "failed to connect");
    doThrow(exception)
        .when(networkConnector)
        .connectToWifi(eq(device), any(WifiConnectArgs.class), eq(log));

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
  }
}
