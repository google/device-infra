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

package com.google.devtools.mobileharness.platform.android.connectivity;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil.LocationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link UsbReverseTetheringUtil}. */
@RunWith(TestParameterInjector.class)
public class UsbReverseTetheringUtilTest {

  private static final String DEVICE_ID = "44121FDKD00042";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private AndroidConnectivityUtil connectivityUtil;
  @Mock private AndroidSystemStateUtil systemStateUtil;
  @Mock private NetUtil netUtil;
  @Mock private DeviceCache deviceCache;
  @Mock private Sleeper sleeper;
  @Mock private Clock clock;
  @Mock private Device device;

  private final Log log = new Log(new Timing());
  private final Map<String, String> deviceProperties = new HashMap<>();
  private AtomicReference<Instant> currentInstant;
  private UsbReverseTetheringUtil util;

  @Before
  public void setUp() throws Exception {
    currentInstant = new AtomicReference<>(Instant.parse("2026-11-15T00:00:00Z"));
    when(clock.instant()).thenAnswer(inv -> currentInstant.get());
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    doAnswer(
            inv -> {
              Duration sleepDuration = inv.getArgument(0);
              currentInstant.updateAndGet(now -> now.plus(sleepDuration));
              return null;
            })
        .when(sleeper)
        .sleep(any(Duration.class));

    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(device.setProperty(anyString(), any()))
        .thenAnswer(inv -> {
          String key = inv.getArgument(0);
          String val = inv.getArgument(1);
          if (val == null) {
            return deviceProperties.remove(key);
          }
          return deviceProperties.put(key, val);
        });
    when(device.getProperty(anyString()))
        .thenAnswer(inv -> deviceProperties.get(inv.getArgument(0)));
    when(device.getBooleanProperty(anyString()))
        .thenAnswer(inv -> Boolean.parseBoolean(deviceProperties.get(inv.getArgument(0))));

    when(netUtil.getLocalHostLocationType()).thenReturn(LocationType.NOT_IN_CHINA);

    util =
        new UsbReverseTetheringUtil(
            adb, connectivityUtil, systemStateUtil, netUtil, deviceCache, sleeper, clock);
  }

  @Test
  public void checkHostSupport_matrix() throws Exception {
    Path root = tmpFolder.getRoot().toPath();
    Path bridgeDir = root.resolve("bridge");
    Path flagsFile = root.resolve("flags");
    Path ipForwardFile = root.resolve("ip_forward");
    Path procNetUdpFile = root.resolve("udp");

    Files.createDirectories(bridgeDir);
    // 0x1003: IFF_UP | IFF_BROADCAST | IFF_MULTICAST
    Files.writeString(flagsFile, "0x1003\n");
    Files.writeString(ipForwardFile, "1\n");
    Files.writeString(
        procNetUdpFile,
        "   sl  local_address rem_address   st tx_queue rx_queue\n"
            + "  123: 0162A8C0:0043 00000000:0000 07 00000000:00000000\n");

    assertThat(util.checkHostSupport(bridgeDir, flagsFile, ipForwardFile, procNetUdpFile, true))
        .isTrue();

    // Missing IPv4 address on interface
    assertThat(util.checkHostSupport(bridgeDir, flagsFile, ipForwardFile, procNetUdpFile, false))
        .isFalse();

    // Interface down (IFF_UP unset)
    Files.writeString(flagsFile, "0x1002\n");
    assertThat(util.checkHostSupport(bridgeDir, flagsFile, ipForwardFile, procNetUdpFile, true))
        .isFalse();
    Files.writeString(flagsFile, "0x1003\n");

    // IPv4 forwarding disabled
    Files.writeString(ipForwardFile, "0\n");
    assertThat(util.checkHostSupport(bridgeDir, flagsFile, ipForwardFile, procNetUdpFile, true))
        .isFalse();
    Files.writeString(ipForwardFile, "1\n");

    // No DHCP server on local UDP port 67 (0x0043); remote port 0043 must not match
    Files.writeString(
        procNetUdpFile,
        "   sl  local_address rem_address   st tx_queue rx_queue\n"
            + "  123: 0100007F:0035 0162A8C0:0043 07 00000000:00000000\n");
    assertThat(util.checkHostSupport(bridgeDir, flagsFile, ipForwardFile, procNetUdpFile, true))
        .isFalse();
    Files.writeString(
        procNetUdpFile,
        "   sl  local_address rem_address   st tx_queue rx_queue\n"
            + "  123: 0162A8C0:0043 00000000:0000 07 00000000:00000000\n");

    // Bridge directory missing
    assertThat(
            util.checkHostSupport(
                root.resolve("nonexistent_bridge"), flagsFile, ipForwardFile, procNetUdpFile, true))
        .isFalse();
  }

  @Test
  public void isDeviceSupported_grepPassAndFail() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CHECK_DEVICE_SUPPORT_CMD), any()))
        .thenReturn("");
    assertThat(util.isDeviceSupported(DEVICE_ID)).isTrue();

    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CHECK_DEVICE_SUPPORT_CMD), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "exit 1"));
    assertThat(util.isDeviceSupported(DEVICE_ID)).isFalse();
  }

  @Test
  public void enableAndDisableUsbReverseTethering_persistsPollsAndCleansUp() throws Exception {
    when(deviceCache.isCached(DEVICE_ID)).thenReturn(false);
    // USB disconnect during svc usb command can throw MobileHarnessException; must be tolerated.
    when(adb.runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.SET_SCREEN_UNLOCKED_USB_FUNCTIONS_RNDIS_CMD),
            any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "protocol fault"));
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis\n");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1))
        .thenReturn(0.0, 0.0, 1.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(30),
        /* customCheckHost= */ null,
        log);

    assertThat(util.isMarkedEnabled(device)).isTrue();
    assertThat(device.getBooleanProperty(UsbReverseTetheringUtil.PROPERTY_PERSIST_ACROSS_REBOOT))
        .isTrue();
    InOrder inOrder = inOrder(deviceCache, adb, systemStateUtil, sleeper);
    inOrder.verify(deviceCache).cache(eq(DEVICE_ID), anyString(), any(Duration.class));
    inOrder
        .verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.SET_SCREEN_UNLOCKED_USB_FUNCTIONS_RNDIS_CMD),
            any());
    inOrder
        .verify(systemStateUtil)
        .waitForState(
            DEVICE_ID,
            DeviceConnectionState.DISCONNECT,
            UsbReverseTetheringUtil.USB_DISCONNECT_WAIT_TIMEOUT);
    inOrder
        .verify(systemStateUtil)
        .waitForState(
            DEVICE_ID,
            DeviceConnectionState.DEVICE,
            UsbReverseTetheringUtil.USB_RECONNECT_WAIT_TIMEOUT);
    inOrder
        .verify(systemStateUtil)
        .waitUntilReady(DEVICE_ID, UsbReverseTetheringUtil.USB_RECONNECT_WAIT_TIMEOUT);
    inOrder.verify(deviceCache).invalidateCache(DEVICE_ID);
    inOrder.verify(sleeper, times(2)).sleep(UsbReverseTetheringUtil.CONNECTIVITY_POLL_INTERVAL);
    verify(adb, never())
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.SET_USB_FUNCTIONS_RNDIS_CMD), any());

    // Now disable and verify both persisted screen-unlocked functions and active USB functions are
    // cleared in order.
    util.disableUsbReverseTethering(device, log);

    assertThat(util.isMarkedEnabled(device)).isFalse();
    inOrder
        .verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD),
            any());
    inOrder
        .verify(adb)
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CLEAR_USB_FUNCTIONS_CMD), any());
  }

  @Test
  public void enableUsbReverseTethering_screenLocked_fallsBackToSetFunctions() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(1.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(10),
        /* customCheckHost= */ null,
        log);

    verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.SET_SCREEN_UNLOCKED_USB_FUNCTIONS_RNDIS_CMD),
            any());
    verify(adb)
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.SET_USB_FUNCTIONS_RNDIS_CMD), any());
  }

  @Test
  public void enableUsbReverseTethering_alreadyCached_preservesOuterCacheAndToleratesFastReconnect()
      throws Exception {
    when(deviceCache.isCached(DEVICE_ID)).thenReturn(true);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR, "timeout"))
        .when(systemStateUtil)
        .waitForState(
            DEVICE_ID,
            DeviceConnectionState.DISCONNECT,
            UsbReverseTetheringUtil.USB_DISCONNECT_WAIT_TIMEOUT);
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "8.8.8.8", 1)).thenReturn(1.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ false,
        Duration.ofSeconds(10),
        /* customCheckHost= */ "8.8.8.8",
        log);

    verify(deviceCache, never()).cache(anyString(), anyString(), any());
    verify(deviceCache, never()).invalidateCache(anyString());
    verify(adb)
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.SET_USB_FUNCTIONS_RNDIS_CMD), any());
    verify(systemStateUtil)
        .waitForState(
            DEVICE_ID,
            DeviceConnectionState.DEVICE,
            UsbReverseTetheringUtil.USB_RECONNECT_WAIT_TIMEOUT);
  }

  @Test
  public void enableUsbReverseTethering_connectivityTimeout_throws() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(0.0);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                util.enableUsbReverseTethering(
                    device,
                    /* persistAcrossReboot= */ true,
                    Duration.ofSeconds(4),
                    /* customCheckHost= */ null,
                    log));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_CONNECTIVITY_VERIFICATION_FAILED);
    // USB functions were already mutated, so enabled state must remain marked for postRunTest.
    assertThat(util.isMarkedEnabled(device)).isTrue();
  }

  @Test
  public void disableUsbReverseTethering_failure_keepsMarkedEnabled() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(1.0);
    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(10),
        /* customCheckHost= */ null,
        log);

    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR, "device offline"))
        .when(systemStateUtil)
        .waitForState(
            DEVICE_ID,
            DeviceConnectionState.DEVICE,
            UsbReverseTetheringUtil.USB_RECONNECT_WAIT_TIMEOUT);

    assertThrows(MobileHarnessException.class, () -> util.disableUsbReverseTethering(device, log));
    assertThat(util.isMarkedEnabled(device)).isTrue();
  }

  @Test
  public void restoreAfterRebootIfEnabled_osAlreadyAppliedRndis_skipsSetFunctions()
      throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(1.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(10),
        /* customCheckHost= */ null,
        log);

    util.restoreAfterRebootIfEnabled(device, log);

    verify(adb, never())
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.SET_USB_FUNCTIONS_RNDIS_CMD), any());
    verify(connectivityUtil, times(2)).pingSuccessRate(DEVICE_ID, "www.google.com", 1);
  }

  @Test
  public void restoreAfterRebootIfEnabled_rndisNotActive_reAppliesSetFunctions() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis", "");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(1.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(10),
        /* customCheckHost= */ null,
        log);

    util.restoreAfterRebootIfEnabled(device, log);

    verify(adb)
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.SET_USB_FUNCTIONS_RNDIS_CMD), any());
  }

  @Test
  public void restoreAfterRebootIfEnabled_connectivityFails_throws() throws Exception {
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.GET_USB_FUNCTIONS_CMD), any()))
        .thenReturn("rndis");
    when(connectivityUtil.pingSuccessRate(DEVICE_ID, "www.google.com", 1)).thenReturn(1.0, 0.0);

    util.enableUsbReverseTethering(
        device,
        /* persistAcrossReboot= */ true,
        Duration.ofSeconds(2),
        /* customCheckHost= */ null,
        log);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> util.restoreAfterRebootIfEnabled(device, log));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_POST_REBOOT_RESTORE_FAILED);
  }

  @Test
  public void clearPersistedFunctionsIfSet_ignoresNonTetheringDumpsys_disablesWhenPersisted()
      throws Exception {
    // Clean device or non-tethering default (MTP) must not trigger USB reset.
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.DUMPSYS_USB_CMD), any()))
        .thenReturn(
            "USB Manager State:\n"
                + "  device_manager={\n"
                + "    handler={\n"
                + "      current_functions=ADB\n"
                + "      screen_unlocked_functions=MTP\n"
                + "    }\n"
                + "  }\n"
                + "  port_manager={\n"
                + "  }\n");

    util.clearPersistedFunctionsIfSet(device);

    verify(adb, never())
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD),
            any());
    verify(adb, never())
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CLEAR_USB_FUNCTIONS_CMD), any());

    // Persisted RNDIS (and 0x400 NCM HAL mask) or active RNDIS in dumpsys usb triggers cleanup.
    assertThat(
            UsbReverseTetheringUtil.hasTetheringUsbFunctionInDump(
                "    handler={\n      screen_unlocked_functions=0x400\n    }\n"))
        .isTrue();
    assertThat(
            UsbReverseTetheringUtil.hasTetheringUsbFunctionInDump(
                "    handler={\n"
                    + "      current_functions=RNDIS\n"
                    + "      screen_unlocked_functions=NONE\n"
                    + "    }\n"))
        .isTrue();
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.DUMPSYS_USB_CMD), any()))
        .thenReturn(
            "USB Manager State:\n"
                + "  device_manager={\n"
                + "    handler={\n"
                + "      current_functions=RNDIS\n"
                + "      screen_unlocked_functions=RNDIS\n"
                + "    }\n"
                + "  }\n",
            "USB Manager State:\n"
                + "  device_manager={\n"
                + "    handler={\n"
                + "      current_functions=ADB\n"
                + "      screen_unlocked_functions=NONE\n"
                + "    }\n"
                + "  }\n");

    util.clearPersistedFunctionsIfSet(device);

    verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD),
            any());
    verify(adb).runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CLEAR_USB_FUNCTIONS_CMD), any());
  }

  @Test
  public void clearPersistedFunctionsIfSet_markedEnabled_disablesWithoutInitialDumpsysCheck()
      throws Exception {
    device.setProperty(UsbReverseTetheringUtil.PROPERTY_ENABLED, "true");
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.DUMPSYS_USB_CMD), any()))
        .thenReturn(
            "USB Manager State:\n"
                + "  device_manager={\n"
                + "    handler={\n"
                + "      current_functions=ADB\n"
                + "      screen_unlocked_functions=NONE\n"
                + "    }\n"
                + "  }\n");

    util.clearPersistedFunctionsIfSet(device);

    assertThat(util.isMarkedEnabled(device)).isFalse();
    // DUMPSYS_USB_CMD is called once post-reset to verify tethering functions were cleared, not
    // before invoking disableUsbReverseTethering.
    verify(adb, times(1))
        .runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.DUMPSYS_USB_CMD), any());
    verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq(UsbReverseTetheringUtil.CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD),
            any());
    verify(adb).runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.CLEAR_USB_FUNCTIONS_CMD), any());
  }

  @Test
  public void disableUsbReverseTethering_functionsRemainActiveAfterReset_throwsAndKeepsMarked()
      throws Exception {
    device.setProperty(UsbReverseTetheringUtil.PROPERTY_ENABLED, "true");
    when(adb.runShell(eq(DEVICE_ID), eq(UsbReverseTetheringUtil.DUMPSYS_USB_CMD), any()))
        .thenReturn(
            "USB Manager State:\n"
                + "  device_manager={\n"
                + "    handler={\n"
                + "      current_functions=RNDIS\n"
                + "      screen_unlocked_functions=NONE\n"
                + "    }\n"
                + "  }\n");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> util.disableUsbReverseTethering(device, log));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DISABLE_ERROR);
    assertThat(util.isMarkedEnabled(device)).isTrue();
  }

  @Test
  public void isValidCheckHost_validHost_returnsTrue(
      @TestParameter({"connectivitycheck.gstatic.com", "a"}) String validHost) {
    boolean isValid = UsbReverseTetheringUtil.isValidCheckHost(validHost);

    assertThat(isValid).isTrue();
  }

  @Test
  public void isValidCheckHost_invalidHost_returnsFalse(
      @TestParameter({"-f", "-", "example.com-", ".example.com", "example.com.", "", "null"})
          String invalidHost) {
    boolean isValid = UsbReverseTetheringUtil.isValidCheckHost(invalidHost);

    assertThat(isValid).isFalse();
  }
}
