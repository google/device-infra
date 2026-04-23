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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidDesktopDeviceHelperTest {

  private static final String DEVICE_ID = "test_device_id:1234";
  private static final String HOST_NAME = "test_device_id";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private BaseDevice mockBaseDevice;
  @Mock private CommandExecutor mockCommandExecutor;
  private AndroidDesktopDeviceHelper androidDesktopDeviceHelper;

  @Before
  public void setUp() {
    androidDesktopDeviceHelper = new AndroidDesktopDeviceHelper(mockCommandExecutor);
  }

  @Test
  public void updateSchedulingDimensions_validJson_updatesDimensions()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    String jsonOutput =
        "{\"Dimensions\": {\"label-pool\": [\"foo\"], \"label-servo_state\":"
            + " [\"WORKING\"]},\"State\": {\"state_a\":[\"VALUE_A\"]}}";
    CommandResult commandResult = FakeCommandResult.of(jsonOutput, "", 0);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);
    when(mockBaseDevice.getDeviceId()).thenReturn(DEVICE_ID);

    // WHEN
    androidDesktopDeviceHelper.updateSchedulingDimensions(mockBaseDevice);

    // THEN
    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("internal-print-bot-info")
                        && command.getCommand().contains(HOST_NAME)));
    verify(mockBaseDevice).updateDimension("label-pool", "foo");
    verify(mockBaseDevice).updateDimension("label-servo_state", "WORKING");
    verify(mockBaseDevice).updateDimension("state_a", "VALUE_A");
  }

  @Test
  public void updateSchedulingDimensions_invalidJson_doesNotUpdateDimensions()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    CommandResult commandResult = FakeCommandResult.of("invalid json", "", 0);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);
    when(mockBaseDevice.getDeviceId()).thenReturn(DEVICE_ID);

    // WHEN THEN
    assertThrows(
        MobileHarnessException.class,
        () -> androidDesktopDeviceHelper.updateSchedulingDimensions(mockBaseDevice));
  }

  @Test
  public void updateSchedulingDimensions_emptyJson_doesNotUpdateDimensions()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    CommandResult commandResult = FakeCommandResult.of("{}", "", 0);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);
    when(mockBaseDevice.getDeviceId()).thenReturn(DEVICE_ID);

    // WHEN
    androidDesktopDeviceHelper.updateSchedulingDimensions(mockBaseDevice);

    // THEN
    verify(mockCommandExecutor).exec(any(Command.class));
    verify(mockBaseDevice, never()).updateDimension(anyString(), anyString());
    verify(mockBaseDevice, never()).updateDimension(anyString(), any(String[].class));
  }

  @Test
  public void updateSchedulingDimensions_shivasCommandFails_doesNotUpdateDimensions()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    CommandResult commandResult = FakeCommandResult.of("", "", 1);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);
    when(mockBaseDevice.getDeviceId()).thenReturn(DEVICE_ID);

    // WHEN
    androidDesktopDeviceHelper.updateSchedulingDimensions(mockBaseDevice);

    // THEN
    verify(mockCommandExecutor).exec(any(Command.class));
    verify(mockBaseDevice, never()).updateDimension(anyString(), anyString());
    verify(mockBaseDevice, never()).updateDimension(anyString(), any(String[].class));
  }

  @Test
  public void getSchedulingLabels_throwsException_testShouldNotFail()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenThrow(new InterruptedException("Error executing command"));
    when(mockBaseDevice.getDeviceId()).thenReturn(DEVICE_ID);

    // WHEN THEN
    assertThrows(
        InterruptedException.class,
        () -> androidDesktopDeviceHelper.updateSchedulingDimensions(mockBaseDevice));

    // THEN
  }

  @Test
  public void getDeviceDutState_validOutput_returnsState()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    CommandResult commandResult = FakeCommandResult.of("dut1: ready ", "", 0);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    // WHEN
    String state = androidDesktopDeviceHelper.getDeviceDutState(DEVICE_ID);

    // THEN
    assertThat(state).isEqualTo("ready");
  }

  @Test
  public void getDeviceDutState_commandFails_returnsEmptyString()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    when(mockCommandExecutor.exec(any())).thenReturn(FakeCommandResult.of("error", "ERROR", 1));

    // WHEN
    String state = androidDesktopDeviceHelper.getDeviceDutState(DEVICE_ID);

    // THEN
    assertThat(state).isEmpty();
  }

  @Test
  public void getDeviceDutState_throwsExceptiong()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    when(mockCommandExecutor.exec(any())).thenThrow(new InterruptedException("test exception"));

    // WHEN THEN
    assertThrows(
        InterruptedException.class, () -> androidDesktopDeviceHelper.getDeviceDutState(DEVICE_ID));
  }
}
