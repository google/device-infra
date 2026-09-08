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
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.LSNEXUS_CIPD_TAG;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.LSNEXUS_PARAM_SUFFIX;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private CommandExecutor commandExecutor;
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
    when(params.has(LSNEXUS_CIPD_TAG)).thenReturn(true);
    when(params.get(LSNEXUS_CIPD_TAG)).thenReturn("");
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
              }
            });
    decorator = injector.getInstance(CrosLsNexusDecorator.class);
    decorator = spy(decorator);
    when(commandExecutor.start(any(Command.class))).thenReturn(commandProcess);
  }

  @After
  public void tearDownDecorator() {
    if (decorator != null) {
      decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    }
  }

  @Test
  public void prepare_success_startsServiceWithCorrectCommand() throws Exception {
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var command = commandCaptor.getValue().getCommand();
    assertThat(command)
        .containsAtLeast(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            "0",
            "-logs",
            "/tmp/gen_files/lsnexus_" + DUT_NAME,
            "-dut",
            DUT_NAME,
            "-labservice",
            INVENTORY_SERVICE_ADDRESS)
        .inOrder();
  }

  @Test
  public void prepare_success_addsServiceAddressToTestProperties() throws Exception {
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    verify(properties).add(LSNEXUS_PARAM_SUFFIX + DUT_NAME, SERVICE_HOSTNAME + ":" + SERVICE_PORT);
  }

  @Test
  public void prepare_readPortFromLsNexusServiceThrowsException_throwsException() throws Exception {
    doThrow(new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "test"))
        .when(decorator)
        .readPortFromLsNexusService(any(), any(), any(), any());

    assertThrows(
        MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
  }

  @Test
  public void createLsNexusServiceLogDir_returnsCorrectPath() throws MobileHarnessException {
    when(testInfo.getGenFileDir()).thenReturn("/tmp/gen");
    String logDir = decorator.createLsNexusServiceLogDir(testInfo, DUT_NAME);
    assertThat(logDir).isEqualTo("/tmp/gen/lsnexus_" + DUT_NAME);
  }

  @Test
  public void startLsNexusService_success_startsProcessWithCorrectCommand()
      throws MobileHarnessException {
    String logDir = "/tmp/service_log_dir";

    var unused = decorator.startLsNexusService(testInfo, DUT_NAME, logDir);

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor).start(commandCaptor.capture());
    var command = commandCaptor.getValue().getCommand();

    assertThat(command)
        .containsExactly(
            LSNEXUS_CIPD_PATH,
            "server",
            "-port",
            "0",
            "-logs",
            logDir,
            "-dut",
            DUT_NAME,
            "-labservice",
            INVENTORY_SERVICE_ADDRESS)
        .inOrder();
  }

  @Test
  public void startLsNexusService_success_returnsProcessFromExecutor() throws Exception {
    CommandProcess mockCommandProcess = mock(CommandProcess.class);
    when(commandExecutor.start(any(Command.class))).thenReturn(mockCommandProcess);

    CommandProcess result = decorator.startLsNexusService(testInfo, DUT_NAME, "/tmp/log_dir");

    assertThat(result).isSameInstanceAs(mockCommandProcess);
  }

  @Test
  public void readPortFromLsNexusService_success_returnsPortNumber() throws Exception {
    File logDir = tempFolder.newFolder("logs");
    File portFile = new File(logDir, "lsnexus_port");
    Files.writeString(portFile.toPath(), " " + SERVICE_PORT + "\n");

    int port =
        decorator.readPortFromLsNexusService(
            testInfo, commandProcess, logDir.getAbsolutePath(), Duration.ofSeconds(1));

    assertThat(port).isEqualTo(SERVICE_PORT);
  }

  @Test
  public void readPortFromLsNexusService_success_doesNotKillProcess() throws Exception {
    File logDir = tempFolder.newFolder("logs");
    File portFile = new File(logDir, "lsnexus_port");
    Files.writeString(portFile.toPath(), " " + SERVICE_PORT + "\n");

    int unused =
        decorator.readPortFromLsNexusService(
            testInfo, commandProcess, logDir.getAbsolutePath(), Duration.ofSeconds(1));

    verify(commandProcess, never()).kill();
  }

  @Test
  public void readPortFromLsNexusService_timeout_throwsException() throws Exception {
    File logDir = tempFolder.newFolder("logs");

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                decorator.readPortFromLsNexusService(
                    testInfo, commandProcess, logDir.getAbsolutePath(), Duration.ofMillis(10)));
    assertThat(e).hasMessageThat().contains("LSNexus service failed to start");
  }

  @Test
  public void readPortFromLsNexusService_timeout_killsProcess() throws Exception {
    File logDir = tempFolder.newFolder("logs");
    assertThrows(
        MobileHarnessException.class,
        () ->
            decorator.readPortFromLsNexusService(
                testInfo, commandProcess, logDir.getAbsolutePath(), Duration.ofMillis(10)));
    verify(commandProcess).kill();
  }

  @Test
  public void tearDown_success_stopsService() throws Exception {
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());
    decorator.setUp(SetupContext.create(testInfo));
    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(commandProcess).stop();
  }

  @Test
  public void tearDown_serviceNotStarted_doesNothing() throws Exception {
    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    verify(commandProcess, never()).stop();
  }

  @Test
  public void tearDown_afterPrepare_isIdempotent() throws Exception {
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());
    // Start the service once.
    decorator.setUp(SetupContext.create(testInfo));
    // Call tearDown twice to ensure it is idempotent.
    decorator.tearDown(TeardownContext.create(testInfo, null, null));
    decorator.tearDown(TeardownContext.create(testInfo, null, null));

    // Verify that the stop method was called exactly once.
    verify(commandProcess).stop();
  }

  @Test
  public void lsnexusAddress_validPortNumber_returnsCorrectlyFormattedString() {
    assertThat(decorator.lsnexusAddress(12)).isEqualTo("localhost:12");
    assertThat(decorator.lsnexusAddress(3456)).isEqualTo("localhost:3456");
  }

  private void mockCipdDownload() throws Exception {
    CommandResult downloadResult = mock(CommandResult.class);
    when(downloadResult.stdout()).thenReturn("install success");
    when(commandExecutor.exec(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              ImmutableList<String> cmdArgs = cmd.getCommand();
              int rootIdx = cmdArgs.indexOf("-root");
              if (rootIdx != -1 && rootIdx + 1 < cmdArgs.size()) {
                Path rootPath = Path.of(cmdArgs.get(rootIdx + 1));
                Path bin = rootPath.resolve("lsnexus");
                if (!Files.exists(bin)) {
                  Files.createFile(bin);
                }
              }
              return downloadResult;
            });
  }

  @Test
  public void setUp_defaultTag_downloadsProdPackage() throws Exception {
    when(params.has(LSNEXUS_CIPD_TAG)).thenReturn(false);
    when(params.get(LSNEXUS_CIPD_TAG)).thenReturn(null);

    mockCipdDownload();
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(decorator.getResolvedLsNexusPath()).isNotEqualTo(LSNEXUS_CIPD_PATH);
    assertThat(decorator.getResolvedLsNexusPath()).contains("lsnexus");
    assertThat(decorator.getCipdDownloadedDir()).isNotNull();
    assertThat(Files.exists(decorator.getCipdDownloadedDir())).isTrue();

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(2)).exec(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsAtLeast(
            "install",
            CrosDecoratorSpec.LSNEXUS_PACKAGE,
            CrosDecoratorSpec.DEFAULT_CIPD_TAG,
            "-root")
        .inOrder();
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(decorator.getResolvedLsNexusPath(), "version")
        .inOrder();

    Path downloadedDir = decorator.getCipdDownloadedDir();
    // Clean up
    decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    assertThat(decorator.getCipdDownloadedDir()).isNull();
    assertThat(Files.exists(downloadedDir)).isFalse();
  }

  @Test
  public void setUp_withCustomCipdTag_downloadsPackageAndUsesDownloadedBinary() throws Exception {
    when(params.has(LSNEXUS_CIPD_TAG)).thenReturn(true);
    when(params.get(LSNEXUS_CIPD_TAG)).thenReturn("custom_tag");

    mockCipdDownload();
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(decorator.getResolvedLsNexusPath()).isNotEqualTo(LSNEXUS_CIPD_PATH);
    Path downloadedDir = decorator.getCipdDownloadedDir();
    assertThat(downloadedDir).isNotNull();
    assertThat(Files.exists(downloadedDir)).isTrue();

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(2)).exec(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsAtLeast("install", CrosDecoratorSpec.LSNEXUS_PACKAGE, "custom_tag", "-root")
        .inOrder();
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(decorator.getResolvedLsNexusPath(), "version")
        .inOrder();

    // Clean up
    decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    assertThat(decorator.getCipdDownloadedDir()).isNull();
    assertThat(Files.exists(downloadedDir)).isFalse();
  }

  @Test
  public void setUp_withKebabCaseCipdTag_downloadsPackageAndUsesDownloadedBinary()
      throws Exception {
    when(params.has(LSNEXUS_CIPD_TAG)).thenReturn(false);
    when(params.has("lsnexus-cipd-tag")).thenReturn(true);
    when(params.get("lsnexus-cipd-tag")).thenReturn("kebab_tag");

    mockCipdDownload();
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(decorator.getResolvedLsNexusPath()).isNotEqualTo(LSNEXUS_CIPD_PATH);
    Path downloadedDir = decorator.getCipdDownloadedDir();
    assertThat(downloadedDir).isNotNull();
    assertThat(Files.exists(downloadedDir)).isTrue();

    var commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(2)).exec(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();
    assertThat(capturedCommands.get(0).getCommand())
        .containsAtLeast("install", CrosDecoratorSpec.LSNEXUS_PACKAGE, "kebab_tag", "-root")
        .inOrder();
    assertThat(capturedCommands.get(1).getCommand())
        .containsExactly(decorator.getResolvedLsNexusPath(), "version")
        .inOrder();

    // Clean up
    decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    assertThat(decorator.getCipdDownloadedDir()).isNull();
    assertThat(Files.exists(downloadedDir)).isFalse();
  }

  @Test
  public void setUp_withEmptyCipdTag_fallsBackToPreinstalledBinary() throws Exception {
    when(params.has(LSNEXUS_CIPD_TAG)).thenReturn(true);
    when(params.get(LSNEXUS_CIPD_TAG)).thenReturn("");
    doReturn(SERVICE_PORT).when(decorator).readPortFromLsNexusService(any(), any(), any(), any());

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(decorator.getResolvedLsNexusPath()).isEqualTo(LSNEXUS_CIPD_PATH);
    assertThat(decorator.getCipdDownloadedDir()).isNull();
  }
}
