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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import javax.inject.Provider;
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
  private static final String SERVICE_ADDRESS = SERVICE_HOSTNAME + ":" + SERVICE_PORT;
  private static final String INVENTORY_SERVICE_HOST_VALUE = "testhost";
  private static final int INVENTORY_SERVICE_PORT_VALUE = 1234;
  private static final String INVENTORY_SERVICE_ADDRESS =
      INVENTORY_SERVICE_HOST_VALUE + ":" + INVENTORY_SERVICE_PORT_VALUE;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private CommandExecutor commandExecutor;
  @Mock private Provider<CrosLsNexusDecorator.ServiceInfo.Builder> serviceInfoBuilderProvider;
  @Mock private CrosLsNexusDecorator.ServiceInfo.Builder serviceInfoBuilder;
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

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Driver.class).toInstance(decoratedDriver);
                bind(TestInfo.class).toInstance(testInfo);
                bind(CommandExecutor.class).toInstance(commandExecutor);
                bind(CrosLsNexusDecorator.ServiceInfo.Builder.class)
                    .toProvider(serviceInfoBuilderProvider);
              }
            });
    decorator = injector.getInstance(CrosLsNexusDecorator.class);

    when(commandExecutor.start(any(Command.class))).thenReturn(commandProcess);
    when(serviceInfoBuilderProvider.get()).thenReturn(serviceInfoBuilder);

    // Use ArgumentCaptors to capture the values passed to the builder
    final ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
    final ArgumentCaptor<String> dutNameCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<CommandProcess> processCaptor =
        ArgumentCaptor.forClass(CommandProcess.class);

    // When the setters are called, just return the builder mock and capture the args
    when(serviceInfoBuilder.setPort(portCaptor.capture())).thenReturn(serviceInfoBuilder);
    when(serviceInfoBuilder.setDutName(dutNameCaptor.capture())).thenReturn(serviceInfoBuilder);
    when(serviceInfoBuilder.setProcess(processCaptor.capture())).thenReturn(serviceInfoBuilder);

    // When build() is called, use the captured values to create a real ServiceInfo object
    when(serviceInfoBuilder.build())
        .thenAnswer(
            invocation -> {
              return CrosLsNexusDecorator.ServiceInfo.builder()
                  .setPort(portCaptor.getValue())
                  .setDutName(dutNameCaptor.getValue())
                  .setProcess(processCaptor.getValue())
                  .build();
            });
  }

  @Test
  public void prepare_startsLsNexusAndAddsProperty() throws Exception {
    decorator.prepare(testInfo);

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var commandList = commandCaptor.getValue().getCommand();
    int portIndex = commandList.indexOf("-port");
    assertThat(portIndex).isNotEqualTo(-1);
    // Port is the next element after -port in the command list.
    int port = Integer.parseInt(commandList.get(portIndex + 1));

    assertThat(commandList)
        .containsAtLeast(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            String.valueOf(port),
            "-logs",
            "/tmp/gen_files/lsnexus_" + DUT_NAME,
            "-dut",
            DUT_NAME,
            /* lab_service_address */ "-labservice",
            INVENTORY_SERVICE_ADDRESS);
    verify(serviceInfoBuilderProvider).get();
    verify(serviceInfoBuilder).setPort(port);
    verify(serviceInfoBuilder).setDutName(DUT_NAME);
    verify(serviceInfoBuilder).setProcess(commandProcess);
    verify(serviceInfoBuilder).build();
    verify(properties).add(LSNEXUS_PARAM_SUFFIX + DUT_NAME, SERVICE_HOSTNAME + ":" + port);
  }

  @Test
  public void startLsNexusService_startsProcessAndReturnsServiceInfo() throws Exception {
    String genFileDir = "/tmp/gen_files";
    when(testInfo.getGenFileDir()).thenReturn(genFileDir);
    CommandProcess mockCommandProcess = mock(CommandProcess.class);
    when(commandExecutor.start(any(Command.class))).thenReturn(mockCommandProcess);

    CrosLsNexusDecorator.ServiceInfo result = decorator.startLsNexusService(testInfo, DUT_NAME);

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var command = commandCaptor.getValue().getCommand();
    int portIndex = command.indexOf("-port");
    assertThat(portIndex).isNotEqualTo(-1);
    // Port is the next element after -port in the command list.
    int port = Integer.parseInt(command.get(portIndex + 1));
    assertThat(result)
        .isEqualTo(
            CrosLsNexusDecorator.ServiceInfo.builder()
                .setPort(port)
                .setDutName(DUT_NAME)
                .setProcess(mockCommandProcess)
                .build());

    assertThat(command)
        .containsAtLeast(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            String.valueOf(port),
            "-logs",
            genFileDir + "/lsnexus_" + DUT_NAME,
            "-dut",
            DUT_NAME,
            /* lab_service_address */ "-labservice",
            INVENTORY_SERVICE_ADDRESS);
    verify(serviceInfoBuilderProvider).get();
    verify(serviceInfoBuilder).setPort(port);
    verify(serviceInfoBuilder).setDutName(DUT_NAME);
    verify(serviceInfoBuilder).setProcess(mockCommandProcess);
    verify(serviceInfoBuilder).build();
  }

  @Test
  public void tearDown_whenCalledTwice_stopsServiceOnce() throws Exception {
    decorator.prepare(testInfo);
    decorator.tearDown(testInfo);
    decorator.tearDown(testInfo);

    // Verify that the stop method was called exactly once.
    verify(commandProcess).stop();
  }

  @Test
  public void serviceInfo_getters_returnCorrectValues() {
    CommandProcess mockProcess = mock(CommandProcess.class);

    CrosLsNexusDecorator.ServiceInfo serviceInfo =
        CrosLsNexusDecorator.ServiceInfo.builder()
            .setPort(SERVICE_PORT)
            .setDutName(DUT_NAME)
            .setProcess(mockProcess)
            .build();

    assertThat(serviceInfo)
        .isEqualTo(
            CrosLsNexusDecorator.ServiceInfo.builder()
                .setPort(SERVICE_PORT)
                .setDutName(DUT_NAME)
                .setProcess(mockProcess)
                .build());
    assertThat(serviceInfo.getAddress()).isEqualTo(SERVICE_ADDRESS);
  }

  @Test
  public void serviceInfo_close_stopsProcess() {
    CommandProcess mockProcess = mock(CommandProcess.class);
    CrosLsNexusDecorator.ServiceInfo serviceInfo =
        CrosLsNexusDecorator.ServiceInfo.builder()
            .setPort(8080)
            .setDutName("test_dut")
            .setProcess(mockProcess)
            .build();
    serviceInfo.close();
    verify(mockProcess).stop();
  }
}
