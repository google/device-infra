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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidJitEmulatorUtilTest {

  @Test
  public void getAcloudInstanceId() {
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6521")).isEqualTo("2");
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6520")).isEqualTo("1");
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6522")).isEqualTo("3");
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6523")).isEqualTo("4");
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6524")).isEqualTo("5");
    assertThat(AndroidJitEmulatorUtil.getAcloudInstanceId("localhost:6510")).isEqualTo("");
  }

  @Test
  public void getVirtualDeviceNameInTradefed() {
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6521"))
        .isEqualTo("local-virtual-device-1");
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6520"))
        .isEqualTo("local-virtual-device-0");
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6522"))
        .isEqualTo("local-virtual-device-2");
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6523"))
        .isEqualTo("local-virtual-device-3");
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6524"))
        .isEqualTo("local-virtual-device-4");
    assertThat(AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed("localhost:6510"))
        .isEqualTo("");
  }
}
