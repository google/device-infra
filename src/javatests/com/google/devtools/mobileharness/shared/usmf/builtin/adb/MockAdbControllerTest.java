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

package com.google.devtools.mobileharness.shared.usmf.builtin.adb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer.AdbInitializer;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary.CommandInvocation;
import com.google.devtools.mobileharness.shared.usmf.UsmfEnvironment;
import com.google.devtools.mobileharness.shared.usmf.builtin.adb.MockAndroidDevice.DeviceStatus;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Standalone unit tests verifying {@link MockAdbController} and {@link MockAndroidDevice}. */
@org.junit.Ignore("The test is slow")
@RunWith(JUnit4.class)
public final class MockAdbControllerTest {

  @Rule public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();
  @Rule public final SetFlags flags = new SetFlags();

  private CommandExecutor executor;
  private AndroidAdbInternalUtil adbInternalUtil;
  private AndroidPackageManagerUtil packageManagerUtil;
  private AndroidSystemSpecUtil systemSpecUtil;

  @Before
  public void setUp() throws Exception {
    AdbInitializer.resetForTest();
    executor = new CommandExecutor();
    adbInternalUtil = new AndroidAdbInternalUtil();
    packageManagerUtil = new AndroidPackageManagerUtil();
    systemSpecUtil = new AndroidSystemSpecUtil();
  }

  @Test
  public void multiDeviceConfiguration_discoversAllDevices() throws Exception {
    MockAndroidDevice device1 = MockAndroidDevice.pixel7("emulator-5554");
    MockAndroidDevice device2 = MockAndroidDevice.defaultDevice("emulator-5556");

    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(device1)
            .addDevice(device2)
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    Map<String, DeviceState> devices = adbInternalUtil.getDeviceSerialsAsMap();
    assertThat(devices)
        .containsExactly(
            "emulator-5554", DeviceState.DEVICE,
            "emulator-5556", DeviceState.DEVICE);

    ImmutableMap<String, MockAndroidDevice> allDevices = controller.getAllDevices();
    assertThat(allDevices.keySet()).containsExactly("emulator-5554", "emulator-5556");
    assertThat(allDevices.get("emulator-5554").getProperty("ro.product.model"))
        .isEqualTo("Pixel 7");
    assertThat(allDevices.get("emulator-5556").getProperty("ro.product.model"))
        .isEqualTo("sdk_gphone64_x86_64");
  }

  @Test
  public void onlineOfflineStatusTransitions_updatesAdbOutputAndErrors() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Initially online
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.DEVICE);
    String prop =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.product.model"));
    assertThat(prop.trim()).isEqualTo("Pixel 7");

    // 2. Transition to OFFLINE
    controller.setDeviceOnline("emulator-5554", false);
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.OFFLINE);

    CommandResult offlineResult =
        executor.exec(
            Command.of(
                    controller.getAdbPath(),
                    "-s",
                    "emulator-5554",
                    "shell",
                    "getprop",
                    "ro.product.model")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(offlineResult.exitCode()).isEqualTo(1);
    assertThat(offlineResult.stderr()).contains("error: device offline");

    // 3. Transition back to ONLINE
    controller.setDeviceOnline("emulator-5554", true);
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.DEVICE);
    String propOnline =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.product.model"));
    assertThat(propOnline.trim()).isEqualTo("Pixel 7");

    // 4. Transition to UNAUTHORIZED
    controller.setDeviceStatus("emulator-5554", DeviceStatus.UNAUTHORIZED);
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.UNAUTHORIZED);

    CommandResult unauthResult =
        executor.exec(
            Command.of(
                    controller.getAdbPath(),
                    "-s",
                    "emulator-5554",
                    "shell",
                    "getprop",
                    "ro.product.model")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(unauthResult.exitCode()).isEqualTo(1);
    assertThat(unauthResult.stderr()).contains("error: device unauthorized");
  }

  @Test
  public void disconnectAndReconnect_simulatesHardwareUnplug() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Device is connected initially
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.DEVICE);

    // 2. Disconnect device
    controller.disconnectDevice("emulator-5554");
    assertThat(adbInternalUtil.getDeviceSerialsAsMap()).isEmpty();

    CommandResult notFoundResult =
        executor.exec(
            Command.of(
                    controller.getAdbPath(),
                    "-s",
                    "emulator-5554",
                    "shell",
                    "getprop",
                    "ro.product.model")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(notFoundResult.exitCode()).isEqualTo(1);
    assertThat(notFoundResult.stderr()).contains("error: device 'emulator-5554' not found");

    // 3. Reconnect device
    controller.reconnectDevice("emulator-5554");
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5554", DeviceState.DEVICE);

    String prop =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.product.model"));
    assertThat(prop.trim()).isEqualTo("Pixel 7");
  }

  @Test
  public void addAndRemoveDeviceDynamically_updatesSandbox() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("device-1"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("device-1", DeviceState.DEVICE);

    // Dynamically add a second device
    controller.addDevice(MockAndroidDevice.defaultDevice("device-2"));
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly(
            "device-1", DeviceState.DEVICE,
            "device-2", DeviceState.DEVICE);

    // Remove first device
    controller.removeDevice("device-1");
    assertThat(adbInternalUtil.getDeviceSerialsAsMap())
        .containsExactly("device-2", DeviceState.DEVICE);
    assertThat(controller.getDevice("device-1")).isEmpty();
    assertThat(controller.getDevice("device-2")).isPresent();
  }

  @Test
  public void getpropAndSetprop_readsAndUpdatesSystemProperties() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Query individual property
    String model =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.product.model"));
    assertThat(model.trim()).isEqualTo("Pixel 7");

    // 2. Query all properties
    String allProps =
        executor.run(
            Command.of(controller.getAdbPath(), "-s", "emulator-5554", "shell", "getprop"));
    assertThat(allProps).contains("[ro.product.model]: [Pixel 7]");
    assertThat(allProps).contains("[ro.build.version.sdk]: [34]");

    // 3. Update property via Java API
    controller.setDeviceProperty("emulator-5554", "ro.product.model", "Pixel Custom");
    String updatedModel =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.product.model"));
    assertThat(updatedModel.trim()).isEqualTo("Pixel Custom");

    // 4. Update property via adb CLI setprop
    executor.run(
        Command.of(
            controller.getAdbPath(),
            "-s",
            "emulator-5554",
            "shell",
            "setprop",
            "test.custom.key",
            "hello12345"));
    Optional<MockAndroidDevice> dev = controller.getDevice("emulator-5554");
    assertThat(dev).isPresent();
    assertThat(dev.get().getProperty("test.custom.key")).isEqualTo("hello12345");
  }

  @Test
  public void packageInstallationAndUninstallation_managesPackageInventory() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Initial installed packages
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.ALL))
        .contains("com.google.android.youtube");
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.ALL))
        .doesNotContain("com.example.newapp");

    // 2. Install APK via adb install command
    String installOutput =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "install",
                "/path/to/com.example.newapp.apk"));
    assertThat(installOutput).contains("Success");
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.ALL))
        .contains("com.example.newapp");

    Optional<MockAndroidDevice> dev = controller.getDevice("emulator-5554");
    assertThat(dev).isPresent();
    assertThat(dev.get().getInstalledPackages()).contains("com.example.newapp");

    // 3. Uninstall package via package manager util
    packageManagerUtil.uninstallApk("emulator-5554", "com.example.newapp");
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.ALL))
        .doesNotContain("com.example.newapp");
  }

  @Test
  public void multipleDevicesDisambiguationAndErrorHandling() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("device-1"))
            .addDevice(MockAndroidDevice.defaultDevice("device-2"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Running command without -s when multiple devices exist fails
    CommandResult disambiguationResult =
        executor.exec(
            Command.of(controller.getAdbPath(), "shell", "getprop")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(disambiguationResult.exitCode()).isEqualTo(1);
    assertThat(disambiguationResult.stderr()).contains("error: more than one device/emulator");

    // 2. Running command with non-existent serial fails
    CommandResult nonExistentResult =
        executor.exec(
            Command.of(controller.getAdbPath(), "-s", "unknown-device", "get-state")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(nonExistentResult.exitCode()).isEqualTo(1);
    assertThat(nonExistentResult.stderr()).contains("error: device 'unknown-device' not found");

    // 3. Running with explicit -s succeeds
    String state1 =
        executor.run(Command.of(controller.getAdbPath(), "-s", "device-1", "get-state"));
    assertThat(state1.trim()).isEqualTo("device");

    String state2 =
        executor.run(Command.of(controller.getAdbPath(), "-s", "device-2", "get-state"));
    assertThat(state2.trim()).isEqualTo("device");
  }

  @Test
  public void defaultDeviceFallback_whenNoDevicesSpecified() throws Exception {
    MockAdbController controller = MockAdbController.builder(usmfEnvironment).buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    Map<String, DeviceState> devices = adbInternalUtil.getDeviceSerialsAsMap();
    assertThat(devices).containsExactly("emulator-5554", DeviceState.DEVICE);
  }

  @Test
  public void clearDevices_createsZeroDeviceSandbox() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment).clearDevices().buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    Map<String, DeviceState> devices = adbInternalUtil.getDeviceSerialsAsMap();
    assertThat(devices).isEmpty();

    CommandResult noDevicesResult =
        executor.exec(
            Command.of(controller.getAdbPath(), "get-state")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(noDevicesResult.exitCode()).isEqualTo(1);
    assertThat(noDevicesResult.stderr()).contains("error: no devices/emulators found");
  }

  @Test
  public void commandInvocationsAudit_capturesCommandHistory() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    executor.run(Command.of(controller.getAdbPath(), "devices", "-l"));
    executor.run(
        Command.of(
            controller.getAdbPath(),
            "-s",
            "emulator-5554",
            "shell",
            "getprop",
            "ro.product.model"));

    ImmutableList<CommandInvocation> invocations = controller.readCommandInvocations();
    assertThat(invocations).hasSize(2);
    assertThat(invocations.get(0).getArgs()).containsExactly("devices", "-l");
    assertThat(invocations.get(0).getStatus()).isEqualTo(CommandInvocation.Status.FINISHED);
    assertThat(invocations.get(0).getResultNonEmpty().getExitCode()).isEqualTo(0);

    assertThat(invocations.get(1).getArgs())
        .containsExactly("-s", "emulator-5554", "shell", "getprop", "ro.product.model");
    assertThat(invocations.get(1).getStatus()).isEqualTo(CommandInvocation.Status.FINISHED);
  }

  @Test
  public void waitForDevice_handlesOnlineAndDisconnected() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    // 1. wait-for-device when device is online succeeds
    CommandResult waitOnline =
        executor.exec(
            Command.of(controller.getAdbPath(), "-s", "emulator-5554", "wait-for-device"));
    assertThat(waitOnline.exitCode()).isEqualTo(0);

    // 2. Disconnect device -> wait-for-device fails
    controller.disconnectDevice("emulator-5554");
    CommandResult waitDisconnected =
        executor.exec(
            Command.of(controller.getAdbPath(), "-s", "emulator-5554", "wait-for-device")
                .redirectStderr(false)
                .successExitCodes(0, 1));
    assertThat(waitDisconnected.exitCode()).isEqualTo(1);
    assertThat(waitDisconnected.stderr()).contains("error: device 'emulator-5554' not found");
  }

  @Test
  public void setpropWithMultiWordValue_preservesCompleteString() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // Set property with spaces via adb shell setprop
    executor.run(
        Command.of(
            controller.getAdbPath(),
            "-s",
            "emulator-5554",
            "shell",
            "setprop",
            "ro.custom.display",
            "Pixel 7 Pro Test Device"));

    String val =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "getprop",
                "ro.custom.display"));
    assertThat(val.trim()).isEqualTo("Pixel 7 Pro Test Device");

    Optional<MockAndroidDevice> dev = controller.getDevice("emulator-5554");
    assertThat(dev).isPresent();
    assertThat(dev.get().getProperty("ro.custom.display")).isEqualTo("Pixel 7 Pro Test Device");
  }

  @Test
  public void packageListing_withNameFilter_filtersPackagesCorrectly() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    assertThat(packageManagerUtil.listPackages("emulator-5554", "youtube"))
        .containsExactly("com.google.android.youtube");
    assertThat(packageManagerUtil.listPackages("emulator-5554", "nonexistent")).isEmpty();
  }

  @Test
  public void adbVersionAndServerCommands_executeSuccessfully() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String versionOutput = executor.run(Command.of(controller.getAdbPath(), "version"));
    assertThat(versionOutput).contains("Android Debug Bridge");

    CommandResult startResult = executor.exec(Command.of(controller.getAdbPath(), "start-server"));
    assertThat(startResult.exitCode()).isEqualTo(0);

    CommandResult killResult = executor.exec(Command.of(controller.getAdbPath(), "kill-server"));
    assertThat(killResult.exitCode()).isEqualTo(0);
  }

  @Test
  public void adbRootUnrootRemount_handlesCommandsCorrectly() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String rootOutput =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "root"));
    assertThat(rootOutput).contains("restarting adbd as root");

    String unrootOutput =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "unroot"));
    assertThat(unrootOutput).contains("restarting adbd as non root");

    String remountOutput =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "remount"));
    assertThat(remountOutput).contains("remount succeeded");
  }

  @Test
  public void adbPushAndPull_createsLocalFile() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String pushOutput =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "push",
                "/tmp/source.txt",
                "/sdcard/dest.txt"));
    assertThat(pushOutput).contains("1 file pushed");

    Path tempOutFile = Files.createTempFile("usmf_test_pull_", ".txt");
    try {
      String pullOutput =
          executor.run(
              Command.of(
                  controller.getAdbPath(),
                  "-s",
                  "emulator-5554",
                  "pull",
                  "/sdcard/dest.txt",
                  tempOutFile.toAbsolutePath().toString()));
      assertThat(pullOutput).contains("1 file pulled");
      assertThat(Files.exists(tempOutFile)).isTrue();
    } finally {
      Files.deleteIfExists(tempOutFile);
    }
  }

  @Test
  public void adbGenericShellCommands_whichIdWmDumpsysEcho() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String whichPm =
        executor.run(
            Command.of(controller.getAdbPath(), "-s", "emulator-5554", "shell", "which", "pm"));
    assertThat(whichPm.trim()).isEqualTo("/system/bin/pm");

    String idOut =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "shell", "id"));
    assertThat(idOut).contains("uid=0(root)");

    String wmSize =
        executor.run(
            Command.of(controller.getAdbPath(), "-s", "emulator-5554", "shell", "wm", "size"));
    assertThat(wmSize).contains("Physical size: 1080x2400");

    String batteryOut =
        executor.run(
            Command.of(
                controller.getAdbPath(), "-s", "emulator-5554", "shell", "dumpsys", "battery"));
    assertThat(batteryOut).contains("Current Battery Service state:");
    assertThat(batteryOut).contains("level: 100");

    String echoOut =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "echo",
                "hello",
                "mock",
                "adb"));
    assertThat(echoOut.trim()).isEqualTo("hello mock adb");
  }

  @Test
  public void adbSystemSpecCommands_meminfoCpuinfoFeaturesAndHardware() throws Exception {
    MockAndroidDevice customDevice =
        MockAndroidDevice.pixel7("emulator-5554").toBuilder()
            .setProperty("mock.meminfo.mem_total_kb", "12000000")
            .build();
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment).addDevice(customDevice).buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 1. Total memory info (/proc/meminfo) with custom property
    assertThat(systemSpecUtil.getTotalMem("emulator-5554")).isEqualTo(12000000);

    // 2. CPU count (/proc/cpuinfo)
    assertThat(systemSpecUtil.getNumberOfCpus("emulator-5554")).isEqualTo(2);

    // 3. Machine hardware name (uname -m)
    assertThat(systemSpecUtil.getMachineHardwareName("emulator-5554")).isEqualTo("aarch64");

    // 4. System features (pm list features)
    Set<String> features = systemSpecUtil.getSystemFeatures("emulator-5554");
    assertThat(features).contains("feature:android.hardware.camera");
    assertThat(features).contains("feature:android.hardware.wifi");

    // 5. WiFi MAC address (/sys/class/net/wlan0/address)
    assertThat(systemSpecUtil.getMacAddress("emulator-5554")).isEqualTo("02:00:00:00:00:00");

    // 6. Bluetooth MAC address (settings get secure bluetooth_address)
    assertThat(systemSpecUtil.getBluetoothMacAddress("emulator-5554"))
        .isEqualTo("02:00:00:00:00:00");

    // 7. Logcat clear command (logcat -c)
    CommandResult clearLogcat =
        executor.exec(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "logcat", "-c"));
    assertThat(clearLogcat.exitCode()).isEqualTo(0);
    assertThat(clearLogcat.stdout()).isEmpty();
  }

  @Test
  public void adbUserCommands_getCurrentUser() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    // 1. am get-current-user
    String amCurrentUser =
        executor.run(
            Command.of(
                controller.getAdbPath(), "-s", "emulator-5554", "shell", "am", "get-current-user"));
    assertThat(amCurrentUser.trim()).isEqualTo("0");

    // 2. cmd user get-current-user
    String cmdCurrentUser =
        executor.run(
            Command.of(
                controller.getAdbPath(),
                "-s",
                "emulator-5554",
                "shell",
                "cmd",
                "user",
                "get-current-user"));
    assertThat(cmdCurrentUser.trim()).isEqualTo("0");
  }

  @Test
  public void adbTcpipConnectDisconnect_managesTcpDevices() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String tcpipOut =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "tcpip", "5555"));
    assertThat(tcpipOut).contains("restarting in TCP mode port: 5555");

    String connectOut =
        executor.run(Command.of(controller.getAdbPath(), "connect", "192.168.1.100:5555"));
    assertThat(connectOut).contains("connected to 192.168.1.100:5555");

    String disconnectOut =
        executor.run(Command.of(controller.getAdbPath(), "disconnect", "192.168.1.100:5555"));
    assertThat(disconnectOut).contains("disconnected 192.168.1.100:5555");
  }

  @Test
  public void adbLogcatAndBugreport_returnsOutput() throws Exception {
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .buildAndDeploy();

    String logcatOut =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "logcat"));
    assertThat(logcatOut).contains("beginning of main");

    String bugreportOut =
        executor.run(Command.of(controller.getAdbPath(), "-s", "emulator-5554", "bugreport"));
    assertThat(bugreportOut).contains("dumpstate");
  }

  @Test
  public void mockAndroidDevice_presetsAndCustomizations() {
    MockAndroidDevice p7 = MockAndroidDevice.pixel7("p7-serial");
    assertThat(p7.getSerial()).isEqualTo("p7-serial");
    assertThat(p7.getStatus()).isEqualTo(DeviceStatus.DEVICE);
    assertThat(p7.isOnline()).isTrue();
    assertThat(p7.getProperty("ro.product.model")).isEqualTo("Pixel 7");
    assertThat(p7.getProperty("ro.product.name")).isEqualTo("panther");
    assertThat(p7.getInstalledPackages()).contains("com.google.android.youtube");

    MockAndroidDevice def = MockAndroidDevice.defaultDevice("def-serial");
    assertThat(def.getSerial()).isEqualTo("def-serial");
    assertThat(def.getProperty("ro.product.model")).isEqualTo("sdk_gphone64_x86_64");

    MockAndroidDevice customized =
        p7.toBuilder()
            .setStatus(DeviceStatus.OFFLINE)
            .setProperty("custom.prop", "custom_val")
            .addPackage("com.custom.pkg")
            .build();

    assertThat(customized.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
    assertThat(customized.isOnline()).isFalse();
    assertThat(customized.getProperty("custom.prop")).isEqualTo("custom_val");
    assertThat(customized.getProperty("ro.product.model")).isEqualTo("Pixel 7");
    assertThat(customized.getInstalledPackages()).contains("com.custom.pkg");
    assertThat(customized.getInstalledPackages()).contains("com.google.android.youtube");
  }

  @Test
  public void mockAndroidDevice_jsonSerializationRoundTrip() {
    MockAndroidDevice original =
        MockAndroidDevice.builder("test-serial")
            .setStatus(DeviceStatus.UNAUTHORIZED)
            .setProperty("k1", "v1")
            .setProperty("k2", "v2")
            .addPackages(ImmutableList.of("pkg1", "pkg2"))
            .build();

    JsonObject json = original.toJsonObject();
    MockAndroidDevice deserialized = MockAndroidDevice.fromJsonObject("test-serial", json);

    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.hashCode()).isEqualTo(original.hashCode());
    assertThat(deserialized.toString()).contains("test-serial");
    assertThat(deserialized.toString()).contains("UNAUTHORIZED");
  }

  @Test
  public void mockAndroidDevice_deviceStatusParsing() {
    assertThat(DeviceStatus.fromString("device")).isEqualTo(DeviceStatus.DEVICE);
    assertThat(DeviceStatus.fromString("DEVICE")).isEqualTo(DeviceStatus.DEVICE);
    assertThat(DeviceStatus.fromString("offline")).isEqualTo(DeviceStatus.OFFLINE);
    assertThat(DeviceStatus.fromString("unauthorized")).isEqualTo(DeviceStatus.UNAUTHORIZED);
    assertThat(DeviceStatus.fromString("disconnected")).isEqualTo(DeviceStatus.DISCONNECTED);
    assertThat(DeviceStatus.fromString(null)).isEqualTo(DeviceStatus.DEVICE);
    assertThat(DeviceStatus.fromString("unknown_status")).isEqualTo(DeviceStatus.DEVICE);
  }

  @Test
  public void controllerBuilder_multipleDeviceRegistrationMethods() throws Exception {
    MockAndroidDevice dev1 = MockAndroidDevice.pixel7("dev-1");
    MockAndroidDevice dev2 =
        MockAndroidDevice.builder("dev-2").setProperty("ro.product.model", "Custom").build();
    MockAndroidDevice dev3 = MockAndroidDevice.defaultDevice("dev-3");

    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevices(dev1, dev2)
            .addDevices(ImmutableList.of(dev3))
            .buildAndDeploy();

    assertThat(controller.getAllDevices().keySet()).containsExactly("dev-1", "dev-2", "dev-3");
  }
}
