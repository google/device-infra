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

package com.google.devtools.mobileharness.platform.androiddesktop.device;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
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
  public void getDeviceDimensions_validJson_returnsDimensions()
      throws MobileHarnessException, InterruptedException {
    // GIVEN
    String jsonOutput =
        "{\"Dimensions\": {\"label-board\": [\"test_board\"], \"label-model\":"
            + " [\"test_model\"], \"label-sku\": [\"test_sku\"]}}";
    CommandResult commandResult = FakeCommandResult.of(jsonOutput, "", 0);
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    // WHEN
    Map<String, String> dimensions = androidDesktopDeviceHelper.getDeviceDimensions(DEVICE_ID);

    // THEN
    assertThat(dimensions).containsEntry("label-board", "test_board");
    assertThat(dimensions).containsEntry("label-model", "test_model");
    assertThat(dimensions).containsEntry("label-sku", "test_sku");
    assertThat(dimensions).containsEntry("board", "test_board");
    assertThat(dimensions).containsEntry("model", "test_model");
    assertThat(dimensions).containsEntry("sku", "test_sku");
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

  @Test
  public void updateDeviceDutState_success() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("success", "", 0));

    androidDesktopDeviceHelper.updateDeviceDutState(DEVICE_ID, "needs_repair");

    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("update")
                        && command.getCommand().contains("dut-state")
                        && command.getCommand().contains("needs_repair")
                        && command.getCommand().contains(HOST_NAME)));
  }

  @Test
  public void updateDeviceDutState_commandFails_throwsException() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("error", "ERROR", 1));

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> androidDesktopDeviceHelper.updateDeviceDutState(DEVICE_ID, "needs_repair"));
    assertThat(e).hasMessageThat().contains("Failed to update device");
  }

  @Test
  public void updateDeviceDutState_provision_success() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("success", "", 0));

    androidDesktopDeviceHelper.updateDeviceDutState(
        DEVICE_ID,
        "needs_repair",
        /* provision= */ true,
        /* reimage= */ false,
        /* usbkey= */ false,
        /* clearRepairRequests= */ false);

    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("update")
                        && command.getCommand().contains("dut-state")
                        && command.getCommand().contains("needs_repair")
                        && command.getCommand().contains(HOST_NAME)
                        && command.getCommand().contains("-provision")));
  }

  @Test
  public void updateDeviceDutState_reimage_success() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("success", "", 0));

    androidDesktopDeviceHelper.updateDeviceDutState(
        DEVICE_ID,
        "needs_repair",
        /* provision= */ false,
        /* reimage= */ true,
        /* usbkey= */ false,
        /* clearRepairRequests= */ false);

    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("update")
                        && command.getCommand().contains("dut-state")
                        && command.getCommand().contains("needs_repair")
                        && command.getCommand().contains(HOST_NAME)
                        && command.getCommand().contains("-reimage")));
  }

  @Test
  public void updateDeviceDutState_usbkey_success() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("success", "", 0));

    androidDesktopDeviceHelper.updateDeviceDutState(
        DEVICE_ID,
        "needs_repair",
        /* provision= */ false,
        /* reimage= */ false,
        /* usbkey= */ true,
        /* clearRepairRequests= */ false);

    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("update")
                        && command.getCommand().contains("dut-state")
                        && command.getCommand().contains("needs_repair")
                        && command.getCommand().contains(HOST_NAME)
                        && command.getCommand().contains("-usbkey")));
  }

  @Test
  public void updateDeviceDutState_clearRepairRequests_success() throws Exception {
    when(mockCommandExecutor.exec(any(Command.class)))
        .thenReturn(FakeCommandResult.of("success", "", 0));

    androidDesktopDeviceHelper.updateDeviceDutState(
        DEVICE_ID,
        "needs_repair",
        /* provision= */ false,
        /* reimage= */ false,
        /* usbkey= */ false,
        /* clearRepairRequests= */ true);

    verify(mockCommandExecutor)
        .exec(
            argThat(
                command ->
                    command.getCommand().get(1).equals("update")
                        && command.getCommand().contains("dut-state")
                        && command.getCommand().contains("needs_repair")
                        && command.getCommand().contains(HOST_NAME)
                        && command.getCommand().contains("-clear-repair-requests")));
  }

  @Test
  public void updateDeviceDutState_noFlags_throwsException() {
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                androidDesktopDeviceHelper.updateDeviceDutState(
                    DEVICE_ID,
                    null,
                    /* provision= */ false,
                    /* reimage= */ false,
                    /* usbkey= */ false,
                    /* clearRepairRequests= */ false));
    assertThat(e).hasMessageThat().contains("At least one update flag");
  }

  @Test
  public void updateDeviceDutState_clearAndSetRepairRequests_throwsException() {
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                androidDesktopDeviceHelper.updateDeviceDutState(
                    DEVICE_ID,
                    "needs_repair",
                    /* provision= */ true,
                    /* reimage= */ false,
                    /* usbkey= */ false,
                    /* clearRepairRequests= */ true));
    assertThat(e).hasMessageThat().contains("Cannot set repair requests with clearRepairRequests");
  }

  @Test
  public void propagateDimensionsToSubLeafTests_success_propagatesAllowlistedDimensions() {
    // Setup dimensions map
    Map<String, String> dimensions = new HashMap<>();
    dimensions.put("board", "my_board");
    dimensions.put("model", "my_model");
    dimensions.put("sku", "my_sku");
    dimensions.put("ignored-dim", "ignored_val");
    dimensions.put("", "empty_key_ignored");
    dimensions.put("hwid", null);
    dimensions.put("dut_name", "my_dut");

    // Setup sub-test structure
    TestInfo parentTestInfo = Mockito.mock(TestInfo.class);
    TestInfos subtests = Mockito.mock(TestInfos.class);
    when(parentTestInfo.subTests()).thenReturn(subtests);

    ListMultimap<String, TestInfo> subTestMap = ArrayListMultimap.create();
    TestInfo subTest1 = Mockito.mock(TestInfo.class);
    subTestMap.put("sub_test_1", subTest1);
    when(subtests.getAll()).thenReturn(subTestMap);

    // subTest1 has no sub-tests (it's a leaf node)
    when(subTest1.subTests()).thenReturn(null);
    Properties subTest1Properties = Mockito.mock(Properties.class);
    when(subTest1.properties()).thenReturn(subTest1Properties);

    androidDesktopDeviceHelper.propagateDimensionsToSubLeafTests(parentTestInfo, dimensions);

    verify(subTest1Properties).add("dut_name", "my_dut");
    verify(subTest1Properties).add("board", "my_board");
    verify(subTest1Properties).add("model", "my_model");
    verify(subTest1Properties).add("sku", "my_sku");
    verify(subTest1Properties, never()).add(eq("ignored-dim"), anyString());
    verify(subTest1Properties, never()).add(eq(""), anyString());
    verify(subTest1Properties, never()).add(eq("hwid"), anyString());
  }

  @Test
  public void propagateDimensionsToSubLeafTests_noSubTests_doesNothing() {
    Map<String, String> dimensions = new HashMap<>();
    dimensions.put("board", "my_board");

    TestInfo parentTestInfo = Mockito.mock(TestInfo.class);
    when(parentTestInfo.subTests()).thenReturn(null); // No sub-tests at all

    // Should not crash and do nothing
    androidDesktopDeviceHelper.propagateDimensionsToSubLeafTests(parentTestInfo, dimensions);

    verify(parentTestInfo, never()).properties();
  }

  @Test
  public void propagateDimensionsToSubLeafTests_recursive_propagatesToLeafSubTestsOnly() {
    // Setup dimensions map
    Map<String, String> dimensions = new HashMap<>();
    dimensions.put("board", "my_board");
    dimensions.put("dut_name", "my_dut");

    // Setup sub-test structure: Parent -> Child -> Grandchild (leaf)
    TestInfo parentTestInfo = Mockito.mock(TestInfo.class);
    TestInfos parentSubtests = Mockito.mock(TestInfos.class);
    when(parentTestInfo.subTests()).thenReturn(parentSubtests);

    ListMultimap<String, TestInfo> parentSubTestMap = ArrayListMultimap.create();
    TestInfo childTestInfo = Mockito.mock(TestInfo.class);
    parentSubTestMap.put("child_test", childTestInfo);
    when(parentSubtests.getAll()).thenReturn(parentSubTestMap);

    // Child has sub-tests (Grandchild)
    TestInfos childSubtests = Mockito.mock(TestInfos.class);
    when(childTestInfo.subTests()).thenReturn(childSubtests);
    when(childSubtests.isEmpty()).thenReturn(false);

    ListMultimap<String, TestInfo> childSubTestMap = ArrayListMultimap.create();
    TestInfo grandchildTestInfo = Mockito.mock(TestInfo.class);
    childSubTestMap.put("grandchild_test", grandchildTestInfo);
    when(childSubtests.getAll()).thenReturn(childSubTestMap);

    // Grandchild has no sub-tests (it's a leaf node)
    when(grandchildTestInfo.subTests()).thenReturn(null);
    Properties grandchildProperties = Mockito.mock(Properties.class);
    when(grandchildTestInfo.properties()).thenReturn(grandchildProperties);

    Properties childProperties = Mockito.mock(Properties.class);
    when(childTestInfo.properties()).thenReturn(childProperties);

    androidDesktopDeviceHelper.propagateDimensionsToSubLeafTests(parentTestInfo, dimensions);

    // Grandchild should get properties
    verify(grandchildProperties).add("dut_name", "my_dut");
    verify(grandchildProperties).add("board", "my_board");

    // Child should NOT get properties (because it is not a leaf)
    verify(childProperties, never()).add(anyString(), anyString());
  }
}
