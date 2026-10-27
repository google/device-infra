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

package com.google.devtools.mobileharness.shared.util.network;

import static com.google.common.truth.Truth.assertThat;

import java.net.Inet4Address;
import java.net.Inet6Address;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** The unit tests for {@link NetworkUtil}. */
@RunWith(JUnit4.class)
public class NetworkUtilTest {
  private NetworkUtil networkUtil;

  @Before
  public void setUp() {
    networkUtil = new NetworkUtil();
  }

  @Test
  public void getLocalHostName() throws Exception {
    assertThat(networkUtil.getLocalHostName()).isNotEmpty();
  }

  @Test
  public void getInetAddress_notLookbackOrLinkLocal() throws Exception {
    networkUtil
        .getInetAddresses()
        .forEach(
            addr -> {
              assertThat(addr.isLoopbackAddress()).isFalse();
              assertThat(addr.isLinkLocalAddress()).isFalse();
            });
  }

  @Test
  public void getInetAddress_withFilter() throws Exception {
    networkUtil
        .getInetAddresses()
        .forEach(
            addr ->
                assertThat(addr instanceof Inet4Address || addr instanceof Inet6Address).isTrue());
    networkUtil
        .getInetAddresses(addr -> addr instanceof Inet4Address)
        .forEach(addr -> assertThat(addr instanceof Inet4Address).isTrue());
    networkUtil
        .getInetAddresses(addr -> addr instanceof Inet6Address)
        .forEach(addr -> assertThat(addr instanceof Inet6Address).isTrue());
  }
}
