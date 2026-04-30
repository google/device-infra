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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidJitEmulatorUtilTest {

  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Test
  public void getVirtualDeviceNameInTradefed_local() {
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "localhost:local-virtual-device-1"))
        .isEqualTo("local-virtual-device-1");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("0.0.0.0:local-virtual-device-0"))
        .isEqualTo("local-virtual-device-0");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "localhost:local-virtual-device-2"))
        .isEqualTo("local-virtual-device-2");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "localhost:local-virtual-device-3"))
        .isEqualTo("local-virtual-device-3");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "localhost:local-virtual-device-4"))
        .isEqualTo("local-virtual-device-4");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "localhost:local-virtual-device-5"))
        .isEqualTo("local-virtual-device-5");
  }

  @Test
  public void getVirtualDeviceNameInTradefed_remote() {
    flags.setAllFlags(
        ImmutableMap.of(
            "virtual_device_server_ip",
            "10.0.0.1",
            "virtual_device_server_username",
            "mobileharness"));
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "gce-device-10.0.0.1-0-mobileharness"))
        .isEqualTo("gce-device-10.0.0.1-0-mobileharness");
    assertThat(
            AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(
                "gce-device-10.0.0.1-1-mobileharness"))
        .isEqualTo("gce-device-10.0.0.1-1-mobileharness");
  }

  @Test
  public void getAllVirtualDeviceIds_defaultSettings_returnsLocalhostPorts() {
    flags.setAllFlags(ImmutableMap.of("android_jit_emulator_num", "2"));

    ImmutableList<String> deviceIds = AndroidJitEmulatorUtil.getAllVirtualDeviceIds();
    assertThat(deviceIds).containsExactly("127.0.0.1:6520", "127.0.0.1:6521").inOrder();
  }

  @Test
  public void getPortFromDeviceId_success() throws Exception {
    assertThat(AndroidJitEmulatorUtil.getPortFromDeviceId("127.0.0.1:6520")).isEqualTo(6520);
  }

  @Test
  public void getPortFromDeviceId_missingColon_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () -> AndroidJitEmulatorUtil.getPortFromDeviceId("127.0.0.1"));
  }

  @Test
  public void getPortFromDeviceId_invalidPort_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () -> AndroidJitEmulatorUtil.getPortFromDeviceId("127.0.0.1:abc"));
  }

  @Test
  public void getPortFromDeviceId_zeroOrNegativePort_throwsException() {
    assertThrows(
        MobileHarnessException.class,
        () -> AndroidJitEmulatorUtil.getPortFromDeviceId("127.0.0.1:0"));
    assertThrows(
        MobileHarnessException.class,
        () -> AndroidJitEmulatorUtil.getPortFromDeviceId("127.0.0.1:-1"));
  }
}
