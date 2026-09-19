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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.common.collect.ImmutableList;

/**
 * Standard open-source host key descriptors for MobileHarness host search.
 *
 * <p>Contains Group 1 (Universal Common Host Keys). Each key declares its {@code GetLabInfo} lab
 * sources, which give both the mask contribution and the raw extraction. {@code device_count} has
 * no source because it is synthesized from the device list. Display transforms, such as bucketing a
 * lab status into a connectivity label, are a key-level combiner concern handled at index time.
 *
 * <p>Display names are the host-search labels (spec §0 host table). When a host attribute is also
 * shown in device search, {@link DeviceKeys} projects it with a device-search name; the projection
 * reuses the id and lab sources declared here.
 */
public final class HostKeys {

  /**
   * Namespace markers of host-search key ids. They exist for the registry's id parsing and for the
   * long-tail minting helper. A built-in key's id is spelled once, in its descriptor below or in a
   * deployment catalog, and is referenced everywhere else through that descriptor constant or
   * {@link HostKeyRegistry#getKey}; no caller combines a prefix with a name to refer to a built-in
   * key.
   */
  public static final String PREFIX_HOST_FIELD = "host_field::";

  public static final String PREFIX_HOST_PROPERTY = "host_property::";

  public static final HostKeyDescriptor HOST_NAME =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "host_name")
          .setLabInfoSource(
              LabInfoSource.field(
                  "lab_locator.host_name", li -> nonEmpty(li.getLabLocator().getHostName())))
          .setDisplay(KeyDisplay.of("Host Name"))
          .setAliases("hostname", "host", "name")
          .build();

  public static final HostKeyDescriptor HOST_IP =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "host_ip")
          .setLabInfoSource(
              LabInfoSource.field("lab_locator.ip", li -> nonEmpty(li.getLabLocator().getIp())))
          .setDisplay(KeyDisplay.of("Host IP"))
          .setAliases("ip")
          .build();

  public static final HostKeyDescriptor CONNECTIVITY =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "connectivity")
          .setLabInfoSource(
              LabInfoSource.field("lab_status", li -> ImmutableList.of(li.getLabStatus().name())))
          .setDisplay(KeyDisplay.of("Lab Server Connectivity"))
          .setAliases("connectivity")
          .build();

  public static final HostKeyDescriptor HOST_OS =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_PROPERTY + "host_os")
          .setLabInfoSource(LabInfoSource.hostProperty("host_os"))
          .setDisplay(KeyDisplay.of("Host OS"))
          .setAliases("os")
          .setIsHostProperty(true)
          .build();

  public static final HostKeyDescriptor LAB_SERVER_VERSION =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "lab_server_version")
          .setLabInfoSource(LabInfoSource.hostProperty("host_version"))
          .setDisplay(KeyDisplay.of("Lab Server Version"))
          .setAliases("version")
          .build();

  /** Synthesized from the device list, so it contributes no mask. Host search only. */
  public static final HostKeyDescriptor DEVICE_COUNT =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "device_count")
          .setDisplay(KeyDisplay.of("Device Count"))
          .setAliases("devices")
          .build();

  /** Standard Group 1 common host keys (present in every deployment). */
  public static final ImmutableList<HostKeyDescriptor> COMMON_HOST_KEYS =
      ImmutableList.of(HOST_NAME, HOST_IP, CONNECTIVITY, HOST_OS, LAB_SERVER_VERSION, DEVICE_COUNT);

  /**
   * Builds a long-tail host-property key for {@code key} discovered from data. The registry mints
   * these on demand; they carry no curated display (raw name) and are flagged long-tail.
   */
  static HostKeyDescriptor hostPropertyKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("Host property key cannot be empty");
    }
    return HostKeyDescriptor.builder()
        .setId(PREFIX_HOST_PROPERTY + key)
        .setLabInfoSource(LabInfoSource.hostProperty(key))
        .setDisplay(KeyDisplay.of(key))
        .setIsHostProperty(true)
        .setIsLongTail(true)
        .build();
  }

  private static ImmutableList<String> nonEmpty(String value) {
    return value.isEmpty() ? ImmutableList.of() : ImmutableList.of(value);
  }

  /**
   * The canonical id for a host property name discovered from data or typed by a user. This is the
   * only sanctioned way to form a {@code host_property::} id outside a catalog; pass the result to
   * {@link HostKeyRegistry#getKey} or {@link DeviceKeyRegistry#getKey} rather than treating it as a
   * key. A built-in key is referenced by its descriptor constant, never rebuilt from its name.
   */
  public static String hostPropertyKeyId(String propertyKey) {
    return PREFIX_HOST_PROPERTY + propertyKey;
  }

  private HostKeys() {}
}
