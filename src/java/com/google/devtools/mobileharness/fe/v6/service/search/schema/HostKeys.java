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
 * Standard open-source host key descriptors for MobileHarness search.
 *
 * <p>Contains Group 1 (Universal Common Host Keys).
 */
public final class HostKeys {

  public static final String PREFIX_HOST_FIELD = "host_field::";
  public static final String PREFIX_HOST_PROPERTY = "host_property::";

  // Group 1: Universal Common Host Keys (LabInfo Typed Fields)
  public static final HostKeyDescriptor HOST_NAME =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "host_name")
          .setSource(HostSource.labInfoField("lab_locator.host_name"))
          .setDeviceSearchName("Host Name")
          .setHostSearchName("Host Name")
          .build();

  public static final HostKeyDescriptor HOST_IP =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "host_ip")
          .setSource(HostSource.labInfoField("lab_locator.ip"))
          .setDeviceSearchName("Host IP")
          .setHostSearchName("Host IP")
          .build();

  public static final HostKeyDescriptor CONNECTIVITY =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "connectivity")
          .setSource(
              HostSource.computed(
                  ImmutableList.of(HostSource.labInfoField("lab_status")),
                  "HostConnectivityStatuses"))
          .setDeviceSearchName("Host Lab Server Connectivity")
          .setHostSearchName("Lab Server Connectivity")
          .build();

  // Group 1: Built-in Host Property (host_os)
  public static final HostKeyDescriptor HOST_OS =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_PROPERTY + "host_os")
          .setSource(HostSource.hostProperty("host_os"))
          .setDeviceSearchName("Host OS")
          .setHostSearchName("Host OS")
          .build();

  // Group 1: Computed Host Keys with Fallbacks
  public static final HostKeyDescriptor LAB_SERVER_VERSION =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "lab_server_version")
          .setSource(
              HostSource.computed(
                  ImmutableList.of(HostSource.hostProperty("host_version")),
                  "HostVersionUtil version fallback"))
          .setDeviceSearchName("Host Lab Server Version")
          .setHostSearchName("Lab Server Version")
          .build();

  public static final HostKeyDescriptor DEVICE_COUNT =
      HostKeyDescriptor.builder()
          .setId(PREFIX_HOST_FIELD + "device_count")
          .setSource(HostSource.computed(ImmutableList.of(), "Host device count aggregation"))
          .setDeviceSearchName("Host Device Count")
          .setHostSearchName("Device Count")
          .build();

  /** Standard Group 1 common host keys (present in every deployment). */
  public static final ImmutableList<HostKeyDescriptor> COMMON_HOST_KEYS =
      ImmutableList.of(HOST_NAME, HOST_IP, CONNECTIVITY, HOST_OS, LAB_SERVER_VERSION, DEVICE_COUNT);

  private HostKeys() {}
}
