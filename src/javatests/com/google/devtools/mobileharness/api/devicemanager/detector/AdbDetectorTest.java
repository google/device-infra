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

package com.google.devtools.mobileharness.api.devicemanager.detector;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.api.devicemanager.detector.AdbDetector.MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult;
import com.google.devtools.mobileharness.api.devicemanager.detector.model.DetectionResult.DetectionType;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidRealDeviceProxyManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.testbed.TestbedUtil;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedLoader;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link AdbDetector}. */
@RunWith(JUnit4.class)
public class AdbDetectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  private static final String DEVICE_0 = "DEVICE_0";
  private static final String DEVICE_1 = "DEVICE_1";
  private static final String OVER_TCP_ID = "192.168.1.1:9527";

  @Mock private AndroidAdbInternalUtil adbInternalUtil;
  @Mock private ApiConfig apiConfig;
  @Mock private TestbedUtil testbedUtil;
  @Mock private TestbedLoader testbedLoader;
  @Mock private CommandFailureException commandFailureException;
  @Mock private SystemUtil systemUtil;

  private AdbDetector detector;

  @Before
  public void setUp() throws Exception {
    detector = new AdbDetector(adbInternalUtil, apiConfig, testbedLoader, testbedUtil, systemUtil);
  }

  @Test
  public void precondition() throws Exception {
    when(adbInternalUtil.checkAdbSupport()).thenReturn(Optional.empty());
    assertThat(detector.precondition()).isTrue();

    when(adbInternalUtil.checkAdbSupport()).thenReturn(Optional.of("ADB not found"));
    assertThat(detector.precondition()).isFalse();
  }

  @Test
  public void detectDevices_noDevice() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap()).thenReturn(ImmutableMap.of());

    assertThat(detector.detectDevices()).isEmpty();
  }

  @Test
  public void detectDevices_hasDevices() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(DEVICE_0, DeviceState.DEVICE, DEVICE_1, DeviceState.OFFLINE));

    assertThat(detector.detectDevices())
        .containsExactly(
            DetectionResult.of(DEVICE_0, DetectionType.ADB, DeviceState.DEVICE),
            DetectionResult.of(DEVICE_1, DetectionType.ADB, DeviceState.OFFLINE));
  }

  @Test
  public void detectDevices_error() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_INVALID_ADB_LINE_FORMAT, ""));

    assertThat(
            assertThrows(MobileHarnessException.class, () -> detector.detectDevices()).getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DM_DETECTOR_ADB_ERROR);
  }

  @Test
  public void detectDevices_filtersRealDeviceProxy() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(DEVICE_0, DeviceState.DEVICE, DEVICE_1, DeviceState.OFFLINE));

    AndroidRealDeviceProxyManager.putRealDeviceProxy("device_serial", "DEVICE_0");
    assertThat(detector.detectDevices())
        .containsExactly(DetectionResult.of(DEVICE_1, DetectionType.ADB, DeviceState.OFFLINE));
    AndroidRealDeviceProxyManager.removeRealDeviceProxy("device_serial");
    assertThat(detector.detectDevices())
        .containsExactly(
            DetectionResult.of(DEVICE_0, DetectionType.ADB, DeviceState.DEVICE),
            DetectionResult.of(DEVICE_1, DetectionType.ADB, DeviceState.OFFLINE));
    AndroidRealDeviceProxyManager.clearRealDeviceProxy();
  }

  @Test
  public void detectDevices_recover() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap()).thenReturn(ImmutableMap.of());

    for (int i = 0; i <= Flags.instance().adbMaxNoDeviceDetectionRounds.getNonNull(); i++) {
      var unused = detector.detectDevices();
    }

    verify(adbInternalUtil).killAdbServer();
  }

  @Test
  public void detectDevices_failedDueToAdbAddressInUse_recover() throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_DEVICE_SERIALS_CMD_ERROR,
                "Failed to list devices with command [/usr/bin/adb devices -l]",
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                    "Failed to run adb command",
                    commandFailureException)));
    when(commandFailureException.getMessage())
        .thenReturn(
            "* daemon not running; starting now at tcp:5037\n"
                + "ADB server didn't ACK\n"
                + "Full server startup log: /tmp/adb.1001.log\n"
                + "Server had pid: 14911\n"
                + "--- adb starting (pid 14925) ---\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Android Debug Bridge version"
                + " 1.0.41\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Version 31.0.2-7425079\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Installed as /usr/bin/adb\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63]\n"
                + "adb F 09-21 02:04:39 14911 14911 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "adb F 09-21 02:04:39 14920 14920 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "adb F 09-21 02:04:39 14925 14925 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "* failed to start daemon\n"
                + "adb: failed to check server version: cannot connect to daemon");

    for (int i = 0; i <= MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS; i++) {
      assertThat(
              assertThrows(MobileHarnessException.class, () -> detector.detectDevices())
                  .getErrorId())
          .isEqualTo(AndroidErrorId.ANDROID_DM_DETECTOR_ADB_ERROR);
    }

    verify(systemUtil).killAllProcesses("adb", KillSignal.SIGKILL);
  }

  @Test
  public void detectDevices_failedWithoutCommandFailureExceptionInChain_noRecover()
      throws Exception {
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_DEVICE_SERIALS_CMD_ERROR,
                "Failed to list devices with command [/usr/bin/adb devices -l]",
                /* cause= */ null));

    for (int i = 0; i <= MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS; i++) {
      assertThat(
              assertThrows(MobileHarnessException.class, () -> detector.detectDevices())
                  .getErrorId())
          .isEqualTo(AndroidErrorId.ANDROID_DM_DETECTOR_ADB_ERROR);
    }

    verify(systemUtil, never()).killAllProcesses(eq("adb"), any());
  }

  @Test
  public void detectDevices_failedDueToAdbAddressInUse_noRecoverIfNotMhManaged() throws Exception {
    flags.setAllFlags(ImmutableMap.of("should_manage_devices", "false"));

    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_DEVICE_SERIALS_CMD_ERROR,
                "Failed to list devices with command [/usr/bin/adb devices -l]",
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                    "Failed to run adb command",
                    commandFailureException)));
    when(commandFailureException.getMessage())
        .thenReturn(
            "* daemon not running; starting now at tcp:5037\n"
                + "ADB server didn't ACK\n"
                + "Full server startup log: /tmp/adb.1001.log\n"
                + "Server had pid: 14911\n"
                + "--- adb starting (pid 14925) ---\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Android Debug Bridge version"
                + " 1.0.41\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Version 31.0.2-7425079\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63] Installed as /usr/bin/adb\n"
                + "adb I 09-21 02:04:39 14925 14925 main.cpp:63]\n"
                + "adb F 09-21 02:04:39 14911 14911 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "adb F 09-21 02:04:39 14920 14920 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "adb F 09-21 02:04:39 14925 14925 main.cpp:160] could not install *smartsocket*"
                + " listener: Address already in use\n"
                + "* failed to start daemon\n"
                + "adb: failed to check server version: cannot connect to daemon");

    for (int i = 0; i <= MAX_ADB_ADDRESS_IN_USE_ERROR_ROUNDS; i++) {
      assertThat(
              assertThrows(MobileHarnessException.class, () -> detector.detectDevices())
                  .getErrorId())
          .isEqualTo(AndroidErrorId.ANDROID_DM_DETECTOR_ADB_ERROR);
    }

    verify(systemUtil, never()).killAllProcesses(eq("adb"), any());
  }

  @Test
  public void detectDevices_apiConfigOverTcpChanged() throws Exception {
    // First round, add the OverTcp devices.
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.OFFLINE))
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.DEVICE));
    when(adbInternalUtil.getDeviceSerialsByState(DeviceState.OFFLINE))
        .thenReturn(ImmutableSet.of(OVER_TCP_ID));
    when(apiConfig.getOverTcpDeviceControlIds()).thenReturn(ImmutableList.of(OVER_TCP_ID));
    when(testbedUtil.getAllIdsOfType(any(), eq(AndroidRealDevice.class)))
        .thenReturn(ImmutableSet.of());

    assertThat(detector.detectDevices())
        .containsExactly(DetectionResult.of(OVER_TCP_ID, DetectionType.ADB, DeviceState.DEVICE));
    verify(adbInternalUtil).disconnect(OVER_TCP_ID);
    verify(adbInternalUtil).connect(OVER_TCP_ID);

    // Second round, delete the OverTcp devices.
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.DEVICE))
        .thenReturn(ImmutableMap.of());
    when(apiConfig.getOverTcpDeviceControlIds()).thenReturn(ImmutableList.of());
    when(testbedUtil.getAllIdsOfType(any(), eq(AndroidRealDevice.class)))
        .thenReturn(ImmutableSet.of());

    assertThat(detector.detectDevices()).isEmpty();
    verify(adbInternalUtil, times(2)).disconnect(OVER_TCP_ID);
  }

  @Test
  public void detectDevices_testbedConfigOverTcpChanged() throws Exception {
    // First round, add the OverTcp devices.
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.OFFLINE))
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.DEVICE));
    when(adbInternalUtil.getDeviceSerialsByState(DeviceState.OFFLINE))
        .thenReturn(ImmutableSet.of(OVER_TCP_ID));
    when(apiConfig.getOverTcpDeviceControlIds()).thenReturn(ImmutableList.of());
    when(testbedUtil.getAllIdsOfType(any(), eq(AndroidRealDevice.class)))
        .thenReturn(ImmutableSet.of(OVER_TCP_ID));

    assertThat(detector.detectDevices())
        .containsExactly(DetectionResult.of(OVER_TCP_ID, DetectionType.ADB, DeviceState.DEVICE));

    // Second round, delete the OverTcp devices.
    when(adbInternalUtil.getDeviceSerialsAsMap())
        .thenReturn(ImmutableMap.of(OVER_TCP_ID, DeviceState.DEVICE))
        .thenReturn(ImmutableMap.of());
    when(apiConfig.getOverTcpDeviceControlIds()).thenReturn(ImmutableList.of());
    when(testbedUtil.getAllIdsOfType(any(), eq(AndroidRealDevice.class)))
        .thenReturn(ImmutableSet.of());

    assertThat(detector.detectDevices()).isEmpty();
    verify(adbInternalUtil, times(2)).disconnect(OVER_TCP_ID);
  }
}
