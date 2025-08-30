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
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DEFAULT_INVENTORY_SERVICE_HOST;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DEFAULT_INVENTORY_SERVICE_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.INVENTORY_SERVICE_HOST;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.INVENTORY_SERVICE_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.LSNEXUS_CIPD_PATH;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.LSNEXUS_PARAM_SUFFIX;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link CrosLsNexusDecorator}. */
@RunWith(JUnit4.class)
public class CrosLsNexusDecoratorTest {

  private static final String DUT_NAME = "test_device";
  private static final String DEVICE_ID = "test_device:5555";
  private static final String DEVICE_UUID = "test_device_uuid";
  private static final String SERVICE_HOSTNAME = "localhost";
  private static final int SERVICE_PORT = 1234;
  private static final String INVENTORY_SERVICE_HOST_VALUE = "testhost";
  private static final int INVENTORY_SERVICE_PORT_VALUE = 1234;
  private static final String INVENTORY_SERVICE_ADDRESS =
      INVENTORY_SERVICE_HOST_VALUE + ":" + INVENTORY_SERVICE_PORT_VALUE;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private CommandExecutor commandExecutor;
  @Mock private CrosLsNexusDecorator.PortPicker portPicker;
  @Mock private Device device;
  @Mock private Properties properties;
  @Mock private Log testLogger;
  @Mock private Api atInfo;
  @Mock private CommandProcess commandProcess;
  @Mock private JobInfo jobInfo;
  @Mock private Params params;

  private CrosLsNexusDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(device.getDeviceUuid()).thenReturn(DEVICE_UUID);
    when(testInfo.log()).thenReturn(testLogger);
    when(testLogger.atInfo()).thenReturn(atInfo);
    when(testLogger.atWarning()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(atInfo.withCause(any(Throwable.class))).thenReturn(atInfo);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.getGenFileDir()).thenReturn("/tmp/gen_files");
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.params()).thenReturn(params);
    when(params.get(INVENTORY_SERVICE_HOST, DEFAULT_INVENTORY_SERVICE_HOST))
        .thenReturn(INVENTORY_SERVICE_HOST_VALUE);
    when(params.getInt(INVENTORY_SERVICE_PORT, DEFAULT_INVENTORY_SERVICE_PORT))
        .thenReturn(INVENTORY_SERVICE_PORT_VALUE);

    decorator = new CrosLsNexusDecorator(decoratedDriver, testInfo, commandExecutor, portPicker);
    when(commandExecutor.start(any(Command.class))).thenReturn(commandProcess);
  }

  @Test
  public void prepare_startsLsNexusAndAddsProperty() throws Exception {
    when(portPicker.pick()).thenReturn(SERVICE_PORT);
    decorator.prepare(testInfo);

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var command = commandCaptor.getValue().getCommand();

    assertThat(command)
        .containsAtLeast(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            String.valueOf(SERVICE_PORT),
            "-logs",
            "/tmp/gen_files/lsnexus_" + DUT_NAME,
            "-dut",
            DUT_NAME,
            /* lab_service_address */ "-labservice",
            INVENTORY_SERVICE_ADDRESS);
    verify(properties).add(LSNEXUS_PARAM_SUFFIX + DUT_NAME, SERVICE_HOSTNAME + ":" + SERVICE_PORT);
  }

  @Test
  public void prepare_getFreePortFails_throwsMobileHarnessException() throws Exception {
    when(portPicker.pick()).thenThrow(new IOException("Test exception"));

    assertThrows(MobileHarnessException.class, () -> decorator.prepare(testInfo));
  }

  @Test
  public void startLsNexusService_startsProcessAndReturnsProcess() throws Exception {
    String genFileDir = "/tmp/gen_files";
    when(testInfo.getGenFileDir()).thenReturn(genFileDir);
    CommandProcess mockCommandProcess = mock(CommandProcess.class);
    when(commandExecutor.start(any(Command.class))).thenReturn(mockCommandProcess);

    CommandProcess result = decorator.startLsNexusService(testInfo, DUT_NAME, SERVICE_PORT);

    assertThat(result).isEqualTo(mockCommandProcess);

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var command = commandCaptor.getValue().getCommand();

    assertThat(command)
        .containsAtLeast(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            String.valueOf(SERVICE_PORT),
            "-logs",
            genFileDir + "/lsnexus_" + DUT_NAME,
            "-dut",
            DUT_NAME,
            /* lab_service_address */ "-labservice",
            INVENTORY_SERVICE_ADDRESS);
  }

  @Test
  public void tearDown_noServiceStarted_doesNothing() {
    decorator.tearDown(testInfo);

    verify(commandProcess, never()).stop();
  }

  @Test
  public void tearDown_whenCalledTwice_stopsServiceOnce() throws Exception {
    when(portPicker.pick()).thenReturn(SERVICE_PORT);
    decorator.prepare(testInfo);
    decorator.tearDown(testInfo);
    decorator.tearDown(testInfo);

    // Verify that the stop method was called exactly once.
    verify(commandProcess).stop();
  }

  @Test
  public void lsnexusAddress_returnsCorrectlyFormattedString() {
    assertThat(decorator.lsnexusAddress(1234)).isEqualTo("localhost:1234");
    assertThat(decorator.lsnexusAddress(5678)).isEqualTo("localhost:5678");
  }
}
