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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDesktopOtaUpdateDecoratorSpec;
import java.io.File;
import java.time.Duration;
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

/** Unit tests for {@link AndroidDesktopOtaUpdateDecorator}. */
@RunWith(JUnit4.class)
public class AndroidDesktopOtaUpdateDecoratorTest {

  private static final String OTA_DOWNGRADE_PROP = "ro.ota.allow_downgrade";
  private static final String UPDATE_SUCCESS_OUTPUT =
      "onPayloadApplicationComplete(ErrorCode::kSuccess (0)";
  private static final Duration WAIT_FOR_DEVICE_TIMEOUT = Duration.ofMinutes(10);

  private AndroidDesktopOtaUpdateDecorator decorator;
  @Mock private CommandExecutor cmdExecutor;
  @Mock private AndroidSystemStateUtil androidSystemStateUtil;
  @Mock private SystemUtil systemUtil;
  @Mock private Adb adb;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private JobSetting jobSetting;
  @Mock private Log log;
  @Mock private Api atInfo;
  @Mock private Properties properties;
  @Mock private CountDownTimer timer;
  private File otaToolsZip;
  private File updateDeviceScript;
  private File otaPackage;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    // Mock TestInfo
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.timer()).thenReturn(timer);
    when(testInfo.properties()).thenReturn(properties);
    when(jobInfo.setting()).thenReturn(jobSetting);
    when(log.atInfo()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("device_id");
    when(androidAdbUtil.getProperty(anyString(), eq(AndroidProperty.INCREMENTAL_BUILD)))
        .thenReturn("build_id");

    // Mock OTA update command
    when(cmdExecutor.run(any())).thenReturn(UPDATE_SUCCESS_OUTPUT);

    // Mock device reboot and wait for device to be ready
    doNothing().when(androidSystemStateUtil).reboot(anyString());
    doNothing()
        .when(androidSystemStateUtil)
        .waitForState(anyString(), eq(DeviceConnectionState.DEVICE), eq(WAIT_FOR_DEVICE_TIMEOUT));
    doNothing().when(androidSystemStateUtil).waitUntilReady(anyString());

    // Mock files
    // Setup OTA tools zip file
    otaToolsZip = tempFolder.newFile("otatools.zip");
    // Setup update_device script
    tempFolder.newFolder("bin");
    updateDeviceScript =
        new File(tempFolder.getRoot().getAbsolutePath() + File.separator + "bin", "update_device");
    updateDeviceScript.createNewFile();
    // Setup OTA package
    otaPackage = tempFolder.newFile("device-ota-1234.zip");
    when(jobSetting.getTmpFileDir()).thenReturn(tempFolder.getRoot().getAbsolutePath());

    // Create decorator with mocks
    decorator =
        new AndroidDesktopOtaUpdateDecorator(
            decoratedDriver,
            testInfo,
            cmdExecutor,
            androidAdbUtil,
            androidSystemStateUtil,
            systemUtil,
            adb,
            localFileUtil);
  }

  @Test
  public void run_otaUpdateSuccessWithoutUserDataWipe() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    Command expectedCommand =
        Command.of(
            updateDeviceScript.getAbsolutePath(), "-s", "device_id", otaPackage.getAbsolutePath());

    // Run decorator
    decorator.run(testInfo);

    // Verify OTA downgrade prop was set
    verify(androidAdbUtil).setProperty("device_id", OTA_DOWNGRADE_PROP, "1");

    // Verify cleanup commands were run
    verify(adb).runShell("device_id", "update_engine_client --cancel");
    verify(adb).runShell("device_id", "snapshotctl unmap-snapshots");
    verify(adb).runShell("device_id", "snapshotctl delete-snapshots");

    // Verify OTA update command
    ArgumentCaptor<Command> cmdCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(cmdCaptor.capture());
    assertThat(cmdCaptor.getValue().getCommand()).isEqualTo(expectedCommand.getCommand());

    // Verify device reboot and wait for device to be ready
    verify(androidSystemStateUtil).reboot("device_id");
    verify(androidSystemStateUtil)
        .waitForState("device_id", DeviceConnectionState.DEVICE, WAIT_FOR_DEVICE_TIMEOUT);
    verify(androidSystemStateUtil).waitUntilReady("device_id");

    // Verify new build ID is obtained after OTA update finishes
    verify(androidAdbUtil, times(2))
        .getProperty(eq("device_id"), eq(AndroidProperty.INCREMENTAL_BUILD));

    // Verify decorated driver is run
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_otaUpdateSuccessWithUserDataWipe() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(true)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    Command expectedCommand =
        Command.of(
            updateDeviceScript.getAbsolutePath(),
            "-s",
            "device_id",
            "--wipe-user-data",
            otaPackage.getAbsolutePath());

    // Run decorator
    decorator.run(testInfo);

    // Verify OTA downgrade prop was set
    verify(androidAdbUtil).setProperty("device_id", OTA_DOWNGRADE_PROP, "1");

    // Verify cleanup commands were run
    verify(adb).runShell("device_id", "update_engine_client --cancel");
    verify(adb).runShell("device_id", "snapshotctl unmap-snapshots");
    verify(adb).runShell("device_id", "snapshotctl delete-snapshots");

    // Verify OTA update command
    ArgumentCaptor<Command> cmdCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(cmdCaptor.capture());
    assertThat(cmdCaptor.getValue().getCommand()).isEqualTo(expectedCommand.getCommand());

    // Verify device reboot and wait for device to be ready
    verify(androidSystemStateUtil).reboot("device_id");
    verify(androidSystemStateUtil)
        .waitForState("device_id", DeviceConnectionState.DEVICE, WAIT_FOR_DEVICE_TIMEOUT);
    verify(androidSystemStateUtil).waitUntilReady("device_id");

    // Verify new build ID is obtained after OTA update finishes
    verify(androidAdbUtil, times(2))
        .getProperty(eq("device_id"), eq(AndroidProperty.INCREMENTAL_BUILD));

    // Verify decorated driver is run
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_otaUpdateNotNeeded() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    // Mock device build ID to be the same as the OTA package
    when(androidAdbUtil.getProperty(anyString(), eq(AndroidProperty.INCREMENTAL_BUILD)))
        .thenReturn("1234");

    // Run decorator
    decorator.run(testInfo);

    // Verify OTA update command is not run
    verify(cmdExecutor, never()).run(any(Command.class));

    // Verify decorated driver is run
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_forceOtaUpdate() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .setForceOtaUpdate(true)
            .build());
    // Mock device build ID to be the same as the OTA package
    when(androidAdbUtil.getProperty(anyString(), eq(AndroidProperty.INCREMENTAL_BUILD)))
        .thenReturn("1234");
    Command expectedCommand =
        Command.of(
            updateDeviceScript.getAbsolutePath(), "-s", "device_id", otaPackage.getAbsolutePath());

    // Run decorator
    decorator.run(testInfo);

    // Verify OTA downgrade prop was set
    verify(androidAdbUtil).setProperty("device_id", OTA_DOWNGRADE_PROP, "1");

    // Verify cleanup commands were run
    verify(adb).runShell("device_id", "update_engine_client --cancel");
    verify(adb).runShell("device_id", "snapshotctl unmap-snapshots");
    verify(adb).runShell("device_id", "snapshotctl delete-snapshots");

    // Verify OTA update command
    ArgumentCaptor<Command> cmdCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(cmdCaptor.capture());
    assertThat(cmdCaptor.getValue().getCommand()).isEqualTo(expectedCommand.getCommand());

    // Verify device reboot and wait for device to be ready
    verify(androidSystemStateUtil).reboot("device_id");
    verify(androidSystemStateUtil)
        .waitForState("device_id", DeviceConnectionState.DEVICE, WAIT_FOR_DEVICE_TIMEOUT);
    verify(androidSystemStateUtil).waitUntilReady("device_id");

    // Verify new build ID is obtained after OTA update finishes
    verify(androidAdbUtil, times(2))
        .getProperty(eq("device_id"), eq(AndroidProperty.INCREMENTAL_BUILD));

    // Verify decorated driver is run
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_otaToolsZipFileUnzipFails() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools("/path/to/non_existent_ota_tools.zip")
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_UNZIP_ERROR, "failed"))
        .when(localFileUtil)
        .unzipFile(eq("/path/to/non_existent_ota_tools.zip"), anyString());

    // Run decorator
    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    // Verify error
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_TOOLS_UNZIP_ERROR);
    assertThat(e)
        .hasMessageThat()
        .contains("Failed to unzip OTA tools zip file at /path/to/non_existent_ota_tools.zip");

    // Verify decorated driver is not run
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void run_updateDeviceScriptFileNotFound() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());

    // Mock update_device script file not found
    updateDeviceScript.delete();

    // Run decorator
    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    // Verify error
    assertThat(e.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_SCRIPT_FILE_NOT_FOUND);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Update device script file at "
                + updateDeviceScript.getAbsolutePath()
                + " does not exist or is not a regular file.");

    // Verify decorated driver is not run
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void run_otaPackageFileNotExists() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());

    // Mock OTA package file not found
    otaPackage.delete();

    // Run decorator
    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    // Verify error
    assertThat(e.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_PACKAGE_FILE_NOT_EXISTS);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "OTA package file at "
                + otaPackage.getAbsolutePath()
                + " does not exist or is not a regular file.");

    // Verify decorated driver is not run
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void run_otaUpdateWithAdbPath() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    when(systemUtil.getEnv("PATH")).thenReturn("/user/bin");
    var adbDir = "my/ate";
    var adbPath = adbDir + "/adb";
    when(adb.getAdbPath()).thenReturn(adbPath);
    when(localFileUtil.isFileExist(adbPath)).thenReturn(true);
    when(localFileUtil.getParentDirPath(adbPath)).thenReturn(adbDir);

    decorator.run(testInfo);

    ArgumentCaptor<Command> cmdCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).run(cmdCaptor.capture());

    var expectedPath = adbDir + File.pathSeparator + "/user/bin";
    var extraEnv = cmdCaptor.getValue().getExtraEnvironment();
    assertThat(extraEnv).containsEntry("PATH", expectedPath);
    assertThat(extraEnv).hasSize(1);
  }

  @Test
  public void getEnvMap_adbPathExists() throws Exception {
    when(systemUtil.getEnv("PATH")).thenReturn("/user/bin");
    String adbDir = "/my/ate";
    String adbPath = adbDir + "/adb";
    when(adb.getAdbPath()).thenReturn(adbPath);
    when(localFileUtil.isFileExist(adbPath)).thenReturn(true);
    when(localFileUtil.getParentDirPath(adbPath)).thenReturn(adbDir);

    var expectedPath = adbDir + File.pathSeparator + "/user/bin";
    assertThat(decorator.getEnvMap(testInfo)).containsExactly("PATH", expectedPath);

    verify(localFileUtil).isFileExist(adbPath);
    verify(localFileUtil).getParentDirPath(adbPath);
  }

  @Test
  public void getEnvMap_adbPathExistsButNotFile() throws Exception {
    when(systemUtil.getEnv("PATH")).thenReturn("/user/bin");
    String adbPath = "/my/ate/adb";
    when(adb.getAdbPath()).thenReturn(adbPath);
    when(localFileUtil.isFileExist(adbPath)).thenReturn(false);

    assertThat(decorator.getEnvMap(testInfo)).containsExactly("PATH", "/user/bin");
    verify(localFileUtil).isFileExist(adbPath);
    verify(localFileUtil, never()).getParentDirPath(anyString());
  }

  @Test
  public void getEnvMap_adbPathDoesNotExist() throws Exception {
    when(systemUtil.getEnv("PATH")).thenReturn("/user/bin");
    when(adb.getAdbPath()).thenReturn("");
    when(localFileUtil.isFileExist("")).thenReturn(false);
    assertThat(decorator.getEnvMap(testInfo)).containsExactly("PATH", "/user/bin");

    verify(localFileUtil).isFileExist("");
    verify(localFileUtil, never()).getParentDirPath(anyString());
  }

  @Test
  public void run_otaUpdateFails_setsNeedsProvisionRepair() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    when(cmdExecutor.run(any(Command.class))).thenThrow(mock(CommandException.class));

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_DESKTOP_OTA_UPDATE_DECORATOR_OTA_UPDATE_ERROR);
    verify(properties).add("needs_provision_repair", "true");
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void run_otaUpdate_cleanupCommandsFail_continues() throws Exception {
    mockSpec(
        getDefaultTestSpecBuilder()
            .setWipeUserData(false)
            .setOtaTools(otaToolsZip.getAbsolutePath())
            .setOtaPackage(otaPackage.getAbsolutePath())
            .build());
    when(adb.runShell(anyString(), anyString())).thenThrow(mock(MobileHarnessException.class));

    // Run decorator
    decorator.run(testInfo);

    // Verify cleanup commands were attempted
    verify(adb).runShell("device_id", "update_engine_client --cancel");
    verify(adb).runShell("device_id", "snapshotctl unmap-snapshots");
    verify(adb).runShell("device_id", "snapshotctl delete-snapshots");

    // Verify OTA update command still ran
    verify(cmdExecutor).run(any(Command.class));
  }

  private void mockSpec(AndroidDesktopOtaUpdateDecoratorSpec spec) throws Exception {
    when(jobInfo.combinedSpec(any(AndroidDesktopOtaUpdateDecorator.class), any(String.class)))
        .thenReturn(spec);
  }

  private static AndroidDesktopOtaUpdateDecoratorSpec.Builder getDefaultTestSpecBuilder() {
    return AndroidDesktopOtaUpdateDecoratorSpec.newBuilder();
  }
}
