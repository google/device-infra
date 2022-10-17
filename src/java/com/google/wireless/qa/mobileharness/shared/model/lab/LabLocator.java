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

package com.google.wireless.qa.mobileharness.shared.model.lab;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.proto.Lab;
import com.google.wireless.qa.mobileharness.shared.proto.Lab.LabPort;
import com.google.wireless.qa.mobileharness.shared.proto.Lab.LabProxy;
import com.google.wireless.qa.mobileharness.shared.proto.Lab.PortType;
import com.google.wireless.qa.mobileharness.shared.proto.Lab.ProxyType;
import java.util.EnumMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** For locating a Mobile Harness lab server. */
public class LabLocator {

  /** Local lab locator, used by local devices. */
  @Deprecated public static final LabLocator LOCALHOST = new LabLocator("127.0.0.1", "localhost");

  private final String ip;
  @Nullable private final String hostName;
  private final EnumMap<PortType, Integer> ports = new EnumMap<>(PortType.class);
  private final EnumMap<ProxyType, String> proxies = new EnumMap<>(ProxyType.class);

  public LabLocator(String ip, @Nullable String hostName) {
    this.ip = checkNotNull(ip);
    this.hostName = hostName;
  }

  /** Creates the lab locator from the proto. */
  public LabLocator(Lab.LabLocator proto) {
    this(proto.getIp(), proto.getHostName());
    for (LabPort port : proto.getPortList()) {
      setPort(port.getType(), port.getNum());
    }
    for (LabProxy proxy : proto.getProxyList()) {
      setProxy(proxy.getType(), proxy.getSpec());
    }
  }

  public LabLocator(com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator proto) {
    this(proto.getIp(), proto.getHostName());
    proto
        .getPortList()
        .forEach(port -> setPort(PortType.forNumber(port.getType().getNumber()), port.getNum()));
  }

  public LabLocator(com.google.devtools.mobileharness.api.model.lab.LabLocator newLabLocator) {
    this(newLabLocator.toProto());
  }

  public String getIp() {
    return ip;
  }

  @Nullable
  public String getHostName() {
    return hostName;
  }

  /**
   * Sets the port number for the given type.
   *
   * @return the previous port number of the given type, or <tt>null</tt> if there was no such port
   */
  @Nullable
  public Integer setPort(PortType type, int port) {
    return ports.put(type, port);
  }

  /** Sets all the given ports. */
  @CanIgnoreReturnValue
  public LabLocator setAllPorts(EnumMap<PortType, Integer> ports) {
    this.ports.putAll(ports);
    return this;
  }

  /**
   * Clears the port number for the given type.
   *
   * @return the previous port number of the given type, or <tt>null</tt> if there was no such port
   */
  @Nullable
  public Integer clearPort(PortType type) {
    return ports.remove(type);
  }

  /** Gets the port number of the given type. If not exist, returns <tt>null</tt>. */
  @Nullable
  public Integer getPort(PortType type) {
    return ports.get(type);
  }

  /**
   * @see #getPort
   */
  public int getPortNonNull(PortType type) {
    return checkNotNull(getPort(type));
  }

  /** Gets all the ports of this lab. */
  public EnumMap<PortType, Integer> getAllPorts() {
    return new EnumMap<>(ports);
  }

  /**
   * Sets the proxy spec for the given proxy type.
   *
   * @return the previous proxy spec of the given type, or <tt>null</tt> if there was no such port
   */
  @Nullable
  public String setProxy(ProxyType type, String spec) {
    return proxies.put(type, spec);
  }

  /** Sets all the given proxies. */
  @CanIgnoreReturnValue
  public LabLocator setAllProxies(EnumMap<ProxyType, String> proxies) {
    this.proxies.putAll(proxies);
    return this;
  }

  /** Gets the proxy spec of the given type. If not exist, returns <tt>null</tt>. */
  @Nullable
  public String getProxy(ProxyType type) {
    return proxies.get(type);
  }

  /** Gets all the proxies of this lab. */
  public EnumMap<ProxyType, String> getAllProxies() {
    return new EnumMap<>(proxies);
  }

  @Override
  public String toString() {
    if (hostName == null) {
      return ip;
    } else {
      return ip + "/" + hostName;
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof LabLocator) {
      LabLocator other = (LabLocator) obj;
      return ip.equals(other.ip)
          && hostName.equals(other.hostName)
          && ports.equals(other.ports)
          && proxies.equals(other.proxies);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(ip, hostName, ports, proxies);
  }

  public Lab.LabLocator toProto() {
    Lab.LabLocator.Builder proto = Lab.LabLocator.newBuilder().setIp(ip).setHostName(hostName);
    for (Entry<PortType, Integer> entry : ports.entrySet()) {
      proto.addPort(LabPort.newBuilder().setType(entry.getKey()).setNum(entry.getValue()).build());
    }
    for (Entry<ProxyType, String> entry : proxies.entrySet()) {
      proto.addProxy(
          LabProxy.newBuilder().setType(entry.getKey()).setSpec(entry.getValue()).build());
    }
    return proto.build();
  }

  public com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator toNewProto() {
    return com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator.newBuilder()
        .setIp(ip)
        .setHostName(hostName)
        .addAllPort(
            ports.entrySet().stream()
                .map(
                    e ->
                        com.google.devtools.mobileharness.api.model.proto.Lab.LabPort.newBuilder()
                            .setType(
                                com.google.devtools.mobileharness.api.model.proto.Lab.PortType
                                    .forNumber(e.getKey().getNumber()))
                            .setNum(e.getValue())
                            .build())
                .collect(Collectors.toList()))
        .build();
  }

  public com.google.devtools.mobileharness.api.model.lab.LabLocator toNewLabLocator() {
    return com.google.devtools.mobileharness.api.model.lab.LabLocator.of(toNewProto());
  }
}
