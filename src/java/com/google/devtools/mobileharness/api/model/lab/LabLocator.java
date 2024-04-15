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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.api.model.lab.in.Ports;
import com.google.devtools.mobileharness.api.model.proto.Lab;
import java.util.Optional;
import javax.annotation.Nullable;

/** For locating a Mobile Harness lab. */
@AutoValue
public abstract class LabLocator {
  /** Local lab locator, used by local devices. */
  public static final LabLocator LOCALHOST = LabLocator.of("127.0.0.1", "localhost");

  public abstract String ip();

  public abstract String hostName();

  public abstract Ports ports();

  public abstract Optional<String> masterDetectedIp();

  /** Creates a locator for a lab. */
  public static LabLocator of(String ip, String hostName) {
    return of(ip, hostName, /* masterDetectedIp= */ null);
  }

  /** Creates a locator for a lab. */
  public static LabLocator of(String ip, String hostName, @Nullable String masterDetectedIp) {
    return new AutoValue_LabLocator(
        ip, hostName, new Ports(), Optional.ofNullable(masterDetectedIp));
  }

  /** Creates the lab locator from the proto. */
  public static LabLocator of(Lab.LabLocator proto) {
    LabLocator labLocator =
        LabLocator.of(
            proto.getIp(),
            proto.getHostName(),
            proto.hasMasterDetectedIp() ? proto.getMasterDetectedIp() : null);
    labLocator.ports().addAll(proto.getPortList());
    return labLocator;
  }

  @Memoized
  @Override
  public String toString() {
    return ip() + "/" + hostName();
  }

  public String toFullString() {
    return String.format(
        "ip=[%s], host_name=[%s], master_detected_ip=%s, ports=%s",
        ip(), hostName(), masterDetectedIp(), ports());
  }

  /** DO NOT memorize this. Lab ports can be added afterwards. */
  public Lab.LabLocator toProto() {
    Lab.LabLocator.Builder result =
        Lab.LabLocator.newBuilder()
            .setIp(ip())
            .setHostName(hostName())
            .addAllPort(ports().toProtos());
    masterDetectedIp().ifPresent(result::setMasterDetectedIp);
    return result.build();
  }

  /** Global unique lab ID. */
  public String getId() {
    return hostName();
  }
}
