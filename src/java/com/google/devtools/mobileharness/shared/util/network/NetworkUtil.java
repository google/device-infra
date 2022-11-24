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

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Utility class for common network operation. */
public class NetworkUtil {
  /**
   * Returns host name of local host.
   *
   * @throws MobileHarnessException if the local host name could not be resolved into an address
   */
  public String getLocalHostName() throws MobileHarnessException {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_HOSTNAME_DETECTION_ERROR, "Failed to detect host name.", e);
    }
  }

  /**
   * Returns the IP addresses from all network interfaces, <b>except</b>:
   *
   * <ul>
   *   <li>Loopback network interface
   *   <li>Disabled network interface
   *   <li>LinkLocal address
   *   <li>Loopback address
   * </ul>
   *
   * <p>Please note IPv6 addresses are included in the result.
   *
   * @throws MobileHarnessException if failed to detect network interfaces or addresses
   */
  public List<InetAddress> getInetAddresses() throws MobileHarnessException {
    return getInetAddresses(null);
  }

  /**
   * Returns the IP addresses from all network interfaces, <b>except</b>:
   *
   * <ul>
   *   <li>Loopback network interface
   *   <li>Disabled network interface
   *   <li>LinkLocal address
   *   <li>Loopback address
   * </ul>
   *
   * <p>Please note IPv6 addresses are included in the result. You can add extra filter to filter
   * the addresses, like: {@code getInetAddresses(addr -> addr instanceof Inet4Address)}.
   *
   * @throws MobileHarnessException if failed to detect network interfaces or addresses
   */
  public List<InetAddress> getInetAddresses(@Nullable Predicate<InetAddress> inetAddressFilter)
      throws MobileHarnessException {
    List<InetAddress> result = new ArrayList<>();
    Enumeration<NetworkInterface> networkInterfaces;
    try {
      networkInterfaces = NetworkInterface.getNetworkInterfaces();

      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        if (!networkInterface.isUp() || networkInterface.isLoopback()) {
          continue;
        }
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress addr = addresses.nextElement();
          if (addr.isLinkLocalAddress() || addr.isLoopbackAddress()) {
            continue;
          }
          if (inetAddressFilter != null && !inetAddressFilter.test(addr)) {
            continue;
          }
          result.add(addr);
        }
      }
    } catch (SocketException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_INTERFACE_DETECTION_ERROR,
          "Failed to detect server network interface.",
          e);
    }
    return result;
  }
}
