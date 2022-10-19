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

import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.EnumMap;
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

  public LabLocator(String ip, @Nullable String hostName) {
    this.ip = checkNotNull(ip);
    this.hostName = hostName;
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
  @CanIgnoreReturnValue
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

  /** See {@link #getPort}. */
  public int getPortNonNull(PortType type) {
    return checkNotNull(getPort(type));
  }

  /** Gets all the ports of this lab. */
  public EnumMap<PortType, Integer> getAllPorts() {
    return new EnumMap<>(ports);
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
      return ip.equals(other.ip) && hostName.equals(other.hostName) && ports.equals(other.ports);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(ip, hostName, ports);
  }

  public com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator toNewProto() {
    return com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator.newBuilder()
        .setIp(ip)
        .setHostName(hostName)
        .addAllPort(
            ports.entrySet().stream()
                .map(e -> LabPort.newBuilder().setType(e.getKey()).setNum(e.getValue()).build())
                .collect(Collectors.toList()))
        .build();
  }

  public com.google.devtools.mobileharness.api.model.lab.LabLocator toNewLabLocator() {
    return com.google.devtools.mobileharness.api.model.lab.LabLocator.of(toNewProto());
  }
}
