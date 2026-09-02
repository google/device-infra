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

/*
 * Copyright 2026 Google LLC
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutionException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil.NetworkInterfaceInfo;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link NetworkSimulationDecorator}. */
@RunWith(JUnit4.class)
public class NetworkSimulationDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private CommandExecutor commandExecutor;
  @Mock private NetUtil netUtil;
  @Mock private AndroidAdbUtil androidAdbUtil;

  @Captor private ArgumentCaptor<Command> commandCaptor;

  private Params params;
  private NetworkSimulationDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("fake_device_id");
    when(device.getDeviceTypes()).thenReturn(ImmutableSet.of("AndroidDevice"));
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    params = new Params(null);
    when(jobInfo.params()).thenReturn(params);

    NetworkInterfaceInfo eth1Info =
        NetworkInterfaceInfo.create(
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            ImmutableList.of(InetAddress.getByName("192.168.1.1")));
    when(netUtil.getNetworkInterfaceAndAddress())
        .thenReturn(Optional.of(ImmutableList.of(eth1Info)));

    decorator =
        new NetworkSimulationDecorator(
            decoratedDriver, testInfo, commandExecutor, netUtil, androidAdbUtil);
  }

  @Test
  public void testConstructor_reflectionConstructorCreatesInstance() {
    assertThat(new NetworkSimulationDecorator(decoratedDriver, testInfo)).isNotNull();
  }

  @Test
  public void testOnSetup_withNetworkParams_executesTcset() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY, "100ms");
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "2%");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    // Verify first command is tcset with auto-detected host network exclusion
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps",
            "--delay",
            "100ms",
            "--loss",
            "2%",
            "--exclude-src-network",
            "192.168.1.1")
        .inOrder();

    // Verify second command is tcdel in teardown
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();
  }

  @Test
  public void testOnSetup_withoutNetworkParams_skipsExecution() throws Exception {
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    // Only tcdel should be called during teardown
    verify(commandExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();
  }

  @Test
  public void testOnSetup_withCustomInterface_executesOnCustomInterface() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "10Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_IFACE, "eth2");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo", NetworkSimulationDecorator.TCSET_BIN, "eth2", "--overwrite", "--rate", "10Mbps")
        .inOrder();

    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly("sudo", NetworkSimulationDecorator.TCDEL_BIN, "eth2", "--all")
        .inOrder();
  }

  @Test
  public void testOnSetup_withIncomingDirection_usesExcludeDstNetwork() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "10Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_DIRECTION, "incoming");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "10Mbps",
            "--direction",
            "incoming",
            "--exclude-dst-network",
            "192.168.1.1")
        .inOrder();
  }

  @Test
  public void testOnTeardown_unconditionallyExecutesTcdel() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "1Mbps");
    CommandException mockException = Mockito.mock(CommandException.class);
    when(mockException.getMessage()).thenReturn("Command failed");
    when(commandExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              if (cmd.getCommand().contains(NetworkSimulationDecorator.TCSET_BIN)) {
                throw mockException;
              }
              return "ok";
            });

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.COMMAND_EXEC_FAIL);

    // Verify driver is never run if setup failed
    verify(decoratedDriver, never()).run(testInfo);

    // Verify tcdel was still unconditionally executed in teardown
    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();
  }

  @Test
  public void testMidTestCrash_driverThrowsException_teardownStillExecutesTcdel() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    MobileHarnessException driverCrash =
        new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "Simulated mid-test crash");
    doThrow(driverCrash).when(decoratedDriver).run(testInfo);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).isSameInstanceAs(driverCrash);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand()).contains(NetworkSimulationDecorator.TCSET_BIN);
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();
  }

  @Test
  public void testOnSetup_with100PercentLoss_appliesWithoutClamping() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "100%");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--loss",
            "100%",
            "--exclude-src-network",
            "192.168.1.1")
        .inOrder();
  }

  @Test
  public void testOnSetup_withLossGreaterThan100Percent_throwsMobileHarnessException()
      throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "150%");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR);
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Invalid loss value for network simulation parameter 'net_loss': '150%'."
                + " Packet loss percentage cannot exceed 100%.");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void testOnSetup_with100GbpsRate_executesCorrectly() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "100Gbps");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "100Gbps",
            "--exclude-src-network",
            "192.168.1.1")
        .inOrder();
  }

  @Test
  public void testOnTeardown_whenTcdelThrows_doesNotMaskTestSuccess() throws Exception {
    CommandException mockException = Mockito.mock(CommandException.class);
    when(mockException.getMessage()).thenReturn("tcdel failed");
    when(commandExecutor.run(any(Command.class))).thenThrow(mockException);

    // Decorator run should complete without throwing exception from teardown
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor).run(any(Command.class));
  }

  @Test
  public void testOnSetup_withWhitespaceAndQuotedParams_sanitizesCorrectly() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "  \"5Mbps\"  ");
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY, "  '100ms'  ");
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "  2%  ");
    params.add(NetworkSimulationDecorator.PARAM_NET_IFACE, "  eth1  ");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            "eth1",
            "--overwrite",
            "--rate",
            "5Mbps",
            "--delay",
            "100ms",
            "--loss",
            "2%",
            "--exclude-src-network",
            "192.168.1.1")
        .inOrder();
  }

  @Test
  public void testOnSetup_withBlankWhitespaceParams_skipsExecution() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "   ");
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY, "");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    // Skips tcset, only runs tcdel in teardown
    verify(commandExecutor).run(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();
  }

  @Test
  public void testOnSetup_whenTcsetFails_extractsDiagnosticOutput() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "10Mbps");
    CommandExecutionException mockExecutionException =
        Mockito.mock(CommandExecutionException.class);
    CommandResult mockResult = Mockito.mock(CommandResult.class);
    when(mockResult.stderr()).thenReturn("[ERROR] tc error: Cannot find device \"eth1\"");
    when(mockResult.stdout()).thenReturn("");
    when(mockExecutionException.result()).thenReturn(mockResult);

    when(commandExecutor.run(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              if (cmd.getCommand().contains(NetworkSimulationDecorator.TCSET_BIN)) {
                throw mockExecutionException;
              }
              return "ok";
            });

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).hasMessageThat().contains("[ERROR] tc error: Cannot find device \"eth1\"");
  }

  @Test
  public void testOnSetup_withAllShapingParams_addsAllFlags() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "20Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY, "50ms");
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY_DISTRO, "10ms");
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "1%");

    decorator.run(testInfo);

    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "20Mbps",
            "--delay",
            "50ms",
            "--delay-distro",
            "10ms",
            "--loss",
            "1%",
            "--exclude-src-network",
            "192.168.1.1")
        .inOrder();
  }

  @Test
  public void testOnSetup_negativeRate_throwsMobileHarnessException() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "-100Mbps");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Invalid negative value for network simulation parameter 'net_rate': '-100Mbps'");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void testOnSetup_negativeDelay_throwsMobileHarnessException() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY, "-50ms");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Invalid negative value for network simulation parameter 'net_delay': '-50ms'");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void testOnSetup_negativeLoss_throwsMobileHarnessException() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_LOSS, "-5%");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR);
    assertThat(thrown)
        .hasMessageThat()
        .contains("Invalid negative value for network simulation parameter 'net_loss': '-5%'");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void testOnSetup_negativeDelayDistro_throwsMobileHarnessException() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_DELAY_DISTRO, "-10ms");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId()).isEqualTo(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR);
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Invalid negative value for network simulation parameter 'net_delay_distro': '-10ms'");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void testOnSetup_withExplicitExcludeNetwork_outgoing_usesExcludeSrcNetwork()
      throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_EXCLUDE_NETWORK, "192.168.1.100");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps",
            "--exclude-src-network",
            "192.168.1.100")
        .inOrder();
  }

  @Test
  public void testOnSetup_withExplicitExcludeNetwork_incoming_usesExcludeDstNetwork()
      throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_DIRECTION, "incoming");
    params.add(NetworkSimulationDecorator.PARAM_NET_EXCLUDE_NETWORK, "192.168.1.0/24");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps",
            "--direction",
            "incoming",
            "--exclude-dst-network",
            "192.168.1.0/24")
        .inOrder();
  }

  @Test
  public void testOnSetup_explicitExcludeNetworkNone_disablesNetworkExclusion() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_EXCLUDE_NETWORK, "none");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps")
        .inOrder();
  }

  @Test
  public void testOnSetup_explicitExcludeNetworkFalse_disablesNetworkExclusion() throws Exception {
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");
    params.add(NetworkSimulationDecorator.PARAM_NET_EXCLUDE_NETWORK, "false");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps")
        .inOrder();
  }

  @Test
  public void testOnSetup_whenNetUtilReturnsEmpty_proceedsWithoutExcludeNetwork() throws Exception {
    when(netUtil.getNetworkInterfaceAndAddress()).thenReturn(Optional.empty());
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps")
        .inOrder();
  }

  @Test
  public void testOnSetup_whenNetUtilThrows_handlesGracefullyAndProceeds() throws Exception {
    when(netUtil.getNetworkInterfaceAndAddress())
        .thenThrow(new MobileHarnessException(BasicErrorId.LOCAL_NETWORK_ERROR, "net failure"));
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(commandExecutor, times(2)).run(commandCaptor.capture());

    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCSET_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--overwrite",
            "--rate",
            "5Mbps")
        .inOrder();
  }

  @Test
  public void testOnTeardown_withNetworkDeviceId_reconnectsAdbViaAndroidAdbUtil() throws Exception {
    when(device.getDeviceId()).thenReturn("192.168.1.2:5555");
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    decorator.run(testInfo);

    // 1 tcset (setup), 1 tcdel (teardown)
    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(
            "sudo",
            NetworkSimulationDecorator.TCDEL_BIN,
            NetworkSimulationDecorator.DEFAULT_INTERFACE,
            "--all")
        .inOrder();

    // Verify ADB reconnect was called via AndroidAdbUtil
    verify(androidAdbUtil)
        .connect("192.168.1.2:5555", NetworkSimulationDecorator.ADB_CONNECT_TIMEOUT);
  }

  @Test
  public void testOnTeardown_withUsbDeviceId_skipsAdbConnect() throws Exception {
    when(device.getDeviceId()).thenReturn("HT1234567890");
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    decorator.run(testInfo);

    // 1 tcset (setup), 1 tcdel (teardown), no adb connect because deviceId has no ':'
    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    verify(androidAdbUtil, never()).connect(any(), any());
  }

  @Test
  public void testOnTeardown_withNonAndroidDevice_skipsAdbConnect() throws Exception {
    when(device.getDeviceId()).thenReturn("192.168.1.2:5555");
    when(device.getDeviceTypes()).thenReturn(ImmutableSet.of("RokuDevice"));
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    decorator.run(testInfo);

    // 1 tcset (setup), 1 tcdel (teardown), no adb connect because device is not Android
    verify(commandExecutor, times(2)).run(commandCaptor.capture());
    verify(androidAdbUtil, never()).connect(any(), any());
  }

  @Test
  public void testOnTeardown_whenAdbConnectFails_doesNotMaskDriverSuccess() throws Exception {
    when(device.getDeviceId()).thenReturn("192.168.1.2:5555");
    params.add(NetworkSimulationDecorator.PARAM_NET_RATE, "5Mbps");

    doThrow(new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "connection refused"))
        .when(androidAdbUtil)
        .connect(any(), any());

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(androidAdbUtil)
        .connect("192.168.1.2:5555", NetworkSimulationDecorator.ADB_CONNECT_TIMEOUT);
  }
}
