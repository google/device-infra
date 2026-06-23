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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DevicePropertyInfoTest {

  @Test
  public void testDevicePropertyInfoBuilder() {
    DevicePropertyInfo info = DevicePropertyInfo.newBuilder().abi("x86").build();

    assertEquals("x86", info.abi());
    assertEquals("unknown", info.bootimageFingerprint());
    assertNull(info.device());
  }

  @Test
  public void testDevicePropertyInfoMap() {
    DevicePropertyInfo info = DevicePropertyInfo.newBuilder().abi("x86").build();

    assertEquals("x86", info.getPropertytMapWithPrefix("prefix_").get("prefix_abi"));
    assertEquals(
        "unknown", info.getPropertytMapWithPrefix("prefix_").get("prefix_bootimage_fingerprint"));
    assertNull(info.getPropertytMapWithPrefix("prefix_").get("prefix_device"));
  }
}
