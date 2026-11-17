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

package com.google.devtools.mobileharness.platform.android.lightning.networkconnector;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WifiConnectArgsTest {

  @Test
  public void toString_withNonEmptyWifiPsk_redactsPassword() {
    WifiConnectArgs args =
        WifiConnectArgs.builder().setWifiSsid("TestWifi").setWifiPsk("super-secret-psk").build();

    String formatted = args.toString();

    assertThat(formatted).contains("wifiSsid=TestWifi");
    assertThat(formatted).contains("wifiPsk=Optional[<redacted>]");
    assertThat(formatted).doesNotContain("super-secret-psk");
  }

  @Test
  public void toString_withEmptyWifiPsk_preservesEmptyString() {
    WifiConnectArgs args = WifiConnectArgs.builder().setWifiSsid("OpenWifi").setWifiPsk("").build();

    assertThat(args.toString()).contains("wifiPsk=Optional[]");
  }
}
