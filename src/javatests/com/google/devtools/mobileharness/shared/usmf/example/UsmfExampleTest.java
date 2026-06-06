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
import com.google.devtools.mobileharness.shared.usmf.UsmfRule;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.BinaryStateMutation;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.CommandBehavior;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.CommandCondition;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Example demonstrating the usage of USMF. */
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
    // 1. Configure and deploy a mock ADB binary in <temp_dir>/adb_sandbox.
    //    - When "devices ..." is called, return a pre-defined list of devices.
    UsmfBinary mockAdb =
        UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
            .addRule(
                UsmfRule.builder()
                    .addCondition(CommandCondition.prefixMatch("devices"))
                    .setBehavior(
                        CommandBehavior.stdout(
                                """
                                List of devices attached
                                emulator-5554\tdevice\tproduct:sdk_gphone64_x86_64
                                """)
                            .build())
                    .build())
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
    // 1. Configure and deploy a mock ADB binary with stateful installation rules.
    //    - When "install ...my_app.apk" is called, append the package to installed_packages.
    //    - When "shell pm list packages" is called, print installed_packages.
    UsmfBinary localMockAdb =
        UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
            .addRule(
                UsmfRule.builder()
                    .addCondition(
                        CommandCondition.regexMatch(
                            ".*(?:-s\\s+(?P<device_id>\\S+))?.*install.*\\s+(?:.*/)?(?P<apk_name>[a-zA-Z0-9_]+)\\.apk"))
                    .setBehavior(
                        CommandBehavior.stdout("Success\n")
                            .addStateMutation(
                                BinaryStateMutation.key("installed_packages_${@C:device_id}")
                                    .addToSet("com.foo.${@C:apk_name}"))
                            .build())
                    .build())
            .addRule(
                UsmfRule.builder()
                    .addCondition(
                        CommandCondition.regexMatch(
                            ".*(?:-s\\s+(?P<device_id>\\S+))?.*shell.*pm\\s+list\\s+packages"))
                    .setBehavior(
                        CommandBehavior.stdout(
                                "${@S:installed_packages_${@C:device_id}:'package:%s\n'}")
                            .build())
                    .build())
            .buildAndDeploy();

    // 2. Configure and deploy a mock AAPT binary.
    //    - When "dump badging <apk>" is called, return package name 'com.foo.my_app'.
    UsmfBinary mockAapt =
        UsmfBinary.builder("aapt", tmpFolder.getRoot().toPath(), "aapt_sandbox")
            .addRule(
                UsmfRule.builder()
                    .addCondition(CommandCondition.prefixMatch("dump", "badging"))
                    .setBehavior(
                        CommandBehavior.stdout(
                                "package: name='com.foo.my_app' versionCode='1'"
                                    + " versionName='1.0'\n")
                            .build())
                    .build())
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
