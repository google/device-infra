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
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.BUILD_ID;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.BUILD_TARGET;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.FOIL_PROVISION_CIPD_PATH;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.FOIL_PROVISION_CIPD_TAG;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.SKIP_STABLE_VERSION;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.USE_SIGNED_IMAGE;
import static com.google.wireless.qa.mobileharness.shared.api.decorator.CrossOverAndroidDesktopProvisionDecorator.USE_TEST_RAMDISK;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DT_CONVERTER_CIPD_PATH;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DT_CONVERTER_CIPD_TAG;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Unit tests for {@link CrossOverAndroidDesktopProvisionDecorator}. */
@RunWith(JUnit4.class)
public class CrossOverAndroidDesktopProvisionDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Driver driver;
  @Mock private Device device;
  @Mock private CommandExecutor commandExecutor;
  @Mock private LocalFileUtil fileUtil;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Log log;
  @Mock private Params params;
  @Mock private Properties properties;
  @Mock private Api atInfo;
  @Mock private Api alsoTo;
  @Mock private CommandResult versionResult;
  @Mock private CommandResult provisionResult;
  @Mock private CommandResult stableVersionResult;

  private CrossOverAndroidDesktopProvisionDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.properties()).thenReturn(properties);
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_files");
    when(jobInfo.params()).thenReturn(params);
    when(driver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("test_dut:1234");
    when(log.atInfo()).thenReturn(atInfo);
    when(log.atWarning()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(alsoTo);
    when(alsoTo.withCause(any(Throwable.class))).thenReturn(alsoTo);

    // Default CIPD tag empty -> fallback to pre-installed binaries
    when(params.has(FOIL_PROVISION_CIPD_TAG)).thenReturn(true);
    when(params.get(FOIL_PROVISION_CIPD_TAG)).thenReturn("");
    when(params.has(DT_CONVERTER_CIPD_TAG)).thenReturn(true);
    when(params.get(DT_CONVERTER_CIPD_TAG)).thenReturn("");

    // Default inventory service address
    when(params.get("inventory_service_host", "localhost")).thenReturn("localhost");
    when(params.getInt("inventory_service_port", 1485)).thenReturn(1485);

    // Default version command result
    when(versionResult.exitCode()).thenReturn(0);
    when(versionResult.stdout()).thenReturn("1.0.0");

    decorator =
        new CrossOverAndroidDesktopProvisionDecorator(driver, testInfo, commandExecutor, fileUtil);
  }

  @After
  public void tearDownDecorator() throws Exception {
    if (decorator != null) {
      decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    }
  }

  @Test
  public void constructors_initializeDefaultDependencies() throws Exception {
    CrossOverAndroidDesktopProvisionDecorator injectedDecorator =
        new CrossOverAndroidDesktopProvisionDecorator(driver, testInfo, commandExecutor);
    assertThat(
            injectedDecorator
                .generateProvisionCommand(testInfo, "test_dut", "123", "brya")
                .getCommand())
        .contains(FOIL_PROVISION_CIPD_PATH);

    CrossOverAndroidDesktopProvisionDecorator defaultDecorator =
        new CrossOverAndroidDesktopProvisionDecorator(driver, testInfo);
    assertThat(
            defaultDecorator
                .generateProvisionCommand(testInfo, "test_dut", "123", "brya")
                .getCommand())
        .contains(FOIL_PROVISION_CIPD_PATH);
  }

  @Test
  public void setUp_explicitBuildParameters_executesFoilProvisionDirectly() throws Exception {
    when(params.has(BUILD_ID)).thenReturn(true);
    when(params.get(BUILD_ID)).thenReturn("12345678");
    when(params.has(BUILD_TARGET)).thenReturn(true);
    when(params.get(BUILD_TARGET)).thenReturn("brya-trunk_staging-userdebug");

    when(provisionResult.exitCode()).thenReturn(0);
    when(provisionResult.stdout()).thenReturn("Foil provision completed successfully.");
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenReturn(provisionResult);

    decorator.setUp(SetupContext.create(testInfo));

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(2)).exec(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    Command versionCommand = capturedCommands.get(0);
    assertThat(versionCommand.getCommand())
        .containsExactly(FOIL_PROVISION_CIPD_PATH, "version")
        .inOrder();

    Command provisionCommand = capturedCommands.get(1);
    assertThat(provisionCommand.getCommand())
        .containsExactly(
            FOIL_PROVISION_CIPD_PATH,
            "ate",
            "-dut-name",
            "test_dut",
            "-build-id",
            "12345678",
            "-build-target",
            "brya-trunk_staging-userdebug",
            "-labservice-address",
            "localhost:1485",
            "-log-path",
            "/tmp/test_gen_files")
        .inOrder();
  }

  @Test
  public void setUp_missingBuildParameters_resolvesViaDtConverterStableVersion() throws Exception {
    when(params.has(BUILD_ID)).thenReturn(false);
    when(params.has(BUILD_TARGET)).thenReturn(false);

    String stableVersionJson =
        """
        {
          "android": {
            "build_id": "98765432",
            "build_target": "brya-trunk_staging-userdebug",
            "os_version": "98765432",
            "os_image_path": "artifacts_list/98765432/brya-ota.zip"
          }
        }
        """;
    when(stableVersionResult.exitCode()).thenReturn(0);
    when(stableVersionResult.stdout()).thenReturn(stableVersionJson);
    when(provisionResult.exitCode()).thenReturn(0);
    when(provisionResult.stdout()).thenReturn("Provisioned OK");

    // Command sequence:
    // 1. dt-converter version
    // 2. dt-converter stable-version
    // 3. foil-provision version
    // 4. foil-provision ate
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenReturn(stableVersionResult)
        .thenReturn(versionResult)
        .thenReturn(provisionResult);

    decorator.setUp(SetupContext.create(testInfo));

    verify(properties).add("crossover_build_id", "98765432");
    verify(properties).add("crossover_build_target", "brya-trunk_staging-userdebug");

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(4)).exec(commandCaptor.capture());
    List<Command> capturedCommands = commandCaptor.getAllValues();

    Command dtVersionCmd = capturedCommands.get(0);
    assertThat(dtVersionCmd.getCommand())
        .containsExactly(DT_CONVERTER_CIPD_PATH, "version")
        .inOrder();

    Command stableVersionCmd = capturedCommands.get(1);
    assertThat(stableVersionCmd.getCommand())
        .containsExactly(
            DT_CONVERTER_CIPD_PATH,
            "stable-version",
            "-unit",
            "test_dut",
            "-labservice",
            "localhost:1485",
            "-device-type",
            "androidos",
            "-json")
        .inOrder();

    Command foilVersionCmd = capturedCommands.get(2);
    assertThat(foilVersionCmd.getCommand())
        .containsExactly(FOIL_PROVISION_CIPD_PATH, "version")
        .inOrder();

    Command provisionCmd = capturedCommands.get(3);
    assertThat(provisionCmd.getCommand())
        .containsExactly(
            FOIL_PROVISION_CIPD_PATH,
            "ate",
            "-dut-name",
            "test_dut",
            "-build-id",
            "98765432",
            "-build-target",
            "brya-trunk_staging-userdebug",
            "-labservice-address",
            "localhost:1485",
            "-log-path",
            "/tmp/test_gen_files")
        .inOrder();
  }

  @Test
  public void setUp_skipStableVersionWithMissingParams_throwsMobileHarnessException()
      throws Exception {
    when(params.has(BUILD_ID)).thenReturn(false);
    when(params.has(BUILD_TARGET)).thenReturn(false);
    when(params.has(SKIP_STABLE_VERSION)).thenReturn(true);
    when(params.get(SKIP_STABLE_VERSION)).thenReturn("true");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Missing build_id or build_target for device test_dut");
  }

  @Test
  public void setUp_missingStableVersionResponseFields_throwsMobileHarnessException()
      throws Exception {
    when(params.has(BUILD_ID)).thenReturn(false);
    when(params.has(BUILD_TARGET)).thenReturn(false);
    when(stableVersionResult.stdout()).thenReturn("{\"android\": {}}");
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenReturn(stableVersionResult);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Missing build_id or build_target for device test_dut");
  }

  @Test
  public void setUp_foilProvisionExecutionFails_throwsMobileHarnessException() throws Exception {
    when(params.has(BUILD_ID)).thenReturn(true);
    when(params.get(BUILD_ID)).thenReturn("12345678");
    when(params.has(BUILD_TARGET)).thenReturn(true);
    when(params.get(BUILD_TARGET)).thenReturn("brya-trunk_staging-userdebug");

    com.google.devtools.mobileharness.shared.util.command.CommandException cmdException =
        org.mockito.Mockito.mock(
            com.google.devtools.mobileharness.shared.util.command.CommandException.class);
    when(cmdException.getErrorId())
        .thenReturn(
            com.google.devtools.mobileharness.api.model.error.BasicErrorId.COMMAND_EXEC_FAIL);
    when(cmdException.getMessage()).thenReturn("Fastboot timeout");
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenThrow(cmdException);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
    assertThat(thrown).hasMessageThat().contains("foil-provision failed for device test_dut");
    assertThat(thrown).hasMessageThat().contains("Fastboot timeout");
  }

  @Test
  public void setUp_withSignedImageAndTestRamdiskFlags_forwardsFlagsToCommand() throws Exception {
    when(params.has(BUILD_ID)).thenReturn(true);
    when(params.get(BUILD_ID)).thenReturn("12345678");
    when(params.has(BUILD_TARGET)).thenReturn(true);
    when(params.get(BUILD_TARGET)).thenReturn("brya-trunk_staging-userdebug");
    when(params.has(USE_SIGNED_IMAGE)).thenReturn(true);
    when(params.get(USE_SIGNED_IMAGE)).thenReturn("true");
    when(params.has(USE_TEST_RAMDISK)).thenReturn(true);
    when(params.get(USE_TEST_RAMDISK)).thenReturn("true");

    when(provisionResult.exitCode()).thenReturn(0);
    when(provisionResult.stdout()).thenReturn("Success");
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenReturn(provisionResult);

    decorator.setUp(SetupContext.create(testInfo));

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(2)).exec(commandCaptor.capture());

    Command executedCommand = commandCaptor.getAllValues().get(1);
    assertThat(executedCommand.getCommand())
        .containsAtLeast("-use-signed-image", "-use-test-ramdisk");
  }

  @Test
  public void setUp_dtConverterCommandFails_throwsMobileHarnessException() throws Exception {
    when(params.has(BUILD_ID)).thenReturn(false);
    when(params.has(BUILD_TARGET)).thenReturn(false);

    com.google.devtools.mobileharness.shared.util.command.CommandException cmdException =
        org.mockito.Mockito.mock(
            com.google.devtools.mobileharness.shared.util.command.CommandException.class);
    when(cmdException.getErrorId())
        .thenReturn(
            com.google.devtools.mobileharness.api.model.error.BasicErrorId.COMMAND_EXEC_FAIL);
    when(cmdException.getMessage()).thenReturn("Labservice connection refused");
    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenThrow(cmdException);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to query stable version for test_dut via dt-converter");
    assertThat(thrown).hasMessageThat().contains("Labservice connection refused");
  }

  @Test
  public void setUp_partialBuildParametersProvided_queriesMissingTargetAndRetainsProvidedId()
      throws Exception {
    when(params.has(BUILD_ID)).thenReturn(true);
    when(params.get(BUILD_ID)).thenReturn("explicit_id_123");
    when(params.has(BUILD_TARGET)).thenReturn(false);

    when(stableVersionResult.exitCode()).thenReturn(0);
    when(stableVersionResult.stdout())
        .thenReturn(
            "{\"android\": {\"build_id\": \"98765432\", \"build_target\":"
                + " \"brya-trunk_staging-userdebug\"}}");
    when(provisionResult.exitCode()).thenReturn(0);
    when(provisionResult.stdout()).thenReturn("Success");

    when(commandExecutor.exec(any(Command.class)))
        .thenReturn(versionResult)
        .thenReturn(stableVersionResult)
        .thenReturn(versionResult)
        .thenReturn(provisionResult);

    decorator.setUp(SetupContext.create(testInfo));

    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(commandExecutor, times(4)).exec(commandCaptor.capture());

    Command provisionCmd = commandCaptor.getAllValues().get(3);
    assertThat(provisionCmd.getCommand())
        .containsAtLeast(
            "-build-id", "explicit_id_123", "-build-target", "brya-trunk_staging-userdebug");
  }

  @Test
  public void parseAndApplyStableVersion_malformedJson_logsWarningAndDoesNotThrow() {
    CrossOverAndroidDesktopProvisionDecorator.BuildInfo buildInfo =
        decorator.parseAndApplyStableVersion("Invalid non-json output", testInfo, null, null);
    assertThat(buildInfo.buildId()).isNull();
    assertThat(buildInfo.buildTarget()).isNull();
    verify(log).atWarning();
  }

  @Test
  public void parseAndApplyStableVersion_missingAndroidKey_doesNotThrow() {
    CrossOverAndroidDesktopProvisionDecorator.BuildInfo buildInfo =
        decorator.parseAndApplyStableVersion("{\"chromeos\": {}}", testInfo, null, null);
    assertThat(buildInfo.buildId()).isNull();
    assertThat(buildInfo.buildTarget()).isNull();
  }

  @Test
  public void parseAndApplyStableVersion_populatesProperties() {
    CrossOverAndroidDesktopProvisionDecorator.BuildInfo buildInfo =
        decorator.parseAndApplyStableVersion(
            "{\"android\": {\"build_id\": \"111\", \"build_target\": \"brya-userdebug\"}}",
            testInfo,
            null,
            null);
    assertThat(buildInfo.buildId()).isEqualTo("111");
    assertThat(buildInfo.buildTarget()).isEqualTo("brya-userdebug");
    verify(properties).add("crossover_build_id", "111");
    verify(properties).add("crossover_build_target", "brya-userdebug");
  }

  @Test
  public void resolveFoilProvisionAndDtConverterPaths_withCipdTag_downloadsAndCleansUpDirs()
      throws Exception {
    decorator =
        new CrossOverAndroidDesktopProvisionDecorator(
            driver, testInfo, commandExecutor, new LocalFileUtil());

    when(params.has(FOIL_PROVISION_CIPD_TAG)).thenReturn(true);
    when(params.get(FOIL_PROVISION_CIPD_TAG)).thenReturn("prod");
    when(params.has(DT_CONVERTER_CIPD_TAG)).thenReturn(true);
    when(params.get(DT_CONVERTER_CIPD_TAG)).thenReturn("prod");

    java.util.List<Path> downloadedRootDirs = new java.util.ArrayList<>();
    when(commandExecutor.exec(any(Command.class)))
        .thenAnswer(
            invocation -> {
              Command cmd = invocation.getArgument(0);
              List<String> cmdArgs = cmd.getCommand();
              int rootIdx = cmdArgs.indexOf("-root");
              if (rootIdx != -1 && rootIdx + 1 < cmdArgs.size()) {
                Path rootPath = Path.of(cmdArgs.get(rootIdx + 1));
                downloadedRootDirs.add(rootPath);
                String pkg = cmdArgs.get(2);
                String binName =
                    pkg.equals(CrosDecoratorSpec.FOIL_PROVISION_PACKAGE)
                        ? "foil-provision"
                        : "dt-converter";
                Path bin = rootPath.resolve(binName);
                if (!Files.exists(bin)) {
                  Files.createFile(bin);
                }
              }
              return versionResult;
            });

    // First resolution downloads packages and records temp dirs.
    decorator.resolveFoilProvisionPath(testInfo);
    decorator.resolveDtConverterPath(testInfo);

    assertThat(downloadedRootDirs).hasSize(2);
    Path firstFoilDir = downloadedRootDirs.get(0);
    Path firstDtDir = downloadedRootDirs.get(1);
    assertThat(Files.exists(firstFoilDir)).isTrue();
    assertThat(Files.exists(firstDtDir)).isTrue();

    // Second resolution cleans up previous temp dirs before downloading new ones.
    decorator.resolveFoilProvisionPath(testInfo);
    decorator.resolveDtConverterPath(testInfo);

    assertThat(downloadedRootDirs).hasSize(4);
    assertThat(Files.exists(firstFoilDir)).isFalse();
    assertThat(Files.exists(firstDtDir)).isFalse();
    Path secondFoilDir = downloadedRootDirs.get(2);
    Path secondDtDir = downloadedRootDirs.get(3);
    assertThat(Files.exists(secondFoilDir)).isTrue();
    assertThat(Files.exists(secondDtDir)).isTrue();

    // Teardown cleanUp removes the active downloaded directories.
    decorator.cleanUp(TeardownContext.create(testInfo, null, null));
    assertThat(Files.exists(secondFoilDir)).isFalse();
    assertThat(Files.exists(secondDtDir)).isFalse();
  }

  @Test
  public void cleanUp_withNullContext_doesNotThrow() {
    decorator.cleanUp(null);
  }
}
