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

package com.google.devtools.mobileharness.platform.android.systemstate;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class GsiToolTest {

  private static final String DEVICE_ID = "363005DC750400ec";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AndroidAdbInternalUtil androidAdbInternalUtil;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private Adb adb;
  @Mock private AndroidSystemStateUtil systemStateUtil;
  @Mock private Fastboot fastboot;

  private GsiTool gsiTool;

  @Before
  public void setUp() {
    gsiTool = new GsiTool(androidAdbInternalUtil, androidAdbUtil, adb, systemStateUtil, fastboot);
  }

  @Test
  public void disableDsuIfRunning_notInDsuMode_noOp() throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("");

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false);

    verifyNoInteractions(adb, systemStateUtil, fastboot);
  }

  @Test
  public void disableDsuIfRunning_inDsuMode_disablesWipesAndReboots() throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false);

    InOrder inOrder = Mockito.inOrder(adb, systemStateUtil);
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool disable");
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool wipe");
    inOrder.verify(systemStateUtil).reboot(DEVICE_ID);
    inOrder
        .verify(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    inOrder.verify(systemStateUtil).waitUntilReady(DEVICE_ID, Duration.ofMinutes(5));
    verifyNoInteractions(fastboot);
  }

  @Test
  public void disableDsuIfRunning_overTcpDeviceInDsuMode_waitsForOverTcpConnection()
      throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("true");

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ true);

    InOrder inOrder = Mockito.inOrder(adb, systemStateUtil);
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool disable");
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool wipe");
    inOrder.verify(systemStateUtil).reboot(DEVICE_ID);
    inOrder
        .verify(systemStateUtil)
        .waitForOverTcpDeviceConnection(DEVICE_ID, Duration.ofMinutes(5));
    inOrder.verify(systemStateUtil).waitUntilReady(DEVICE_ID, Duration.ofMinutes(5));
    verify(systemStateUtil, never()).waitForState(eq(DEVICE_ID), any(), any());
  }

  @Test
  public void disableDsuIfRunning_gsiToolThrows_continuesAndReboots() throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");
    when(adb.runShellWithRetry(DEVICE_ID, "gsi_tool disable"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "disable failed"));
    when(adb.runShellWithRetry(DEVICE_ID, "gsi_tool wipe"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "wipe failed"));

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false);

    verify(systemStateUtil).reboot(DEVICE_ID);
    verify(systemStateUtil).waitUntilReady(DEVICE_ID, Duration.ofMinutes(5));
  }

  @Test
  public void disableDsuIfRunning_entersRecoveryAfterReboot_recoversViaFastbootd()
      throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR,
                "Get unexpected state RECOVERY after wait-for-device"))
        .doNothing()
        .when(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY))
        .thenReturn(ImmutableSet.of(DEVICE_ID));
    when(fastboot.getVar(DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("yes");

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false);

    InOrder inOrder = Mockito.inOrder(adb, systemStateUtil, fastboot);
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool disable");
    inOrder.verify(adb).runShellWithRetry(DEVICE_ID, "gsi_tool wipe");
    inOrder.verify(systemStateUtil).reboot(DEVICE_ID);
    inOrder.verify(systemStateUtil).reboot(DEVICE_ID, RebootMode.FASTBOOT);
    inOrder.verify(fastboot).getVar(DEVICE_ID, FastbootProperty.IS_USERSPACE);
    inOrder
        .verify(fastboot)
        .runWithRetry(
            DEVICE_ID,
            new String[] {"gsi", "wipe"},
            Duration.ofMinutes(2),
            /* flashSemaphore= */ false);
    inOrder.verify(fastboot).wipe(DEVICE_ID, /* retryTask= */ null);
    inOrder.verify(fastboot).reboot(DEVICE_ID);
    inOrder
        .verify(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    inOrder.verify(systemStateUtil).waitUntilReady(DEVICE_ID, Duration.ofMinutes(5));
  }

  @Test
  public void disableDsuIfRunning_notUserspaceFastbootd_skipsFastbootWipeAndReboots()
      throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR,
                "Get unexpected state RECOVERY after wait-for-device"))
        .doNothing()
        .when(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY))
        .thenReturn(ImmutableSet.of(DEVICE_ID));
    when(fastboot.getVar(DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("no");

    gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false);

    verify(fastboot, never()).runWithRetry(eq(DEVICE_ID), any(), any(), eq(false));
    verify(fastboot, never()).wipe(eq(DEVICE_ID), any());
    verify(fastboot).reboot(DEVICE_ID);
    verify(systemStateUtil).waitUntilReady(DEVICE_ID, Duration.ofMinutes(5));
  }

  @Test
  public void disableDsuIfRunning_fastbootWipeThrows_throwsAndStillRebootsFromFastboot()
      throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR,
                "Get unexpected state RECOVERY after wait-for-device"))
        .when(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY))
        .thenReturn(ImmutableSet.of(DEVICE_ID));
    when(fastboot.getVar(DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("yes");
    when(fastboot.wipe(DEVICE_ID, /* retryTask= */ null))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FASTBOOT_COMMAND_EXEC_ERROR,
                "Failed to wipe cache partition"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FASTBOOT_COMMAND_EXEC_ERROR);

    InOrder inOrder = Mockito.inOrder(systemStateUtil, fastboot);
    inOrder.verify(systemStateUtil).reboot(DEVICE_ID, RebootMode.FASTBOOT);
    inOrder.verify(fastboot).getVar(DEVICE_ID, FastbootProperty.IS_USERSPACE);
    inOrder.verify(fastboot).wipe(DEVICE_ID, /* retryTask= */ null);
    inOrder.verify(fastboot).reboot(DEVICE_ID);
  }

  @Test
  public void disableDsuIfRunning_rebootFailsAndNotInRecovery_rethrowsOriginalException()
      throws Exception {
    when(androidAdbUtil.getProperty(DEVICE_ID, ImmutableList.of("ro.gsid.image_running")))
        .thenReturn("1");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR, "Wait for state timeout"))
        .when(systemStateUtil)
        .waitForState(DEVICE_ID, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    when(androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY))
        .thenReturn(ImmutableSet.of());

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> gsiTool.disableDsuIfRunning(DEVICE_ID, /* isOverTcpDevice= */ false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR);

    verifyNoInteractions(fastboot);
  }
}
