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
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DT_CONVERTER_CIPD_PATH;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyTestSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link CrosDutTopologyDecorator}. */
@RunWith(JUnit4.class)
public class CrosDutTopologyDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver driver;
  @Mock private Device device;
  @Mock private CommandExecutor commandExecutor;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Log log;
  @Mock private Params params;
  @Mock private Files files;
  @Mock private Properties properties;
  @Mock private Api atInfo;
  @Mock private Api alsoTo;
  @Mock private CommandResult commandResult;

  private CrosDutTopologyDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_logs");
    when(jobInfo.params()).thenReturn(params);
    when(jobInfo.files()).thenReturn(files);
    when(driver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("test_device:1234");
    when(log.atInfo()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(alsoTo);

    decorator = new CrosDutTopologyDecorator(driver, testInfo, commandExecutor);
  }

  @Test
  public void prepare_success() throws Exception {
    // Arrange
    when(properties.has("lsnexus_addr_test_device")).thenReturn(true);
    when(properties.get("lsnexus_addr_test_device")).thenReturn("localhost:10001");

    when(params.get("inventory_service_host", "localhost")).thenReturn("inv_host");
    when(params.getInt("inventory_service_port", 1485)).thenReturn(9999);

    String converterOutput = "-c /tmp/mobly_config.yaml\n--param1 val1";
    when(commandResult.stdout()).thenReturn(converterOutput);
    when(commandExecutor.exec(any(Command.class))).thenReturn(commandResult);

    // Act
    decorator.prepare(testInfo);

    // Assert
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).exec(commandCaptor.capture());
    Command actualCommand = commandCaptor.getValue();

    assertThat(actualCommand.getCommand())
        .containsExactly(
            DT_CONVERTER_CIPD_PATH,
            "convert",
            "-unit",
            "test_device",
            "-lsnexus",
            "test_device=localhost:10001",
            "-labservice",
            "inv_host:9999",
            "-logdir",
            "/tmp/test_logs",
            "-config_name",
            "config_test_device",
            "-mh")
        .inOrder();

    verify(properties).add(MoblyTestSpec.FILE_MOBLY_CONFIG, "/tmp/mobly_config.yaml");
    verify(properties).add("--param1", "val1");
  }

  @Test
  public void prepare_commandExecutionFails_throwsInterruptedException() throws Exception {
    // Arrange
    when(properties.has("lsnexus_addr_test_device")).thenReturn(true);
    when(properties.get("lsnexus_addr_test_device")).thenReturn("localhost:10001");
    when(commandExecutor.exec(any(Command.class)))
        .thenThrow(new InterruptedException("interrupted"));

    // Act & Assert
    assertThrows(InterruptedException.class, () -> decorator.prepare(testInfo));
  }

  @Test
  public void collectLsNexusAddresses_noLsNexus() {
    // Arrange
    ImmutableList<String> dutNames = ImmutableList.of("dut1", "dut2");
    when(properties.has("lsnexus_addr_dut1")).thenReturn(false);
    when(properties.has("lsnexus_addr_dut2")).thenReturn(false);

    // Act
    Map<String, String> lsnexusServiceAddresses =
        decorator.collectLsNexusAddresses(testInfo, dutNames);

    // Assert
    assertThat(lsnexusServiceAddresses).isEmpty();
  }

  @Test
  public void collectLsNexusAddresses_success() {
    // Arrange
    ImmutableList<String> dutNames = ImmutableList.of("dut1", "dut2");
    when(properties.has("lsnexus_addr_dut1")).thenReturn(true);
    when(properties.get("lsnexus_addr_dut1")).thenReturn("localhost:10001");
    when(properties.has("lsnexus_addr_dut2")).thenReturn(false);

    // Act
    Map<String, String> lsnexusServiceAddresses =
        decorator.collectLsNexusAddresses(testInfo, dutNames);

    // Assert
    assertThat(lsnexusServiceAddresses).containsExactly("dut1", "localhost:10001");
  }

  @Test
  public void prepare_noDuts_throwsException() throws Exception {
    // This test is no longer relevant as there's no inventoryClient to return no duts.
    // The decorator now assumes at least one device from getDeviceId().
  }

  @Test
  public void generateCommand_noDutsOrLsNexus() throws Exception {
    // Arrange
    ImmutableList<String> dutNames = ImmutableList.of();
    ImmutableMap<String, String> lsnexusServiceAddresses = ImmutableMap.of();
    when(params.get("inventory_service_host", "localhost")).thenReturn("inv_host");
    when(params.getInt("inventory_service_port", 1485)).thenReturn(9999);

    // Act
    Command command = decorator.generateCommand(testInfo, dutNames, lsnexusServiceAddresses);

    // Assert
    assertThat(command.getCommand())
        .containsExactly(
            DT_CONVERTER_CIPD_PATH,
            "convert",
            "-labservice",
            "inv_host:9999",
            "-logdir",
            "/tmp/test_logs",
            "-config_name",
            "config_test_device",
            "-mh")
        .inOrder();
  }

  @Test
  public void generateCommand_success() throws Exception {
    // Arrange
    ImmutableList<String> dutNames = ImmutableList.of("dut1", "dut2");
    ImmutableMap<String, String> lsnexusServiceAddresses =
        ImmutableMap.of("dut1", "localhost:10001");
    when(params.get("inventory_service_host", "localhost")).thenReturn("inv_host");
    when(params.getInt("inventory_service_port", 1485)).thenReturn(9999);

    // Act
    Command command = decorator.generateCommand(testInfo, dutNames, lsnexusServiceAddresses);

    // Assert
    assertThat(command.getCommand())
        .containsExactly(
            DT_CONVERTER_CIPD_PATH,
            "convert",
            "-unit",
            "dut1",
            "-unit",
            "dut2",
            "-lsnexus",
            "dut1=localhost:10001",
            "-labservice",
            "inv_host:9999",
            "-logdir",
            "/tmp/test_logs",
            "-config_name",
            "config_test_device",
            "-mh")
        .inOrder();
  }

  @Test
  public void updateTestInfo_emptyOutput() throws Exception {
    // Arrange
    String converterOutput = "";

    // Act
    decorator.updateTestInfo(converterOutput, testInfo);

    // Assert
    verify(properties, never()).add(anyString(), anyString());
  }

  @Test
  public void updateTestInfo_success() throws Exception {
    // Arrange
    String converterOutput = "-c /tmp/mobly_config.yaml\n--param1 val1";

    // Act
    decorator.updateTestInfo(converterOutput, testInfo);

    // Assert
    verify(properties).add(MoblyTestSpec.FILE_MOBLY_CONFIG, "/tmp/mobly_config.yaml");
    verify(properties).add("--param1", "val1");
  }

  @Test
  public void updateTestInfo_multipleValuesForSameKey() throws Exception {
    // Arrange
    String converterOutput = "--param1 val1\n--param1 val2";

    // Act
    decorator.updateTestInfo(converterOutput, testInfo);

    // Assert
    verify(properties).add("--param1", "val1");
    verify(properties).add("--param1", "val2");
  }

  @Test
  public void parseOutput_success() {
    // Arrange
    String output = "-c /tmp/mobly_config.yaml\n--param1 val1\n  --param2  val2  ";

    // Act
    List<Map.Entry<String, String>> parsedParams = decorator.parseOutput(output);

    // Assert
    assertThat(parsedParams)
        .containsExactly(
            new AbstractMap.SimpleEntry<>("-c", "/tmp/mobly_config.yaml"),
            new AbstractMap.SimpleEntry<>("--param1", "val1"),
            new AbstractMap.SimpleEntry<>("--param2", "val2"));
  }

  @Test
  public void parseOutput_multipleValuesForSameKey() {
    // Arrange
    String output = "--param1 val1\n--param1 val2";

    // Act
    List<Map.Entry<String, String>> parsedParams = decorator.parseOutput(output);

    // Assert
    assertThat(parsedParams)
        .containsExactly(
            new AbstractMap.SimpleEntry<>("--param1", "val1"),
            new AbstractMap.SimpleEntry<>("--param1", "val2"));
  }

  @Test
  public void parseOutput_emptyOutput() {
    // Arrange
    String output = "";

    // Act
    List<Map.Entry<String, String>> parsedParams = decorator.parseOutput(output);

    // Assert
    assertThat(parsedParams).isEmpty();
  }

  @Test
  public void tearDown_doesNothing() throws Exception {
    // Act & Assert
    // The method is empty, so no exception is expected.
    decorator.tearDown(testInfo);
  }
}
