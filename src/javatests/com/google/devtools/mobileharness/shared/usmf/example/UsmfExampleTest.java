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

package com.google.devtools.mobileharness.shared.usmf.example;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer.AdbInitializer;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary.CommandInvocation;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Example demonstrating the usage of USMF to mock command line dependencies in integration tests.
 */
@RunWith(JUnit4.class)
public final class UsmfExampleTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlags flags = new SetFlags();

  @Before
  public void setUp() throws Exception {
    AdbInitializer.resetForTest();
    Aapt.resetForTest();
  }

  @Test
  public void getDeviceSerialsAsMap_withMockAdb() throws Exception {
    // 1. Configure and deploy a mock ADB binary in <temp_dir>/adb_sandbox using Starlark rules.
    UsmfBinary mockAdb =
        UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if "devices" in ctx.args:
                        return Result(stdout="List of devices attached\\nemulator-5554\\tdevice\\tproduct:sdk_gphone64_x86_64\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    // 2. Use the mock ADB binary in MH libraries.
    flags.set("adb", mockAdb.getPath());
    AndroidAdbInternalUtil adbInternalUtil = new AndroidAdbInternalUtil();

    // 3. Get devices via the mock ADB binary.
    Map<String, DeviceState> devices = adbInternalUtil.getDeviceSerialsAsMap();

    // 4. Assert the result.
    assertThat(devices).containsExactly("emulator-5554", DeviceState.DEVICE);

    // 5. Assert the invocation on the mock ADB binary.
    ImmutableList<CommandInvocation> invocations = mockAdb.readCommandInvocations();
    assertThat(
            invocations.stream()
                .anyMatch(
                    invocation -> invocation.getArgs().equals(ImmutableList.of("devices", "-l"))))
        .isTrue();
  }

  @Test
  public void installAndListPackages_withMockAdb() throws Exception {
    // 1. Configure and deploy a mock ADB binary with Starlark rules for package installation.
    UsmfBinary localMockAdb =
        UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
            .setRules(
                """
                PKG_MAP = {
                    "my_app": "com.foo.my_app"
                }

                def get_package_name(command):
                    m = re_search(r"install.*\\s+(?P<apk_path>\\S+\\.apk)", command)
                    if not m:
                        return None
                    apk = m["apk_path"].split("/")[-1]
                    apk_name = apk.split(".")[0]
                    return PKG_MAP.get(apk_name, apk_name)

                def handle_install(ctx):
                    pkg_name = get_package_name(ctx.command)
                    if not pkg_name:
                        return None
                    m_s = re_search(r"-s\\s+(?P<device_id>\\S+)", ctx.command)
                    device_id = m_s["device_id"] if m_s else "default"

                    if "installed_packages" not in ctx.state:
                        ctx.state["installed_packages"] = {}

                    installed = ctx.state["installed_packages"].get(device_id, [])
                    if pkg_name not in installed:
                        installed.append(pkg_name)
                    ctx.state["installed_packages"][device_id] = installed
                    return Result(stdout="Success\\n")

                def handle_list_packages(ctx):
                    if "pm" not in ctx.command or "list" not in ctx.command or "packages" not in ctx.command:
                        return None
                    m_s = re_search(r"-s\\s+(?P<device_id>\\S+)", ctx.command)
                    device_id = m_s["device_id"] if m_s else "default"

                    installed = ctx.state.get("installed_packages", {}).get(device_id, [])
                    stdout = ""
                    for pkg in installed:
                        stdout += "package:" + pkg + "\\n"
                    return Result(stdout=stdout)

                usmf_rules = [handle_install, handle_list_packages]
                """)
            .buildAndDeploy();

    // 2. Configure and deploy a mock AAPT binary.
    UsmfBinary mockAapt =
        UsmfBinary.builder("aapt", tmpFolder.getRoot().toPath(), "aapt_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if "dump" in ctx.args and "badging" in ctx.args:
                        return Result(stdout="package: name='com.foo.my_app' versionCode='1' versionName='1.0'\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    // 3. Use the mock binaries in MH libraries.
    flags.set("adb", localMockAdb.getPath());
    flags.set("aapt", mockAapt.getPath());
    AndroidPackageManagerUtil packageManagerUtil = new AndroidPackageManagerUtil();

    // 4. Assert the pre-installation state.
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.THIRD_PARTY)).isEmpty();

    // 5. Install the APK.
    packageManagerUtil.installApk("emulator-5554", /* sdkVersion= */ 28, "/path/to/my_app.apk");

    // 6. Assert the post-installation state.
    assertThat(packageManagerUtil.listPackages("emulator-5554", PackageType.THIRD_PARTY))
        .contains("com.foo.my_app");
  }
}
