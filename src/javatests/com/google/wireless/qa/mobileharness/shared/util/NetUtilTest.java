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

package com.google.wireless.qa.mobileharness.shared.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil.NetworkInterfaceInfo;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** The unit tests for {@link NetUtil}. */
@RunWith(JUnit4.class)
public class NetUtilTest {

  private NetUtil netUtil;

  @Before
  public void setUp() {
    netUtil = new NetUtil();
  }

  @Test
  public void getUniqueHostIpOrEmpty_emptyInterface() throws Exception {
    assertThat(netUtil.getUniqueHostIpOrEmpty(Optional.empty())).isEmpty();
  }

  @Test
  public void getUniqueHostIpOrEmpty_onlySiteLocalAddress() throws Exception {
    String siteLocalAddress = "192.168.1.1";
    Optional<List<NetworkInterfaceInfo>> interfaces = mockNetworkInterfaceInfo(siteLocalAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).hasValue(siteLocalAddress);
  }

  @Test
  public void getUniqueHostIpOrEmpty_onlyCorpAddress() throws Exception {
    String corpLocalAddress = "100.90.104.32";
    Optional<List<NetworkInterfaceInfo>> interfaces = mockNetworkInterfaceInfo(corpLocalAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).hasValue(corpLocalAddress);
  }

  @Test
  public void getUniqueHostIpOrEmpty_oneCorpAndSiteLocalAddress() throws Exception {
    String siteLocalAddress = "192.168.1.1";
    String corpAddress = "100.90.104.32";
    Optional<List<NetworkInterfaceInfo>> interfaces =
        mockNetworkInterfaceInfo(corpAddress, siteLocalAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).hasValue(corpAddress);
  }

  @Test
  public void getUniqueHostIpOrEmpty_twoCorpAddress() throws Exception {
    String corpLocalAddress = "100.90.104.31";
    String corpAddress = "100.90.104.32";
    Optional<List<NetworkInterfaceInfo>> interfaces =
        mockNetworkInterfaceInfo(corpAddress, corpLocalAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).isEmpty();
  }

  @Test
  public void getUniqueHostIpOrEmpty_twoSiteLocalAddress() throws Exception {
    String siteLocalAddress = "192.168.1.1";
    String siteAddress = "192.168.1.2";
    Optional<List<NetworkInterfaceInfo>> interfaces =
        mockNetworkInterfaceInfo(siteAddress, siteLocalAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).isEmpty();
  }

  @Test
  public void getUniqueHostIpOrEmpty_multiCorpAndSiteLocalAddress() throws Exception {
    String siteLocalAddressOne = "192.168.1.1";
    String siteLocalAddressTwo = "192.168.1.2";
    String corpAddress = "100.90.104.32";
    Optional<List<NetworkInterfaceInfo>> interfaces =
        mockNetworkInterfaceInfo(siteLocalAddressOne, siteLocalAddressTwo, corpAddress);

    assertThat(netUtil.getUniqueHostIpOrEmpty(interfaces)).hasValue(corpAddress);
  }

  private Optional<List<NetworkInterfaceInfo>> mockNetworkInterfaceInfo(String... ipAddresses)
      throws Exception {
    int index = 0;
    List<NetworkInterfaceInfo> networkInterfaceInfoList = new ArrayList<>();
    for (String ipAddress : ipAddresses) {
      ImmutableList<InetAddress> ips = ImmutableList.of(InetAddress.getByName(ipAddress));
      String interfaceName = String.format("eth%s", index++);
      NetworkInterfaceInfo localInterface = NetworkInterfaceInfo.create(interfaceName, ips);
      networkInterfaceInfoList.add(localInterface);
    }

    return Optional.of(networkInterfaceInfoList);
  }
}
