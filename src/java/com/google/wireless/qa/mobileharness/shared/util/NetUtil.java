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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.util.network.localhost.LocalHost;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for common network operation. It contains methods for determining what location a
 * particular IP address of a Mobile Harness lab server belongs to.
 */
public class NetUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final NetworkUtil newUtil;

  /** The location type of host. */
  public enum LocationType {
    IN_CHINA,
    NOT_IN_CHINA,
    INDETERMINABLE
  }

  public NetUtil() {
    this(new NetworkUtil());
  }

  @VisibleForTesting
  NetUtil(NetworkUtil networkUtil) {
    this.newUtil = networkUtil;
  }

  /** The network interface info matching an interface with its valid IP addresses. */
  @AutoValue
  public abstract static class NetworkInterfaceInfo {
    public static NetworkInterfaceInfo create(String name, List<InetAddress> ips) {
      return new AutoValue_NetUtil_NetworkInterfaceInfo(name, ImmutableList.copyOf(ips));
    }

    public abstract String name();

    public abstract ImmutableList<InetAddress> ips();
  }

  /**
   * DO NOT USE. This method may return a local IP when there are multiple IPs. Use {@link
   * NetworkUtil#getInetAddresses()} instead.
   */
  @Deprecated
  public String getLocalHostIp() throws MobileHarnessException {
    try {
      // In Mac machine, sometimes InetAddress.getLocalHost().getHostAddress() will return
      // 127.0.0.1, which is not the real IP.
      return LocalHost.getAddress().getHostAddress();
    } catch (UnknownHostException e) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_NETWORK_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Returns host name of local host.
   *
   * @throws MobileHarnessException if the local host name could not be resolved into an address
   */
  public String getLocalHostName() throws MobileHarnessException {
    try {
      return newUtil.getLocalHostName();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_NETWORK_ERROR, e.getMessage(), e);
    }
  }

  public Optional<String> getLocalHostLocation() throws MobileHarnessException {
    return Optional.empty();
  }

  /**
   * Returns a list of network info including interface name and a list of its IP addresses from all
   * network interfaces.
   *
   * @throws MobileHarnessException if failed to find network interface
   */
  public Optional<List<NetworkInterfaceInfo>> getNetworkInterfaceAndAddress()
      throws MobileHarnessException {
    List<NetworkInterfaceInfo> interfaceAndAddresses = null;
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      if (interfaces == null) {
        return Optional.empty();
      }

      while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        // Skip loopback interface.
        if (networkInterface.isLoopback()) {
          continue;
        }

        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        List<InetAddress> addressList = null;
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          // Remove all loopback and link-local address.
          if (isValidIpAddress(address)) {
            if (addressList == null) {
              addressList = new ArrayList<>();
            }
            addressList.add(address);
          }
        }

        // Only keep the interface who has IP address.
        if (addressList != null) {
          if (interfaceAndAddresses == null) {
            interfaceAndAddresses = new ArrayList<>();
          }
          interfaceAndAddresses.add(
              NetworkInterfaceInfo.create(networkInterface.getName(), addressList));
        }
      }

      return Optional.ofNullable(interfaceAndAddresses);
    } catch (SocketException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_INTERFACE_DETECTION_ERROR,
          "Failed to detect server network interface.",
          e);
    }
  }

  /** If lab has only one IP address then return it otherwise return empty. */
  public Optional<String> getUniqueHostIpOrEmpty() {
    try {
      Optional<List<NetworkInterfaceInfo>> interfaces = getNetworkInterfaceAndAddress();
      return getUniqueHostIpOrEmpty(interfaces);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Failed to get network interface information");
      return Optional.empty();
    }
  }

  /** If lab has only one IP address then return it otherwise return empty. */
  public Optional<String> getUniqueHostIpOrEmpty(Optional<List<NetworkInterfaceInfo>> interfaces) {
    if (interfaces.isPresent()) {
      List<InetAddress> ips = new ArrayList<>();
      interfaces.get().forEach(networkInterfaceInfo -> ips.addAll(networkInterfaceInfo.ips()));
      // Could return site local address if it is the only one address in system.
      if (ips.size() == 1) {
        return Optional.of(ips.get(0).getHostAddress());
      }

      // List the non-site-local IP addresses.
      List<InetAddress> corpAddressList =
          ips.stream().filter(address -> !address.isSiteLocalAddress()).collect(toImmutableList());
      // If only one non-site-local address in the list.
      if (corpAddressList != null && corpAddressList.size() == 1) {
        return Optional.of(corpAddressList.get(0).getHostAddress());
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the location type of the local host.
   *
   * @return {@link LocationType}
   * @throws MobileHarnessException if the local host name could not be resolved into an address
   */
  public LocationType getLocalHostLocationType() throws MobileHarnessException {
    String hostName = getLocalHostName();
    return getLocationType(hostName);
  }

  /**
   * Returns the location type of the given host.
   *
   * @return {@link LocationType}
   */
  public static LocationType getLocationType(String hostName) {
    return LocationType.IN_CHINA;
  }

  private static boolean isValidIpAddress(InetAddress address) {
    return !(address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address instanceof Inet6Address);
  }
}
