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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsDeviceRecoveryPluginTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private AndroidSystemStateUtil androidUtil;
  @Captor private ArgumentCaptor<Duration> durationCaptor;
  @Mock private LocalTestEndingEvent testEndingEvent;
  @Mock private LocalTestStartingEvent testStartingEvent;
  @Mock private Device device;

  private static final String DEVICE_ID = "123456";
  private AtsDeviceRecoveryPlugin plugin;

  @Before
  public void setUp() {
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(device.getDeviceTypes()).thenReturn(ImmutableSet.of("AndroidRealDevice"));
    when(testEndingEvent.getLocalDevices()).thenReturn(ImmutableMap.of(DEVICE_ID, device));
    when(testStartingEvent.getLocalDevices()).thenReturn(ImmutableMap.of(DEVICE_ID, device));
    plugin = new AtsDeviceRecoveryPlugin(adb, androidUtil);
  }

  @Test
  public void onTestStarting_deviceOnline_unlocked() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled")).thenReturn("true");

    plugin.onTestStarting(testStartingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings get-disabled");
    verify(adb, never()).runShell(DEVICE_ID, "locksettings clear --old 0000");
  }

  @Test
  public void onTestEnding_deviceOnline_unlocked() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled")).thenReturn("true");

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings get-disabled");
    verify(adb, never()).runShell(DEVICE_ID, "locksettings clear --old 0000");
  }

  @Test
  public void onTestEnding_deviceOnline_locked_unlock0000Success() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled"))
        .thenReturn("false")
        .thenReturn("true");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 0000"))
        .thenReturn("Lock credential cleared");

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 0000");
  }

  @Test
  public void onTestEnding_deviceOnline_locked_unlock1234Success() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled"))
        .thenReturn("false")
        .thenReturn("true");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 0000")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 1234"))
        .thenReturn("Lock credential cleared");

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 0000");
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 1234");
  }

  @Test
  public void onTestEnding_deviceOnline_locked_unlockFails() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled")).thenReturn("false");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 0000")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 1234")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old 12345")).thenReturn("");
    when(adb.runShell(DEVICE_ID, "locksettings clear --old private")).thenReturn("");

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 0000");
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 1234");
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old 12345");
    verify(adb).runShell(DEVICE_ID, "locksettings clear --old private");
  }

  @Test
  public void onTestEnding_waitUntilReadyFails_deviceNotOnline() throws Exception {
    doThrow(MobileHarnessException.class).when(androidUtil).waitUntilReady(eq(DEVICE_ID), any());
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(false);

    plugin.onTestEnding(testEndingEvent);

    verify(adb, never()).runShell(anyString(), anyString());
  }

  @Test
  public void onTestEnding_waitUntilReadyFails_deviceOnline_unlocked() throws Exception {
    doThrow(MobileHarnessException.class).when(androidUtil).waitUntilReady(eq(DEVICE_ID), any());
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled")).thenReturn("true");

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    assertThat(durationCaptor.getValue())
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    verify(adb).runShell(DEVICE_ID, "locksettings get-disabled");
    verify(adb, never()).runShell(DEVICE_ID, "locksettings clear --old 0000");
  }

  @Test
  public void onTestEnding_multipleDevices_verifyTimeout() throws Exception {
    when(androidUtil.isOnline(DEVICE_ID)).thenReturn(true);
    when(adb.runShell(DEVICE_ID, "locksettings get-disabled")).thenReturn("true");
    String deviceId2 = "654321";
    Device device2 = mock(Device.class);
    when(device2.getDeviceId()).thenReturn(deviceId2);
    when(device2.getDeviceTypes()).thenReturn(ImmutableSet.of("AndroidRealDevice"));
    when(androidUtil.isOnline(deviceId2)).thenReturn(true);
    when(adb.runShell(deviceId2, "locksettings get-disabled")).thenReturn("true");
    when(testEndingEvent.getLocalDevices())
        .thenReturn(ImmutableMap.of(DEVICE_ID, device, deviceId2, device2));

    plugin.onTestEnding(testEndingEvent);

    verify(androidUtil).waitUntilReady(eq(DEVICE_ID), durationCaptor.capture());
    verify(androidUtil).waitUntilReady(eq(deviceId2), durationCaptor.capture());
    List<Duration> capturedDurations = durationCaptor.getAllValues();
    assertThat(capturedDurations).hasSize(2);
    assertThat(capturedDurations.get(0))
        .isAtMost(Flags.instance().atsDeviceRecoveryTimeout.getNonNull());
    assertThat(capturedDurations.get(1)).isAtMost(capturedDurations.get(0));
  }
}
